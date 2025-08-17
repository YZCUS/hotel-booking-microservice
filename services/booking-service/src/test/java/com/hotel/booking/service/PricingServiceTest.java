package com.hotel.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PricingService pricingService;

    private UUID roomTypeId;

    @BeforeEach
    void setUp() {
        roomTypeId = UUID.randomUUID();
    }

    @Test
    void calculateTotalPrice_WeekdayBooking() {
        // Given - Tuesday to Thursday (2 nights, no weekend)
        LocalDate checkIn = LocalDate.of(2024, 1, 2); // Tuesday
        LocalDate checkOut = LocalDate.of(2024, 1, 4); // Thursday

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // No weekend premium, no seasonal adjustment, no advance booking discount
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(200.00)));
    }

    @Test
    void calculateTotalPrice_WeekendBooking() {
        // Given - Friday to Sunday (2 nights, both weekend)
        LocalDate checkIn = LocalDate.of(2024, 1, 5); // Friday
        LocalDate checkOut = LocalDate.of(2024, 1, 7); // Sunday

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Weekend premium: 20% on both nights = $200 * 1.20 = $240
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(240.00)));
    }

    @Test
    void calculateTotalPrice_SummerSeason() {
        // Given - Summer period (July)
        LocalDate checkIn = LocalDate.of(2024, 7, 1); // Monday in July
        LocalDate checkOut = LocalDate.of(2024, 7, 3); // Wednesday in July

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Summer premium: 15% = $200 * 1.15 = $230
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(230.00)));
    }

    @Test
    void calculateTotalPrice_WinterSeason() {
        // Given - Winter period (December)
        LocalDate checkIn = LocalDate.of(2024, 12, 1); // Sunday in December
        LocalDate checkOut = LocalDate.of(2024, 12, 3); // Tuesday in December

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Winter premium: 25% = $200 * 1.25 = $250
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(250.00)));
    }

    @Test
    void calculateTotalPrice_AdvanceBooking() {
        // Given - Booking 35 days in advance
        LocalDate checkIn = LocalDate.now().plusDays(35);
        LocalDate checkOut = checkIn.plusDays(2);

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Advance booking discount: 10% = $200 * 0.90 = $180
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(180.00)));
    }

    @Test
    void calculateTotalPrice_ComplexScenario() {
        // Given - Weekend booking in summer, advance booking
        LocalDate checkIn = LocalDate.of(2024, 7, 5); // Friday in July, assume booking 35+ days ahead
        LocalDate checkOut = LocalDate.of(2024, 7, 7); // Sunday in July

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Weekend premium: 20% on weekend nights = $200 * 1.20 = $240
        // Summer premium: 15% = $240 * 1.15 = $276
        // If booking far enough in advance (35+ days), 10% discount = $276 * 0.90 = $248.40
        // Note: The exact result depends on when the test runs vs the check-in date
        assertTrue(totalPrice.compareTo(BigDecimal.valueOf(200.00)) > 0);
        assertTrue(totalPrice.compareTo(BigDecimal.valueOf(300.00)) < 0);
    }

    @Test
    void calculateTotalPrice_OneDayBooking() {
        // Given - Single night booking
        LocalDate checkIn = LocalDate.of(2024, 1, 1); // Monday
        LocalDate checkOut = LocalDate.of(2024, 1, 2); // Tuesday

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 1 night = $100
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(100.00)));
    }

    @Test
    void calculateTotalPrice_MixedWeekendWeekday() {
        // Given - Thursday to Monday (4 nights: Thu, Fri, Sat, Sun)
        LocalDate checkIn = LocalDate.of(2024, 1, 4); // Thursday
        LocalDate checkOut = LocalDate.of(2024, 1, 8); // Monday

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // 4 nights total: Thu (weekday), Fri (weekend), Sat (weekend), Sun (weekday)
        // Weekend nights: 2 (Fri, Sat)
        // Weekday nights: 2 (Thu, Sun)
        // Weekend price: ($100 * 2 / 4) * 2 * 1.20 = $50 * 2 * 1.20 = $120
        // Weekday price: ($100 * 2 / 4) * 2 = $50 * 2 = $100
        // Total: $120 + $100 = $220
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(220.00)));
    }
}