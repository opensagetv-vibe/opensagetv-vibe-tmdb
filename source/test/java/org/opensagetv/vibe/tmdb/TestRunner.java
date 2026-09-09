package org.opensagetv.vibe.tmdb;

import java.nio.file.Paths;
import java.util.Collections;
import org.opensagetv.vibe.tmdb.plugin.OpenSageTVVibeTmdbFacade;

public final class TestRunner {
  private TestRunner() {}

  public static void main(String[] args) throws Exception {
    TmdbConfigurationTest.run();
    MediaTitleParserTest.run();
    TmdbCacheTest.run();
    TmdbServiceTest.run();
    LibraryEnrichmentServiceTest.run();
    TmdbApiClientFailureTest.run();
    SageTvPluginCompatibilityTest.run();
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
      try {
        TmdbServiceRegistry.start(Paths.get(localConfig));
        String[][] programme = OpenSageTVVibeTmdbFacade.getProgrammeMetadata(
            "movie", "The Matrix", 1999, 0, 0);
        if (!hasValue(programme, "tmdb_id") || !hasValue(programme, "description")) {
          throw new AssertionError("TMDB live programme-metadata facade returned no exact movie match");
        }
      } finally {
        TmdbServiceRegistry.stop();
      }
      System.out.println("PASS: optional local TMDB config loaded; " + configuration.redactedSummary());
      System.out.println("PASS: optional live TMDB authentication/API smoke test");
      System.out.println("PASS: optional live XMLTV programme-metadata facade smoke test");
    }
    System.out.println("PASS: TMDB configuration and SQLite cache foundation");
  }

  private static boolean hasValue(String[][] rows, String key) {
    for (String[] row : rows) {
      if (row != null && row.length >= 2 && key.equals(row[0])
          && row[1] != null && !row[1].trim().isEmpty()) return true;
    }
    return false;
  }
}
