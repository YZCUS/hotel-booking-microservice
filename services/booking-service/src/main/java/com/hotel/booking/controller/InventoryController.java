package com.hotel.booking.controller;

import com.hotel.booking.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
    
    @GetMapping("/check-availability")
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
    public ResponseEntity<Map<UUID, Integer>> getAvailableRoomsForTodayBatch(@RequestBody List<UUID> roomTypeIds) {
        log.info("Checking today's availability for {} room types", roomTypeIds.size());
        Map<UUID, Integer> availabilities = inventoryService.getAvailableRoomsForTodayBatch(roomTypeIds);
        return ResponseEntity.ok(availabilities);
    }
    
    @PostMapping("/initialize")
    public ResponseEntity<Void> initializeInventory(
            @RequestParam UUID roomTypeId,
            @RequestParam int totalRooms,
            @RequestParam(defaultValue = "365") int daysAhead) {
        
        log.info("Initializing inventory for roomType {} with {} rooms for {} days", 
            roomTypeId, totalRooms, daysAhead);
        
        inventoryService.initializeInventory(roomTypeId, totalRooms, daysAhead);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{roomTypeId}")
    public ResponseEntity<Void> deleteInventory(@PathVariable UUID roomTypeId) {
        log.info("Deleting inventory for roomType: {}", roomTypeId);
        inventoryService.deleteInventory(roomTypeId);
        return ResponseEntity.ok().build();
    }
}