package com.hotel.booking.controller;

import com.hotel.booking.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {
    
    private final InventoryService inventoryService;

    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getAvailability(
            @RequestParam UUID roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(inventoryService.getAvailableRooms(roomTypeId, date));
    }
    
    @GetMapping("/check-availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkAvailability(
            @RequestParam UUID roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate,
            @RequestParam(defaultValue = "1") int rooms) {
        
        log.info("Checking availability for {} rooms of type {} from {} to {}", 
            rooms, roomTypeId, checkInDate, checkOutDate);
        
        boolean available = inventoryService.checkAvailability(roomTypeId, checkInDate, checkOutDate, rooms);
        return ResponseEntity.ok(available);
    }

    @PostMapping("/availabilities-for-today")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<UUID, Integer>> getAvailableRoomsForTodayBatch(@RequestBody List<UUID> roomTypeIds) {
        log.info("Checking today's availability for {} room types", roomTypeIds.size());
        Map<UUID, Integer> availabilities = inventoryService.getAvailableRoomsForTodayBatch(roomTypeIds);
        return ResponseEntity.ok(availabilities);
    }
    
    @PostMapping("/initialize")
    @PreAuthorize("hasRole('INTERNAL_HOTEL')")
    public ResponseEntity<Void> initializeInventory(
            @RequestParam UUID roomTypeId,
            @RequestParam int totalRooms,
            @RequestParam(defaultValue = "395") int daysAhead) {
        
        log.info("Initializing inventory for roomType {} with {} rooms for {} days", 
            roomTypeId, totalRooms, daysAhead);
        
        inventoryService.initializeInventory(roomTypeId, totalRooms, daysAhead);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{roomTypeId}/capacity")
    @PreAuthorize("hasRole('INTERNAL_HOTEL')")
    public ResponseEntity<Void> setDesiredCapacity(
            @PathVariable UUID roomTypeId,
            @RequestParam int totalRooms,
            @RequestParam(defaultValue = "395") int daysAhead) {
        inventoryService.setDesiredCapacity(roomTypeId, totalRooms, daysAhead);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/{roomTypeId}")
    @PreAuthorize("hasRole('INTERNAL_HOTEL')")
    public ResponseEntity<Void> deleteInventory(@PathVariable UUID roomTypeId) {
        log.info("Deleting inventory for roomType: {}", roomTypeId);
        inventoryService.deleteInventory(roomTypeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deactivate")
    @PreAuthorize("hasRole('INTERNAL_HOTEL')")
    public ResponseEntity<Void> deleteInventories(@RequestBody List<UUID> roomTypeIds) {
        inventoryService.deleteInventories(roomTypeIds);
        return ResponseEntity.noContent().build();
    }
}
