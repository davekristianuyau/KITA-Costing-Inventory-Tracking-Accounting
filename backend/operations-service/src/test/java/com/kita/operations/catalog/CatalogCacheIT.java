package com.kita.operations.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * T030 [US4]: shared-cache demonstrator on the catalog list read (contracts/cache-contract.md). Proves
 * (a) a repeat read is served from the cache, (b) a create invalidates it (no stale), and (c) with the
 * cache down the read still returns correct data from the database (graceful degradation).
 */
@TestMethodOrder(OrderAnnotation.class)
class CatalogCacheIT extends AbstractIntegrationTest {

  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired private CatalogService catalog;
  @Autowired private ItemRepository items;
  @Autowired private UnitOfMeasureRepository uoms;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearCatalogCache() {
    var cache = cacheManager.getCache("catalog:items");
    if (cache != null) {
      cache.clear();
    }
  }

  private void seedUom() {
    catalog.createUom("ea", UomFamily.COUNT);
  }

  @Test
  @Order(1)
  void repeatReadServedFromCacheAndCreateInvalidatesIt() {
    seedUom();
    catalog.createItem("SKU-1", "One", ItemType.COMPONENT, "ea", null, false);

    List<ItemView> first = catalog.listItemViews();
    assertThat(first).hasSize(1);
    assertThat(cacheManager.getCache("catalog:items").get(SimpleKey.EMPTY)).isNotNull();

    // Insert directly via the repository, bypassing @CacheEvict — the cached list is now stale.
    items.save(new Item("SKU-2", "Two", ItemType.COMPONENT, uoms.findByCode("ea").orElseThrow()));

    // The repeat read is served from the (stale) cache, proving it was a cache hit.
    assertThat(catalog.listItemViews()).hasSize(1);

    // Creating through the service evicts the cache — the next read reflects everything (no stale).
    catalog.createItem("SKU-3", "Three", ItemType.COMPONENT, "ea", null, false);
    assertThat(catalog.listItemViews()).hasSize(3);
  }

  @Test
  @Order(2)
  void readStillWorksWhenCacheIsUnavailable() {
    seedUom();
    catalog.createItem("SKU-A", "A", ItemType.COMPONENT, "ea", null, false);
    catalog.listItemViews(); // populate the cache

    REDIS.stop(); // cache is now unavailable

    // Graceful degradation: the read still returns correct data from the database, no exception.
    assertThat(catalog.listItemViews()).hasSize(1);
  }
}
