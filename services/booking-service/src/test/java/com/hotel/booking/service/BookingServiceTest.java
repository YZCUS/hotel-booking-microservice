package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.RoomTypeResponse;
import com.hotel.booking.dto.CheckInRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.event.EventPublisher;
import com.hotel.booking.exception.BookingConflictException;
import com.hotel.booking.exception.BookingNotFoundException;
import com.hotel.booking.exception.InsufficientInventoryException;
import com.hotel.booking.exception.ServiceCommunicationException;
import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private HotelCatalogClient hotelCatalogClient;

    @Mock
    private EventPublisher eventPublisher;

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
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(4));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(pricingService.calculateTotalPrice(any(RoomTypeResponse.class), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(booking);

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
        verify(pricingService).calculateTotalPrice(any(RoomTypeResponse.class), eq(bookingRequest.getCheckInDate()),
                eq(bookingRequest.getCheckOutDate()));
        verify(bookingRepository).saveAndFlush(any(Booking.class));
        verify(eventPublisher).publishBookingCreated(any());
    }

    @Test
    void createBooking_InsufficientInventory() {
        // Given
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(4));
        when(pricingService.calculateTotalPrice(any(RoomTypeResponse.class), any(), any()))
                .thenReturn(BigDecimal.valueOf(200));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(false);

        // When & Then
        assertThrows(InsufficientInventoryException.class, 
                () -> bookingService.createBooking(bookingRequest));
        
        verify(inventoryService).reserveInventory(roomTypeId, bookingRequest.getCheckInDate(), 
                bookingRequest.getCheckOutDate(), 1);
        verify(pricingService).calculateTotalPrice(any(RoomTypeResponse.class), any(), any());
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_OptimisticLockingFailure() {
        // Given
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(4));
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
        verify(eventPublisher).publishBookingCancelled(any());
        verify(eventPublisher, never()).publishBookingCreated(any());
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
    void createBooking_BookingTransactionFailureReleasesCommittedInventory() {
        // Given
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(4));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(pricingService.calculateTotalPrice(any(RoomTypeResponse.class), any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> bookingService.createBooking(bookingRequest));

        verify(inventoryService).releaseInventory(roomTypeId, bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), 1);
    }

    @Test
    void createBooking_FetchesCatalogAndPriceBeforeInventoryLock() {
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(4));
        when(pricingService.calculateTotalPrice(any(RoomTypeResponse.class), any(), any()))
                .thenReturn(BigDecimal.valueOf(200));
        when(inventoryService.reserveInventory(any(), any(), any(), eq(1))).thenReturn(true);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(booking);

        bookingService.createBooking(bookingRequest, "request-1");

        InOrder ordered = inOrder(
                hotelCatalogClient, pricingService, inventoryService, bookingRepository, eventPublisher);
        ordered.verify(hotelCatalogClient).getRoomType(roomTypeId);
        ordered.verify(pricingService).calculateTotalPrice(any(RoomTypeResponse.class), any(), any());
        ordered.verify(inventoryService).reserveInventory(roomTypeId,
                bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate(), 1);
        ordered.verify(bookingRepository).saveAndFlush(any(Booking.class));
        ordered.verify(eventPublisher).publishBookingCreated(any());
    }

    @Test
    void createBooking_GuestsExceedCapacity_DoesNotReserveInventory() {
        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(roomType(1));

        assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(bookingRequest, "request-capacity"));

        verify(inventoryService, never()).reserveInventory(any(), any(), any(), anyInt());
    }

    @Test
    void createBooking_CatalogFailureDoesNotReserveOrUseFallbackPrice() {
        when(hotelCatalogClient.getRoomType(roomTypeId))
                .thenThrow(new ServiceCommunicationException("catalog unavailable"));

        assertThrows(ServiceCommunicationException.class,
                () -> bookingService.createBooking(bookingRequest, "request-catalog-failure"));

        verifyNoInteractions(pricingService, inventoryService);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_IdempotentRetryReturnsExistingWithoutSecondReservation() {
        booking.setIdempotencyKey("request-retry");
        when(bookingRepository.findByUserIdAndIdempotencyKey(userId, "request-retry"))
                .thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.createBooking(bookingRequest, "request-retry");

        assertEquals(bookingId, response.getId());
        verifyNoInteractions(hotelCatalogClient, inventoryService, pricingService);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_BlankIdempotencyKeyIsRejectedBeforeSideEffects() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(bookingRequest, "   "));

        verifyNoInteractions(hotelCatalogClient, pricingService, inventoryService);
    }

    @Test
    void checkOut_EarlyCheckoutReleasesUnconsumedNights() {
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckInDate(LocalDate.now().minusDays(1));
        booking.setCheckOutDate(LocalDate.now().plusDays(2));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        bookingService.checkOut(bookingId);

        verify(inventoryService).releaseInventory(roomTypeId, LocalDate.now(),
                booking.getCheckOutDate(), 1);
    }

    @Test
    void checkIn_OverlappingAssignedRoom_ReturnsConflict() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckInDate(LocalDate.now());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.existsOverlappingCheckedInRoom(
                roomTypeId, "101", bookingId, booking.getCheckInDate(), booking.getCheckOutDate()))
                .thenReturn(true);

        assertThrows(BookingConflictException.class,
                () -> bookingService.checkIn(bookingId,
                        CheckInRequest.builder().roomNumber("101").build()));

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkIn_UniqueIndexRace_ReturnsConflict() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckInDate(LocalDate.now());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate room assignment"));

        assertThrows(BookingConflictException.class,
                () -> bookingService.checkIn(bookingId,
                        CheckInRequest.builder().roomNumber("101").build()));
    }

    private RoomTypeResponse roomType(int capacity) {
        return RoomTypeResponse.builder()
                .id(roomTypeId)
                .capacity(capacity)
                .pricePerNight(BigDecimal.valueOf(100))
                .build();
    }

}
