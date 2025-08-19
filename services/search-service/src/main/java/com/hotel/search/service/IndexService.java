package com.hotel.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import com.hotel.search.model.HotelDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {
    
    private final Client meilisearchClient;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private WebClient.Builder webClientBuilder;
    
    public static final String HOTEL_INDEX = "hotels";
    private static final int BATCH_SIZE = 100;
    
    @PostConstruct
    public void initializeIndex() {
        try {
            // Create or get the index
            Index index;
            try {
                index = meilisearchClient.getIndex(HOTEL_INDEX);
                log.info("Index '{}' already exists", HOTEL_INDEX);
            } catch (Exception e) {
                meilisearchClient.createIndex(HOTEL_INDEX, "id");
                index = meilisearchClient.getIndex(HOTEL_INDEX);
                log.info("Created new index '{}'", HOTEL_INDEX);
            }
            
            // Configure index settings
            Settings settings = new Settings();
            
            // Searchable attributes (fields that can be searched)
            settings.setSearchableAttributes(new String[]{
                "name",
                "description", 
                "city",
                "country",
                "amenities",
                "address"
            });
            
            // Displayed attributes (fields returned in search results)
            settings.setDisplayedAttributes(new String[]{
                "id",
                "name",
                "description",
                "city",
                "country",
                "address",
                "starRating",
                "minPrice",
                "maxPrice",
                "amenities",
                "latitude",
                "longitude",
                "imageUrls",
                "totalRooms",
                "availableRooms",
                "averageRating",
                "reviewCount",
                "isActive",
                "_geo"
            });
            
            // Filterable attributes (fields that can be filtered)
            settings.setFilterableAttributes(new String[]{
                "city",
                "country",
                "starRating",
                "minPrice",
                "maxPrice",
                "amenities",
                "isActive",
                "averageRating",
                "reviewCount",
                "_geo"  // Enable geo filtering
            });
            
            // Sortable attributes (fields that can be sorted)
            settings.setSortableAttributes(new String[]{
                "starRating",
                "minPrice",
                "maxPrice",
                "averageRating",
                "reviewCount",
                "name",
                "_geo"  // Enable geo sorting (distance)
            });
            
            // Ranking rules (order matters!)
            settings.setRankingRules(new String[]{
                "words",        // Number of words matching
                "typo",         // Typo tolerance
                "proximity",    // Proximity of words to each other
                "attribute",    // Attribute order importance
                "sort",         // Custom sorting
                "exactness"     // Exact match bonus
            });
            
            // Configure synonyms for better search experience (simplified for SDK 0.11.1)
            HashMap<String, String[]> synonyms = new HashMap<>();
            synonyms.put("hotel", new String[]{"accommodation", "lodge", "inn", "resort", "motel"});
            synonyms.put("luxury", new String[]{"premium", "deluxe", "high-end", "upscale", "5-star"});
            synonyms.put("budget", new String[]{"cheap", "affordable", "economic", "value", "low-cost"});
            synonyms.put("wifi", new String[]{"wi-fi", "internet", "wireless", "connection"});
            synonyms.put("pool", new String[]{"swimming pool", "swim", "aquatic"});
            synonyms.put("gym", new String[]{"fitness", "workout", "exercise", "fitness center"});
            synonyms.put("restaurant", new String[]{"dining", "food", "cuisine", "eatery"});
            synonyms.put("parking", new String[]{"car park", "garage", "valet"});
            
            settings.setSynonyms(synonyms);
            
            // Note: TypoTolerance configuration not available in SDK 0.11.1
            log.debug("Advanced typo tolerance configuration skipped for SDK 0.11.1 compatibility");
            
            // Apply settings to index
            index.updateSettings(settings);
            
            log.info("Meilisearch index '{}' configured successfully", HOTEL_INDEX);
            
            // Initialize with sample data if empty
            initializeSampleData(index);
            
        } catch (Exception e) {
            log.error("Failed to initialize Meilisearch index", e);
            throw new RuntimeException("Index initialization failed", e);
        }
    }
    
    public void indexHotel(HotelDocument hotel) {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            
            // Add geo coordinates if available
            if (hotel.getLatitude() != null && hotel.getLongitude() != null) {
                Map<String, Object> hotelMap = objectMapper.convertValue(hotel, Map.class);
                hotelMap.put("_geo", Map.of(
                    "lat", hotel.getLatitude(),
                    "lng", hotel.getLongitude()
                ));
                
                String json = objectMapper.writeValueAsString(List.of(hotelMap));
                index.addDocuments(json);
            } else {
                String json = objectMapper.writeValueAsString(List.of(hotel));
                index.addDocuments(json);
            }
            
            log.info("Hotel indexed successfully: {}", hotel.getId());
        } catch (Exception e) {
            log.error("Failed to index hotel: {}", hotel.getId(), e);
            throw new RuntimeException("Hotel indexing failed", e);
        }
    }
    
    public void indexHotels(List<HotelDocument> hotels) {
        try {
            if (hotels == null || hotels.isEmpty()) {
                return;
            }
            
            Index index = meilisearchClient.index(HOTEL_INDEX);
            
            // Process in batches
            for (int i = 0; i < hotels.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, hotels.size());
                List<HotelDocument> batch = hotels.subList(i, end);
                
                // Add geo coordinates to each hotel
                List<Map<String, Object>> hotelMaps = batch.stream()
                    .map(hotel -> {
                        Map<String, Object> hotelMap = objectMapper.convertValue(hotel, Map.class);
                        if (hotel.getLatitude() != null && hotel.getLongitude() != null) {
                            hotelMap.put("_geo", Map.of(
                                "lat", hotel.getLatitude(),
                                "lng", hotel.getLongitude()
                            ));
                        }
                        return hotelMap;
                    })
                    .collect(Collectors.toList());
                
                String json = objectMapper.writeValueAsString(hotelMaps);
                index.addDocuments(json);
                
                log.info("Indexed batch of {} hotels", batch.size());
            }
            
            log.info("Indexed {} hotels successfully", hotels.size());
        } catch (Exception e) {
            log.error("Failed to index hotels batch", e);
            throw new RuntimeException("Batch hotel indexing failed", e);
        }
    }
    
    public void updateHotel(HotelDocument hotel) {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            
            // Add geo coordinates if available
            if (hotel.getLatitude() != null && hotel.getLongitude() != null) {
                Map<String, Object> hotelMap = objectMapper.convertValue(hotel, Map.class);
                hotelMap.put("_geo", Map.of(
                    "lat", hotel.getLatitude(),
                    "lng", hotel.getLongitude()
                ));
                
                String json = objectMapper.writeValueAsString(List.of(hotelMap));
                index.updateDocuments(json);
            } else {
                String json = objectMapper.writeValueAsString(List.of(hotel));
                index.updateDocuments(json);
            }
            
            log.info("Hotel updated successfully: {}", hotel.getId());
        } catch (Exception e) {
            log.error("Failed to update hotel: {}", hotel.getId(), e);
            throw new RuntimeException("Hotel update failed", e);
        }
    }
    
    public void deleteHotel(String hotelId) {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            index.deleteDocument(hotelId);
            log.info("Hotel deleted successfully: {}", hotelId);
        } catch (Exception e) {
            log.error("Failed to delete hotel: {}", hotelId, e);
            throw new RuntimeException("Hotel deletion failed", e);
        }
    }
    
    public void clearIndex() {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            index.deleteAllDocuments();
            log.info("Hotel index cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear hotel index", e);
            throw new RuntimeException("Index clearing failed", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000) // Sync every hour
    public void syncWithHotelService() {
        if (webClientBuilder == null) {
            log.debug("WebClient not configured, skipping hotel sync");
            return;
        }
        
        try {
            log.info("Starting hotel data synchronization");
            
            WebClient webClient = webClientBuilder.build();
            
            // Fetch hotels from hotel service
            List<HotelDocument> hotels = webClient.get()
                .uri("http://hotel-service:8082/api/v1/hotels/export")
                .retrieve()
                .bodyToFlux(HotelDocument.class)
                .collectList()
                .block();
            
            if (hotels != null && !hotels.isEmpty()) {
                indexHotels(hotels);
                log.info("Synchronized {} hotels from hotel service", hotels.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to sync hotels from hotel service", e);
        }
    }
    
    private void initializeSampleData(Index index) {
        try {
            // Check if index is empty
            com.meilisearch.sdk.SearchRequest searchRequest = new com.meilisearch.sdk.SearchRequest("");
            searchRequest.setLimit(1);
            
            if (index.search(searchRequest).getHits().isEmpty()) {
                log.info("Index is empty, initializing with sample data");
                
                List<HotelDocument> sampleHotels = createSampleHotels();
                indexHotels(sampleHotels);
                
                log.info("Initialized index with {} sample hotels", sampleHotels.size());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize sample data", e);
        }
    }
    
    private List<HotelDocument> createSampleHotels() {
        return Arrays.asList(
            HotelDocument.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .name("Grand Hotel Taipei")
                .description("Luxury hotel in the heart of Taipei with stunning city views")
                .city("Taipei")
                .country("Taiwan")
                .address("123 Main St, Xinyi District")
                .starRating(5)
                .minPrice(new java.math.BigDecimal("150.00"))
                .maxPrice(new java.math.BigDecimal("500.00"))
                .amenities(Arrays.asList("WiFi", "Pool", "Gym", "Spa", "Restaurant", "Bar"))
                .latitude(25.0330)
                .longitude(121.5654)
                .totalRooms(200)
                .availableRooms(45)
                .averageRating(4.8)
                .reviewCount(1250)
                .isActive(true)
                .build(),
                
            HotelDocument.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
                .name("City Business Hotel")
                .description("Modern business hotel with excellent conference facilities")
                .city("Taipei")
                .country("Taiwan")
                .address("456 Business Ave, Zhongshan District")
                .starRating(4)
                .minPrice(new java.math.BigDecimal("80.00"))
                .maxPrice(new java.math.BigDecimal("200.00"))
                .amenities(Arrays.asList("WiFi", "Conference Room", "Restaurant", "Gym", "Business Center"))
                .latitude(25.0478)
                .longitude(121.5319)
                .totalRooms(150)
                .availableRooms(30)
                .averageRating(4.5)
                .reviewCount(890)
                .isActive(true)
                .build(),
                
            HotelDocument.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))
                .name("Budget Inn Express")
                .description("Affordable accommodation for budget-conscious travelers")
                .city("Taipei")
                .country("Taiwan")
                .address("789 Budget St, Wanhua District")
                .starRating(3)
                .minPrice(new java.math.BigDecimal("40.00"))
                .maxPrice(new java.math.BigDecimal("80.00"))
                .amenities(Arrays.asList("WiFi", "24h Reception", "Breakfast"))
                .latitude(25.0375)
                .longitude(121.4999)
                .totalRooms(80)
                .availableRooms(25)
                .averageRating(4.0)
                .reviewCount(450)
                .isActive(true)
                .build()
        );
    }
}