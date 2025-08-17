package com.hotel.search.controller;

import com.hotel.search.dto.NaturalLanguageSearchRequest;
import com.hotel.search.dto.SearchRequest;
import com.hotel.search.dto.SearchResponse;
import com.hotel.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {
    
    private final SearchService searchService;
    
    @PostMapping("/hotels")
    public ResponseEntity<SearchResponse> searchHotels(@Valid @RequestBody SearchRequest request) {
        log.info("Searching hotels with request: {}", request);
        SearchResponse response = searchService.searchHotels(request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/hotels")
    public ResponseEntity<SearchResponse> searchHotelsGet(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .city(city)
                .country(country)
                .minRating(minRating)
                .maxRating(maxRating)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .offset(offset)
                .limit(limit)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();
        
        log.info("Searching hotels with GET request: {}", request);
        SearchResponse response = searchService.searchHotels(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/hotels/natural")
    public ResponseEntity<SearchResponse> naturalLanguageSearch(
            @Valid @RequestBody NaturalLanguageSearchRequest request) {
        log.info("Natural language search: {}", request.getQuery());
        SearchResponse response = searchService.naturalLanguageSearch(request.getQuery());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/hotels/natural")
    public ResponseEntity<SearchResponse> naturalLanguageSearchGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        
        log.info("Natural language search GET: {}", query);
        SearchResponse response = searchService.naturalLanguageSearch(query);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/suggestions")
    public ResponseEntity<java.util.List<String>> getSearchSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer limit) {
        log.info("Getting search suggestions for: {}", query);
        
        // Simple suggestion implementation
        java.util.List<String> suggestions = java.util.Arrays.asList(
            query + " hotels",
            query + " luxury hotels",
            query + " budget hotels",
            query + " business hotels",
            query + " resort"
        );
        
        return ResponseEntity.ok(suggestions.subList(0, Math.min(limit, suggestions.size())));
    }
}