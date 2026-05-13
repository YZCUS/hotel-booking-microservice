package com.hotel.booking.integration;

import com.hotel.booking.entity.RoomInventory;
import com.hotel.booking.repository.RoomInventoryRepository;
import com.hotel.booking.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PostgresInventoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("booking_test")
            .withUsername("hotel_user")
            .withPassword("hotel_pass");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private RoomInventoryRepository inventoryRepository;

    @MockBean
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("room-availability")).thenReturn(cache);
    }

    @Test
    void reserveInventory_PersistsDecrementAcrossStayDatesInPostgres() {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = checkIn.plusDays(2);
        inventoryRepository.saveAll(List.of(
                inventory(roomTypeId, checkIn, 5),
                inventory(roomTypeId, checkIn.plusDays(1), 5)));

        boolean reserved = inventoryService.reserveInventory(roomTypeId, checkIn, checkOut, 2);

        assertTrue(reserved);
        List<RoomInventory> persisted = inventoryRepository
                .findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1));
        assertEquals(List.of(3, 3), persisted.stream().map(RoomInventory::getAvailableRooms).toList());
    }

    @Test
    void releaseInventory_PersistsIncrementAcrossStayDatesInPostgres() {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 7, 1);
        LocalDate checkOut = checkIn.plusDays(2);
        inventoryRepository.saveAll(List.of(
                inventory(roomTypeId, checkIn, 1),
                inventory(roomTypeId, checkIn.plusDays(1), 2)));

        inventoryService.releaseInventory(roomTypeId, checkIn, checkOut, 3);

        List<RoomInventory> persisted = inventoryRepository
                .findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1));
        assertEquals(List.of(4, 5), persisted.stream().map(RoomInventory::getAvailableRooms).toList());
    }

    private RoomInventory inventory(UUID roomTypeId, LocalDate date, int availableRooms) {
        return RoomInventory.builder()
                .roomTypeId(roomTypeId)
                .date(date)
                .availableRooms(availableRooms)
                .build();
    }
}
