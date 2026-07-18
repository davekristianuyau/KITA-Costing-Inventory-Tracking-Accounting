-- Runs once on first Postgres init (docker-entrypoint-initdb.d), before any service migrates.
-- Ensures shared extensions live in `public` so per-service schemas (search_path = <svc>,public)
-- can resolve them. gen_random_uuid() is core since PG13, but pgcrypto is created defensively.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
