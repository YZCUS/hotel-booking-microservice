package com.hotel.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.hotel.search.model.HotelDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {
    
    private final Client meilisearchClient;
    private final ObjectMapper objectMapper;
    
    public static final String HOTEL_INDEX = "hotels";
    
    @PostConstruct
    public void initializeIndex() {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            
            // Set searchable attributes
            index.updateSearchableAttributesSettings(
                new String[]{"name", "description", "city", "country", "amenities", "address"}
            );
            
            // Set filterable attributes
            index.updateFilterableAttributesSettings(
                new String[]{"city", "country", "starRating", "minPrice", "maxPrice", "amenities", "isActive"}
            );
            
            // Set sortable attributes
            index.updateSortableAttributesSettings(
                new String[]{"starRating", "minPrice", "maxPrice", "averageRating", "reviewCount"}
            );
            
            // Set ranking rules
            index.updateRankingRulesSettings(
                new String[]{"words", "typo", "proximity", "attribute", "sort", "exactness"}
            );
            
            // Set synonyms for better search experience
            index.updateSynonymsSettings(
                java.util.Map.of(
                    "hotel", new String[]{"accommodation", "lodge", "inn"},
                    "luxury", new String[]{"premium", "deluxe", "high-end"},
                    "budget", new String[]{"cheap", "affordable", "economic"}
                )
            );
            
            log.info("Meilisearch index '{}' initialized successfully", HOTEL_INDEX);
            
        } catch (Exception e) {
            log.error("Failed to initialize Meilisearch index", e);
            throw new RuntimeException("Index initialization failed", e);
        }
    }
    
    public void indexHotel(HotelDocument hotel) {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            String json = objectMapper.writeValueAsString(java.util.List.of(hotel));
            index.addDocuments(json);
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
            String json = objectMapper.writeValueAsString(hotels);
            index.addDocuments(json);
            log.info("Indexed {} hotels successfully", hotels.size());
        } catch (Exception e) {
            log.error("Failed to index hotels batch", e);
            throw new RuntimeException("Batch hotel indexing failed", e);
        }
    }
    
    public void updateHotel(HotelDocument hotel) {
        try {
            Index index = meilisearchClient.index(HOTEL_INDEX);
            String json = objectMapper.writeValueAsString(java.util.List.of(hotel));
            index.updateDocuments(json);
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
}