package com.hotel.search.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hotel.search.dto.SearchRequest;
import com.hotel.search.dto.SearchResponse;
import com.hotel.search.model.HotelDocument;
import com.hotel.search.service.IndexService;
import com.hotel.search.service.SearchService;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.json.JacksonJsonHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MeilisearchIntegrationTest {

    private static final String MASTER_KEY = "masterKey123456789";

    @Container
    static final GenericContainer<?> MEILISEARCH = new GenericContainer<>("getmeili/meilisearch:v1.6")
            .withExposedPorts(7700)
            .withEnv("MEILI_NO_ANALYTICS", "true")
            .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
            .waitingFor(Wait.forHttp("/health").forPort(7700).withStartupTimeout(Duration.ofSeconds(60)));

    private IndexService indexService;
    private SearchService searchService;

    @BeforeEach
    void setUpIndex() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Client client = new Client(new Config(meilisearchUrl(), MASTER_KEY, new JacksonJsonHandler(objectMapper)));
        indexService = new IndexService(client, objectMapper);
        searchService = new SearchService(client, objectMapper);
        indexService.initializeIndex();
        indexService.clearIndex();
    }

    @Test
    void searchHotels_ReturnsIndexedHotelFromMeilisearchContainer() throws Exception {
        HotelDocument hotel = HotelDocument.builder()
                .id(UUID.randomUUID())
                .name("Taipei Integration Hotel")
                .description("Container-backed search hotel near Taipei station")
                .city("Taipei")
                .country("Taiwan")
                .address("100 Test Road")
                .starRating(5)
                .minPrice(new BigDecimal("120.00"))
                .maxPrice(new BigDecimal("240.00"))
                .amenities(List.of("WiFi", "Gym"))
                .latitude(25.0478)
                .longitude(121.5170)
                .totalRooms(80)
                .availableRooms(12)
                .averageRating(4.7)
                .reviewCount(42)
                .isActive(true)
                .build();
        indexService.indexHotel(hotel);

        SearchResponse response = waitForHotel("Taipei Integration", "Taipei");

        assertTrue(response.getTotal() >= 1);
        assertFalse(response.getHotels().isEmpty());
        assertTrue(response.getHotels().stream()
                .anyMatch(result -> hotel.getId().equals(result.getId())));
    }

    private SearchResponse waitForHotel(String query, String city) throws Exception {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .city(city)
                .limit(10)
                .offset(0)
                .build();

        SearchResponse lastResponse = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            lastResponse = searchService.searchHotels(request);
            if (lastResponse.getHotels() != null && !lastResponse.getHotels().isEmpty()) {
                return lastResponse;
            }
            Thread.sleep(250);
        }
        return lastResponse;
    }

    private String meilisearchUrl() {
        return "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(7700);
    }
}
