package com.hotel.booking.service;

import com.hotel.booking.dto.RoomTypeResponse;
import com.hotel.booking.exception.ServiceCommunicationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomPricingClient {

    private final WebClient.Builder webClientBuilder;

    @CircuitBreaker(name = "hotel-service", fallbackMethod = "getRoomTypePriceFallback")
    @Retry(name = "hotel-service")
    @TimeLimiter(name = "hotel-service")
    @Cacheable(value = "room-prices", key = "#roomTypeId", unless = "#result == null")
    public Mono<BigDecimal> getRoomTypePriceAsync(UUID roomTypeId) {
        log.debug("Fetching room type price for: {}", roomTypeId);

        WebClient webClient = webClientBuilder
            .baseUrl("http://hotel-service:8082")
            .build();

        return webClient.get()
            .uri("/api/v1/hotels/rooms/{id}", roomTypeId)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                log.warn("Room type not found: {}", roomTypeId);
                return Mono.error(new ServiceCommunicationException("Room type not found: " + roomTypeId));
            })
            .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                log.error("Hotel service unavailable for room type: {}", roomTypeId);
                return Mono.error(new ServiceCommunicationException("Hotel service unavailable"));
            })
            .bodyToMono(RoomTypeResponse.class)
            .map(response -> {
                if (response != null && response.getPricePerNight() != null) {
                    log.debug("Retrieved price {} for room type {}", response.getPricePerNight(), roomTypeId);
                    return response.getPricePerNight();
                }
                log.warn("Invalid response for room type: {}, using default price", roomTypeId);
                return getDefaultPrice();
            })
            .onErrorReturn(ServiceCommunicationException.class, getDefaultPrice())
            .doOnError(e -> log.error("Error fetching room type price for: {}", roomTypeId, e));
    }

    public Mono<BigDecimal> getRoomTypePriceFallback(UUID roomTypeId, Exception ex) {
        log.warn("Hotel service circuit breaker activated for room type {}, using fallback price. Error: {}",
                roomTypeId, ex.getMessage());
        return Mono.just(getDefaultPrice());
    }

    private BigDecimal getDefaultPrice() {
        log.warn("Using default price as fallback");
        return BigDecimal.valueOf(100.00);
    }
}
