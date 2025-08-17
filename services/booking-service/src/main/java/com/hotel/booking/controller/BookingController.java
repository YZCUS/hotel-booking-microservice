package com.hotel.booking.controller;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CheckInRequest;
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
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
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
            @Valid @RequestBody CheckInRequest request) {
        log.info("Checking in booking: {}", bookingId);
        BookingResponse response = bookingService.checkIn(bookingId, request);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{bookingId}/check-out")
    public ResponseEntity<BookingResponse> checkOut(@PathVariable UUID bookingId) {
        log.info("Checking out booking: {}", bookingId);
        BookingResponse response = bookingService.checkOut(bookingId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<BookingResponse>> getUserBookings(
            @PathVariable UUID userId,
            Pageable pageable) {
        log.info("Getting bookings for user: {}", userId);
        Page<BookingResponse> bookings = bookingService.getUserBookings(userId, pageable);
        return ResponseEntity.ok(bookings);
    }
}