package org.opensagetv.vibe.tmdb;

import java.nio.file.Paths;
import java.util.Collections;

public final class TestRunner {
  private TestRunner() {}

  public static void main(String[] args) throws Exception {
    TmdbConfigurationTest.run();
    TmdbCacheTest.run();
    TmdbServiceTest.run();
    String localConfig = System.getenv("TMDB_TEST_CONFIG");
    if (localConfig != null && !localConfig.trim().isEmpty()) {
      TmdbConfiguration configuration = TmdbConfiguration.load(Paths.get(localConfig));
      if (!configuration.hasCredentials()) {
        throw new AssertionError("The optional local TMDB commissioning config has no credential");
      }
      String liveResponse = new TmdbApiClient(configuration).getJson("configuration", Collections.<String, String>emptyMap());
      if (!liveResponse.contains("images")) {
        throw new AssertionError("TMDB live commissioning response did not contain configuration data");
      }
      System.out.println("PASS: optional local TMDB config loaded; " + configuration.redactedSummary());
      System.out.println("PASS: optional live TMDB authentication/API smoke test");
    }
    System.out.println("PASS: TMDB configuration and SQLite cache foundation");
  }
}
