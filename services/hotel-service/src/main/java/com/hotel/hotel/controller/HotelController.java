package com.hotel.hotel.controller;

import com.hotel.hotel.dto.HotelRequest;
import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.dto.RoomTypeRequest;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.dto.SearchCriteria;
import com.hotel.hotel.service.HotelService;
import com.hotel.hotel.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class HotelController {
    
    private final HotelService hotelService;
    private final RoomService roomService;
    
    @GetMapping
    public ResponseEntity<Page<HotelResponse>> searchHotels(
            @ModelAttribute SearchCriteria criteria,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        log.info("Searching hotels with criteria: {}", criteria);
        
        UUID userId = extractUserIdFromRequest(request);
        Page<HotelResponse> hotels = hotelService.searchHotels(criteria, pageable, userId);
        
        return ResponseEntity.ok(hotels);
    }
    
    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> getHotel(
            @PathVariable UUID hotelId,
            HttpServletRequest request) {
        log.info("Getting hotel: {}", hotelId);
        
        UUID userId = extractUserIdFromRequest(request);
        HotelResponse hotel = userId != null ?
                hotelService.getHotelById(hotelId, userId) :
                hotelService.getHotelById(hotelId);
        
        return ResponseEntity.ok(hotel);
    }
    
    @GetMapping("/{hotelId}/rooms")
    public ResponseEntity<List<RoomTypeResponse>> getRooms(@PathVariable UUID hotelId) {
        log.info("Getting rooms for hotel: {}", hotelId);
        
        List<RoomTypeResponse> rooms = roomService.getRoomsByHotel(hotelId);
        return ResponseEntity.ok(rooms);
    }
    
    @GetMapping("/{hotelId}/rooms/capacity/{minCapacity}")
    public ResponseEntity<List<RoomTypeResponse>> getRoomsByCapacity(
            @PathVariable UUID hotelId,
            @PathVariable Integer minCapacity) {
        log.info("Getting rooms for hotel: {} with minimum capacity: {}", hotelId, minCapacity);
        
        List<RoomTypeResponse> rooms = roomService.getRoomsByHotelAndCapacity(hotelId, minCapacity);
        return ResponseEntity.ok(rooms);
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody HotelRequest request) {
        log.info("Creating new hotel: {}", request.getName());
        
        HotelResponse hotel = hotelService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(hotel);
    }
    
    @PutMapping("/{hotelId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponse> updateHotel(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequest request) {
        log.info("Updating hotel: {}", hotelId);
        
        HotelResponse hotel = hotelService.updateHotel(hotelId, request);
        return ResponseEntity.ok(hotel);
    }
    
    @DeleteMapping("/{hotelId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHotel(@PathVariable UUID hotelId) {
        log.info("Deleting hotel: {}", hotelId);
        
        hotelService.deleteHotel(hotelId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{hotelId}/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomTypeResponse> createRoom(
            @PathVariable UUID hotelId,
            @Valid @RequestBody RoomTypeRequest request) {
        log.info("Creating new room for hotel: {}", hotelId);
        
        RoomTypeResponse room = roomService.createRoom(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }
    
    @PutMapping("/rooms/{roomTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomTypeResponse> updateRoom(
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody RoomTypeRequest request) {
        log.info("Updating room: {}", roomTypeId);
        
        RoomTypeResponse room = roomService.updateRoom(roomTypeId, request);
        return ResponseEntity.ok(room);
    }
    
    @DeleteMapping("/rooms/{roomTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID roomTypeId) {
        log.info("Deleting room: {}", roomTypeId);
        
        roomService.deleteRoom(roomTypeId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/rooms/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> getRoomType(@PathVariable UUID roomTypeId) {
        log.info("Getting room type: {}", roomTypeId);
        
        RoomTypeResponse room = roomService.getRoomTypeById(roomTypeId);
        return ResponseEntity.ok(room);
    }
    
    @GetMapping("/cities")
    public ResponseEntity<List<String>> getCities() {
        List<String> cities = hotelService.getAllCities();
        return ResponseEntity.ok(cities);
    }
    
    @GetMapping("/countries")
    public ResponseEntity<List<String>> getCountries() {
        List<String> countries = hotelService.getAllCountries();
        return ResponseEntity.ok(countries);
    }
    
    private UUID extractUserIdFromRequest(HttpServletRequest request) {
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.trim().isEmpty()) {
                return UUID.fromString(userIdHeader);
            }
            return null;
        } catch (Exception e) {
            log.debug("Could not extract user ID from request: {}", e.getMessage());
            return null;
        }
    }
}