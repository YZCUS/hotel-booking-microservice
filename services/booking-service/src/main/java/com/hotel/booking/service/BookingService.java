package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CheckInRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.event.BookingCancelledEvent;
import com.hotel.booking.event.BookingCreatedEvent;
import com.hotel.booking.event.EventPublisher;
import com.hotel.booking.exception.AccessDeniedException;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        try {
            boolean inventoryReserved = inventoryService.reserveInventory(
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
            
            BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(saved.getId())
                .userId(saved.getUserId())
                .roomTypeId(saved.getRoomTypeId())
                .checkInDate(saved.getCheckInDate())
                .checkOutDate(saved.getCheckOutDate())
                .guests(saved.getGuests())
                .totalPrice(saved.getTotalPrice())
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                .build();
            publishAfterCommit(() -> eventPublisher.publishBookingCreated(event));
            
            log.info("Successfully created booking: {} for user: {}", saved.getId(), request.getUserId());
            return mapToResponse(saved);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure during booking creation, retrying... Attempt: {}", e.getMessage());
            throw e;  // Let @Retryable handle retry
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
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BookingResponse cancelBooking(UUID bookingId, UUID userId) {
        log.info("Cancelling booking: {} for user: {}", bookingId, userId);
        
        Booking booking = bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("Booking {} already cancelled; returning idempotent response", bookingId);
            return mapToResponse(booking);
        }
        
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingConflictException("Only confirmed bookings can be cancelled");
        }
        
        // Check cancellation policy (24 hours before check-in)
        if (!canCancel(booking)) {
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
            
            BookingCancelledEvent event = BookingCancelledEvent.builder()
                .bookingId(updated.getId())
                .userId(updated.getUserId())
                .roomTypeId(updated.getRoomTypeId())
                .checkInDate(updated.getCheckInDate())
                .checkOutDate(updated.getCheckOutDate())
                .totalPrice(updated.getTotalPrice())
                .cancelledAt(LocalDateTime.now())
                .reason("User cancellation")
                .build();
            publishAfterCommit(() -> eventPublisher.publishBookingCancelled(event));
            
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
    
    public void validateBookingOwnership(UUID bookingId, UUID userId) {
        log.debug("Validating ownership of booking {} for user {}", bookingId, userId);
        
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));
        
        if (!booking.getUserId().equals(userId)) {
            log.warn("User {} attempted to access booking {} owned by user {}", 
                    userId, bookingId, booking.getUserId());
            throw new AccessDeniedException("You can only access your own bookings");
        }
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
    
    private void publishAfterCommit(Runnable publisher) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.run();
                } catch (Exception e) {
                    log.error("Failed to publish booking event after commit", e);
                }
            }
        });
    }
    
    private BookingResponse mapToResponse(Booking booking) {
        int numberOfNights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        
        boolean canCancel = booking.getStatus() == BookingStatus.CONFIRMED && canCancel(booking);
        
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

    private boolean canCancel(Booking booking) {
        LocalDate lastCancellationDate = booking.getCheckInDate().minusDays(1);
        return !LocalDate.now().isAfter(lastCancellationDate);
    }
}
