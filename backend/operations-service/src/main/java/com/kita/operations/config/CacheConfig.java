package com.kita.operations.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Shared cache wiring (008-docker-cache-database). Enables Spring's cache abstraction over Redis (TTL is
 * set via {@code spring.cache.redis.time-to-live}) with a {@link CacheErrorHandler} that logs and
 * swallows Redis failures so a cache outage degrades gracefully to the database — the cache is an
 * accelerator, never the source of truth (FR-013).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("cache get failed ({} / {}) — serving from source", cache.getName(), key, e);
      }

      @Override
      public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("cache put failed ({} / {}) — result not cached", cache.getName(), key, e);
      }

      @Override
      public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("cache evict failed ({} / {})", cache.getName(), key, e);
      }

      @Override
      public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("cache clear failed ({})", cache.getName(), e);
      }
    };
  }
}
