package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.response.HotelResponse;
import com.example.hotelbooking.dto.response.HotelSearchResult;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.repository.HotelRepository;
import com.example.hotelbooking.repository.HotelRepository.HotelMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Geo search (PostGIS) and "Search by Vibe" (pgvector + Spring AI embeddings).
 */
@Service
@RequiredArgsConstructor
public class HotelSearchService {

    private static final double DEFAULT_RADIUS_KM = 5.0;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 500;

    private final HotelRepository hotelRepository;
    private final HotelEmbeddingService embeddingService;

    @Value("${app.geo-search.max-radius-km:100}")
    private double maxRadiusKm;

    /** Hotels within radiusKm of (lat, lng), nearest first. */
    @Transactional(readOnly = true)
    public List<HotelSearchResult> findNearby(double lat, double lng, Double radiusKm, int limit) {
        validateCoordinates(lat, lng);
        double radius = validateRadius(radiusKm);
        List<HotelMatch> matches = hotelRepository.findWithinRadius(lat, lng, radius * 1000, validateLimit(limit));
        return toResults(matches);
    }

    /**
     * Natural-language search: embed the query and rank hotels by cosine similarity.
     * Optionally restricted to a radius around (lat, lng).
     */
    @Transactional(readOnly = true)
    public List<HotelSearchResult> vibeSearch(String query, Double lat, Double lng, Double radiusKm, int limit) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query can be at most " + MAX_QUERY_LENGTH + " characters");
        }
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("Provide both lat and lng, or neither");
        }
        int safeLimit = validateLimit(limit);

        String embedding = embeddingService.embedQuery(query);

        List<HotelMatch> matches;
        if (lat != null) {
            validateCoordinates(lat, lng);
            double radius = validateRadius(radiusKm);
            matches = hotelRepository.findMostSimilarWithinRadius(embedding, lat, lng, radius * 1000, safeLimit);
        } else {
            matches = hotelRepository.findMostSimilar(embedding, safeLimit);
        }
        return toResults(matches);
    }

    private List<HotelSearchResult> toResults(List<HotelMatch> matches) {
        Map<Long, Hotel> hotelsById = hotelRepository.findAllById(matches.stream().map(HotelMatch::getId).toList())
                .stream()
                .collect(Collectors.toMap(Hotel::getId, Function.identity()));

        // Keep the database's ranking order
        return matches.stream()
                .map(match -> {
                    Hotel hotel = hotelsById.get(match.getId());
                    if (hotel == null) {
                        return null;
                    }
                    return HotelSearchResult.builder()
                            .hotel(HotelResponse.from(hotel))
                            .distanceKm(match.getDistanceMeters() == null ? null : round(match.getDistanceMeters() / 1000, 2))
                            .matchScore(match.getSimilarity() == null ? null : round(match.getSimilarity(), 4))
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng must be between -180 and 180");
        }
    }

    private double validateRadius(Double radiusKm) {
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (radius <= 0 || radius > maxRadiusKm) {
            throw new IllegalArgumentException("radiusKm must be greater than 0 and at most " + maxRadiusKm);
        }
        return radius;
    }

    private static int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
