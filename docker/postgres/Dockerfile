# PostgreSQL 17 with PostGIS (geo search) + pgvector (semantic "vibe" search).
# Used by docker-compose.yml and by the integration tests (Testcontainers builds this same file).
# Based on the official multi-arch image (runs natively on Apple Silicon; postgis/postgis is amd64-only).
# The extensions themselves are enabled by src/main/resources/db/postgis-pgvector.sql on app startup.
FROM postgres:17

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       postgresql-17-postgis-3 \
       postgresql-17-pgvector \
    && rm -rf /var/lib/apt/lists/*
