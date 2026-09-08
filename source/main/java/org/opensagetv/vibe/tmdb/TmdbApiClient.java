package org.opensagetv.vibe.tmdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class TmdbApiClient {
  interface Sleeper { void sleep(long millis) throws InterruptedException; }

  private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
  private final String apiBase;
  private final String token;
  private final String apiKey;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final int retries;
  private final long retryBackoffMillis;
  private final long minimumIntervalNanos;
  private final Sleeper sleeper;
  private long lastRequestNanos;

  TmdbApiClient(TmdbConfiguration configuration) {
    this(
        "https://api.themoviedb.org/3",
        configuration.getApiReadAccessToken(),
        configuration.getApiKey(),
        configuration.getConnectTimeoutMillis(),
        configuration.getReadTimeoutMillis(),
        configuration.getRetries(),
        configuration.getRetryBackoffMillis(),
        configuration.getMaxRequestsPerSecond(),
        new Sleeper() { public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); } });
  }

  TmdbApiClient(
      String apiBase,
      String token,
      String apiKey,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      int retries,
      long retryBackoffMillis,
      double maxRequestsPerSecond,
      Sleeper sleeper) {
    if ((token == null || token.isEmpty()) && (apiKey == null || apiKey.isEmpty())) {
      throw new IllegalArgumentException("TMDB API Read Access Token or API key is required");
    }
    this.apiBase = apiBase.replaceAll("/+$", "");
    this.token = token;
    this.apiKey = apiKey;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.retries = retries;
    this.retryBackoffMillis = retryBackoffMillis;
    this.minimumIntervalNanos = (long) (1_000_000_000d / maxRequestsPerSecond);
    this.sleeper = sleeper;
  }

  String getJson(String path, Map<String, String> parameters) throws IOException {
    Map<String, String> query = new LinkedHashMap<String, String>(parameters);
    // Prefer the bearer token. Keeping an API key out of the URL also keeps it
    // out of proxy logs and low-level URLConnection exception messages.
    if ((token == null || token.isEmpty()) && apiKey != null && !apiKey.isEmpty()) {
      query.put("api_key", apiKey);
    }
    URL url = new URL(apiBase + "/" + path.replaceFirst("^/+", "") + encodeQuery(query));
    for (int attempt = 0; attempt <= retries; attempt++) {
      pace();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(connectTimeoutMillis);
      connection.setReadTimeout(readTimeoutMillis);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "OpenSageTV-Vibe-TMDB/0.1");
      if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
      try {
        int status = connection.getResponseCode();
        if (status >= 200 && status < 300) return readBounded(connection.getInputStream());
        TmdbApiException error = statusError(status);
        if (!retryable(status) || attempt >= retries) throw error;
        sleepRetry(connection.getHeaderField("Retry-After"), attempt);
      } catch (TmdbApiException error) {
        throw error;
      } catch (IOException error) {
        if (attempt >= retries) break;
        sleepMillis(retryBackoffMillis * (1L << attempt));
      } finally {
        connection.disconnect();
      }
    }
    // Do not retain a URLConnection cause: its message can contain the full
    // URL, including the v3 API key when key authentication is used.
    throw new TmdbApiException("TMDB request failed after bounded retries", -1);
  }

  private synchronized void pace() throws IOException {
    long waitNanos = lastRequestNanos + minimumIntervalNanos - System.nanoTime();
    if (waitNanos > 0) sleepMillis((waitNanos + 999_999L) / 1_000_000L);
    lastRequestNanos = System.nanoTime();
  }

  private void sleepRetry(String retryAfter, int attempt) throws IOException {
    long serverMillis = 0;
    if (retryAfter != null) {
      try { serverMillis = Math.max(0L, Long.parseLong(retryAfter.trim()) * 1000L); }
      catch (NumberFormatException ignored) { serverMillis = 0; }
    }
    sleepMillis(Math.max(serverMillis, retryBackoffMillis * (1L << attempt)));
  }

  private void sleepMillis(long millis) throws IOException {
    try { sleeper.sleep(millis); }
    catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IOException("TMDB request interrupted", error);
    }
  }

  private static boolean retryable(int status) {
    return status == 408 || status == 425 || status == 429 || status == 500
        || status == 502 || status == 503 || status == 504;
  }

  private static TmdbApiException statusError(int status) {
    if (status == 401 || status == 403) return new TmdbApiException("TMDB authentication failed", status);
    if (status == 404) return new TmdbApiException("TMDB resource was not found", status);
    return new TmdbApiException("TMDB request failed with HTTP " + status, status);
  }

  private static String readBounded(InputStream input) throws IOException {
    try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int total = 0;
      int count;
      while ((count = stream.read(buffer)) != -1) {
        total += count;
        if (total > MAX_RESPONSE_BYTES) throw new IOException("TMDB response exceeded 4 MiB limit");
        output.write(buffer, 0, count);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static String encodeQuery(Map<String, String> parameters) throws IOException {
    if (parameters.isEmpty()) return "";
    StringBuilder result = new StringBuilder("?");
    boolean first = true;
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
      if (!first) result.append('&');
      first = false;
      result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
      result.append('=');
      result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
    }
    return first ? "" : result.toString();
  }
}
