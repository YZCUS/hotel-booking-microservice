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

import java.math.BigDecimal;
import java.time.LocalDate;
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
        // block() 是為了在事務性方法中簡化處理，更好的做法是讓整個鏈路都反應式
        return inventoryService.getAvailableRoomsForTodayBatch(roomTypeIds).block();
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
        
        RoomType saved = roomTypeRepository.save(roomType);
        
        // Initialize inventory for the room type
        initializeInventory(saved);
        
        return mapToResponse(saved);
    }
    
    public RoomTypeResponse updateRoom(UUID roomTypeId, RoomTypeRequest request) {
        log.info("Updating room type: {}", roomTypeId);
        
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found"));
        
        roomType.setName(request.getName());
        roomType.setDescription(request.getDescription());
        roomType.setCapacity(request.getCapacity());
        roomType.setPricePerNight(request.getPricePerNight());
        roomType.setTotalInventory(request.getTotalInventory());
        
        RoomType updated = roomTypeRepository.save(roomType);
        
        return mapToResponse(updated);
    }
    
    public void deleteRoom(UUID roomTypeId) {
        log.info("Deleting room type: {}", roomTypeId);
        
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found"));
        
        roomTypeRepository.delete(roomType);
    }
    
    public Long getRoomCountByHotel(UUID hotelId) {
        return roomTypeRepository.countByHotelId(hotelId);
    }
    
    private void initializeInventory(RoomType roomType) {
        log.info("Initializing inventory for room type: {}", roomType.getId());
        
        // Initialize inventory for the next 90 days
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(90);
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // This should ideally publish an event or call an inventory service
            // For now, we'll just log the action
            log.debug("Would initialize inventory for room {} on date {} with {} rooms",
                    roomType.getId(), date, roomType.getTotalInventory());
        }
        
        // TODO: Publish InventoryInitializedEvent or call Inventory Service
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
                .availableRooms(availableRooms) // 使用傳入的庫存
                .isAvailable(availableRooms != null && availableRooms > 0)
                .build();
    }
    
    private RoomTypeResponse mapToResponse(RoomType roomType) {
        // Get real-time availability for today
        Integer availableRooms = inventoryService.getAvailableRoomsForToday(roomType.getId());
        return mapToResponse(roomType, availableRooms);
    }
}