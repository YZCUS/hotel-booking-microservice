package com.hotel.search.dto;

import com.hotel.search.model.HotelDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    
    private List<HotelDocument> hotels;
    
    private Long total;
    
    private Integer offset;
    
    private Integer limit;
    
    private Long processingTime;
    
    private String query;
    
    private List<String> appliedFilters;
    
    private Boolean hasMore;
    
    public Boolean getHasMore() {
        if (total == null || offset == null || limit == null) {
            return false;
        }
        return (offset + limit) < total;
    }
}