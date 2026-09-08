package org.opensagetv.vibe.tmdb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class TmdbServiceTest {
  static void run() throws Exception {
    final AtomicInteger requests = new AtomicInteger();
    final AtomicInteger firstSearch = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/3/", new HttpHandler() {
      public void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        require(query != null && query.contains("api_key=fake-key"), "API key query");
        if (path.equals("/3/search/tv") && firstSearch.getAndIncrement() == 0) {
          exchange.getResponseHeaders().add("Retry-After", "0");
          send(exchange, 429, "{\"status_message\":\"retry\"}");
          return;
        }
        if (path.equals("/3/search/tv") && query.contains("query=Sample+Show")) {
          send(exchange, 200,
              "{\"results\":[{\"id\":101,\"name\":\"Sample Show\","
                  + "\"original_name\":\"Sample Show\",\"overview\":\"Example\","
                  + "\"first_air_date\":\"2026-01-02\",\"poster_path\":\"/poster.jpg\","
                  + "\"origin_country\":[\"US\"]}]}");
          return;
        }
        if (path.equals("/3/search/tv") && query.contains("query=Missing")) {
          send(exchange, 200, "{\"results\":[]}");
          return;
        }
        if (path.equals("/3/tv/101")) {
          send(exchange, 200, "{\"id\":101,\"name\":\"Sample Show\"}");
          return;
        }
        send(exchange, 404, "{}");
      }
    });
    server.start();

    TmdbCache cache = new TmdbCache(Files.createTempDirectory("tmdb-service-test").resolve("cache.sqlite3"));
    TmdbApiClient api = new TmdbApiClient(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/3",
        "", "fake-key", 2000, 2000, 2, 1, 1000.0d,
        new TmdbApiClient.Sleeper() { public void sleep(long millis) {} });
    CachingTmdbMetadataService service = new CachingTmdbMetadataService(api, cache, "en-US", "US");
    try {
      List<TmdbSearchResult> result = service.search(MediaType.TV, "Sample Show", Integer.valueOf(2026));
      require(result.size() == 1 && result.get(0).getId() == 101, "search result");
      require(firstSearch.get() == 2, "HTTP 429 retry");
      LookupResult lookup = service.resolveExact(MediaType.TV, "Sample Show", Integer.valueOf(2026));
      require(lookup.getStatus() == LookupResult.Status.MATCHED, "exact resolution");
      require(service.getDetailsJson(MediaType.TV, 101, "credits").contains("Sample Show"), "details");

      int beforeMissing = requests.get();
      require(service.resolveExact(MediaType.TV, "Missing", null).getStatus() == LookupResult.Status.NO_MATCH,
          "negative resolution");
      int afterMissing = requests.get();
      require(afterMissing == beforeMissing + 1, "first negative request");
      require(service.resolveExact(MediaType.TV, "Missing", null).getStatus() == LookupResult.Status.NO_MATCH,
          "cached negative resolution");
      require(requests.get() == afterMissing, "negative cache avoids second HTTP request");

      int beforeCachedSearch = requests.get();
      service.search(MediaType.TV, "Sample Show", Integer.valueOf(2026));
      require(requests.get() == beforeCachedSearch, "search cache avoids HTTP request");
    } finally {
      server.stop(0);
      service.close();
    }
  }

  private static void send(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
