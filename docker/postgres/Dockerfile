# PostgreSQL 18 + PostGIS + pgvector (ADR-0008). Used by compose.yml (dev), deploy/ (prod) and
# Testcontainers (TestcontainersConfiguration) - keep them on this one file.
FROM postgis/postgis:18-3.6

# pgvector from the PGDG apt repository that the official postgres image already configures.
# The postgis image's init script creates postgis + tiger geocoder + topology in every new database;
# remove it so Flyway alone decides which extensions exist (like on a managed cloud database).
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-18-pgvector \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /docker-entrypoint-initdb.d/10_postgis.sh
