package com.hotel.booking.service;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.exception.BookingConflictException;
import com.hotel.booking.exception.InventoryNotFoundException;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.RoomInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    public static final int BOOKING_HORIZON_DAYS = 395;
    
    private final RoomInventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final CacheManager cacheManager;
    
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean reserveInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        validateInventoryRequest(checkIn, checkOut, rooms);
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
            clearAvailabilityCacheAfterCommit();
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
    public void releaseInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        validateInventoryRequest(checkIn, checkOut, rooms);
        log.info("Releasing {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        List<LocalDate> dates = getDateRange(checkIn, checkOut);
        
        try {
            // Batch query to avoid N+1 problem
            List<RoomInventory> inventories = inventoryRepository
                .findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1));
            
            if (inventories.size() != dates.size()) {
                throw new InventoryNotFoundException(
                    "Incomplete inventory found for roomType " + roomTypeId + 
                    ". Expected " + dates.size() + " dates, found " + inventories.size());
            }
            
            // Batch update inventory
            for (RoomInventory inventory : inventories) {
                inventory.setAvailableRooms(Math.min(
                        inventory.getTotalRooms(), inventory.getAvailableRooms() + rooms));
                log.debug("Released {} rooms for date: {}. Available: {}", 
                    rooms, inventory.getDate(), inventory.getAvailableRooms());
            }
            
            // Save all updates at once
            inventoryRepository.saveAll(inventories);
            
            log.info("Successfully released {} rooms for roomType {} from {} to {}", 
                rooms, roomTypeId, checkIn, checkOut);
            clearAvailabilityCacheAfterCommit();
                
        } catch (OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure when releasing inventory for roomType: {}", roomTypeId);
            throw e;  // Let @Retryable handle the retry
        }
    }
    
    @Cacheable(value = "room-availability", 
               key = "#roomTypeId + '_' + #checkIn + '_' + #checkOut + '_' + #rooms",
               unless = "#result == false")
    public boolean checkAvailability(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        validateInventoryRequest(checkIn, checkOut, rooms);
        log.debug("Checking availability for {} rooms for roomType {} from {} to {}", 
            rooms, roomTypeId, checkIn, checkOut);
        
        List<LocalDate> requiredDates = getDateRange(checkIn, checkOut);
        List<RoomInventory> inventories = inventoryRepository
                .findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1));

        if (inventories.size() != requiredDates.size()) {
            log.warn("Incomplete availability data for roomType {}. Required {} dates, found {}",
                    roomTypeId, requiredDates.size(), inventories.size());
            return false;
        }

        Set<LocalDate> actualDates = inventories.stream()
                .map(RoomInventory::getDate)
                .collect(Collectors.toSet());
        if (!actualDates.containsAll(requiredDates)) {
            log.warn("Availability rows do not cover every requested date for roomType {}", roomTypeId);
            return false;
        }

        int minAvailable = inventories.stream()
                .mapToInt(RoomInventory::getAvailableRooms)
                .min()
                .orElse(0);
        boolean available = minAvailable >= rooms;
        log.debug("Availability check result: {} (min available: {})", available, minAvailable);
        
        return available;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> getAvailableRoomsForTodayBatch(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        List<UUID> distinctRoomTypeIds = roomTypeIds.stream().distinct().toList();
        List<RoomInventory> inventories = inventoryRepository.findByRoomTypeIdInAndDate(distinctRoomTypeIds, today);
        Map<UUID, Integer> availability = inventories.stream()
                .collect(Collectors.toMap(RoomInventory::getRoomTypeId, RoomInventory::getAvailableRooms));

        List<UUID> missing = distinctRoomTypeIds.stream()
                .filter(id -> !availability.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new InventoryNotFoundException(
                    "Inventory is missing for room types " + missing + " on " + today);
        }
        return availability;
    }

    @Transactional(readOnly = true)
    public int getAvailableRooms(UUID roomTypeId, LocalDate date) {
        return inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, date)
                .map(RoomInventory::getAvailableRooms)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory is missing for room type " + roomTypeId + " on " + date));
    }
    
    public void initializeInventory(UUID roomTypeId, int totalRooms, int daysAhead) {
        if (totalRooms < 0) {
            throw new IllegalArgumentException("Total rooms cannot be negative");
        }
        int effectiveDaysAhead = effectiveHorizon(daysAhead);
        log.info("Initializing inventory for roomType {} with {} rooms for {} days", 
            roomTypeId, totalRooms, effectiveDaysAhead);
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(effectiveDaysAhead);
        
        // Batch query existing inventory to avoid duplicate inserts
        List<RoomInventory> existingInventories = inventoryRepository
            .findByRoomTypeIdAndDateBetween(roomTypeId, startDate, endDate);
        
        // Create a set of existing dates for fast lookup
        java.util.Set<LocalDate> existingDates = existingInventories.stream()
            .map(RoomInventory::getDate)
            .collect(Collectors.toSet());
        
        // Batch create non-existing inventory records
        List<RoomInventory> newInventories = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) {
                RoomInventory inventory = RoomInventory.builder()
                    .roomTypeId(roomTypeId)
                    .date(date)
                    .totalRooms(totalRooms)
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
        
        log.info("Successfully initialized inventory for roomType {} for {} days",
                roomTypeId, effectiveDaysAhead);
        clearAvailabilityCacheAfterCommit();
    }

    public void setDesiredCapacity(UUID roomTypeId, int desiredCapacity, int daysAhead) {
        if (desiredCapacity < 0) {
            throw new IllegalArgumentException("Desired capacity cannot be negative");
        }
        int effectiveDaysAhead = effectiveHorizon(daysAhead);

        LocalDate today = LocalDate.now();
        LocalDate horizonEnd = today.plusDays(effectiveDaysAhead);
        List<RoomInventory> existing = inventoryRepository
                .findFutureByRoomTypeIdForUpdate(roomTypeId, today);

        for (RoomInventory inventory : existing) {
            int soldRooms = inventory.getTotalRooms() - inventory.getAvailableRooms();
            if (desiredCapacity < soldRooms) {
                throw new BookingConflictException(
                        "Cannot reduce room capacity below " + soldRooms
                                + " already sold rooms on " + inventory.getDate());
            }
        }

        Map<LocalDate, RoomInventory> byDate = existing.stream()
                .collect(Collectors.toMap(RoomInventory::getDate, inventory -> inventory));
        List<RoomInventory> toSave = new ArrayList<>(existing.size() + effectiveDaysAhead + 1);
        for (RoomInventory inventory : existing) {
            int soldRooms = inventory.getTotalRooms() - inventory.getAvailableRooms();
            inventory.setTotalRooms(desiredCapacity);
            inventory.setAvailableRooms(desiredCapacity - soldRooms);
            toSave.add(inventory);
        }

        for (LocalDate date = today; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            if (!byDate.containsKey(date)) {
                toSave.add(RoomInventory.builder()
                        .roomTypeId(roomTypeId)
                        .date(date)
                        .totalRooms(desiredCapacity)
                        .availableRooms(desiredCapacity)
                        .build());
            }
        }

        inventoryRepository.saveAll(toSave);
        clearAvailabilityCacheAfterCommit();
    }
    
    public void deleteInventory(UUID roomTypeId) {
        inventoryRepository.findFutureByRoomTypeIdForUpdate(roomTypeId, LocalDate.now());
        if (bookingRepository.existsActiveBookingForRoomType(roomTypeId)) {
            throw new BookingConflictException(
                    "Cannot remove room inventory while confirmed or checked-in bookings exist");
        }
        inventoryRepository.deleteByRoomTypeId(roomTypeId);
        clearAvailabilityCacheAfterCommit();
    }

    public void deleteInventories(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return;
        }
        List<UUID> distinctIds = roomTypeIds.stream().distinct().toList();
        distinctIds.stream().sorted().forEach(id ->
                inventoryRepository.findFutureByRoomTypeIdForUpdate(id, LocalDate.now()));
        if (bookingRepository.existsActiveBookingForAnyRoomType(distinctIds)) {
            throw new BookingConflictException(
                    "Cannot remove room inventory while confirmed or checked-in bookings exist");
        }
        log.info("Deleting inventory for {} room types", distinctIds.size());
        inventoryRepository.deleteByRoomTypeIdIn(distinctIds);
        clearAvailabilityCacheAfterCommit();
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

    private int effectiveHorizon(int requestedDaysAhead) {
        if (requestedDaysAhead < 0 || requestedDaysAhead > 730) {
            throw new IllegalArgumentException("Inventory horizon must be between 0 and 730 days");
        }
        return Math.max(requestedDaysAhead, BOOKING_HORIZON_DAYS);
    }

    private void validateInventoryRequest(LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
        if (rooms < 1) {
            throw new IllegalArgumentException("Rooms must be at least 1");
        }
    }

    private void clearAvailabilityCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearAvailabilityCache();
                }
            });
            return;
        }
        clearAvailabilityCache();
    }

    private void clearAvailabilityCache() {
        Cache cache = cacheManager.getCache("room-availability");
        if (cache == null) {
            return;
        }

        cache.clear();
    }
}
