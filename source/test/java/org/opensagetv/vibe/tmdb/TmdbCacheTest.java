package org.opensagetv.vibe.tmdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class TmdbCacheTest {
  static void run() throws Exception {
    Path database = Files.createTempDirectory("tmdb-cache-test").resolve("tmdb.sqlite3");
    long now = 2_000_000_000L;
    try (TmdbCache cache = new TmdbCache(database)) {
      require(cache.schemaVersion() == TmdbCache.SCHEMA_VERSION, "schema version");
      require("wal".equalsIgnoreCase(cache.journalMode()), "WAL journal mode");
      cache.putResource(
          "tv:101:details:en-US:US",
          "tv",
          Long.valueOf(101),
          "details",
          "en-US",
          "US",
          "",
          "{\"id\":101}",
          now,
          CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
      Optional<CachedResource> resource = cache.getResource("tv:101:details:en-US:US", now + 1);
      require(resource.isPresent(), "resource cache hit");
      require(resource.get().getPayloadJson().contains("101"), "resource payload");

      cache.putLookup(
          "tv:sample show:2026:en-US:US",
          "tv",
          "sample show",
          Integer.valueOf(2026),
          "en-US",
          "US",
          LookupResult.Status.MATCHED,
          Long.valueOf(101),
          "Sample Show",
          now,
          CachePolicy.DEFAULT_SEARCH_TTL_SECONDS);
      LookupResult match = cache.getLookup("tv:sample show:2026:en-US:US", now + 1).get();
      require(match.getStatus() == LookupResult.Status.MATCHED, "matched lookup status");
      require(Long.valueOf(101).equals(match.getTmdbId()), "matched lookup ID");

      cache.putManualMapping("tv", "sample show", Integer.valueOf(2026), 101, "Sample Show", now);
      require(
          Long.valueOf(101).equals(cache.getManualMapping("tv", "sample show", Integer.valueOf(2026)).get()),
          "manual mapping");

      cache.putLookup(
          "tv:missing::en-US:US",
          "tv",
          "missing",
          null,
          "en-US",
          "US",
          LookupResult.Status.NO_MATCH,
          null,
          "",
          now,
          1);
      require(!cache.getLookup("tv:missing::en-US:US", now + 2).isPresent(), "expired negative lookup");
      require(cache.cleanup(now + 2) >= 1, "expired row cleanup");
    }

    try (TmdbCache reopened = new TmdbCache(database)) {
      require(reopened.getResource("tv:101:details:en-US:US", now + 2).isPresent(), "persistent cache");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
