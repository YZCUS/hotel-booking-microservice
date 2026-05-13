package com.hotel.notification.service;

import com.hotel.notification.dto.BookingConfirmationData;
import com.hotel.notification.dto.HotelInfo;
import com.hotel.notification.dto.UserInfo;
import com.hotel.notification.service.NotificationService.BookingCancelledEvent;
import com.hotel.notification.service.NotificationService.BookingCreatedEvent;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private EmailService emailService;
    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.ResponseSpec responseSpec;
    private NotificationService notificationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emailService = mock(EmailService.class);
        webClientBuilder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        notificationService = new NotificationService(emailService, webClientBuilder);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(UUID.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.header(anyString(), anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(emailService.sendBookingConfirmationEmail(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(emailService.sendBookingCancellationEmail(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(emailService.sendWelcomeEmail(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void sendBookingConfirmation_FetchesDetailsAndSendsEmail() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        when(responseSpec.bodyToMono(UserInfo.class)).thenReturn(Mono.just(UserInfo.builder()
                .id(userId)
                .email("alice@example.com")
                .fullName("Alice Chen")
                .build()));
        when(responseSpec.bodyToMono(HotelInfo.class)).thenReturn(Mono.just(HotelInfo.builder()
                .id(UUID.randomUUID())
                .name("Grand Hotel Taipei")
                .address("123 Main St")
                .phoneNumber("+886-2-1234-5678")
                .email("frontdesk@example.com")
                .build()));
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setBookingId(bookingId);
        event.setUserId(userId);
        event.setRoomTypeId(roomTypeId);
        event.setCheckInDate(LocalDate.of(2026, 6, 1));
        event.setCheckOutDate(LocalDate.of(2026, 6, 3));
        event.setGuests(2);
        event.setTotalPrice(BigDecimal.valueOf(250));
        event.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 30));

        notificationService.sendBookingConfirmation(event);

        ArgumentCaptor<BookingConfirmationData> captor = ArgumentCaptor.forClass(BookingConfirmationData.class);
        verify(emailService).sendBookingConfirmationEmail(captor.capture());
        BookingConfirmationData data = captor.getValue();
        assertEquals(bookingId, data.getBookingId());
        assertEquals("Alice Chen", data.getUserName());
        assertEquals("alice@example.com", data.getUserEmail());
        assertEquals("Grand Hotel Taipei", data.getHotelName());
        assertEquals("123 Main St", data.getHotelAddress());
        assertEquals(2, data.getGuests());
        assertEquals(BigDecimal.valueOf(250), data.getTotalPrice());
        assertEquals("Free cancellation up to 24 hours before check-in", data.getCancellationPolicy());
    }

    @Test
    void sendBookingConfirmation_PropagatesAsyncEmailFailure() {
        when(responseSpec.bodyToMono(UserInfo.class)).thenReturn(Mono.just(UserInfo.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .fullName("Alice Chen")
                .build()));
        when(responseSpec.bodyToMono(HotelInfo.class)).thenReturn(Mono.just(HotelInfo.builder()
                .id(UUID.randomUUID())
                .name("Grand Hotel Taipei")
                .address("123 Main St")
                .build()));
        CompletableFuture<Void> failedEmail = new CompletableFuture<>();
        failedEmail.completeExceptionally(new RuntimeException("SMTP unavailable"));
        when(emailService.sendBookingConfirmationEmail(any())).thenReturn(failedEmail);
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setRoomTypeId(UUID.randomUUID());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> notificationService.sendBookingConfirmation(event));

        assertTrue(thrown.getMessage().contains("Failed to send booking confirmation"));
    }

    @Test
    void sendBookingConfirmation_ThrowsAndDoesNotEmailWhenUserLookupIsInvalid() {
        when(responseSpec.bodyToMono(UserInfo.class)).thenReturn(Mono.just(UserInfo.builder()
                .id(UUID.randomUUID())
                .email(" ")
                .build()));
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setRoomTypeId(UUID.randomUUID());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> notificationService.sendBookingConfirmation(event));

        assertTrue(thrown.getMessage().contains("Failed to send booking confirmation"));
        verify(emailService, never()).sendBookingConfirmationEmail(any());
    }

    @Test
    void sendCancellationConfirmation_FetchesDetailsAndSendsCancellationEmail() {
        UUID bookingId = UUID.randomUUID();
        when(responseSpec.bodyToMono(UserInfo.class)).thenReturn(Mono.just(UserInfo.builder()
                .email("alice@example.com")
                .fullName("Alice Chen")
                .build()));
        when(responseSpec.bodyToMono(HotelInfo.class)).thenReturn(Mono.just(HotelInfo.builder()
                .name("Grand Hotel Taipei")
                .address("123 Main St")
                .build()));
        BookingCancelledEvent event = new BookingCancelledEvent();
        event.setBookingId(bookingId);
        event.setUserId(UUID.randomUUID());
        event.setRoomTypeId(UUID.randomUUID());
        event.setCheckInDate(LocalDate.of(2026, 6, 1));
        event.setCheckOutDate(LocalDate.of(2026, 6, 3));
        event.setTotalPrice(BigDecimal.valueOf(250));

        notificationService.sendCancellationConfirmation(event);

        ArgumentCaptor<BookingConfirmationData> captor = ArgumentCaptor.forClass(BookingConfirmationData.class);
        verify(emailService).sendBookingCancellationEmail(captor.capture());
        assertEquals(bookingId, captor.getValue().getBookingId());
        assertEquals("alice@example.com", captor.getValue().getUserEmail());
        assertEquals("Grand Hotel Taipei", captor.getValue().getHotelName());
    }

    @Test
    void sendWelcomeMessage_UsesResolvedFullName() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("alice@example.com");
        event.setFirstName("Alice");
        event.setLastName("Chen");

        notificationService.sendWelcomeMessage(event);

        verify(emailService).sendWelcomeEmail("alice@example.com", "Alice Chen");
    }
}
