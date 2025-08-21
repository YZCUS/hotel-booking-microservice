package com.hotel.booking.service;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.exception.InventoryNotFoundException;
import com.hotel.booking.repository.RoomInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {
    
    private final RoomInventoryRepository inventoryRepository;
    
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = "room-availability", allEntries = true, beforeInvocation = false)
    public boolean reserveInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.info("Reserving {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        // Use SELECT ... FOR UPDATE for true atomic locking
        List<RoomInventory> inventories = inventoryRepository
            .findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1));
        
        if (inventories.isEmpty()) {
            log.warn("No inventory found for roomType {} from {} to {}", roomTypeId, checkIn, checkOut);
            return false;
        }
        
        // Check if we have inventory for all required dates
        List<LocalDate> requiredDates = getDateRange(checkIn, checkOut);
        if (inventories.size() != requiredDates.size()) {
            log.warn("Incomplete inventory found for roomType {}. Required {} dates, found {} records", 
                roomTypeId, requiredDates.size(), inventories.size());
            return false;
        }
        
        // Check availability atomically
        for (RoomInventory inventory : inventories) {
            if (inventory.getAvailableRooms() < rooms) {
                log.warn("Insufficient inventory on date: {}. Available: {}, Required: {}", 
                    inventory.getDate(), inventory.getAvailableRooms(), rooms);
                return false;
            }
        }
        
        // If all dates have sufficient inventory, reserve them atomically
        try {
            for (RoomInventory inventory : inventories) {
                inventory.setAvailableRooms(inventory.getAvailableRooms() - rooms);
                inventoryRepository.save(inventory);
                log.debug("Reserved {} rooms for date: {}. Remaining: {}", 
                    rooms, inventory.getDate(), inventory.getAvailableRooms());
            }
            
            log.info("Successfully reserved {} rooms for roomType {} from {} to {}", 
                rooms, roomTypeId, checkIn, checkOut);
            return true;
            
        } catch (OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure when reserving inventory for roomType: {}", roomTypeId);
            throw e;  // Let @Retryable handle the retry
        }
    }
    
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = "room-availability", allEntries = true, beforeInvocation = false)
    public void releaseInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.info("Releasing {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        List<LocalDate> dates = getDateRange(checkIn, checkOut);
        
        try {
            // Batch query to avoid N+1 problem
            List<RoomInventory> inventories = inventoryRepository
                .findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1));
            
            if (inventories.size() != dates.size()) {
                throw new InventoryNotFoundException(
                    "Incomplete inventory found for roomType " + roomTypeId + 
                    ". Expected " + dates.size() + " dates, found " + inventories.size());
            }
            
            // Batch update inventory
            for (RoomInventory inventory : inventories) {
                inventory.setAvailableRooms(inventory.getAvailableRooms() + rooms);
                log.debug("Released {} rooms for date: {}. Available: {}", 
                    rooms, inventory.getDate(), inventory.getAvailableRooms());
            }
            
            // Save all updates at once
            inventoryRepository.saveAll(inventories);
            
            log.info("Successfully released {} rooms for roomType {} from {} to {}", 
                rooms, roomTypeId, checkIn, checkOut);
                
        } catch (OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure when releasing inventory for roomType: {}", roomTypeId);
            throw e;  // Let @Retryable handle the retry
        }
    }
    
    @Cacheable(value = "room-availability", 
               key = "#roomTypeId + '_' + #checkIn + '_' + #checkOut + '_' + #rooms",
               unless = "#result == false")
    public boolean checkAvailability(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.debug("Checking availability for {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        Integer minAvailable = inventoryRepository
            .findMinAvailableRoomsInRange(roomTypeId, checkIn, checkOut.minusDays(1))
            .orElse(0);
        
        boolean available = minAvailable >= rooms;
        log.debug("Availability check result: {} (min available: {})", available, minAvailable);
        
        return available;
    }
    
    public void initializeInventory(UUID roomTypeId, int totalRooms, int daysAhead) {
        log.info("Initializing inventory for roomType {} with {} rooms for {} days", 
            roomTypeId, totalRooms, daysAhead);
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);
        
        // Batch query existing inventory to avoid duplicate inserts
        List<RoomInventory> existingInventories = inventoryRepository
            .findByRoomTypeIdAndDateBetween(roomTypeId, startDate, endDate);
        
        // Create a set of existing dates for fast lookup
        List<LocalDate> existingDates = existingInventories.stream()
            .map(RoomInventory::getDate)
            .toList();
        
        // Batch create non-existing inventory records
        List<RoomInventory> newInventories = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) {
                RoomInventory inventory = RoomInventory.builder()
                    .roomTypeId(roomTypeId)
                    .date(date)
                    .availableRooms(totalRooms)
                    .build();
                newInventories.add(inventory);
            }
        }
        
        // Batch save all new inventory records
        if (!newInventories.isEmpty()) {
            inventoryRepository.saveAll(newInventories);
            log.info("Batch inserted {} new inventory records for roomType {}", 
                newInventories.size(), roomTypeId);
        }
        
        log.info("Successfully initialized inventory for roomType {} for {} days", roomTypeId, daysAhead);
    }
    
    public void deleteInventory(UUID roomTypeId) {
        log.info("Deleting all inventory for roomType: {}", roomTypeId);
        inventoryRepository.deleteByRoomTypeId(roomTypeId);
    }
    
    private List<LocalDate> getDateRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        
        // Include all nights from check-in to check-out (exclusive of check-out date)
        while (!current.isEqual(end)) {
            dates.add(current);
            current = current.plusDays(1);
        }
        
        return dates;
    }
}