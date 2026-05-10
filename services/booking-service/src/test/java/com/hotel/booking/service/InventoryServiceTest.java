package com.hotel.booking.service;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.exception.InventoryNotFoundException;
import com.hotel.booking.repository.RoomInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;

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
                .availableRooms(5)
                .version(0)
                .build();

        inventory2 = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(checkIn.plusDays(1))
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
        when(cacheManager.getCache("room-availability")).thenReturn(availabilityCache);

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
    void reserveInventory_InventoryNotFound() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateRangeForUpdate(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of());

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1);

        // Then
        assertFalse(result);
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
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(inventory1, inventory2));
        when(inventoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("room-availability")).thenReturn(availabilityCache);

        // When
        inventoryService.releaseInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        verify(inventoryRepository).saveAll(anyList());
        verify(availabilityCache).clear();
        assertEquals(7, inventory1.getAvailableRooms());
        assertEquals(7, inventory2.getAvailableRooms());
    }

    @Test
    void releaseInventory_OptimisticLockingFailure() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1)))
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
        when(inventoryRepository.findMinAvailableRoomsInRange(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(Optional.of(5));

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 3);

        // Then
        assertTrue(result);
    }

    @Test
    void checkAvailability_NotAvailable() {
        // Given
        when(inventoryRepository.findMinAvailableRoomsInRange(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(Optional.of(2));

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 3);

        // Then
        assertFalse(result);
    }

    @Test
    void checkAvailability_NoInventoryData() {
        // Given
        when(inventoryRepository.findMinAvailableRoomsInRange(roomTypeId, checkIn, checkOut.minusDays(1)))
                .thenReturn(Optional.empty());

        // When
        boolean result = inventoryService.checkAvailability(roomTypeId, checkIn, checkOut, 1);

        // Then
        assertFalse(result);
    }

    @Test
    void initializeInventory_Success() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(inventoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 7);

        // Then
        verify(inventoryRepository).saveAll(argThat(inventories -> {
            int count = 0;
            for (RoomInventory ignored : inventories) {
                count++;
            }
            return count == 8; // 7 days + today
        }));
    }

    @Test
    void initializeInventory_SkipExisting() {
        // Given
        LocalDate today = LocalDate.now();
        RoomInventory todayInventory = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(today)
                .availableRooms(10)
                .build();
        RoomInventory tomorrowInventory = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(today.plusDays(1))
                .availableRooms(10)
                .build();
        RoomInventory dayAfterInventory = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(today.plusDays(2))
                .availableRooms(10)
                .build();
        when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(todayInventory, tomorrowInventory, dayAfterInventory));

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 2);

        // Then
        verify(inventoryRepository, never()).saveAll(anyList());
    }
}
