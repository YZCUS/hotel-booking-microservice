package com.hotel.hotel.controller;

import com.hotel.hotel.config.SecurityConfig;
import com.hotel.hotel.security.InternalServiceTokenService;
import com.hotel.hotel.security.TrustedHeaderAuthenticationFilter;
import com.hotel.hotel.service.HotelService;
import com.hotel.hotel.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, TrustedHeaderAuthenticationFilter.class})
class HotelControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HotelService hotelService;

    @MockBean
    private RoomService roomService;

    @MockBean
    private InternalServiceTokenService tokenService;

    @Test
    void exportRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportRejectsGatewayCallerEvenWithValidInternalToken() throws Exception {
        when(tokenService.isValid("api-gateway", "valid-token")).thenReturn(true);

        mockMvc.perform(get("/api/v1/hotels/export")
                        .header("X-Internal-Service", "api-gateway")
                        .header("X-Internal-Token", "valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportAllowsSearchServiceWithValidInternalToken() throws Exception {
        when(tokenService.isValid("search-service", "valid-token")).thenReturn(true);
        when(hotelService.exportHotels()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/hotels/export")
                        .header("X-Internal-Service", "search-service")
                        .header("X-Internal-Token", "valid-token"))
                .andExpect(status().isOk());
    }
}
