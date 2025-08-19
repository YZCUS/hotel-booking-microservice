package com.hotel.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.search.dto.SearchRequest;
import com.hotel.search.dto.SearchResponse;
import com.hotel.search.model.HotelDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    
    private final Client meilisearchClient;
    private final ObjectMapper objectMapper;
    
    public SearchResponse searchHotels(SearchRequest request) {
        try {
            Index index = meilisearchClient.index(IndexService.HOTEL_INDEX);
            
            String query = request.getQuery() != null ? request.getQuery() : "";
            List<String> filters = buildFilters(request);
            
            // Create Meilisearch SearchRequest with filters applied
            com.meilisearch.sdk.SearchRequest searchRequest = new com.meilisearch.sdk.SearchRequest(query);
            
            if (!filters.isEmpty()) {
                searchRequest.setFilter(String.join(" AND ", filters));
                log.debug("Applied filters: {}", filters);
            }
            
            // Apply pagination
            if (request.getOffset() != null) {
                searchRequest.setOffset(request.getOffset());
            }
            if (request.getLimit() != null) {
                searchRequest.setLimit(request.getLimit());
            }
            
            SearchResult result = index.search(searchRequest);
            
            List<HotelDocument> hotels = new ArrayList<>();
            if (result.getHits() != null && !result.getHits().isEmpty()) {
                for (Object hit : result.getHits()) {
                    try {
                        HotelDocument hotel = objectMapper.convertValue(hit, HotelDocument.class);
                        if (hotel != null) {
                            hotels.add(hotel);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to convert search hit to HotelDocument", e);
                    }
                }
            }
            
            long total = (long) result.getEstimatedTotalHits();
            long processingMs = (long) result.getProcessingTimeMs();
            
            return SearchResponse.builder()
                .hotels(hotels)
                .total(total)
                .offset(request.getOffset())
                .limit(request.getLimit())
                .processingTime(processingMs)
                .query(request.getQuery())
                .appliedFilters(filters)
                .build();
                
        } catch (Exception e) {
            log.error("Search failed for request: {}", request, e);
            throw new RuntimeException("Search operation failed", e);
        }
    }
    
    public SearchResponse naturalLanguageSearch(String query) {
        log.info("Processing natural language search: {}", query);
        SearchRequest processedRequest = processNaturalLanguage(query);
        return searchHotels(processedRequest);
    }
    
    private List<String> buildFilters(SearchRequest request) {
        List<String> filters = new ArrayList<>();
        filters.add("isActive = true");
        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            filters.add("city = '" + escapeFilterValue(request.getCity()) + "'");
        }
        if (request.getCountry() != null && !request.getCountry().trim().isEmpty()) {
            filters.add("country = '" + escapeFilterValue(request.getCountry()) + "'");
        }
        if (request.getMinRating() != null) {
            filters.add("starRating >= " + request.getMinRating());
        }
        if (request.getMaxRating() != null) {
            filters.add("starRating <= " + request.getMaxRating());
        }
        if (request.getMinPrice() != null) {
            filters.add("minPrice >= " + request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            filters.add("maxPrice <= " + request.getMaxPrice());
        }
        if (request.getAmenities() != null && !request.getAmenities().isEmpty()) {
            List<String> amenityFilters = new ArrayList<>();
            for (String amenity : request.getAmenities()) {
                amenityFilters.add("amenities = '" + escapeFilterValue(amenity) + "'");
            }
            filters.add("(" + String.join(" OR ", amenityFilters) + ")");
        }
        return filters;
    }
    
    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"");
    }
    
    private SearchRequest processNaturalLanguage(String query) {
        SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
            .query(query)
            .limit(20);
        String lowerQuery = query.toLowerCase();
        String city = extractCity(lowerQuery);
        if (city != null) builder.city(city);
        Integer rating = extractRating(lowerQuery);
        if (rating != null) builder.minRating(rating);
        extractPrice(lowerQuery, builder);
        List<String> amenities = extractAmenities(lowerQuery);
        if (!amenities.isEmpty()) builder.amenities(amenities);
        return builder.build();
    }

    private String extractCity(String query) {
        Pattern cityPattern = Pattern.compile("in\\s+([a-zA-Z\\s]+?)(?:\\s|$|,|\\.)");
        Matcher matcher = cityPattern.matcher(query);
        if (matcher.find()) return matcher.group(1).trim();
        String[] cities = {"tokyo", "paris", "london", "new york", "singapore", "taipei", "hong kong"};
        for (String city : cities) if (query.contains(city)) return city;
        return null;
    }

    private Integer extractRating(String query) {
        Pattern ratingPattern = Pattern.compile("(\\d+)\\s*star");
        Matcher matcher = ratingPattern.matcher(query);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        if (query.contains("luxury") || query.contains("premium")) return 4;
        if (query.contains("budget") || query.contains("cheap")) return 2;
        return null;
    }

    private void extractPrice(String query, SearchRequest.SearchRequestBuilder builder) {
        Pattern pricePattern = Pattern.compile("under\\s*\\$?(\\d+)");
        Matcher matcher = pricePattern.matcher(query);
        if (matcher.find()) builder.maxPrice(new java.math.BigDecimal(matcher.group(1)));
        Pattern minPricePattern = Pattern.compile("over\\s*\\$?(\\d+)");
        Matcher minMatcher = minPricePattern.matcher(query);
        if (minMatcher.find()) builder.minPrice(new java.math.BigDecimal(minMatcher.group(1)));
    }

    private List<String> extractAmenities(String query) {
        List<String> amenities = new ArrayList<>();
        String[] amenityKeywords = {"pool", "swimming pool", "spa", "gym", "fitness", "wifi", "parking", 
            "restaurant", "bar", "breakfast", "room service", "business center",
            "conference", "meeting room", "concierge", "valet"};
        for (String amenity : amenityKeywords) if (query.contains(amenity)) amenities.add(amenity);
        return amenities;
    }
}