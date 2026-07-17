package com.hotel.booking.service;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.exception.InventoryNotFoundException;
import com.hotel.booking.exception.BookingConflictException;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.RoomInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private RoomInventoryRepository inventoryRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache availabilityCache;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID roomTypeId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private RoomInventory inventory1;
    private RoomInventory inventory2;

    @BeforeEach
    void setUp() {
        roomTypeId = UUID.randomUUID();
        checkIn = LocalDate.now().plusDays(1);
        checkOut = LocalDate.now().plusDays(3);

        inventory1 = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(checkIn)
                .totalRooms(7)
                .availableRooms(5)
                .version(0)
                .build();

        inventory2 = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(checkIn.plusDays(1))
                .totalRooms(7)
                .availableRooms(5)
                .version(0)
                .build();
    }

    @Test
    void reserveInventory_Success() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubAvailabilityCache();

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        assertTrue(result);
        verify(inventoryRepository, times(2)).save(any(RoomInventory.class));
        verify(availabilityCache).clear();
        assertEquals(3, inventory1.getAvailableRooms());
        assertEquals(3, inventory2.getAvailableRooms());
    }

    @Test
    void reserveInventory_ClearsAvailabilityCacheOnlyAfterCommit() {
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(
                roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("room-availability")).thenReturn(availabilityCache);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1));
            verify(availabilityCache, never()).clear();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            verify(availabilityCache).clear();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void reserveInventory_InsufficientRooms() {
        // Given
        inventory1.setAvailableRooms(1); // Not enough rooms
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveInventory_NoInventoryReturnsFalse() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of());

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1);

        // Then
        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveInventory_OptimisticLockingFailure() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch"));

        // When & Then
        assertThrows(OptimisticLockingFailureException.class, 
                () -> inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1));
    }

    @Test
    void releaseInventory_Success() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubAvailabilityCache();

        // When
        inventoryService.releaseInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        verify(inventoryRepository).saveAll(List.of(inventory1, inventory2));
        verify(availabilityCache).clear();
        assertEquals(7, inventory1.getAvailableRooms());
        assertEquals(7, inventory2.getAvailableRooms());
    }

    @Test
    void releaseInventory_OptimisticLockingFailure() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.saveAll(anyList()))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch"));

        // When & Then
        assertThrows(OptimisticLockingFailureException.class, 
                () -> inventoryService.releaseInventory(roomTypeId, checkIn, checkOut, 2));
    }

    @Test
    void checkAvailability_Available() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void checkAvailability_NotAvailable() {
        // Given
        inventory1.setAvailableRooms(2);
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 3);

        // Then
        assertFalse(result);
    }

    @Test
    void checkAvailability_NoInventoryData() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of());

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 1);

        // Then
        assertFalse(result);
    }

    @Test
    void checkAvailability_IncompleteDateRows_ReturnsFalse() {
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1));

        assertFalse(inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 1));
    }

    @Test
    void getAvailableRooms_MissingDate_ThrowsInsteadOfPretendingZero() {
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn)).thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class,
                () -> inventoryService.getAvailableRooms(roomTypeId, checkIn));
    }

    @Test
    void setDesiredCapacity_PreservesSoldRooms() {
        when(inventoryRepository.findFutureByRoomTypeIdForUpdate(eq(roomTypeId), any(LocalDate.class)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.setDesiredCapacity(roomTypeId, 10, 2);

        assertEquals(10, inventory1.getTotalRooms());
        assertEquals(8, inventory1.getAvailableRooms());
        assertEquals(10, inventory2.getTotalRooms());
        assertEquals(8, inventory2.getAvailableRooms());
    }

    @Test
    void setDesiredCapacity_BelowSoldRooms_RejectsUpdate() {
        when(inventoryRepository.findFutureByRoomTypeIdForUpdate(eq(roomTypeId), any(LocalDate.class)))
                .thenReturn(List.of(inventory1, inventory2));

        assertThrows(BookingConflictException.class,
                () -> inventoryService.setDesiredCapacity(roomTypeId, 1, 2));

        verify(inventoryRepository, never()).saveAll(anyList());
    }

    @Test
    void deleteInventory_WithActiveBooking_ReturnsConflict() {
        when(bookingRepository.existsActiveBookingForRoomType(roomTypeId)).thenReturn(true);

        assertThrows(BookingConflictException.class,
                () -> inventoryService.deleteInventory(roomTypeId));

        verify(inventoryRepository, never()).deleteByRoomTypeId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeInventory_Success() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(eq(roomTypeId), any(), any()))
                .thenReturn(List.of());
        when(inventoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 7);

        // Then
        ArgumentCaptor<List<RoomInventory>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(captor.capture());
        assertEquals(396, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(inventory ->
                inventory.getTotalRooms() == 10 && inventory.getAvailableRooms() == 10));
    }

    @Test
    void initializeInventory_SkipExisting() {
        // Given
        LocalDate today = LocalDate.now();
        List<RoomInventory> completeHorizon = java.util.stream.IntStream.rangeClosed(0, 395)
                .mapToObj(offset -> RoomInventory.builder()
                        .roomTypeId(roomTypeId)
                        .date(today.plusDays(offset))
                        .totalRooms(10)
                        .availableRooms(10)
                        .build())
                .toList();
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(eq(roomTypeId), any(), any()))
                .thenReturn(completeHorizon);

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 2);

        // Then
        verify(inventoryRepository, never()).saveAll(anyList());
    }

    @Test
    void checkAvailability_InvalidDateRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.checkAvailability(roomTypeId, checkOut, checkIn, 1));
        verifyNoInteractions(inventoryRepository);
    }

    private void stubAvailabilityCache() {
        when(cacheManager.getCache("room-availability")).thenReturn(availabilityCache);
    }
}
