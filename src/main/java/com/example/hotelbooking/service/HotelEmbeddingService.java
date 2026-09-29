package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.exception.SearchUnavailableException;
import com.example.hotelbooking.repository.HotelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns hotels and search queries into embedding vectors (via Spring AI's EmbeddingModel,
 * backed by Amazon Bedrock Titan Text Embeddings V2) and stores hotel vectors in the pgvector `embedding` column.
 */
@Slf4j
@Service
public class HotelEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final HotelRepository hotelRepository;
    private final int dimensions;
    private final String documentPrefix;
    private final String queryPrefix;

    public HotelEmbeddingService(EmbeddingModel embeddingModel,
                                 HotelRepository hotelRepository,
                                 @Value("${app.vibe-search.dimensions:768}") int dimensions,
                                 // nomic-embed-text needed these task prefixes; Titan V2 sets them to "" in application.yml
                                 @Value("${app.vibe-search.document-prefix:search_document: }") String documentPrefix,
                                 @Value("${app.vibe-search.query-prefix:search_query: }") String queryPrefix) {
        this.embeddingModel = embeddingModel;
        this.hotelRepository = hotelRepository;
        this.dimensions = dimensions;
        this.documentPrefix = documentPrefix;
        this.queryPrefix = queryPrefix;
    }

    public record RefreshResult(int embedded, int failed, int remaining) {
    }

    /** Embeds a user's natural-language search. Throws SearchUnavailableException if the model can't be reached. */
    public String embedQuery(String query) {
        try {
            return toVectorLiteral(embed(queryPrefix + query.trim()));
        } catch (RuntimeException ex) {
            log.warn("Could not embed search query: {}", ex.getMessage());
            throw new SearchUnavailableException(
                    "Vibe search is temporarily unavailable (embedding model not reachable). Please try again later.", ex);
        }
    }

    /**
     * Computes and stores the hotel's embedding. Never throws: if the model is down the hotel just
     * stays out of vibe search until the next refresh. Returns whether it worked.
     */
    public boolean embedHotel(Hotel hotel) {
        float[] vector;
        try {
            vector = embed(documentPrefix + buildDocumentText(hotel));
        } catch (RuntimeException ex) {
            log.warn("Could not embed hotel {} ({}): {}", hotel.getId(), hotel.getName(), ex.getMessage());
            return false;
        }
        hotelRepository.updateEmbedding(hotel.getId(), toVectorLiteral(vector));
        return true;
    }

    /**
     * (Re)embeds hotels. onlyMissing=true only handles hotels without an embedding (e.g. seeded rows,
     * or ones added while the model was unreachable). Stops at the first failure, since that almost always means
     * the model is unreachable and every other hotel would fail too.
     */
    public RefreshResult refreshEmbeddings(boolean onlyMissing) {
        List<Long> ids = onlyMissing ? hotelRepository.findIdsWithoutEmbedding() : hotelRepository.findAllIds();
        int embedded = 0;
        for (int i = 0; i < ids.size(); i++) {
            Hotel hotel = hotelRepository.findById(ids.get(i)).orElse(null);
            if (hotel == null) {
                continue;
            }
            if (!embedHotel(hotel)) {
                return new RefreshResult(embedded, 1, ids.size() - i - 1);
            }
            embedded++;
        }
        return new RefreshResult(embedded, 0, 0);
    }

    /** The text that represents a hotel's "vibe": everything a guest might describe in words. */
    String buildDocumentText(Hotel hotel) {
        List<String> parts = new ArrayList<>();
        parts.add(hotel.getName());

        List<String> place = new ArrayList<>();
        for (String p : new String[]{hotel.getAddress(), hotel.getCity(), hotel.getState(), hotel.getCountry()}) {
            if (StringUtils.hasText(p)) {
                place.add(p.trim());
            }
        }
        if (!place.isEmpty()) {
            parts.add("Located in " + String.join(", ", place));
        }
        if (hotel.getRating() != null) {
            parts.add(String.format(Locale.ROOT, "Guest rating %.1f out of 5", hotel.getRating()));
        }
        if (StringUtils.hasText(hotel.getDescription())) {
            parts.add(hotel.getDescription().trim());
        }
        if (hotel.getAmenities() != null && !hotel.getAmenities().isEmpty()) {
            parts.add("Amenities: " + String.join(", ", hotel.getAmenities()));
        }
        return String.join(". ", parts);
    }

    private float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != dimensions) {
            // The DB column is vector(<dimensions>); a different model needs a schema change too.
            throw new IllegalStateException("Embedding model returned " + vector.length
                    + " dimensions but app.vibe-search.dimensions is " + dimensions);
        }
        return vector;
    }

    /** pgvector's text format: [0.1,0.2,...] */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
