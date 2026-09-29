-- Runs on every startup AFTER Hibernate has created/updated the tables (defer-datasource-initialization).
-- Everything here must be idempotent.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

-- Geo search: a geography Point derived from latitude/longitude. GENERATED means it can never drift
-- out of sync with the lat/lng columns that Hibernate writes. geography (not geometry) so distances are metres.
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS location geography(Point, 4326)
    GENERATED ALWAYS AS (
        CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL
             THEN ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
        END
    ) STORED;
CREATE INDEX IF NOT EXISTS idx_hotels_location ON hotels USING GIST (location);

-- Vibe search: 1024 dims = Amazon Titan Text Embeddings V2 (amazon.titan-embed-text-v2:0).
-- Changing model/dimensions means changing this column (and app.vibe-search.dimensions), then re-embedding all hotels.
-- An existing local DB still has the old vector(768) column: drop it once with
--   ALTER TABLE hotels DROP COLUMN embedding;   (or `docker compose down -v`) and restart; hotels are re-embedded on startup.
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS embedding vector(1024);
CREATE INDEX IF NOT EXISTS idx_hotels_embedding ON hotels USING hnsw (embedding vector_cosine_ops);
