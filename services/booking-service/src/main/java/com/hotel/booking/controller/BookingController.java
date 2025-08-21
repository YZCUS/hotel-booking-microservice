package com.hotel.booking.controller;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CheckInRequest;
import com.hotel.booking.exception.AccessDeniedException;
import com.hotel.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {
    
    private final BookingService bookingService;
    
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("X-User-Id") UUID authenticatedUserId,
            @Valid @RequestBody BookingRequest request) {
        
        // 驗證當前用戶只能為自己創建預訂
        if (!authenticatedUserId.equals(request.getUserId())) {
            log.warn("User {} attempted to create booking for user {}", authenticatedUserId, request.getUserId());
            throw new AccessDeniedException("Cannot create booking for another user");
        }
        
        log.info("Creating booking for user: {}", request.getUserId());
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable UUID bookingId,
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("Getting booking: {} for user: {}", bookingId, userId);
        BookingResponse response = bookingService.getBooking(bookingId, userId);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("Cancelling booking: {} for user: {}", bookingId, userId);
        BookingResponse response = bookingService.cancelBooking(bookingId, userId);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{bookingId}/check-in")
    public ResponseEntity<BookingResponse> checkIn(
            @PathVariable UUID bookingId,
            @RequestHeader("X-User-Id") UUID authenticatedUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @Valid @RequestBody CheckInRequest request) {
        
        // 只有預訂者本人或酒店員工可以辦理入住
        if (!"HOTEL_STAFF".equals(userRole) && !"ADMIN".equals(userRole)) {
            // 驗證是否為預訂者本人
            bookingService.validateBookingOwnership(bookingId, authenticatedUserId);
        }
        
        log.info("Checking in booking: {} by user: {} (role: {})", bookingId, authenticatedUserId, userRole);
        BookingResponse response = bookingService.checkIn(bookingId, request);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{bookingId}/check-out")
    public ResponseEntity<BookingResponse> checkOut(
            @PathVariable UUID bookingId,
            @RequestHeader("X-User-Id") UUID authenticatedUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole) {
        
        // 只有預訂者本人或酒店員工可以辦理退房
        if (!"HOTEL_STAFF".equals(userRole) && !"ADMIN".equals(userRole)) {
            // 驗證是否為預訂者本人
            bookingService.validateBookingOwnership(bookingId, authenticatedUserId);
        }
        
        log.info("Checking out booking: {} by user: {} (role: {})", bookingId, authenticatedUserId, userRole);
        BookingResponse response = bookingService.checkOut(bookingId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<BookingResponse>> getUserBookings(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") UUID authenticatedUserId,
            Pageable pageable) {
        
        // 驗證用戶只能查看自己的預訂
        if (!authenticatedUserId.equals(userId)) {
            log.warn("User {} attempted to access bookings for user {}", authenticatedUserId, userId);
            throw new AccessDeniedException("Cannot access another user's bookings");
        }
        
        log.info("Getting bookings for user: {}", userId);
        Page<BookingResponse> bookings = bookingService.getUserBookings(userId, pageable);
        return ResponseEntity.ok(bookings);
    }
}