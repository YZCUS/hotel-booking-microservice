package com.hotel.booking.service;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.exception.InventoryNotFoundException;
import com.hotel.booking.repository.RoomInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
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
    public boolean reserveInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.info("Reserving {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        List<LocalDate> dates = getDateRange(checkIn, checkOut);
        List<RoomInventory> inventoriesToUpdate = new ArrayList<>();
        
        // First, check availability for all dates
        for (LocalDate date : dates) {
            RoomInventory inventory = inventoryRepository
                .findByRoomTypeIdAndDate(roomTypeId, date)
                .orElseThrow(() -> new InventoryNotFoundException(
                    "Inventory not found for roomType " + roomTypeId + " on date: " + date));
            
            if (inventory.getAvailableRooms() < rooms) {
                log.warn("Insufficient inventory on date: {}. Available: {}, Required: {}", 
                    date, inventory.getAvailableRooms(), rooms);
                return false;
            }
            
            inventoriesToUpdate.add(inventory);
        }
        
        // If all dates have sufficient inventory, reserve them
        try {
            for (RoomInventory inventory : inventoriesToUpdate) {
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
    public void releaseInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        log.info("Releasing {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        List<LocalDate> dates = getDateRange(checkIn, checkOut);
        
        try {
            for (LocalDate date : dates) {
                RoomInventory inventory = inventoryRepository
                    .findByRoomTypeIdAndDate(roomTypeId, date)
                    .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for roomType " + roomTypeId + " on date: " + date));
                
                // Increase available inventory
                inventory.setAvailableRooms(inventory.getAvailableRooms() + rooms);
                inventoryRepository.save(inventory);
                
                log.debug("Released {} rooms for date: {}. Available: {}", 
                    rooms, date, inventory.getAvailableRooms());
            }
            
            log.info("Successfully released {} rooms for roomType {} from {} to {}", 
                rooms, roomTypeId, checkIn, checkOut);
                
        } catch (OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure when releasing inventory for roomType: {}", roomTypeId);
            throw e;  // Let @Retryable handle the retry
        }
    }
    
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
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // Check if inventory already exists for this date
            if (!inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, date).isPresent()) {
                RoomInventory inventory = RoomInventory.builder()
                    .roomTypeId(roomTypeId)
                    .date(date)
                    .availableRooms(totalRooms)
                    .build();
                
                inventoryRepository.save(inventory);
                log.debug("Initialized inventory for roomType {} on date {} with {} rooms", 
                    roomTypeId, date, totalRooms);
            }
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