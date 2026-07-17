package com.hotel.search.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchResponseTest {

    @Test
    void getHasMore_ReturnsTrueWhenMoreResultsRemain() {
        SearchResponse response = SearchResponse.builder()
                .total(25L)
                .offset(0)
                .limit(20)
                .build();

        assertTrue(response.getHasMore());
    }

    @Test
    void getHasMore_ReturnsFalseAtEndOfResults() {
        SearchResponse response = SearchResponse.builder()
                .total(20L)
                .offset(0)
                .limit(20)
                .build();

        assertFalse(response.getHasMore());
    }

    @Test
    void getHasMore_ReturnsFalseWhenPaginationDataIsIncomplete() {
        SearchResponse response = SearchResponse.builder()
                .total(25L)
                .offset(null)
                .limit(20)
                .build();

        assertFalse(response.getHasMore());
    }
}
