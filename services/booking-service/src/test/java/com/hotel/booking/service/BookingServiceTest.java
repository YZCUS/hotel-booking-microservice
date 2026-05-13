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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

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

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TransactionOperations bookingTransactionOperations;

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

        lenient().when(bookingTransactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
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
        verify(applicationEventPublisher).publishEvent(booking);
        verify(eventPublisher, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_CalculatesPriceBeforeReservingInventory() {
        when(pricingService.calculateTotalPrice(any(), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        bookingService.createBooking(bookingRequest);

        InOrder inOrder = inOrder(pricingService, inventoryService, bookingRepository);
        inOrder.verify(pricingService).calculateTotalPrice(
                roomTypeId, bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());
        inOrder.verify(inventoryService).reserveInventory(
                roomTypeId, bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate(), 1);
        inOrder.verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_InsufficientInventory() {
        // Given
        when(pricingService.calculateTotalPrice(any(), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(false);

        // When & Then
        assertThrows(InsufficientInventoryException.class, 
                () -> bookingService.createBooking(bookingRequest));
        
        verify(inventoryService).reserveInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
        verify(pricingService).calculateTotalPrice(roomTypeId, bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate());
        verify(bookingRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
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
        when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        // When
        BookingResponse response = bookingService.cancelBooking(bookingId, userId);

        // Then
        assertNotNull(response);
        verify(bookingRepository).findByIdAndUserIdForUpdate(bookingId, userId);
        verify(inventoryService).releaseInventory(roomTypeId, booking.getCheckInDate(), 
                booking.getCheckOutDate(), 1);
        verify(applicationEventPublisher).publishEvent(booking);
        verify(eventPublisher, never()).publishBookingCancelled(any());
    }

    @Test
    void cancelBooking_TooLateToCancel() {
        // Given
        booking.setCheckInDate(LocalDate.now()); // Same day - within 24 hours
        when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)).thenReturn(Optional.of(booking));

        // When & Then
        assertThrows(BookingConflictException.class, 
                () -> bookingService.cancelBooking(bookingId, userId));
    }

    @Test
    void cancelBooking_AlreadyCancelled_Idempotent() {
        // Given
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)).thenReturn(Optional.of(booking));

        // When & Then
        BookingResponse response = bookingService.cancelBooking(bookingId, userId);
        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(inventoryService, never()).releaseInventory(any(), any(), any(), anyInt());
    }

    @Test
    void cancelBooking_OptimisticLockingFailure() {
        // Given
        booking.setCheckInDate(LocalDate.now().plusDays(2));
        when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)).thenReturn(Optional.of(booking));
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

    @Test
    void handleBookingCreated_PublishesCreatedEvent() {
        // When
        bookingService.handleBookingCreated(booking);

        // Then
        verify(eventPublisher).publishBookingCreated(argThat(event ->
                bookingId.equals(event.getBookingId())
                        && userId.equals(event.getUserId())
                        && roomTypeId.equals(event.getRoomTypeId())));
    }

    @Test
    void handleBookingCancelled_PublishesCancelledEventForCancelledBooking() {
        // Given
        booking.setStatus(BookingStatus.CANCELLED);

        // When
        bookingService.handleBookingCancelled(booking);

        // Then
        verify(eventPublisher).publishBookingCancelled(argThat(event ->
                bookingId.equals(event.getBookingId())
                        && userId.equals(event.getUserId())
                        && roomTypeId.equals(event.getRoomTypeId())));
    }

    @Test
    void handleBookingCancelled_IgnoresNonCancelledBooking() {
        // When
        bookingService.handleBookingCancelled(booking);

        // Then
        verify(eventPublisher, never()).publishBookingCancelled(any());
    }
}
