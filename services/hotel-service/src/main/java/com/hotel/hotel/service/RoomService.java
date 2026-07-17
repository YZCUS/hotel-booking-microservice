package com.hotel.hotel.service;

import com.hotel.hotel.dto.RoomTypeRequest;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.exception.RoomTypeNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomService {
    
    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final InventoryService inventoryService;
    
    public List<RoomTypeResponse> getRoomsByHotel(UUID hotelId) {
        log.info("Getting rooms for hotel: {}", hotelId);
        
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException("Hotel not found with id: " + hotelId);
        }
        
        List<RoomType> rooms = roomTypeRepository.findByHotelId(hotelId);
        return rooms.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    public RoomTypeResponse getRoomTypeById(UUID roomTypeId) {
        log.info("Getting room type by id: {}", roomTypeId);
        
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found with id: " + roomTypeId));
        
        return mapToResponse(roomType);
    }

    public RoomTypeResponse getRoomTypeCatalog(UUID roomTypeId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException(
                        "Room type not found with id: " + roomTypeId));
        return RoomTypeResponse.builder()
                .id(roomType.getId())
                .hotelId(roomType.getHotel().getId())
                .hotelName(roomType.getHotel().getName())
                .name(roomType.getName())
                .description(roomType.getDescription())
                .capacity(roomType.getCapacity())
                .pricePerNight(roomType.getPricePerNight())
                .totalInventory(roomType.getTotalInventory())
                .createdAt(roomType.getCreatedAt())
                .build();
    }
    
    public List<RoomTypeResponse> getRoomsByHotelAndCapacity(UUID hotelId, Integer minCapacity) {
        log.info("Getting rooms for hotel: {} with minimum capacity: {}", hotelId, minCapacity);
        
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException("Hotel not found with id: " + hotelId);
        }
        
        List<RoomType> rooms = roomTypeRepository.findByHotelIdAndCapacityGreaterThanEqual(hotelId, minCapacity);
        return rooms.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    public List<RoomTypeResponse> getRoomsByHotelAndPriceRange(UUID hotelId, BigDecimal minPrice, BigDecimal maxPrice) {
        log.info("Getting rooms for hotel: {} with price range: {} - {}", hotelId, minPrice, maxPrice);
        
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException("Hotel not found with id: " + hotelId);
        }
        
        List<RoomType> rooms = roomTypeRepository.findByHotelIdAndPriceRange(hotelId, minPrice, maxPrice);
        return rooms.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Map<UUID, Integer> getRoomAvailabilities(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return Map.of();
        }
        try {
            // Availability enriches catalog reads; an inventory outage must not hide the catalog.
            Map<UUID, Integer> availability = inventoryService.getAvailableRoomsForTodayBatch(roomTypeIds).block();
            return availability == null ? Map.of() : availability;
        } catch (RuntimeException error) {
            log.warn("Booking inventory unavailable while enriching {} room types", roomTypeIds.size(), error);
            return Map.of();
        }
    }

    
    public RoomTypeResponse createRoom(UUID hotelId, RoomTypeRequest request) {
        log.info("Creating new room type for hotel: {}", hotelId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found"));
        
        RoomType roomType = RoomType.builder()
                .hotel(hotel)
                .name(request.getName())
                .description(request.getDescription())
                .capacity(request.getCapacity())
                .pricePerNight(request.getPricePerNight())
                .totalInventory(request.getTotalInventory())
                .build();
        
        RoomType saved = roomTypeRepository.saveAndFlush(roomType);

        compensateOnRollback(
                "remove inventory for rolled-back room creation " + saved.getId(),
                () -> inventoryService.deleteInventory(saved.getId()));
        inventoryService.initializeInventory(saved.getId(), saved.getTotalInventory());
        
        return mapToResponse(saved);
    }
    
    public RoomTypeResponse updateRoom(UUID roomTypeId, RoomTypeRequest request) {
        log.info("Updating room type: {}", roomTypeId);
        
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found"));

        int previousInventory = roomType.getTotalInventory();
        
        roomType.setName(request.getName());
        roomType.setDescription(request.getDescription());
        roomType.setCapacity(request.getCapacity());
        roomType.setPricePerNight(request.getPricePerNight());
        roomType.setTotalInventory(request.getTotalInventory());
        
        RoomType updated = roomTypeRepository.saveAndFlush(roomType);

        compensateOnRollback(
                "restore capacity for rolled-back room update " + roomTypeId,
                () -> inventoryService.setDesiredCapacity(roomTypeId, previousInventory));
        inventoryService.setDesiredCapacity(roomTypeId, request.getTotalInventory());

        return mapToResponse(updated);
    }
    
    public void deleteRoom(UUID roomTypeId) {
        log.info("Deleting room type: {}", roomTypeId);
        
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found"));

        roomTypeRepository.delete(roomType);
        roomTypeRepository.flush();

        compensateOnRollback(
                "restore inventory for rolled-back room deletion " + roomTypeId,
                () -> inventoryService.initializeInventory(roomTypeId, roomType.getTotalInventory()));
        inventoryService.deleteInventory(roomTypeId);
    }
    
    public Long getRoomCountByHotel(UUID hotelId) {
        return roomTypeRepository.countByHotelId(hotelId);
    }
    
    public void deleteInventories(Map<UUID, Integer> inventoryCapacities) {
        if (inventoryCapacities == null || inventoryCapacities.isEmpty()) {
            return;
        }
        Map<UUID, Integer> capacities = Map.copyOf(inventoryCapacities);
        compensateOnRollback("restore inventories for rolled-back hotel deletion", () ->
                capacities.forEach((roomTypeId, capacity) -> {
                    try {
                        inventoryService.initializeInventory(roomTypeId, capacity);
                    } catch (RuntimeException error) {
                        log.error("Failed to compensate inventory for room type {}", roomTypeId, error);
                    }
                }));
        inventoryService.deleteInventories(capacities.keySet().stream().toList());
    }

    public RoomTypeResponse mapToResponse(RoomType roomType, Integer availableRooms) {
        return RoomTypeResponse.builder()
                .id(roomType.getId())
                .hotelId(roomType.getHotel().getId())
                .hotelName(roomType.getHotel().getName())
                .name(roomType.getName())
                .description(roomType.getDescription())
                .capacity(roomType.getCapacity())
                .pricePerNight(roomType.getPricePerNight())
                .totalInventory(roomType.getTotalInventory())
                .createdAt(roomType.getCreatedAt())
                .availableRooms(availableRooms)
                .isAvailable(availableRooms == null ? null : availableRooms > 0)
                .build();
    }
    
    private RoomTypeResponse mapToResponse(RoomType roomType) {
        Integer availableRooms = null;
        try {
            availableRooms = inventoryService.getAvailableRoomsForToday(roomType.getId());
        } catch (RuntimeException error) {
            log.warn("Booking inventory unavailable while enriching room type {}", roomType.getId(), error);
        }
        return mapToResponse(roomType, availableRooms);
    }

    private void compensateOnRollback(String description, Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("No active transaction; rollback compensation not registered for {}", description);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    compensation.run();
                    log.warn("Applied rollback compensation: {}", description);
                } catch (RuntimeException error) {
                    log.error("Rollback compensation failed: {}", description, error);
                }
            }
        });
    }
}
