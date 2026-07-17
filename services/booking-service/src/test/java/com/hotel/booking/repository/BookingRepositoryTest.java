package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class BookingRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private BookingRepository bookingRepository;

    @Test
    void userIdAndIdempotencyKey_AreUnique() {
        UUID userId = UUID.randomUUID();
        bookingRepository.saveAndFlush(booking(userId, "same-key"));

        assertThrows(DataIntegrityViolationException.class,
                () -> bookingRepository.saveAndFlush(booking(userId, "same-key")));
    }

    private Booking booking(UUID userId, String idempotencyKey) {
        return Booking.builder()
                .userId(userId)
                .roomTypeId(UUID.randomUUID())
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .guests(1)
                .totalPrice(BigDecimal.TEN)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
