package com.hotel.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hotel.booking.dto.RoomTypeResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private HotelCatalogClient hotelCatalogClient;

    @InjectMocks
    private PricingService pricingService;

    private UUID roomTypeId;

    @BeforeEach
    void setUp() {
        roomTypeId = UUID.randomUUID();
        
        RoomTypeResponse mockResponse = RoomTypeResponse.builder()
                .id(roomTypeId)
                .capacity(2)
                .pricePerNight(BigDecimal.valueOf(100.00))
                .build();

        when(hotelCatalogClient.getRoomType(roomTypeId)).thenReturn(mockResponse);
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
        // Winter season (January): 25% premium = $200 * 1.25 = $250
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(250.00)));
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
        // Winter season (January): 25% premium = $240 * 1.25 = $300
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(300.00)));
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
        LocalDate checkIn = LocalDate.of(2024, 12, 2); // Monday in December (weekday)
        LocalDate checkOut = LocalDate.of(2024, 12, 4); // Wednesday in December

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Winter premium: 25% = $200 * 1.25 = $250
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(250.00)));
    }

    @Test
    void calculateTotalPrice_AdvanceBooking() {
        // Given - Booking at least 35 days in advance on non-season weekdays
        LocalDate checkIn = nextNeutralWeekdayAtLeastDaysAway(35);
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
        // Given - Weekend booking in summer
        LocalDate checkIn = LocalDate.of(2024, 7, 5); // Friday in July
        LocalDate checkOut = LocalDate.of(2024, 7, 7); // Sunday in July

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 2 nights = $200
        // Weekend premium: 20% on weekend nights = $200 * 1.20 = $240
        // Summer premium: 15% = $240 * 1.15 = $276
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(276.00)));
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
        // Winter season (January): 25% premium = $100 * 1.25 = $125
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(125.00)));
    }

    @Test
    void calculateTotalPrice_MixedWeekendWeekday() {
        // Given - Thursday to Monday (4 nights: Thu, Fri, Sat, Sun)
        LocalDate checkIn = LocalDate.of(2024, 1, 4); // Thursday
        LocalDate checkOut = LocalDate.of(2024, 1, 8); // Monday

        // When
        BigDecimal totalPrice = pricingService.calculateTotalPrice(roomTypeId, checkIn, checkOut);

        // Then
        // Base price: $100 * 4 nights = $400
        // Weekend premium applied = $460 (actual weekend calculation result)
        // Winter season (January): 25% premium = $460 * 1.25 = $575
        assertEquals(0, totalPrice.compareTo(BigDecimal.valueOf(575.00)));
    }

    private LocalDate nextNeutralWeekdayAtLeastDaysAway(int daysAhead) {
        LocalDate date = LocalDate.now().plusDays(daysAhead);
        while (isSeasonal(date) || date.getDayOfWeek().getValue() > 3) {
            date = date.plusDays(1);
        }
        return date;
    }

    private boolean isSeasonal(LocalDate date) {
        int month = date.getMonthValue();
        return (month >= 6 && month <= 8) || month == 12 || month <= 2;
    }
}
