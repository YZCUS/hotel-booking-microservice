package com.hotel.booking.integration;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.repository.RoomInventoryRepository;
import com.hotel.booking.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OptimisticLockingIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private RoomInventoryRepository inventoryRepository;

    private UUID roomTypeId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        roomTypeId = UUID.randomUUID();
        testDate = LocalDate.now().plusDays(1);
        
        // Initialize inventory for testing
        RoomInventory inventory = RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(testDate)
                .availableRooms(10)
                .build();
        inventoryRepository.save(inventory);
    }

    @Test
    @Transactional
    void testOptimisticLocking_ConcurrentReservation() throws ExecutionException, InterruptedException {
        // Given - Two concurrent reservation attempts
        CompletableFuture<Boolean> reservation1 = CompletableFuture.supplyAsync(() -> {
            try {
                return inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 5);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> reservation2 = CompletableFuture.supplyAsync(() -> {
            try {
                // Small delay to create race condition
                Thread.sleep(10);
                return inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 5);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // When - Both reservations execute
        Boolean result1 = reservation1.get();
        Boolean result2 = reservation2.get();

        // Then - Both should succeed due to retry mechanism
        assertTrue(result1);
        assertTrue(result2);

        // Verify final inventory state
        RoomInventory finalInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        assertEquals(0, finalInventory.getAvailableRooms()); // 10 - 5 - 5 = 0
        assertTrue(finalInventory.getVersion() > 0); // Version should be incremented
    }

    @Test
    @Transactional
    void testOptimisticLocking_ReservationExceedsAvailability() throws ExecutionException, InterruptedException {
        // Given - Two concurrent reservations that exceed available inventory
        CompletableFuture<Boolean> reservation1 = CompletableFuture.supplyAsync(() -> {
            try {
                return inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> reservation2 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(10);
                return inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // When - Both reservations execute
        Boolean result1 = reservation1.get();
        Boolean result2 = reservation2.get();

        // Then - One should succeed, one should fail due to insufficient inventory
        assertTrue(result1 ^ result2); // XOR - exactly one should be true

        // Verify final inventory state
        RoomInventory finalInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        assertEquals(2, finalInventory.getAvailableRooms()); // 10 - 8 = 2 (only one reservation succeeded)
    }

    @Test
    @Transactional
    void testOptimisticLocking_ReservationAndRelease() throws ExecutionException, InterruptedException {
        // Given - First reserve some inventory
        boolean reserved = inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 5);
        assertTrue(reserved);

        // Then - Concurrent release and reserve operations
        CompletableFuture<Void> release = CompletableFuture.runAsync(() -> {
            try {
                inventoryService.releaseInventory(roomTypeId, testDate, testDate.plusDays(1), 3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> reserve = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(10);
                return inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // When - Both operations execute
        release.get();
        Boolean reserveResult = reserve.get();

        // Then - Reserve should succeed
        assertTrue(reserveResult);

        // Verify final inventory state
        RoomInventory finalInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        // Started with 10, reserved 5 (left 5), released 3 (left 8), reserved 2 (left 6)
        assertEquals(6, finalInventory.getAvailableRooms());
    }

    @Test
    void testOptimisticLocking_VersionIncrement() {
        // Given - Get initial inventory
        RoomInventory initialInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        Integer initialVersion = initialInventory.getVersion();

        // When - Reserve inventory
        boolean reserved = inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 2);

        // Then - Version should be incremented
        assertTrue(reserved);
        RoomInventory updatedInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        assertEquals(initialVersion + 1, updatedInventory.getVersion());
        assertEquals(8, updatedInventory.getAvailableRooms()); // 10 - 2 = 8
    }

    @Test
    void testOptimisticLocking_MultipleUpdates() {
        // Given - Initial state
        RoomInventory initialInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        assertEquals(0, initialInventory.getVersion());

        // When - Multiple sequential operations
        inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 2);
        inventoryService.reserveInventory(roomTypeId, testDate, testDate.plusDays(1), 1);
        inventoryService.releaseInventory(roomTypeId, testDate, testDate.plusDays(1), 1);

        // Then - Version should reflect all updates
        RoomInventory finalInventory = inventoryRepository.findByRoomTypeIdAndDate(roomTypeId, testDate).orElseThrow();
        assertEquals(3, finalInventory.getVersion()); // 3 operations = version 3
        assertEquals(8, finalInventory.getAvailableRooms()); // 10 - 2 - 1 + 1 = 8
    }
}