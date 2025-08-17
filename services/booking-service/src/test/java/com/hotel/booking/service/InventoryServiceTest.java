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
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private RoomInventoryRepository inventoryRepository;

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
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.of(inventory1));
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn.plusDays(1)))
                .thenReturn(Optional.of(inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        assertTrue(result);
        verify(inventoryRepository, times(2)).save(any(RoomInventory.class));
        assertEquals(3, inventory1.getAvailableRooms());
        assertEquals(3, inventory2.getAvailableRooms());
    }

    @Test
    void reserveInventory_InsufficientRooms() {
        // Given
        inventory1.setAvailableRooms(1); // Not enough rooms
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.of(inventory1));

        // When
        boolean result = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveInventory_InventoryNotFound() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(InventoryNotFoundException.class, 
                () -> inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1));
    }

    @Test
    void reserveInventory_OptimisticLockingFailure() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.of(inventory1));
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn.plusDays(1)))
                .thenReturn(Optional.of(inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch"));

        // When & Then
        assertThrows(OptimisticLockingFailureException.class, 
                () -> inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 1));
    }

    @Test
    void releaseInventory_Success() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.of(inventory1));
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn.plusDays(1)))
                .thenReturn(Optional.of(inventory2));
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        inventoryService.releaseInventory(roomTypeId, checkIn, checkOut, 2);

        // Then
        verify(inventoryRepository, times(2)).save(any(RoomInventory.class));
        assertEquals(7, inventory1.getAvailableRooms());
        assertEquals(7, inventory2.getAvailableRooms());
    }

    @Test
    void releaseInventory_OptimisticLockingFailure() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, checkIn))
                .thenReturn(Optional.of(inventory1));
        when(inventoryRepository.save(any(RoomInventory.class)))
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
        when(inventoryRepository.findByRoomTypeIdAndDate(any(), any()))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(RoomInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 7);

        // Then
        verify(inventoryRepository, times(8)).save(any(RoomInventory.class)); // 7 days + today
    }

    @Test
    void initializeInventory_SkipExisting() {
        // Given
        when(inventoryRepository.findByRoomTypeIdAndDate(any(), any()))
                .thenReturn(Optional.of(inventory1));

        // When
        inventoryService.initializeInventory(roomTypeId, 10, 2);

        // Then
        verify(inventoryRepository, never()).save(any());
    }
}