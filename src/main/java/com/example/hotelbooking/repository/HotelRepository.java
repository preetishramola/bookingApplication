package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * The geo and vibe queries are native SQL because they use PostGIS / pgvector operators.
 * `location` (geography) and `embedding` (vector) columns are created by db/postgis-pgvector.sql.
 */
@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    /** SELECT ... FOR UPDATE: serialises review writes for one hotel while its average is recomputed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hotel h WHERE h.id = :id")
    Optional<Hotel> findByIdForUpdate(@Param("id") Long id);

    /** id + a distance (metres) and/or cosine similarity for a search hit. */
    interface HotelMatch {
        Long getId();
        Double getDistanceMeters();
        Double getSimilarity();
    }

    // ST_DWithin on geography uses the GIST index and works in metres.
    @Query(value = """
            SELECT h.id AS id,
                   ST_Distance(h.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanceMeters,
                   NULL AS similarity
            FROM hotels h
            WHERE ST_DWithin(h.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            ORDER BY distanceMeters
            LIMIT :limit
            """, nativeQuery = true)
    List<HotelMatch> findWithinRadius(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusMeters") double radiusMeters,
                                      @Param("limit") int limit);

    // <=> is pgvector's cosine distance (0 = same direction), so similarity = 1 - distance.
    // Ordering by the raw operator lets Postgres use the HNSW index.
    @Query(value = """
            SELECT h.id AS id,
                   NULL AS distanceMeters,
                   1 - (h.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM hotels h
            WHERE h.embedding IS NOT NULL
            ORDER BY h.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<HotelMatch> findMostSimilar(@Param("embedding") String embedding,
                                     @Param("limit") int limit);

    // Vibe search restricted to a radius: "romantic hotels within 5 km of me".
    @Query(value = """
            SELECT h.id AS id,
                   ST_Distance(h.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanceMeters,
                   1 - (h.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM hotels h
            WHERE h.embedding IS NOT NULL
              AND ST_DWithin(h.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            ORDER BY h.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<HotelMatch> findMostSimilarWithinRadius(@Param("embedding") String embedding,
                                                 @Param("lat") double lat,
                                                 @Param("lng") double lng,
                                                 @Param("radiusMeters") double radiusMeters,
                                                 @Param("limit") int limit);

    // Own short transaction, so callers never hold a DB transaction open while waiting on the embedding model.
    @Transactional
    @Modifying
    @Query(value = "UPDATE hotels SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    @Query(value = "SELECT id FROM hotels WHERE embedding IS NULL ORDER BY id", nativeQuery = true)
    List<Long> findIdsWithoutEmbedding();

    @Query(value = "SELECT id FROM hotels ORDER BY id", nativeQuery = true)
    List<Long> findAllIds();
}
