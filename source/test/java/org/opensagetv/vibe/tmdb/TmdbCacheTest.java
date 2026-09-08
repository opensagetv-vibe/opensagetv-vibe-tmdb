package org.opensagetv.vibe.tmdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    testConcurrentWriters(database.resolveSibling("concurrent.sqlite3"), now);
    testAbruptProcessRecovery(database.resolveSibling("abrupt.sqlite3"), now);
    testVersionOneMigration(database.resolveSibling("legacy-v1.sqlite3"), now);
    testFutureSchemaRejection(database.resolveSibling("future.sqlite3"));

    Path corrupt = database.resolveSibling("corrupt.sqlite3");
    Files.write(corrupt, "not a sqlite database".getBytes(StandardCharsets.UTF_8));
    try {
      new TmdbCache(corrupt);
      throw new AssertionError("corrupt database should fail safely");
    } catch (java.sql.SQLException expected) {
      require(expected.getMessage() != null, "corrupt database diagnostic");
    }
  }

  private static void testAbruptProcessRecovery(Path database, long now) throws Exception {
    String javaExecutable = new java.io.File(
        new java.io.File(System.getProperty("java.home"), "bin"),
        System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")
        .getAbsolutePath();
    Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
        TmdbAbruptWriter.class.getName(), database.toString(), Long.toString(now)).start();
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("abrupt cache writer timed out");
    }
    require(process.exitValue() == 0, "abrupt cache writer exit");
    try (TmdbCache recovered = new TmdbCache(database)) {
      require(recovered.getResource("abrupt", now + 1).isPresent(),
          "WAL recovers committed data after abrupt process exit");
    }
  }

  private static void testConcurrentWriters(final Path database, final long now) throws Exception {
    final int writers = 4;
    final int entries = 20;
    final CountDownLatch ready = new CountDownLatch(writers);
    final CountDownLatch start = new CountDownLatch(1);
    final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
    Thread[] threads = new Thread[writers];
    for (int writer = 0; writer < writers; writer++) {
      final int writerId = writer;
      threads[writer] = new Thread(new Runnable() {
        public void run() {
          ready.countDown();
          try {
            start.await();
            try (TmdbCache cache = new TmdbCache(database)) {
              for (int entry = 0; entry < entries; entry++) {
                cache.putResource("writer:" + writerId + ":" + entry, "tv", null,
                    "test", "en-US", "US", "", "{}", now,
                    CachePolicy.DEFAULT_SEARCH_TTL_SECONDS);
              }
            }
          } catch (Throwable error) {
            failure.compareAndSet(null, error);
          }
        }
      }, "tmdb-cache-writer-" + writer);
      threads[writer].start();
    }
    ready.await();
    start.countDown();
    for (Thread thread : threads) thread.join(10000L);
    if (failure.get() != null) throw new AssertionError("concurrent cache write", failure.get());
    try (TmdbCache cache = new TmdbCache(database)) {
      for (int writer = 0; writer < writers; writer++) {
        for (int entry = 0; entry < entries; entry++) {
          require(cache.getResource("writer:" + writer + ":" + entry, now + 1).isPresent(),
              "concurrent row " + writer + ":" + entry);
        }
      }
    }
  }

  private static void testVersionOneMigration(Path database, long now) throws Exception {
    try (TmdbCache cache = new TmdbCache(database)) {
      cache.putResource("legacy", "movie", Long.valueOf(10), "details", "en-US", "US", "",
          "{\"id\":10}", now, CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE cache_metadata");
      statement.execute("PRAGMA user_version=1");
    }
    try (TmdbCache migrated = new TmdbCache(database)) {
      require(migrated.schemaVersion() == TmdbCache.SCHEMA_VERSION, "v1 to v2 migration");
      require(migrated.getResource("legacy", now + 1).isPresent(), "migration preserves cache rows");
    }
  }

  private static void testFutureSchemaRejection(Path database) throws Exception {
    try (TmdbCache cache = new TmdbCache(database)) {
      require(cache.schemaVersion() == TmdbCache.SCHEMA_VERSION, "future fixture baseline");
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA user_version=99");
    }
    try {
      new TmdbCache(database);
      throw new AssertionError("future schema should be rejected");
    } catch (java.sql.SQLException expected) {
      require(expected.getMessage().contains("newer than supported"), "future schema diagnostic");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
