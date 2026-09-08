package org.opensagetv.vibe.tmdb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

final class TmdbApiClientFailureTest {
  static void run() throws Exception {
    final AtomicInteger retryRequests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/3/", new HttpHandler() {
      public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/auth")) { send(exchange, 401, "{}"); return; }
        if (path.endsWith("/missing")) { send(exchange, 404, "{}"); return; }
        if (path.endsWith("/malformed")) { send(exchange, 200, "{not-json"); return; }
        if (path.endsWith("/oversized")) {
          byte[] bytes = new byte[4 * 1024 * 1024 + 1];
          java.util.Arrays.fill(bytes, (byte) ' ');
          bytes[0] = '{';
          bytes[bytes.length - 1] = '}';
          send(exchange, 200, bytes);
          return;
        }
        if (path.endsWith("/retry")) {
          retryRequests.incrementAndGet();
          send(exchange, 503, "{}");
          return;
        }
        if (path.endsWith("/bearer")) {
          String authorization = exchange.getRequestHeaders().getFirst("Authorization");
          String query = exchange.getRequestURI().getRawQuery();
          require("Bearer secret-token".equals(authorization), "bearer header");
          require(query == null || !query.contains("secret-key"), "bearer suppresses key URL");
          send(exchange, 200, "{\"ok\":true}");
          return;
        }
        send(exchange, 500, "{}");
      }
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/3";
    try {
      TmdbApiClient keyClient = client(base, "", "very-secret-key", 0);
      requireFailure(keyClient, "auth", 401, "authentication", "very-secret-key");
      requireFailure(keyClient, "missing", 404, "not found", "very-secret-key");
      requireFailure(keyClient, "malformed", -1, "malformed JSON", "very-secret-key");
      requireFailure(keyClient, "oversized", -1, "4 MiB", "very-secret-key");

      TmdbApiClient retryClient = client(base, "", "very-secret-key", 2);
      requireFailure(retryClient, "retry", 503, "HTTP 503", "very-secret-key");
      require(retryRequests.get() == 3, "bounded retry count");

      TmdbApiClient bearerClient = new TmdbApiClient(
          base, "secret-token", "secret-key", 2000, 2000, 0, 0, 1000.0d,
          new TmdbApiClient.Sleeper() { public void sleep(long millis) {} });
      require(bearerClient.getJson("bearer", Collections.<String, String>emptyMap())
          .contains("true"), "bearer response");
    } finally {
      server.stop(0);
    }
    testTimeout();
    try {
      client("http://127.0.0.1:1/3", "", "", 0);
      throw new AssertionError("missing credentials should fail");
    } catch (IllegalArgumentException expected) {
      require(!expected.toString().contains("secret"), "missing credential diagnostic");
    }
  }

  private static void testTimeout() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/3/slow", new HttpHandler() {
      public void handle(HttpExchange exchange) throws IOException {
        try {
          Thread.sleep(300L);
          send(exchange, 200, "{\"ok\":true}");
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
        } catch (IOException ignored) {
          // A timed-out client is expected to close before this response.
        }
      }
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/3";
      TmdbApiClient client = new TmdbApiClient(
          base, "", "timeout-secret", 100, 100, 0, 0, 1000.0d,
          new TmdbApiClient.Sleeper() { public void sleep(long millis) {} });
      requireFailure(client, "slow", -1, "bounded retries", "timeout-secret");
    } finally {
      server.stop(0);
    }
  }

  private static TmdbApiClient client(String base, String token, String key, int retries) {
    return new TmdbApiClient(base, token, key, 2000, 2000, retries, 0, 1000.0d,
        new TmdbApiClient.Sleeper() { public void sleep(long millis) {} });
  }

  private static void requireFailure(TmdbApiClient client, String path, int status,
      String expectedMessage, String secret) throws Exception {
    try {
      client.getJson(path, Collections.<String, String>emptyMap());
      throw new AssertionError("Expected failure for " + path);
    } catch (IOException error) {
      require(error.getMessage().contains(expectedMessage), path + " message");
      require(!error.toString().contains(secret), path + " redaction");
      if (status >= 0) {
        require(error instanceof TmdbApiException, path + " typed HTTP error");
        require(((TmdbApiException) error).getStatusCode() == status, path + " status");
      }
    }
  }

  private static void send(HttpExchange exchange, int status, String body) throws IOException {
    send(exchange, status, body.getBytes(StandardCharsets.UTF_8));
  }

  private static void send(HttpExchange exchange, int status, byte[] bytes) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
