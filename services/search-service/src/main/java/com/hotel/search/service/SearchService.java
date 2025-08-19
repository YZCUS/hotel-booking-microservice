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

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    
    private final Client meilisearchClient;
    private final ObjectMapper objectMapper;
    
    public SearchResponse searchHotels(SearchRequest request) {
        try {
            Index index = meilisearchClient.index(IndexService.HOTEL_INDEX);
            
            // Build search parameters
            String query = request.getQuery() != null ? request.getQuery() : "";
            
            // Create Meilisearch search request with all parameters
            com.meilisearch.sdk.SearchRequest searchRequest = 
                new com.meilisearch.sdk.SearchRequest(query);
            
            // Apply filters
            String filterString = buildFilterString(request);
            if (!filterString.isEmpty()) {
                searchRequest.setFilter(new String[]{filterString});
            }
            
            // Apply pagination
            if (request.getLimit() != null) {
                searchRequest.setLimit(request.getLimit());
            }
            if (request.getOffset() != null) {
                searchRequest.setOffset(request.getOffset());
            }
            
            // Apply sorting
            if (request.getSortBy() != null && !request.getSortBy().isEmpty()) {
                String sortParam = buildSortParameter(request.getSortBy(), request.getSortOrder());
                searchRequest.setSort(new String[]{sortParam});
            }
            
            // Apply geo-radius filter if location is provided
            if (request.getLatitude() != null && request.getLongitude() != null && 
                request.getRadiusKm() != null) {
                String geoFilter = String.format("_geoRadius(%f, %f, %f)", 
                    request.getLatitude(), 
                    request.getLongitude(), 
                    request.getRadiusKm() * 1000); // Convert km to meters
                    
                if (!filterString.isEmpty()) {
                    searchRequest.setFilter(new String[]{filterString + " AND " + geoFilter});
                } else {
                    searchRequest.setFilter(new String[]{geoFilter});
                }
            }
            
            // Execute search (handle different return types in SDK 0.11.1)
            SearchResult result = (SearchResult) index.search(searchRequest);
            
            // Process results
            List<HotelDocument> hotels = processSearchResults(result);
            
            // Build response
            return SearchResponse.builder()
                .hotels(hotels)
                .total((long) result.getEstimatedTotalHits())
                .offset(request.getOffset())
                .limit(request.getLimit())
                .processingTime((long) result.getProcessingTimeMs())
                .query(request.getQuery())
                .appliedFilters(getAppliedFilters(request))
                .build();
                
        } catch (Exception e) {
            log.error("Search failed for request: {}", request, e);
            throw new RuntimeException("Search operation failed", e);
        }
    }
    
    public SearchResponse naturalLanguageSearch(String query) {
        log.info("Processing natural language search: {}", query);
        
        // Parse natural language query
        SearchRequest processedRequest = parseNaturalLanguageQuery(query);
        
        // Execute search with processed request
        return searchHotels(processedRequest);
    }
    
    private String buildFilterString(SearchRequest request) {
        List<String> filters = new ArrayList<>();
        
        // Always filter for active hotels
        filters.add("isActive = true");
        
        // City filter
        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            filters.add(String.format("city = \"%s\"", escapeFilterValue(request.getCity())));
        }
        
        // Country filter
        if (request.getCountry() != null && !request.getCountry().trim().isEmpty()) {
            filters.add(String.format("country = \"%s\"", escapeFilterValue(request.getCountry())));
        }
        
        // Star rating filters
        if (request.getMinRating() != null) {
            filters.add(String.format("starRating >= %d", request.getMinRating()));
        }
        if (request.getMaxRating() != null) {
            filters.add(String.format("starRating <= %d", request.getMaxRating()));
        }
        
        // Price filters
        if (request.getMinPrice() != null) {
            filters.add(String.format("minPrice >= %s", request.getMinPrice()));
        }
        if (request.getMaxPrice() != null) {
            filters.add(String.format("maxPrice <= %s", request.getMaxPrice()));
        }
        
        // Amenities filter (OR condition for multiple amenities)
        if (request.getAmenities() != null && !request.getAmenities().isEmpty()) {
            List<String> amenityFilters = request.getAmenities().stream()
                .map(amenity -> String.format("amenities = \"%s\"", escapeFilterValue(amenity)))
                .collect(Collectors.toList());
            filters.add("(" + String.join(" OR ", amenityFilters) + ")");
        }
        
        // Join all filters with AND
        return String.join(" AND ", filters);
    }
    
    private String buildSortParameter(String sortBy, String sortOrder) {
        // Validate sort field
        List<String> validSortFields = Arrays.asList(
            "starRating", "minPrice", "maxPrice", "averageRating", "reviewCount"
        );
        
        if (!validSortFields.contains(sortBy)) {
            log.warn("Invalid sort field: {}, using default", sortBy);
            return "starRating:desc";
        }
        
        // Determine sort direction
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        
        return sortBy + ":" + direction;
    }
    
    private List<HotelDocument> processSearchResults(SearchResult result) {
        List<HotelDocument> hotels = new ArrayList<>();
        
        if (result.getHits() != null && !result.getHits().isEmpty()) {
            for (Object hit : result.getHits()) {
                try {
                    // Convert LinkedHashMap to HotelDocument
                    HotelDocument hotel = objectMapper.convertValue(hit, HotelDocument.class);
                    if (hotel != null) {
                        hotels.add(hotel);
                    }
                } catch (Exception e) {
                    log.warn("Failed to convert search hit to HotelDocument: {}", e.getMessage());
                }
            }
        }
        
        return hotels;
    }
    
    private List<String> getAppliedFilters(SearchRequest request) {
        List<String> filters = new ArrayList<>();
        
        if (request.getCity() != null) {
            filters.add("city: " + request.getCity());
        }
        if (request.getCountry() != null) {
            filters.add("country: " + request.getCountry());
        }
        if (request.getMinRating() != null) {
            filters.add("min rating: " + request.getMinRating());
        }
        if (request.getMaxRating() != null) {
            filters.add("max rating: " + request.getMaxRating());
        }
        if (request.getMinPrice() != null) {
            filters.add("min price: $" + request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            filters.add("max price: $" + request.getMaxPrice());
        }
        if (request.getAmenities() != null && !request.getAmenities().isEmpty()) {
            filters.add("amenities: " + String.join(", ", request.getAmenities()));
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            filters.add(String.format("location: within %.1f km", request.getRadiusKm()));
        }
        
        return filters;
    }
    
    private String escapeFilterValue(String value) {
        // Escape quotes and special characters for Meilisearch
        return value.replace("\"", "\\\"")
                   .replace("\\", "\\\\");
    }
    
    private SearchRequest parseNaturalLanguageQuery(String query) {
        SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
            .query(query)
            .limit(20)
            .offset(0);
        
        String lowerQuery = query.toLowerCase();
        
        // Extract city
        String city = extractCity(lowerQuery);
        if (city != null) {
            builder.city(city);
        }
        
        // Extract star rating
        Integer rating = extractRating(lowerQuery);
        if (rating != null) {
            builder.minRating(rating);
        }
        
        // Extract price range
        extractPriceRange(lowerQuery, builder);
        
        // Extract amenities
        List<String> amenities = extractAmenities(lowerQuery);
        if (!amenities.isEmpty()) {
            builder.amenities(amenities);
        }
        
        // Extract sorting preference
        String sortBy = extractSortPreference(lowerQuery);
        if (sortBy != null) {
            builder.sortBy(sortBy);
            builder.sortOrder(extractSortOrder(lowerQuery));
        }
        
        return builder.build();
    }
    
    private String extractCity(String query) {
        // Pattern to find city after "in"
        Pattern cityPattern = Pattern.compile("\\bin\\s+([a-zA-Z\\s]+?)(?:\\s|$|,|\\.)");
        Matcher matcher = cityPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Check for known cities
        List<String> knownCities = Arrays.asList(
            "tokyo", "paris", "london", "new york", "singapore", 
            "taipei", "hong kong", "bangkok", "dubai", "amsterdam"
        );
        
        for (String city : knownCities) {
            if (query.contains(city)) {
                return city.substring(0, 1).toUpperCase() + city.substring(1);
            }
        }
        
        return null;
    }
    
    private Integer extractRating(String query) {
        // Pattern to find star rating
        Pattern ratingPattern = Pattern.compile("(\\d+)\\s*star");
        Matcher matcher = ratingPattern.matcher(query);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        // Check for quality keywords
        if (query.contains("luxury") || query.contains("premium") || query.contains("deluxe")) {
            return 4;
        }
        if (query.contains("budget") || query.contains("cheap") || query.contains("economy")) {
            return 2;
        }
        if (query.contains("mid-range") || query.contains("moderate")) {
            return 3;
        }
        
        return null;
    }
    
    private void extractPriceRange(String query, SearchRequest.SearchRequestBuilder builder) {
        // Pattern for "under $X" or "below $X"
        Pattern maxPricePattern = Pattern.compile("(?:under|below|less than|cheaper than)\\s*\\$?(\\d+)");
        Matcher maxMatcher = maxPricePattern.matcher(query);
        if (maxMatcher.find()) {
            builder.maxPrice(new BigDecimal(maxMatcher.group(1)));
        }
        
        // Pattern for "over $X" or "above $X"
        Pattern minPricePattern = Pattern.compile("(?:over|above|more than)\\s*\\$?(\\d+)");
        Matcher minMatcher = minPricePattern.matcher(query);
        if (minMatcher.find()) {
            builder.minPrice(new BigDecimal(minMatcher.group(1)));
        }
        
        // Pattern for price range "$X to $Y"
        Pattern rangePattern = Pattern.compile("\\$?(\\d+)\\s*(?:to|-)\\s*\\$?(\\d+)");
        Matcher rangeMatcher = rangePattern.matcher(query);
        if (rangeMatcher.find()) {
            builder.minPrice(new BigDecimal(rangeMatcher.group(1)));
            builder.maxPrice(new BigDecimal(rangeMatcher.group(2)));
        }
    }
    
    private List<String> extractAmenities(String query) {
        List<String> amenities = new ArrayList<>();
        
        Map<String, List<String>> amenityKeywords = Map.of(
            "WiFi", Arrays.asList("wifi", "wi-fi", "internet", "wireless"),
            "Pool", Arrays.asList("pool", "swimming pool", "swim"),
            "Spa", Arrays.asList("spa", "wellness", "massage"),
            "Gym", Arrays.asList("gym", "fitness", "workout", "exercise"),
            "Parking", Arrays.asList("parking", "car park", "garage"),
            "Restaurant", Arrays.asList("restaurant", "dining", "food"),
            "Bar", Arrays.asList("bar", "lounge", "drinks"),
            "Breakfast", Arrays.asList("breakfast", "morning meal"),
            "Business Center", Arrays.asList("business center", "conference", "meeting room"),
            "Pet Friendly", Arrays.asList("pet", "dog", "cat", "pet-friendly")
        );
        
        for (Map.Entry<String, List<String>> entry : amenityKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (query.contains(keyword)) {
                    amenities.add(entry.getKey());
                    break;
                }
            }
        }
        
        return amenities;
    }
    
    private String extractSortPreference(String query) {
        if (query.contains("cheapest") || query.contains("lowest price")) {
            return "minPrice";
        }
        if (query.contains("best rated") || query.contains("highest rated") || query.contains("top rated")) {
            return "averageRating";
        }
        if (query.contains("most popular") || query.contains("most reviewed")) {
            return "reviewCount";
        }
        if (query.contains("luxury") || query.contains("highest quality")) {
            return "starRating";
        }
        
        return null;
    }
    
    private String extractSortOrder(String query) {
        if (query.contains("cheapest") || query.contains("lowest")) {
            return "asc";
        }
        return "desc"; // Default to descending for ratings and popularity
    }
}