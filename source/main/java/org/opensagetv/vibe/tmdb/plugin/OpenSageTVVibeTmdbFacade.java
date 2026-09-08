package org.opensagetv.vibe.tmdb.plugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.opensagetv.vibe.tmdb.LookupResult;
import org.opensagetv.vibe.tmdb.MediaType;
import org.opensagetv.vibe.tmdb.TmdbSearchResult;
import org.opensagetv.vibe.tmdb.TmdbServiceRegistry;

/** Small array/scalar facade suitable for SageTV Studio reflection calls. */
public final class OpenSageTVVibeTmdbFacade {
  private OpenSageTVVibeTmdbFacade() {}

  public static boolean isAvailable() { return TmdbServiceRegistry.isStarted(); }

  public static String getStatus() { return OpenSageTVVibeTmdbPlugin.getStatus(); }

  public static long resolveId(String mediaType, String title, int year)
      throws IOException, SQLException {
    LookupResult result = TmdbServiceRegistry.get().resolveExact(
        parseType(mediaType), title, year <= 0 ? null : Integer.valueOf(year));
    if (result.getStatus() == LookupResult.Status.AMBIGUOUS) return -2L;
    return result.getTmdbId() == null ? -1L : result.getTmdbId().longValue();
  }

  public static String[][] search(String mediaType, String title, int year)
      throws IOException, SQLException {
    List<TmdbSearchResult> results = TmdbServiceRegistry.get().search(
        parseType(mediaType), title, year <= 0 ? null : Integer.valueOf(year));
    String[][] rows = new String[results.size()][5];
    for (int index = 0; index < results.size(); index++) {
      TmdbSearchResult result = results.get(index);
      rows[index][0] = Long.toString(result.getId());
      rows[index][1] = result.getTitle();
      rows[index][2] = result.getOriginalTitle();
      rows[index][3] = result.getDate();
      rows[index][4] = result.getPosterPath();
    }
    return rows;
  }

  public static String getDetailsJson(String mediaType, long tmdbId, String append)
      throws IOException, SQLException {
    return TmdbServiceRegistry.get().getDetailsJson(parseType(mediaType), tmdbId, append);
  }

  private static MediaType parseType(String value) {
    if (value == null) throw new IllegalArgumentException("media type is required");
    String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
    if ("SHOW".equals(normalized) || "SERIES".equals(normalized)) normalized = "TV";
    return MediaType.valueOf(normalized);
  }
}
