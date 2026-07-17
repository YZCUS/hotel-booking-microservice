package com.hotel.hotel.service;

import com.hotel.hotel.dto.RoomTypeRequest;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.exception.InventoryCommunicationException;
import com.hotel.hotel.exception.InventoryLifecycleConflictException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private InventoryService inventoryService;
    @InjectMocks
    private RoomService roomService;

    private UUID hotelId;
    private UUID roomTypeId;
    private Hotel hotel;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();
        hotel = Hotel.builder().id(hotelId).name("Hotel").build();
        roomType = RoomType.builder()
                .id(roomTypeId)
                .hotel(hotel)
                .name("King")
                .capacity(2)
                .pricePerNight(BigDecimal.valueOf(120))
                .totalInventory(5)
                .build();
    }

    @Test
    void createRoom_InitializesFullBookingHorizon() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.saveAndFlush(any(RoomType.class))).thenReturn(roomType);
        when(inventoryService.getAvailableRoomsForToday(roomTypeId)).thenReturn(5);

        roomService.createRoom(hotelId, request(5));

        var ordered = inOrder(roomTypeRepository, inventoryService);
        ordered.verify(roomTypeRepository).saveAndFlush(any(RoomType.class));
        ordered.verify(inventoryService).initializeInventory(roomTypeId, 5);
    }

    @Test
    void updateRoom_FlushesCatalogBeforeRemoteCapacityChange() {
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.saveAndFlush(roomType)).thenReturn(roomType);
        when(inventoryService.getAvailableRoomsForToday(roomTypeId)).thenReturn(8);

        roomService.updateRoom(roomTypeId, request(8));

        assertEquals(8, roomType.getTotalInventory());
        var ordered = inOrder(roomTypeRepository, inventoryService);
        ordered.verify(roomTypeRepository).saveAndFlush(roomType);
        ordered.verify(inventoryService).setDesiredCapacity(roomTypeId, 8);
    }

    @Test
    void deleteRoom_ActiveBookingConflictLeavesCatalogIntact() {
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(roomType));
        org.mockito.Mockito.doThrow(new InventoryLifecycleConflictException("active booking"))
                .when(inventoryService).deleteInventory(roomTypeId);

        assertThrows(InventoryLifecycleConflictException.class,
                () -> roomService.deleteRoom(roomTypeId));

        var ordered = inOrder(roomTypeRepository, inventoryService);
        ordered.verify(roomTypeRepository).delete(roomType);
        ordered.verify(roomTypeRepository).flush();
        ordered.verify(inventoryService).deleteInventory(roomTypeId);
    }

    @Test
    void createRoom_LocalRollbackCompensatesRemoteInventory() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.saveAndFlush(any(RoomType.class))).thenReturn(roomType);
        when(inventoryService.getAvailableRoomsForToday(roomTypeId)).thenReturn(5);
        TransactionSynchronizationManager.initSynchronization();
        try {
            roomService.createRoom(hotelId, request(5));

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(inventoryService).deleteInventory(roomTypeId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getRoomType_InventoryOutageReturnsCatalogWithUnknownAvailability() {
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(roomType));
        when(inventoryService.getAvailableRoomsForToday(roomTypeId))
                .thenThrow(new InventoryCommunicationException("inventory unavailable"));

        var response = roomService.getRoomTypeById(roomTypeId);

        assertEquals(roomTypeId, response.getId());
        assertNull(response.getAvailableRooms());
        assertNull(response.getIsAvailable());
    }

    @Test
    void batchAvailabilityOutageReturnsEmptyEnrichmentInsteadOfFailingCatalogRead() {
        when(inventoryService.getAvailableRoomsForTodayBatch(List.of(roomTypeId)))
                .thenReturn(reactor.core.publisher.Mono.error(
                        new InventoryCommunicationException("inventory unavailable")));

        var availability = roomService.getRoomAvailabilities(List.of(roomTypeId));

        assertEquals(java.util.Map.of(), availability);
    }

    private RoomTypeRequest request(int totalInventory) {
        return RoomTypeRequest.builder()
                .name("King")
                .capacity(2)
                .pricePerNight(BigDecimal.valueOf(120))
                .totalInventory(totalInventory)
                .build();
    }
}
