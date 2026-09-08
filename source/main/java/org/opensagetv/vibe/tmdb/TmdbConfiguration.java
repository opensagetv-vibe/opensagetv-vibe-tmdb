package org.opensagetv.vibe.tmdb;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Minimal, strict reader for the plugin's [tmdb] TOML section. */
public final class TmdbConfiguration {
  private final Path source;
  private final String apiReadAccessToken;
  private final String apiKey;
  private final Path cachePath;
  private final String language;
  private final String region;
  private final double maxRequestsPerSecond;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final int retries;
  private final long retryBackoffMillis;

  private TmdbConfiguration(
      Path source,
      String apiReadAccessToken,
      String apiKey,
      Path cachePath,
      String language,
      String region,
      double maxRequestsPerSecond,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      int retries,
      long retryBackoffMillis) {
    this.source = source;
    this.apiReadAccessToken = apiReadAccessToken;
    this.apiKey = apiKey;
    this.cachePath = cachePath;
    this.language = language;
    this.region = region;
    this.maxRequestsPerSecond = maxRequestsPerSecond;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.retries = retries;
    this.retryBackoffMillis = retryBackoffMillis;
  }

  public static TmdbConfiguration load(Path source) throws IOException {
    Path absolute = source.toAbsolutePath().normalize();
    Map<String, String> values = new HashMap<String, String>();
    String section = "";
    try (BufferedReader reader = Files.newBufferedReader(absolute, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String value = stripComment(line).trim();
        if (value.isEmpty()) {
          continue;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
          section = value.substring(1, value.length() - 1).trim();
          continue;
        }
        if (!"tmdb".equals(section)) {
          continue;
        }
        int separator = value.indexOf('=');
        if (separator <= 0) {
          throw new IOException("Invalid TMDB TOML at line " + lineNumber);
        }
        String key = value.substring(0, separator).trim();
        String parsed = unquote(value.substring(separator + 1).trim(), lineNumber);
        values.put(key, parsed);
      }
    }

    String token = firstNonEmpty(System.getenv("TMDB_API_READ_ACCESS_TOKEN"), values.get("api_read_access_token"));
    String key = firstNonEmpty(System.getenv("TMDB_API_KEY"), values.get("api_key"));
    String cacheValue = firstNonEmpty(values.get("cache_path"), "tmdb-cache.sqlite3");
    Path cache = Paths.get(cacheValue);
    if (!cache.isAbsolute()) {
      Path parent = absolute.getParent();
      cache = (parent == null ? cache : parent.resolve(cache)).normalize();
    }
    String language = firstNonEmpty(values.get("language"), "en-US");
    String region = firstNonEmpty(values.get("region"), "US").toUpperCase();
    double maxRps = parsePositiveDouble(values.get("max_requests_per_second"), 4.0d);
    int connectTimeout = parseBoundedInteger(values.get("connect_timeout_ms"), 10000, 100, 120000, "connect_timeout_ms");
    int readTimeout = parseBoundedInteger(values.get("read_timeout_ms"), 30000, 100, 300000, "read_timeout_ms");
    int retries = parseBoundedInteger(values.get("retries"), 3, 0, 10, "retries");
    int retryBackoff = parseBoundedInteger(values.get("retry_backoff_ms"), 1000, 0, 60000, "retry_backoff_ms");
    return new TmdbConfiguration(
        absolute, token, key, cache, language, region, maxRps,
        connectTimeout, readTimeout, retries, retryBackoff);
  }

  private static String stripComment(String line) {
    boolean quoted = false;
    char quote = 0;
    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if ((current == '\'' || current == '"') && (index == 0 || line.charAt(index - 1) != '\\')) {
        if (!quoted) {
          quoted = true;
          quote = current;
        } else if (quote == current) {
          quoted = false;
        }
      } else if (current == '#' && !quoted) {
        return line.substring(0, index);
      }
    }
    return line;
  }

  private static String unquote(String value, int lineNumber) throws IOException {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    if (value.startsWith("\"") || value.startsWith("'") || value.endsWith("\"") || value.endsWith("'")) {
      throw new IOException("Unterminated quoted value at line " + lineNumber);
    }
    return value;
  }

  private static String firstNonEmpty(String first, String second) {
    return first != null && !first.trim().isEmpty() ? first.trim() : second;
  }

  private static double parsePositiveDouble(String value, double defaultValue) throws IOException {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      double parsed = Double.parseDouble(value.trim());
      if (parsed <= 0 || parsed > 40) {
        throw new IOException("max_requests_per_second must be greater than 0 and at most 40");
      }
      return parsed;
    } catch (NumberFormatException error) {
      throw new IOException("Invalid max_requests_per_second", error);
    }
  }

  private static int parseBoundedInteger(
      String value, int defaultValue, int minimum, int maximum, String name) throws IOException {
    if (value == null || value.trim().isEmpty()) return defaultValue;
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < minimum || parsed > maximum) {
        throw new IOException(name + " must be between " + minimum + " and " + maximum);
      }
      return parsed;
    } catch (NumberFormatException error) {
      throw new IOException("Invalid " + name, error);
    }
  }

  public boolean hasCredentials() {
    return (apiReadAccessToken != null && !apiReadAccessToken.isEmpty())
        || (apiKey != null && !apiKey.isEmpty());
  }

  public String getApiReadAccessToken() {
    return apiReadAccessToken;
  }

  public String getApiKey() {
    return apiKey;
  }

  public Path getCachePath() {
    return cachePath;
  }

  public String getLanguage() {
    return language;
  }

  public String getRegion() {
    return region;
  }

  public double getMaxRequestsPerSecond() {
    return maxRequestsPerSecond;
  }

  public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
  public int getReadTimeoutMillis() { return readTimeoutMillis; }
  public int getRetries() { return retries; }
  public long getRetryBackoffMillis() { return retryBackoffMillis; }

  public String redactedSummary() {
    return "TmdbConfiguration{source="
        + source
        + ", credentials="
        + (hasCredentials() ? "configured" : "missing")
        + ", cachePath="
        + cachePath
        + ", language="
        + language
        + ", region="
        + region
        + ", maxRequestsPerSecond="
        + maxRequestsPerSecond
        + "}";
  }
}
