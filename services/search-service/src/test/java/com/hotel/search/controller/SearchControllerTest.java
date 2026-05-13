package com.hotel.search.controller;

import com.hotel.search.dto.NaturalLanguageSearchRequest;
import com.hotel.search.dto.SearchRequest;
import com.hotel.search.dto.SearchResponse;
import com.hotel.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SearchControllerTest {

    private SearchService searchService;
    private SearchController controller;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        controller = new SearchController(searchService);
    }

    @Test
    void searchHotelsGet_MapsQueryParametersToSearchRequest() {
        SearchResponse expected = SearchResponse.builder()
                .total(0L)
                .offset(5)
                .limit(10)
                .query("spa")
                .build();
        when(searchService.searchHotels(any(SearchRequest.class))).thenReturn(expected);

        SearchResponse response = controller.searchHotelsGet(
                "spa",
                "Taipei",
                "Taiwan",
                4,
                5,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(300),
                5,
                10,
                "minPrice",
                "desc"
        ).getBody();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchService).searchHotels(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals(expected, response);
        assertEquals("spa", request.getQuery());
        assertEquals("Taipei", request.getCity());
        assertEquals("Taiwan", request.getCountry());
        assertEquals(4, request.getMinRating());
        assertEquals(5, request.getMaxRating());
        assertEquals(BigDecimal.valueOf(100), request.getMinPrice());
        assertEquals(BigDecimal.valueOf(300), request.getMaxPrice());
        assertEquals(5, request.getOffset());
        assertEquals(10, request.getLimit());
        assertEquals("minPrice", request.getSortBy());
        assertEquals("desc", request.getSortOrder());
    }

    @Test
    void naturalLanguageSearch_DelegatesQueryToService() {
        SearchResponse expected = SearchResponse.builder()
                .query("luxury hotel in taipei")
                .total(3L)
                .build();
        when(searchService.naturalLanguageSearch("luxury hotel in taipei")).thenReturn(expected);

        SearchResponse response = controller.naturalLanguageSearch(
                NaturalLanguageSearchRequest.builder()
                        .query("luxury hotel in taipei")
                        .build()
        ).getBody();

        assertEquals(expected, response);
        verify(searchService).naturalLanguageSearch("luxury hotel in taipei");
    }

    @Test
    void getSearchSuggestions_ClampsResultsToLimit() {
        List<String> suggestions = controller.getSearchSuggestions("taipei", 3).getBody();

        assertEquals(List.of(
                "taipei hotels",
                "taipei luxury hotels",
                "taipei budget hotels"
        ), suggestions);
    }
}
