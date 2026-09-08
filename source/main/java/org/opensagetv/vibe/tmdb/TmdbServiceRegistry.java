package org.opensagetv.vibe.tmdb;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

/** Process-wide owner used by SageTV plugin and consumer adapters. */
public final class TmdbServiceRegistry {
  private static TmdbMetadataService service;

  private TmdbServiceRegistry() {}

  public static synchronized TmdbMetadataService start(Path configurationPath)
      throws IOException, SQLException {
    if (service != null) return service;
    TmdbConfiguration configuration = TmdbConfiguration.load(configurationPath);
    if (!configuration.hasCredentials()) {
      throw new IOException("TMDB credentials are not configured");
    }
    service = new CachingTmdbMetadataService(configuration);
    return service;
  }

  public static synchronized TmdbMetadataService get() {
    if (service == null) throw new IllegalStateException("TMDB service is not started");
    return service;
  }

  public static synchronized boolean isStarted() { return service != null; }

  public static synchronized void stop() throws IOException {
    if (service != null) {
      try { service.close(); }
      finally { service = null; }
    }
  }
}
