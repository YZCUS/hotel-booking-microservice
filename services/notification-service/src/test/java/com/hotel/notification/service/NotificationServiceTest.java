package com.hotel.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
    }

    @Test
    void sendBookingConfirmation_PropagatesAsyncEmailFailure() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            String body = request.url().getPath().contains("/users/")
                    ? "{\"id\":\"550e8400-e29b-41d4-a716-446655440001\",\"email\":\"guest@example.com\",\"fullName\":\"Guest User\"}"
                    : "{\"id\":\"550e8400-e29b-41d4-a716-446655440002\",\"name\":\"Grand Hotel\",\"address\":\"1 Main St\"}";
            return Mono.just(jsonResponse(body));
        });
        NotificationService service = configuredService(builder);
        RuntimeException smtpFailure = new RuntimeException("smtp unavailable");
        when(emailService.sendBookingConfirmationEmail(any()))
                .thenReturn(CompletableFuture.failedFuture(smtpFailure));

        assertThatThrownBy(() -> service.sendBookingConfirmation(bookingCreatedEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasRootCause(smtpFailure);
    }

    @Test
    void sendWelcomeMessage_PropagatesAsyncEmailFailure() {
        NotificationService service = configuredService(WebClient.builder());
        RuntimeException smtpFailure = new RuntimeException("smtp unavailable");
        when(emailService.sendWelcomeEmail("guest@example.com", "Guest User"))
                .thenReturn(CompletableFuture.failedFuture(smtpFailure));
        NotificationService.UserRegisteredEvent event = new NotificationService.UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("guest@example.com");
        event.setFullName("Guest User");

        assertThatThrownBy(() -> service.sendWelcomeMessage(event))
                .isInstanceOf(RuntimeException.class)
                .hasRootCause(smtpFailure);
    }

    @Test
    void sendBookingConfirmation_AuthenticatesHotelEnrichmentRequest() {
        AtomicReference<ClientRequest> hotelRequest = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            boolean userRequest = request.url().getPath().contains("/users/");
            if (!userRequest) {
                hotelRequest.set(request);
            }
            String body = userRequest
                    ? "{\"id\":\"550e8400-e29b-41d4-a716-446655440001\",\"email\":\"guest@example.com\",\"fullName\":\"Guest User\"}"
                    : "{\"id\":\"550e8400-e29b-41d4-a716-446655440002\",\"name\":\"Grand Hotel\",\"address\":\"1 Main St\"}";
            return Mono.just(jsonResponse(body));
        });
        NotificationService service = configuredService(builder);
        when(emailService.sendBookingConfirmationEmail(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.sendBookingConfirmation(bookingCreatedEvent());

        assertThat(hotelRequest.get()).isNotNull();
        assertThat(hotelRequest.get().headers().getFirst("X-Internal-Service"))
                .isEqualTo("notification-service");
        assertThat(hotelRequest.get().headers().getFirst("X-Internal-Token")).hasSize(32);
    }

    @Test
    void sendBookingConfirmation_TimesOutSlowEnrichmentCall() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.delay(Duration.ofMillis(100)).thenReturn(jsonResponse("{}")));
        NotificationService service = configuredService(builder);
        ReflectionTestUtils.setField(service, "httpTimeout", Duration.ofMillis(10));

        assertThatThrownBy(() -> service.sendBookingConfirmation(bookingCreatedEvent()))
                .isInstanceOf(RuntimeException.class);
    }

    private NotificationService configuredService(WebClient.Builder builder) {
        NotificationService service = new NotificationService(emailService, builder);
        ReflectionTestUtils.setField(service, "userServiceUrl", "http://user-service:8081");
        ReflectionTestUtils.setField(service, "hotelServiceUrl", "http://hotel-service:8082");
        ReflectionTestUtils.setField(service, "serviceName", "notification-service");
        ReflectionTestUtils.setField(service, "serviceSecret", "test-secret");
        ReflectionTestUtils.setField(service, "serviceHeader", "X-Internal-Service");
        ReflectionTestUtils.setField(service, "tokenHeader", "X-Internal-Token");
        ReflectionTestUtils.setField(service, "httpTimeout", Duration.ofSeconds(1));
        return service;
    }

    private NotificationService.BookingCreatedEvent bookingCreatedEvent() {
        NotificationService.BookingCreatedEvent event = new NotificationService.BookingCreatedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setRoomTypeId(UUID.randomUUID());
        event.setCheckInDate(LocalDate.now().plusDays(1));
        event.setCheckOutDate(LocalDate.now().plusDays(2));
        event.setGuests(2);
        return event;
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
