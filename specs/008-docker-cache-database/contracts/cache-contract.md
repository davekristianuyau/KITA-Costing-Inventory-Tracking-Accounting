# Contract — Cache

Governs the shared Redis cache and the demonstrator cached read path.

## Guarantees

- **C1 — Non-authoritative**: the cache holds copies of data only. The database is always the source of
  truth. No write path treats the cache as durable state.
- **C2 — Read acceleration**: a cache-eligible read populates the cache on first miss and is served from
  the cache on subsequent reads until invalidated or TTL-expired.
- **C3 — No stale after write**: a completed write to a cached resource MUST evict or refresh the
  affected entry before the next read observes the old value (FR-014).
- **C4 — Graceful degradation**: if Redis is unreachable, cache-eligible reads MUST still return correct
  results from the database; the request MUST NOT fail because of the cache (FR-013, SC-007).
- **C5 — Opt-in per service**: any backend service MAY add its own cache following this contract; only
  paths with a concrete need are cached (no blanket caching).

## Demonstrator (operations-service catalog)

- `@Cacheable` on the catalog/item read; `@CacheEvict` (or `@CachePut`) on the corresponding catalog write.
- Cache name/key and TTL per [data-model.md](../data-model.md#cache-model-redis-non-authoritative).

## Acceptance (tests)

- Second read of unchanged data does not hit the database (≥ 80% fewer DB queries on repeat). (SC-006)
- Read → write → read returns the new value every time (no stale). (C3)
- With Redis stopped, the read still returns the correct value from the database. (C4, SC-007)
