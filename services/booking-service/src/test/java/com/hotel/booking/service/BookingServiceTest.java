package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.event.EventPublisher;
import com.hotel.booking.exception.BookingConflictException;
import com.hotel.booking.exception.BookingNotFoundException;
import com.hotel.booking.exception.InsufficientInventoryException;
import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PricingService pricingService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private BookingRequest bookingRequest;
    private Booking booking;
    private UUID userId;
    private UUID roomTypeId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        bookingRequest = BookingRequest.builder()
                .userId(userId)
                .roomTypeId(roomTypeId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .guests(2)
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .roomTypeId(roomTypeId)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .guests(2)
                .totalPrice(BigDecimal.valueOf(200))
                .status(BookingStatus.CONFIRMED)
                .version(0)
                .build();
    }

    @Test
    void createBooking_Success() {
        // Given
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(pricingService.calculateTotalPrice(any(), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        // When
        BookingResponse response = bookingService.createBooking(bookingRequest);

        // Then
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(roomTypeId, response.getRoomTypeId());
        assertEquals(BigDecimal.valueOf(200), response.getTotalPrice());
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        
        verify(inventoryService).reserveInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
        verify(pricingService).calculateTotalPrice(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate());
        verify(bookingRepository).save(any(Booking.class));
        verify(eventPublisher).publishBookingCreated(any());
    }

    @Test
    void createBooking_InsufficientInventory() {
        // Given
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(false);

        // When & Then
        assertThrows(InsufficientInventoryException.class, 
                () -> bookingService.createBooking(bookingRequest));
        
        verify(inventoryService).reserveInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
        verify(pricingService, never()).calculateTotalPrice(any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_OptimisticLockingFailure() {
        // Given
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1)))
                .thenThrow(new OptimisticLockingFailureException("Optimistic lock failure"));

        // When & Then
        assertThrows(OptimisticLockingFailureException.class, 
                () -> bookingService.createBooking(bookingRequest));
        
        verify(inventoryService).reserveInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
    }

    @Test
    void createBooking_InvalidDates_CheckInInPast() {
        // Given
        bookingRequest = BookingRequest.builder()
                .userId(userId)
                .roomTypeId(roomTypeId)
                .checkInDate(LocalDate.now().minusDays(1))
                .checkOutDate(LocalDate.now().plusDays(1))
                .guests(2)
                .build();

        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> bookingService.createBooking(bookingRequest));
    }

    @Test
    void getBooking_Success() {
        // Given
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));

        // When
        BookingResponse response = bookingService.getBooking(bookingId, userId);

        // Then
        assertNotNull(response);
        assertEquals(bookingId, response.getId());
        assertEquals(userId, response.getUserId());
        verify(bookingRepository).findByIdAndUserId(bookingId, userId);
    }

    @Test
    void getBooking_NotFound() {
        // Given
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BookingNotFoundException.class, 
                () -> bookingService.getBooking(bookingId, userId));
    }

    @Test
    void cancelBooking_Success() {
        // Given
        booking.setCheckInDate(LocalDate.now().plusDays(2)); // More than 24 hours ahead
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        // When
        BookingResponse response = bookingService.cancelBooking(bookingId, userId);

        // Then
        assertNotNull(response);
        verify(bookingRepository).findByIdAndUserId(bookingId, userId);
        verify(inventoryService).releaseInventory(roomTypeId, booking.getCheckInDate(), 
                booking.getCheckOutDate(), 1);
        verify(eventPublisher).publishBookingCancelled(any());
    }

    @Test
    void cancelBooking_TooLateToCancel() {
        // Given
        booking.setCheckInDate(LocalDate.now()); // Same day - within 24 hours
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));

        // When & Then
        assertThrows(BookingConflictException.class, 
                () -> bookingService.cancelBooking(bookingId, userId));
    }

    @Test
    void cancelBooking_AlreadyCancelled() {
        // Given
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));

        // When & Then
        assertThrows(BookingConflictException.class, 
                () -> bookingService.cancelBooking(bookingId, userId));
    }

    @Test
    void cancelBooking_OptimisticLockingFailure() {
        // Given
        booking.setCheckInDate(LocalDate.now().plusDays(2));
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch"));

        // When & Then
        assertThrows(OptimisticLockingFailureException.class, 
                () -> bookingService.cancelBooking(bookingId, userId));
    }

    @Test
    void createBooking_FailureWithInventoryCleanup() {
        // Given
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(pricingService.calculateTotalPrice(any(), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(bookingRepository.save(any(Booking.class))).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> bookingService.createBooking(bookingRequest));
        
        // Verify inventory is released on failure
        verify(inventoryService).releaseInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
    }
}