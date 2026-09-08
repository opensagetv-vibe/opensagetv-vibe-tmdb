package org.opensagetv.vibe.tmdb;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Stable consumer boundary. SageMC and XMLTV must depend on this API, not SQLite. */
public interface TmdbMetadataService extends Closeable {
  List<TmdbSearchResult> search(MediaType type, String query, Integer year)
      throws IOException, SQLException;

  LookupResult resolveExact(MediaType type, String title, Integer year)
      throws IOException, SQLException;

  String getDetailsJson(MediaType type, long tmdbId, String appendToResponse)
      throws IOException, SQLException;
}
