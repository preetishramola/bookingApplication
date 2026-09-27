package com.example.hotelbooking.config;

import com.example.hotelbooking.service.HotelEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * On startup, embeds any hotels that don't have a vibe-search embedding yet (seeded rows, or hotels
 * added while Ollama was down). If Ollama isn't running the app still starts; it just logs a warning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.vibe-search.backfill-on-startup", havingValue = "true")
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private final HotelEmbeddingService embeddingService;

    @Override
    public void run(ApplicationArguments args) {
        HotelEmbeddingService.RefreshResult result = embeddingService.refreshEmbeddings(true);
        if (result.failed() > 0) {
            log.warn("Vibe search: embedded {} hotel(s) but could not reach the embedding model; {} hotel(s) still "
                    + "have no embedding. Start Ollama and call POST /api/admin/hotels/embeddings/refresh.",
                    result.embedded(), result.failed() + result.remaining());
        } else if (result.embedded() > 0) {
            log.info("Vibe search: generated embeddings for {} hotel(s)", result.embedded());
        }
    }
}
