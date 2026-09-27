package com.example.hotelbooking.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic stand-in for Ollama: one dimension per keyword, so texts that share
 * keywords are "semantically" close. Lets tests check real pgvector ranking without a model.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 768;
    private static final List<String> KEYWORDS = List.of(
            "quiet", "romantic", "boutique", "breakfast", "couple",
            "business", "meeting", "conference", "co-working",
            "family", "kids", "pool", "lively", "nightlife",
            "spa", "yoga", "harbor", "sea");

    private final AtomicBoolean unavailable = new AtomicBoolean(false);

    /** Simulate Ollama being down. */
    public void setUnavailable(boolean value) {
        unavailable.set(value);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (unavailable.get()) {
            throw new IllegalStateException("Connection refused: fake embedding model is down");
        }
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vectorFor(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    private static float[] vectorFor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < KEYWORDS.size(); i++) {
            if (lower.contains(KEYWORDS.get(i))) {
                vector[i] = 1f;
            }
        }
        vector[DIMENSIONS - 1] = 0.1f; // never a zero vector (cosine would be undefined)
        return vector;
    }
}
