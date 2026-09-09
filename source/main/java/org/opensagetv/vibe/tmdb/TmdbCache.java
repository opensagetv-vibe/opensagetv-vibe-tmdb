package org.opensagetv.vibe.tmdb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

/** Single-owner SQLite cache shared by SageMC, XMLTV, and later metadata consumers. */
public final class TmdbCache implements Closeable {
  public static final int SCHEMA_VERSION = 3;
  private static final Object INITIALIZATION_LOCK = new Object();
  private final Path databasePath;
  private final Connection connection;

  public TmdbCache(Path databasePath) throws SQLException, IOException {
    Path absolute = databasePath.toAbsolutePath().normalize();
    this.databasePath = absolute;
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException error) {
      throw new SQLException("SQLite JDBC driver is not installed", error);
    }
    connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
    try {
      synchronized (INITIALIZATION_LOCK) {
        configure();
        migrate();
      }
    } catch (SQLException error) {
      try {
        connection.close();
      } catch (SQLException closeError) {
        error.addSuppressed(closeError);
      }
      throw error;
    }
  }

  private void configure() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys=ON");
      statement.execute("PRAGMA busy_timeout=5000");
      boolean wal;
      try (ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
        wal = result.next() && "wal".equalsIgnoreCase(result.getString(1));
      }
      if (!wal) statement.execute("PRAGMA journal_mode=WAL");
      statement.execute("PRAGMA synchronous=NORMAL");
    }
  }

  private void migrate() throws SQLException {
    int current = schemaVersion();
    if (current > SCHEMA_VERSION) {
      throw new SQLException(
          "TMDB cache schema " + current + " is newer than supported schema " + SCHEMA_VERSION);
    }
    if (current == SCHEMA_VERSION) {
      return;
    }
    boolean originalAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      if (current == 0) {
        statement.execute(
            "CREATE TABLE resource_cache ("
                + "cache_key TEXT PRIMARY KEY, media_type TEXT NOT NULL, tmdb_id INTEGER, "
                + "resource_kind TEXT NOT NULL, language TEXT NOT NULL, region TEXT NOT NULL, "
                + "request_variant TEXT NOT NULL, payload_json TEXT NOT NULL, "
                + "fetched_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, last_accessed INTEGER NOT NULL)");
        statement.execute("CREATE INDEX resource_cache_expiry ON resource_cache(expires_at)");
        statement.execute(
            "CREATE TABLE lookup_cache ("
                + "lookup_key TEXT PRIMARY KEY, media_type TEXT NOT NULL, normalized_title TEXT NOT NULL, "
                + "release_year INTEGER, language TEXT NOT NULL, region TEXT NOT NULL, status TEXT NOT NULL, "
                + "tmdb_id INTEGER, matched_title TEXT NOT NULL, fetched_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
        statement.execute("CREATE INDEX lookup_cache_expiry ON lookup_cache(expires_at)");
        statement.execute(
            "CREATE TABLE manual_mapping ("
                + "media_type TEXT NOT NULL, normalized_title TEXT NOT NULL, release_year INTEGER NOT NULL DEFAULT 0, "
                + "tmdb_id INTEGER NOT NULL, display_title TEXT NOT NULL, updated_at INTEGER NOT NULL, "
                + "PRIMARY KEY(media_type, normalized_title, release_year))");
        current = 1;
      }
      if (current == 1) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS cache_metadata ("
                + "metadata_key TEXT PRIMARY KEY, metadata_value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        statement.execute(
            "INSERT OR REPLACE INTO cache_metadata(metadata_key,metadata_value,updated_at) "
                + "VALUES('schema','2',strftime('%s','now'))");
        current = 2;
      }
      if (current == 2) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS enrichment_job ("
                + "job_id TEXT PRIMARY KEY, request_fingerprint TEXT NOT NULL, total INTEGER NOT NULL, "
                + "completed INTEGER NOT NULL, state TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        statement.execute(
            "CREATE TABLE IF NOT EXISTS enrichment_result ("
                + "job_id TEXT NOT NULL, item_key TEXT NOT NULL, status TEXT NOT NULL, "
                + "tmdb_id INTEGER, matched_title TEXT NOT NULL, message TEXT NOT NULL, "
                + "processed_at INTEGER NOT NULL, PRIMARY KEY(job_id,item_key), "
                + "FOREIGN KEY(job_id) REFERENCES enrichment_job(job_id) ON DELETE CASCADE)");
        statement.execute(
            "CREATE INDEX IF NOT EXISTS enrichment_result_status "
                + "ON enrichment_result(job_id,status)");
        statement.execute(
            "INSERT OR REPLACE INTO cache_metadata(metadata_key,metadata_value,updated_at) "
                + "VALUES('schema','3',strftime('%s','now'))");
        current = 3;
      }
      statement.execute("PRAGMA user_version=" + current);
      connection.commit();
    } catch (SQLException error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(originalAutoCommit);
    }
  }

  public synchronized int schemaVersion() throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("PRAGMA user_version")) {
      return result.next() ? result.getInt(1) : 0;
    }
  }

  public synchronized String journalMode() throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
      return result.next() ? result.getString(1) : "";
    }
  }

  public synchronized Optional<CachedResource> getResource(String cacheKey, long now) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT payload_json, fetched_at, expires_at FROM resource_cache "
                + "WHERE cache_key=? AND expires_at>?")) {
      statement.setString(1, cacheKey);
      statement.setLong(2, now);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        try (PreparedStatement touch =
            connection.prepareStatement("UPDATE resource_cache SET last_accessed=? WHERE cache_key=?")) {
          touch.setLong(1, now);
          touch.setString(2, cacheKey);
          touch.executeUpdate();
        }
        return Optional.of(
            new CachedResource(cacheKey, result.getString(1), result.getLong(2), result.getLong(3)));
      }
    }
  }

  public synchronized void putResource(
      String cacheKey,
      String mediaType,
      Long tmdbId,
      String resourceKind,
      String language,
      String region,
      String requestVariant,
      String payloadJson,
      long now,
      long ttlSeconds)
      throws SQLException {
    requireText(cacheKey, "cacheKey");
    requireText(payloadJson, "payloadJson");
    long expires = now + CachePolicy.boundedTtl(ttlSeconds);
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO resource_cache(cache_key,media_type,tmdb_id,resource_kind,language,region,request_variant,payload_json,fetched_at,expires_at,last_accessed) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(cache_key) DO UPDATE SET "
                + "media_type=excluded.media_type,tmdb_id=excluded.tmdb_id,resource_kind=excluded.resource_kind,"
                + "language=excluded.language,region=excluded.region,request_variant=excluded.request_variant,"
                + "payload_json=excluded.payload_json,fetched_at=excluded.fetched_at,expires_at=excluded.expires_at,last_accessed=excluded.last_accessed")) {
      statement.setString(1, cacheKey);
      statement.setString(2, normalized(mediaType));
      if (tmdbId == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, tmdbId);
      statement.setString(4, normalized(resourceKind));
      statement.setString(5, safe(language));
      statement.setString(6, safe(region).toUpperCase(Locale.ROOT));
      statement.setString(7, safe(requestVariant));
      statement.setString(8, payloadJson);
      statement.setLong(9, now);
      statement.setLong(10, expires);
      statement.setLong(11, now);
      statement.executeUpdate();
    }
  }

  public synchronized Optional<LookupResult> getLookup(String lookupKey, long now) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT status,tmdb_id,matched_title,fetched_at,expires_at FROM lookup_cache WHERE lookup_key=? AND expires_at>?")) {
      statement.setString(1, lookupKey);
      statement.setLong(2, now);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) return Optional.empty();
        long id = result.getLong(2);
        Long tmdbId = result.wasNull() ? null : Long.valueOf(id);
        return Optional.of(
            new LookupResult(
                LookupResult.Status.valueOf(result.getString(1)),
                tmdbId,
                result.getString(3),
                result.getLong(4),
                result.getLong(5)));
      }
    }
  }

  public synchronized void putLookup(
      String lookupKey,
      String mediaType,
      String normalizedTitle,
      Integer releaseYear,
      String language,
      String region,
      LookupResult.Status status,
      Long tmdbId,
      String matchedTitle,
      long now,
      long ttlSeconds)
      throws SQLException {
    if (status == LookupResult.Status.MATCHED && tmdbId == null) {
      throw new IllegalArgumentException("A matched lookup requires a TMDB ID");
    }
    long expires = now + CachePolicy.boundedTtl(ttlSeconds);
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO lookup_cache(lookup_key,media_type,normalized_title,release_year,language,region,status,tmdb_id,matched_title,fetched_at,expires_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(lookup_key) DO UPDATE SET "
                + "status=excluded.status,tmdb_id=excluded.tmdb_id,matched_title=excluded.matched_title,"
                + "fetched_at=excluded.fetched_at,expires_at=excluded.expires_at")) {
      statement.setString(1, lookupKey);
      statement.setString(2, normalized(mediaType));
      statement.setString(3, normalizedTitle);
      if (releaseYear == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setInt(4, releaseYear);
      statement.setString(5, safe(language));
      statement.setString(6, safe(region).toUpperCase(Locale.ROOT));
      statement.setString(7, status.name());
      if (tmdbId == null) statement.setNull(8, java.sql.Types.BIGINT); else statement.setLong(8, tmdbId);
      statement.setString(9, safe(matchedTitle));
      statement.setLong(10, now);
      statement.setLong(11, expires);
      statement.executeUpdate();
    }
  }

  public synchronized void putManualMapping(
      String mediaType, String normalizedTitle, Integer releaseYear, long tmdbId, String displayTitle, long now)
      throws SQLException {
    if (tmdbId <= 0) throw new IllegalArgumentException("TMDB ID must be positive");
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO manual_mapping(media_type,normalized_title,release_year,tmdb_id,display_title,updated_at) "
                + "VALUES(?,?,?,?,?,?) ON CONFLICT(media_type,normalized_title,release_year) DO UPDATE SET "
                + "tmdb_id=excluded.tmdb_id,display_title=excluded.display_title,updated_at=excluded.updated_at")) {
      statement.setString(1, normalized(mediaType));
      statement.setString(2, normalizedTitle);
      statement.setInt(3, releaseYear == null ? 0 : releaseYear.intValue());
      statement.setLong(4, tmdbId);
      statement.setString(5, displayTitle);
      statement.setLong(6, now);
      statement.executeUpdate();
    }
  }

  public synchronized Optional<Long> getManualMapping(
      String mediaType, String normalizedTitle, Integer releaseYear) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT tmdb_id FROM manual_mapping WHERE media_type=? AND normalized_title=? AND release_year=?")) {
      statement.setString(1, normalized(mediaType));
      statement.setString(2, normalizedTitle);
      statement.setInt(3, releaseYear == null ? 0 : releaseYear.intValue());
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(Long.valueOf(result.getLong(1))) : Optional.<Long>empty();
      }
    }
  }

  public synchronized int cleanup(long now) throws SQLException {
    long oldestAllowed = now - CachePolicy.MAX_RETENTION_SECONDS;
    int removed = 0;
    try (PreparedStatement resources =
            connection.prepareStatement("DELETE FROM resource_cache WHERE expires_at<=? OR fetched_at<?");
        PreparedStatement lookups =
            connection.prepareStatement("DELETE FROM lookup_cache WHERE expires_at<=? OR fetched_at<?")) {
      resources.setLong(1, now);
      resources.setLong(2, oldestAllowed);
      removed += resources.executeUpdate();
      lookups.setLong(1, now);
      lookups.setLong(2, oldestAllowed);
      removed += lookups.executeUpdate();
    }
    return removed;
  }

  synchronized void putEnrichmentJob(String jobId, String fingerprint, int total,
      int completed, String state, long updatedAt) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO enrichment_job(job_id,request_fingerprint,total,completed,state,updated_at) "
            + "VALUES(?,?,?,?,?,?) ON CONFLICT(job_id) DO UPDATE SET "
            + "request_fingerprint=excluded.request_fingerprint,total=excluded.total,"
            + "completed=excluded.completed,state=excluded.state,updated_at=excluded.updated_at")) {
      statement.setString(1, requireValue(jobId, "jobId"));
      statement.setString(2, requireValue(fingerprint, "fingerprint"));
      statement.setInt(3, total);
      statement.setInt(4, completed);
      statement.setString(5, requireValue(state, "state"));
      statement.setLong(6, updatedAt);
      statement.executeUpdate();
    }
  }

  synchronized String[] getEnrichmentJob(String jobId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT request_fingerprint,total,completed,state,updated_at "
            + "FROM enrichment_job WHERE job_id=?")) {
      statement.setString(1, requireValue(jobId, "jobId"));
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) return null;
        return new String[] {result.getString(1), Integer.toString(result.getInt(2)),
            Integer.toString(result.getInt(3)), result.getString(4),
            Long.toString(result.getLong(5))};
      }
    }
  }

  synchronized void putEnrichmentResult(String jobId, String itemKey, String status,
      Long tmdbId, String matchedTitle, String message, long processedAt) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO enrichment_result(job_id,item_key,status,tmdb_id,matched_title,message,processed_at) "
            + "VALUES(?,?,?,?,?,?,?) ON CONFLICT(job_id,item_key) DO UPDATE SET "
            + "status=excluded.status,tmdb_id=excluded.tmdb_id,matched_title=excluded.matched_title,"
            + "message=excluded.message,processed_at=excluded.processed_at")) {
      statement.setString(1, requireValue(jobId, "jobId"));
      statement.setString(2, requireValue(itemKey, "itemKey"));
      statement.setString(3, requireValue(status, "status"));
      if (tmdbId == null) statement.setNull(4, java.sql.Types.BIGINT);
      else statement.setLong(4, tmdbId.longValue());
      statement.setString(5, safe(matchedTitle));
      statement.setString(6, safe(message));
      statement.setLong(7, processedAt);
      statement.executeUpdate();
    }
  }

  synchronized Set<String> getCompletedEnrichmentKeys(String jobId) throws SQLException {
    Set<String> keys = new LinkedHashSet<String>();
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT item_key FROM enrichment_result WHERE job_id=? "
            + "AND status IN ('PREVIEWED','SAVED','NO_MATCH') ORDER BY item_key")) {
      statement.setString(1, requireValue(jobId, "jobId"));
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) keys.add(result.getString(1));
      }
    }
    return keys;
  }

  /** Creates a transactionally consistent SQLite snapshot without exposing table ownership. */
  public synchronized void backup(Path destination) throws SQLException, IOException {
    Path target = destination.toAbsolutePath().normalize();
    if (target.equals(databasePath)) {
      throw new IllegalArgumentException("Backup destination must differ from the live cache");
    }
    Path parent = target.getParent();
    if (parent != null) Files.createDirectories(parent);
    Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
    Files.deleteIfExists(temporary);
    String quoted = temporary.toString().replace("'", "''");
    try (Statement statement = connection.createStatement()) {
      statement.execute("VACUUM INTO '" + quoted + "'");
    }
    try {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String normalized(String value) {
    requireText(value, "value");
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static void requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private static String requireValue(String value, String name) {
    requireText(value, name);
    return value.trim();
  }

  @Override
  public synchronized void close() throws IOException {
    SQLException failure = null;
    try {
      if (!connection.isClosed()) {
        try (Statement statement = connection.createStatement()) {
          statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException error) {
          failure = error;
        } finally {
          try {
            connection.close();
          } catch (SQLException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
          }
        }
      }
    } catch (SQLException error) {
      failure = error;
    }
    if (failure != null) throw new IOException("Unable to close TMDB cache", failure);
  }
}
