package com.hotel.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchResult;
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
    
    public SearchResponse searchHotels(SearchRequest request) {
        try {
            Index index = meilisearchClient.index(IndexService.HOTEL_INDEX);
            
            // Build search parameters
            com.meilisearch.sdk.SearchRequest.Builder searchBuilder = 
                com.meilisearch.sdk.SearchRequest.builder()
                    .q(request.getQuery() != null ? request.getQuery() : "")
                    .limit(request.getLimit())
                    .offset(request.getOffset());
            
            // Build filters
            List<String> filters = buildFilters(request);
            if (!filters.isEmpty()) {
                searchBuilder.filter(String.join(" AND ", filters));
            }
            
            // Build sort
            if (request.getSortBy() != null) {
                String sortDirection = "desc".equalsIgnoreCase(request.getSortOrder()) ? ":desc" : ":asc";
                searchBuilder.sort(new String[]{request.getSortBy() + sortDirection});
            }
            
            // Execute search
            SearchResult result = index.search(searchBuilder.build());
            
            // Convert to HotelDocument list
            List<HotelDocument> hotels = new ArrayList<>();
            if (result.getHits() != null) {
                for (Object hit : result.getHits()) {
                    try {
                        // Convert hit to HotelDocument
                        HotelDocument hotel = convertToHotelDocument(hit);
                        if (hotel != null) {
                            hotels.add(hotel);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to convert search hit to HotelDocument", e);
                    }
                }
            }
            
            return SearchResponse.builder()
                .hotels(hotels)
                .total(result.getTotalHits() != null ? result.getTotalHits() : 0L)
                .offset(request.getOffset())
                .limit(request.getLimit())
                .processingTime(result.getProcessingTimeMs() != null ? result.getProcessingTimeMs() : 0L)
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
        
        // Process natural language query
        SearchRequest processedRequest = processNaturalLanguage(query);
        
        return searchHotels(processedRequest);
    }
    
    private List<String> buildFilters(SearchRequest request) {
        List<String> filters = new ArrayList<>();
        
        // Active hotels only
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
        
        // Extract city mentions
        String city = extractCity(lowerQuery);
        if (city != null) {
            builder.city(city);
        }
        
        // Extract star rating
        Integer rating = extractRating(lowerQuery);
        if (rating != null) {
            builder.minRating(rating);
        }
        
        // Extract price mentions
        extractPrice(lowerQuery, builder);
        
        // Extract amenities
        List<String> amenities = extractAmenities(lowerQuery);
        if (!amenities.isEmpty()) {
            builder.amenities(amenities);
        }
        
        return builder.build();
    }
    
    private String extractCity(String query) {
        // Simple pattern matching for city extraction
        Pattern cityPattern = Pattern.compile("in\\s+([a-zA-Z\\s]+?)(?:\\s|$|,|\\.)");
        Matcher matcher = cityPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Common city names
        String[] cities = {"tokyo", "paris", "london", "new york", "singapore", "taipei", "hong kong"};
        for (String city : cities) {
            if (query.contains(city)) {
                return city;
            }
        }
        
        return null;
    }
    
    private Integer extractRating(String query) {
        // Extract star ratings
        Pattern ratingPattern = Pattern.compile("(\\d+)\\s*star");
        Matcher matcher = ratingPattern.matcher(query);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        if (query.contains("luxury") || query.contains("premium")) {
            return 4;
        }
        
        if (query.contains("budget") || query.contains("cheap")) {
            return 2;
        }
        
        return null;
    }
    
    private void extractPrice(String query, SearchRequest.SearchRequestBuilder builder) {
        // Extract price ranges
        Pattern pricePattern = Pattern.compile("under\\s*\\$?(\\d+)");
        Matcher matcher = pricePattern.matcher(query);
        if (matcher.find()) {
            builder.maxPrice(new java.math.BigDecimal(matcher.group(1)));
        }
        
        Pattern minPricePattern = Pattern.compile("over\\s*\\$?(\\d+)");
        Matcher minMatcher = minPricePattern.matcher(query);
        if (minMatcher.find()) {
            builder.minPrice(new java.math.BigDecimal(minMatcher.group(1)));
        }
    }
    
    private List<String> extractAmenities(String query) {
        List<String> amenities = new ArrayList<>();
        
        String[] amenityKeywords = {
            "pool", "swimming pool", "spa", "gym", "fitness", "wifi", "parking", 
            "restaurant", "bar", "breakfast", "room service", "business center",
            "conference", "meeting room", "concierge", "valet"
        };
        
        for (String amenity : amenityKeywords) {
            if (query.contains(amenity)) {
                amenities.add(amenity);
            }
        }
        
        return amenities;
    }
    
    private HotelDocument convertToHotelDocument(Object hit) {
        // This is a simplified conversion - in a real implementation,
        // you would use proper JSON deserialization
        try {
            // For now, return null and let the caller handle it
            // In a real implementation, you would convert the Meilisearch result to HotelDocument
            return null;
        } catch (Exception e) {
            log.warn("Failed to convert search hit to HotelDocument", e);
            return null;
        }
    }
}