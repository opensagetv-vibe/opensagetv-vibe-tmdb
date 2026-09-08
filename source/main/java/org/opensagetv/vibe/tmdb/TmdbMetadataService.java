package org.opensagetv.vibe.tmdb;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/** Stable consumer boundary. SageMC and XMLTV must depend on this API, not SQLite. */
public interface TmdbMetadataService extends Closeable {
  List<TmdbSearchResult> search(MediaType type, String query, Integer year)
      throws IOException, SQLException;

  LookupResult resolveExact(MediaType type, String title, Integer year)
      throws IOException, SQLException;

  Optional<LookupResult> resolveExactCached(MediaType type, String title, Integer year)
      throws SQLException;

  Map<MetadataLookupRequest, LookupResult> resolveExactBatch(List<MetadataLookupRequest> requests)
      throws IOException, SQLException;

  String getDetailsJson(MediaType type, long tmdbId, String appendToResponse)
      throws IOException, SQLException;

  Optional<String> getCachedDetailsJson(MediaType type, long tmdbId, String appendToResponse)
      throws SQLException;

  TmdbEpisode getEpisode(long seriesId, int seasonNumber, int episodeNumber)
      throws IOException, SQLException;

  Optional<TmdbEpisode> getCachedEpisode(long seriesId, int seasonNumber, int episodeNumber)
      throws IOException, SQLException;

  TmdbArtworkConfiguration getArtworkConfiguration() throws IOException, SQLException;

  Optional<TmdbArtworkConfiguration> getCachedArtworkConfiguration()
      throws IOException, SQLException;
}
