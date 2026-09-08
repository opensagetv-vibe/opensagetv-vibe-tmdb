package org.opensagetv.vibe.tmdb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TmdbConfigurationTest {
  static void run() throws Exception {
    Path directory = Files.createTempDirectory("tmdb-config-test");
    Path config = directory.resolve("tmdb_config.toml");
    Files.write(
        config,
        ("[tmdb]\n"
                + "api_read_access_token = \"test-secret-token\"\n"
                + "api_key = 'test-secret-key' # redacted test value\n"
                + "cache_path = \"cache/metadata.sqlite3\"\n"
                + "language = \"en-CA\"\n"
                + "region = \"ca\"\n"
                + "max_requests_per_second = 3.5\n")
            .getBytes(StandardCharsets.UTF_8));
    TmdbConfiguration parsed = TmdbConfiguration.load(config);
    require(parsed.hasCredentials(), "credentials should load");
    require("en-CA".equals(parsed.getLanguage()), "language should load");
    require("CA".equals(parsed.getRegion()), "region should normalize");
    require(parsed.getCachePath().equals(directory.resolve("cache/metadata.sqlite3")), "relative cache path");
    String summary = parsed.redactedSummary();
    require(!summary.contains("test-secret-token"), "token leaked in summary");
    require(!summary.contains("test-secret-key"), "API key leaked in summary");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
