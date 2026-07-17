package com.hotel.notification.service;

import com.hotel.notification.dto.BookingConfirmationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
    }

    @Test
    void sendBookingConfirmation_FetchesDetailsAndSendsEmail() {
        NotificationService service = configuredService(validEnrichmentClient());
        when(emailService.sendBookingConfirmationEmail(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        NotificationService.BookingCreatedEvent event = bookingCreatedEvent();
        event.setTotalPrice(BigDecimal.valueOf(250));
        event.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 30));

        service.sendBookingConfirmation(event);

        ArgumentCaptor<BookingConfirmationData> captor =
                ArgumentCaptor.forClass(BookingConfirmationData.class);
        verify(emailService).sendBookingConfirmationEmail(captor.capture());
        BookingConfirmationData data = captor.getValue();
        assertThat(data.getBookingId()).isEqualTo(event.getBookingId());
        assertThat(data.getUserName()).isEqualTo("Guest User");
        assertThat(data.getUserEmail()).isEqualTo("guest@example.com");
        assertThat(data.getHotelName()).isEqualTo("Grand Hotel");
        assertThat(data.getHotelAddress()).isEqualTo("1 Main St");
        assertThat(data.getGuests()).isEqualTo(2);
        assertThat(data.getTotalPrice()).isEqualByComparingTo("250");
        assertThat(data.getCancellationPolicy())
                .isEqualTo("Free cancellation up to 24 hours before check-in");
    }

    @Test
    void sendBookingConfirmation_PropagatesAsyncEmailFailure() {
        NotificationService service = configuredService(validEnrichmentClient());
        RuntimeException smtpFailure = new RuntimeException("smtp unavailable");
        when(emailService.sendBookingConfirmationEmail(any()))
                .thenReturn(CompletableFuture.failedFuture(smtpFailure));

        assertThatThrownBy(() -> service.sendBookingConfirmation(bookingCreatedEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasRootCause(smtpFailure);
    }

    @Test
    void sendBookingConfirmation_ThrowsAndDoesNotEmailWhenUserLookupIsInvalid() {
        WebClient.Builder builder = enrichmentClient(
                "{\"id\":\"550e8400-e29b-41d4-a716-446655440001\",\"email\":\" \"}",
                validHotelJson(), null);
        NotificationService service = configuredService(builder);

        assertThatThrownBy(() -> service.sendBookingConfirmation(bookingCreatedEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send booking confirmation");
        verify(emailService, never()).sendBookingConfirmationEmail(any());
    }

    @Test
    void sendCancellationConfirmation_FetchesDetailsAndSendsCancellationEmail() {
        NotificationService service = configuredService(validEnrichmentClient());
        when(emailService.sendBookingCancellationEmail(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        NotificationService.BookingCancelledEvent event =
                new NotificationService.BookingCancelledEvent();
        event.setBookingId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setRoomTypeId(UUID.randomUUID());
        event.setCheckInDate(LocalDate.of(2026, 6, 1));
        event.setCheckOutDate(LocalDate.of(2026, 6, 3));
        event.setTotalPrice(BigDecimal.valueOf(250));

        service.sendCancellationConfirmation(event);

        ArgumentCaptor<BookingConfirmationData> captor =
                ArgumentCaptor.forClass(BookingConfirmationData.class);
        verify(emailService).sendBookingCancellationEmail(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo(event.getBookingId());
        assertThat(captor.getValue().getUserEmail()).isEqualTo("guest@example.com");
        assertThat(captor.getValue().getHotelName()).isEqualTo("Grand Hotel");
    }

    @Test
    void sendWelcomeMessage_UsesResolvedFullName() {
        NotificationService service = configuredService(WebClient.builder());
        when(emailService.sendWelcomeEmail("alice@example.com", "Alice Chen"))
                .thenReturn(CompletableFuture.completedFuture(null));
        NotificationService.UserRegisteredEvent event =
                new NotificationService.UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("alice@example.com");
        event.setFirstName("Alice");
        event.setLastName("Chen");

        service.sendWelcomeMessage(event);

        verify(emailService).sendWelcomeEmail("alice@example.com", "Alice Chen");
    }

    @Test
    void sendWelcomeMessage_PropagatesAsyncEmailFailure() {
        NotificationService service = configuredService(WebClient.builder());
        RuntimeException smtpFailure = new RuntimeException("smtp unavailable");
        when(emailService.sendWelcomeEmail("guest@example.com", "Guest User"))
                .thenReturn(CompletableFuture.failedFuture(smtpFailure));
        NotificationService.UserRegisteredEvent event =
                new NotificationService.UserRegisteredEvent();
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
        NotificationService service = configuredService(enrichmentClient(
                validUserJson(), validHotelJson(), hotelRequest));
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

    private WebClient.Builder validEnrichmentClient() {
        return enrichmentClient(validUserJson(), validHotelJson(), null);
    }

    private WebClient.Builder enrichmentClient(
            String userJson,
            String hotelJson,
            AtomicReference<ClientRequest> hotelRequest) {
        return WebClient.builder().exchangeFunction(request -> {
            boolean userRequest = request.url().getPath().contains("/users/");
            if (!userRequest && hotelRequest != null) {
                hotelRequest.set(request);
            }
            return Mono.just(jsonResponse(userRequest ? userJson : hotelJson));
        });
    }

    private String validUserJson() {
        return "{\"id\":\"550e8400-e29b-41d4-a716-446655440001\","
                + "\"email\":\"guest@example.com\",\"fullName\":\"Guest User\"}";
    }

    private String validHotelJson() {
        return "{\"id\":\"550e8400-e29b-41d4-a716-446655440002\","
                + "\"name\":\"Grand Hotel\",\"address\":\"1 Main St\"}";
    }

    private NotificationService.BookingCreatedEvent bookingCreatedEvent() {
        NotificationService.BookingCreatedEvent event =
                new NotificationService.BookingCreatedEvent();
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
