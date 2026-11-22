-- Extensions used by the app (ADR-0008). Created here rather than by the image, so the schema is
-- reproducible on any PostgreSQL that ships them (e.g. a managed cloud database).
CREATE EXTENSION IF NOT EXISTS postgis;   -- photo/observation locations (geography)
CREATE EXTENSION IF NOT EXISTS vector;    -- image embeddings + nearest-neighbour search
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- Latin/Polish name autocomplete
