package org.opensagetv.vibe.tmdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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

      cache.putResource("bounded", "movie", Long.valueOf(5), "details", "en-US", "US", "",
          "{\"id\":5}", now, CachePolicy.MAX_RETENTION_SECONDS + CachePolicy.DAY_SECONDS);
      CachedResource bounded = cache.getResource("bounded", now + 1).get();
      require(bounded.getExpiresAtEpochSeconds() == now + CachePolicy.MAX_RETENTION_SECONDS,
          "hard retention ceiling");

      Path backup = database.resolveSibling("tmdb-backup.sqlite3");
      cache.backup(backup);
      try (TmdbCache restored = new TmdbCache(backup)) {
        require(restored.getResource("bounded", now + 1).isPresent(), "consistent backup");
      }
    }

    try (TmdbCache reopened = new TmdbCache(database)) {
      require(reopened.getResource("tv:101:details:en-US:US", now + 2).isPresent(), "persistent cache");
    }

    try (TmdbCache first = new TmdbCache(database);
        TmdbCache second = new TmdbCache(database)) {
      first.putResource("concurrent", "tv", Long.valueOf(202), "details", "en-US", "US", "",
          "{\"id\":202}", now, CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
      require(second.getResource("concurrent", now + 1).isPresent(),
          "independent connections share WAL state");
    }

    Path corrupt = database.resolveSibling("corrupt.sqlite3");
    Files.write(corrupt, "not a sqlite database".getBytes(StandardCharsets.UTF_8));
    try {
      new TmdbCache(corrupt);
      throw new AssertionError("corrupt database should fail safely");
    } catch (java.sql.SQLException expected) {
      require(expected.getMessage() != null, "corrupt database diagnostic");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
