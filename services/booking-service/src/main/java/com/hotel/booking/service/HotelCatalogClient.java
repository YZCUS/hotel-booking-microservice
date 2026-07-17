package com.hotel.booking.service;

import com.hotel.booking.dto.RoomTypeResponse;
import com.hotel.booking.exception.ServiceCommunicationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelCatalogClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.hotel-service.url:http://hotel-service:8082}")
    private String hotelServiceUrl;

    @CircuitBreaker(name = "hotel-service")
    @Retry(name = "hotel-service")
    public RoomTypeResponse getRoomType(UUID roomTypeId) {
        try {
            RoomTypeResponse roomType = webClientBuilder.baseUrl(hotelServiceUrl).build()
                    .get()
                    .uri("/api/v1/hotels/rooms/{id}/catalog", roomTypeId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.createException().map(error -> new ServiceCommunicationException(
                                    "Room type is unavailable: " + roomTypeId, error)))
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.createException().map(error -> new ServiceCommunicationException(
                                    "Hotel catalog is unavailable", error)))
                    .bodyToMono(RoomTypeResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (roomType == null || roomType.getPricePerNight() == null
                    || roomType.getCapacity() == null || roomType.getId() == null) {
                throw new ServiceCommunicationException("Hotel catalog returned an incomplete room type");
            }
            return roomType;
        } catch (ServiceCommunicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load room type {} from hotel catalog", roomTypeId, e);
            throw new ServiceCommunicationException("Unable to load room type from hotel catalog", e);
        }
    }
}
