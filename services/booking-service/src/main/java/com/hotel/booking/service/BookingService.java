package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CheckInRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.event.BookingCancelledEvent;
import com.hotel.booking.event.BookingCreatedEvent;
import com.hotel.booking.event.EventPublisher;
import com.hotel.booking.exception.BookingConflictException;
import com.hotel.booking.exception.BookingNotFoundException;
import com.hotel.booking.exception.InsufficientInventoryException;
import com.hotel.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final EventPublisher eventPublisher;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(BookingRequest request) {
        log.info("Creating booking for user: {} from {} to {}", 
            request.getUserId(), request.getCheckInDate(), request.getCheckOutDate());
        
        // Validate booking dates
        validateBookingDates(request.getCheckInDate(), request.getCheckOutDate());
        
        // Check and reserve inventory (with optimistic locking)
        boolean inventoryReserved = false;
        try {
            inventoryReserved = inventoryService.reserveInventory(
                request.getRoomTypeId(),
                request.getCheckInDate(),
                request.getCheckOutDate(),
                1  // Reserve one room
            );
            
            if (!inventoryReserved) {
                throw new InsufficientInventoryException("No rooms available for selected dates");
            }
            
            // Calculate total price
            BigDecimal totalPrice = pricingService.calculateTotalPrice(
                request.getRoomTypeId(),
                request.getCheckInDate(),
                request.getCheckOutDate()
            );
            
            // Create booking
            Booking booking = Booking.builder()
                .userId(request.getUserId())
                .roomTypeId(request.getRoomTypeId())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .guests(request.getGuests())
                .totalPrice(totalPrice)
                .status(BookingStatus.CONFIRMED)
                .build();
            
            Booking saved = bookingRepository.save(booking);
            
            // Publish event
            publishBookingCreatedEvent(saved);
            
            log.info("Successfully created booking: {} for user: {}", saved.getId(), request.getUserId());
            return mapToResponse(saved);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure during booking creation, retrying... Attempt: {}", e.getMessage());
            throw e;  // Let @Retryable handle retry
        } catch (Exception e) {
            // If booking fails, release reserved inventory
            if (inventoryReserved) {
                try {
                    inventoryService.releaseInventory(
                        request.getRoomTypeId(),
                        request.getCheckInDate(),
                        request.getCheckOutDate(),
                        1
                    );
                    log.info("Released inventory due to booking failure");
                } catch (Exception releaseEx) {
                    log.error("Failed to release inventory after booking failure", releaseEx);
                }
            }
            throw e;
        }
    }
    
    public BookingResponse getBooking(UUID bookingId, UUID userId) {
        log.info("Getting booking: {} for user: {}", bookingId, userId);
        
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        return mapToResponse(booking);
    }
    
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public BookingResponse cancelBooking(UUID bookingId, UUID userId) {
        log.info("Cancelling booking: {} for user: {}", bookingId, userId);
        
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingConflictException("Only confirmed bookings can be cancelled");
        }
        
        // Check cancellation policy (24 hours before check-in)
        if (booking.getCheckInDate().minusDays(1).isBefore(LocalDate.now())) {
            throw new BookingConflictException("Cannot cancel booking within 24 hours of check-in");
        }
        
        try {
            // Update booking status
            booking.setStatus(BookingStatus.CANCELLED);
            Booking updated = bookingRepository.save(booking);
            
            // Release inventory
            inventoryService.releaseInventory(
                booking.getRoomTypeId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                1
            );
            
            // Publish cancellation event
            publishBookingCancelledEvent(updated);
            
            log.info("Successfully cancelled booking: {}", bookingId);
            return mapToResponse(updated);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure during booking cancellation, retrying...");
            throw e;
        }
    }
    
    public BookingResponse checkIn(UUID bookingId, CheckInRequest request) {
        log.info("Checking in booking: {} to room: {}", bookingId, request.getRoomNumber());
        
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingConflictException("Booking is not in confirmed status");
        }
        
        if (!booking.getCheckInDate().equals(LocalDate.now())) {
            throw new BookingConflictException("Check-in is only allowed on the scheduled date");
        }
        
        // Assign room and update status
        booking.setRoomNumber(request.getRoomNumber());
        booking.setStatus(BookingStatus.CHECKED_IN);
        
        Booking updated = bookingRepository.save(booking);
        
        log.info("Successfully checked in booking: {} to room: {}", bookingId, request.getRoomNumber());
        return mapToResponse(updated);
    }
    
    public BookingResponse checkOut(UUID bookingId) {
        log.info("Checking out booking: {}", bookingId);
        
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BookingConflictException("Booking is not checked in");
        }
        
        if (LocalDate.now().isBefore(booking.getCheckOutDate())) {
            log.warn("Early checkout for booking: {}", bookingId);
        }
        
        booking.setStatus(BookingStatus.CHECKED_OUT);
        Booking updated = bookingRepository.save(booking);
        
        log.info("Successfully checked out booking: {}", bookingId);
        return mapToResponse(updated);
    }
    
    public Page<BookingResponse> getUserBookings(UUID userId, Pageable pageable) {
        log.info("Getting bookings for user: {}", userId);
        
        Page<Booking> bookings = bookingRepository.findByUserId(userId, pageable);
        return bookings.map(this::mapToResponse);
    }
    
    private void validateBookingDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }
        
        if (checkOut.isBefore(checkIn.plusDays(1))) {
            throw new IllegalArgumentException("Check-out must be at least 1 day after check-in");
        }
        
        if (checkIn.isAfter(LocalDate.now().plusDays(365))) {
            throw new IllegalArgumentException("Cannot book more than 365 days in advance");
        }
        
        long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (numberOfNights > 30) {
            throw new IllegalArgumentException("Cannot book for more than 30 nights");
        }
    }
    
    private void publishBookingCreatedEvent(Booking booking) {
        try {
            BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .roomTypeId(booking.getRoomTypeId())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .guests(booking.getGuests())
                .totalPrice(booking.getTotalPrice())
                .createdAt(booking.getCreatedAt())
                .build();
            
            eventPublisher.publishBookingCreated(event);
        } catch (Exception e) {
            log.error("Failed to publish booking created event for booking: {}", booking.getId(), e);
            // Don't fail the booking process due to event publishing failure
        }
    }
    
    private void publishBookingCancelledEvent(Booking booking) {
        try {
            BookingCancelledEvent event = BookingCancelledEvent.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .roomTypeId(booking.getRoomTypeId())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .totalPrice(booking.getTotalPrice())
                .cancelledAt(LocalDateTime.now())
                .reason("User cancellation")
                .build();
            
            eventPublisher.publishBookingCancelled(event);
        } catch (Exception e) {
            log.error("Failed to publish booking cancelled event for booking: {}", booking.getId(), e);
            // Don't fail the cancellation process due to event publishing failure
        }
    }
    
    private BookingResponse mapToResponse(Booking booking) {
        int numberOfNights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        
        boolean canCancel = booking.getStatus() == BookingStatus.CONFIRMED && 
                           booking.getCheckInDate().minusDays(1).isAfter(LocalDate.now());
        
        boolean canCheckIn = booking.getStatus() == BookingStatus.CONFIRMED &&
                            booking.getCheckInDate().equals(LocalDate.now());
        
        return BookingResponse.builder()
            .id(booking.getId())
            .userId(booking.getUserId())
            .roomTypeId(booking.getRoomTypeId())
            .checkInDate(booking.getCheckInDate())
            .checkOutDate(booking.getCheckOutDate())
            .guests(booking.getGuests())
            .totalPrice(booking.getTotalPrice())
            .status(booking.getStatus())
            .roomNumber(booking.getRoomNumber())
            .version(booking.getVersion())
            .createdAt(booking.getCreatedAt())
            .updatedAt(booking.getUpdatedAt())
            .numberOfNights(numberOfNights)
            .canCancel(canCancel)
            .canCheckIn(canCheckIn)
            .build();
    }
}