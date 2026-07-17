package com.hotel.booking.controller;

import com.hotel.booking.config.SecurityConfig;
import com.hotel.booking.security.InternalServiceTokenService;
import com.hotel.booking.security.TrustedHeaderAuthenticationFilter;
import com.hotel.booking.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, TrustedHeaderAuthenticationFilter.class})
class InventoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InternalServiceTokenService tokenService;

    @Test
    void initializeRejectsGatewayAdminToPreventCatalogBypass() throws Exception {
        when(tokenService.isValid("api-gateway", "valid-token")).thenReturn(true);

        mockMvc.perform(post("/api/v1/inventory/initialize")
                        .header("X-Internal-Service", "api-gateway")
                        .header("X-Internal-Token", "valid-token")
                        .header("X-User-Role", "ADMIN")
                        .param("roomTypeId", UUID.randomUUID().toString())
                        .param("totalRooms", "5"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(inventoryService);
    }

    @Test
    void initializeAllowsHotelServiceInternalIdentity() throws Exception {
        UUID roomTypeId = UUID.randomUUID();
        when(tokenService.isValid("hotel-service", "valid-token")).thenReturn(true);

        mockMvc.perform(post("/api/v1/inventory/initialize")
                        .header("X-Internal-Service", "hotel-service")
                        .header("X-Internal-Token", "valid-token")
                        .param("roomTypeId", roomTypeId.toString())
                        .param("totalRooms", "5"))
                .andExpect(status().isOk());

        verify(inventoryService).initializeInventory(roomTypeId, 5, 395);
    }
}
