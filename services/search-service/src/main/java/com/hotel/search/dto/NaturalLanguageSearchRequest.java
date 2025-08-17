package com.hotel.search.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalLanguageSearchRequest {
    
    @NotBlank(message = "Query cannot be blank")
    private String query;
    
    @Builder.Default
    private Integer limit = 20;
    
    @Builder.Default
    private Integer offset = 0;
    
    private String context;
}