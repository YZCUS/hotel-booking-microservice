package com.hotel.booking.controller;

import com.hotel.booking.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerTest {

    private InventoryService inventoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService)).build();
    }

    @Test
    void availability_ReturnsExactDateCount() throws Exception {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        when(inventoryService.getAvailableRooms(roomTypeId, date)).thenReturn(7);

        mockMvc.perform(get("/api/v1/inventory/availability")
                        .param("roomTypeId", roomTypeId.toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));

        verify(inventoryService).getAvailableRooms(roomTypeId, date);
    }
}
