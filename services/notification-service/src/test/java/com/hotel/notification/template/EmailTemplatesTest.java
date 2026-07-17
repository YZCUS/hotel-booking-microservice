package com.hotel.notification.template;

import com.hotel.notification.dto.BookingConfirmationData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void getBookingConfirmationTemplate_IncludesBookingDetailsAndFallbackContactText() {
        UUID bookingId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        BookingConfirmationData data = BookingConfirmationData.builder()
                .bookingId(bookingId)
                .userName("Alice Chen")
                .hotelName("Grand Hotel Taipei")
                .hotelAddress("123 Main St")
                .checkInDate(LocalDate.of(2026, 6, 1))
                .checkOutDate(LocalDate.of(2026, 6, 3))
                .guests(2)
                .totalPrice(BigDecimal.valueOf(250.00))
                .bookingTime(LocalDateTime.of(2026, 5, 13, 10, 30))
                .build();

        String html = templates.getBookingConfirmationTemplate(data);

        assertTrue(html.contains("Alice Chen"));
        assertTrue(html.contains("11111111"));
        assertTrue(html.contains("Grand Hotel Taipei"));
        assertTrue(html.contains("June 01, 2026"));
        assertTrue(html.contains("Contact hotel directly"));
    }

    @Test
    void getBookingCancellationTemplate_IncludesCancelledStayDetails() {
        BookingConfirmationData data = BookingConfirmationData.builder()
                .bookingId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
                .userName("Alice Chen")
                .hotelName("Grand Hotel Taipei")
                .checkInDate(LocalDate.of(2026, 6, 1))
                .checkOutDate(LocalDate.of(2026, 6, 3))
                .totalPrice(BigDecimal.valueOf(250.00))
                .build();

        String html = templates.getBookingCancellationTemplate(data);

        assertTrue(html.contains("Booking Cancellation"));
        assertTrue(html.contains("Alice Chen"));
        assertTrue(html.contains("Grand Hotel Taipei"));
        assertTrue(html.contains("June 03, 2026"));
    }
}
