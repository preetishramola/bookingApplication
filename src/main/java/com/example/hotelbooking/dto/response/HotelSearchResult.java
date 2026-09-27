package com.example.hotelbooking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

// A hotel plus how it matched: distance (geo search) and/or similarity (vibe search).
// Fields that don't apply to the search are left out of the JSON.
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HotelSearchResult {
    private HotelResponse hotel;
    private Double distanceKm;
    private Double matchScore; // cosine similarity, 1.0 = identical meaning
}
