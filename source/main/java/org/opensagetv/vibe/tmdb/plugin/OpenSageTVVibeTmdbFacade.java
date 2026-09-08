package org.opensagetv.vibe.tmdb.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensagetv.vibe.tmdb.LookupResult;
import org.opensagetv.vibe.tmdb.MediaType;
import org.opensagetv.vibe.tmdb.TmdbEpisode;
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

  /**
   * Consumer-neutral, scalar metadata intended for import plugins.
   * An empty matrix means no unambiguous match; callers must preserve source data.
   */
  public static String[][] getProgrammeMetadata(String mediaType, String title, int year,
      int season, int episode) throws IOException, SQLException {
    MediaType type = parseType(mediaType);
    LookupResult match = TmdbServiceRegistry.get().resolveExact(
        type, title, year <= 0 ? null : Integer.valueOf(year));
    if (match.getStatus() != LookupResult.Status.MATCHED || match.getTmdbId() == null) {
      return new String[0][0];
    }
    long id = match.getTmdbId().longValue();
    JsonObject details = JsonParser.parseString(
        TmdbServiceRegistry.get().getDetailsJson(type, id, "")).getAsJsonObject();
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("tmdb_id", Long.toString(id));
    values.put("media_type", type.apiPath());
    values.put("title", first(details, "title", "name"));
    values.put("description", string(details, "overview"));
    values.put("year", firstYear(details, "release_date", "first_air_date"));
    values.put("original_language", string(details, "original_language"));
    values.put("genres", joinObjects(details.getAsJsonArray("genres"), "name", "\u001f"));
    values.put("countries", joinCountries(details, "\u001f"));
    if (type == MediaType.TV && season > 0 && episode > 0) {
      TmdbEpisode item = TmdbServiceRegistry.get().getEpisode(id, season, episode);
      values.put("episode_name", item.getName());
      values.put("episode_description", item.getOverview());
      values.put("episode_air_date", item.getAirDate());
    }
    String[][] rows = new String[values.size()][2];
    int index = 0;
    for (Map.Entry<String, String> value : values.entrySet()) {
      rows[index][0] = value.getKey();
      rows[index][1] = value.getValue() == null ? "" : value.getValue();
      index++;
    }
    return rows;
  }

  private static String first(JsonObject object, String first, String second) {
    String value = string(object, first);
    return value.isEmpty() ? string(object, second) : value;
  }

  private static String firstYear(JsonObject object, String first, String second) {
    String value = first(object, first, second);
    return value.length() >= 4 ? value.substring(0, 4) : "";
  }

  private static String string(JsonObject object, String key) {
    JsonElement value = object == null ? null : object.get(key);
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  private static String joinObjects(JsonArray values, String field, String separator) {
    if (values == null) return "";
    StringBuilder result = new StringBuilder();
    for (JsonElement item : values) {
      if (!item.isJsonObject()) continue;
      String value = string(item.getAsJsonObject(), field);
      if (value.isEmpty()) continue;
      if (result.length() > 0) result.append(separator);
      result.append(value);
    }
    return result.toString();
  }

  private static String joinCountries(JsonObject object, String separator) {
    JsonArray values = object.getAsJsonArray("production_countries");
    if (values != null && values.size() > 0) return joinObjects(values, "name", separator);
    values = object.getAsJsonArray("origin_country");
    if (values == null) return "";
    StringBuilder result = new StringBuilder();
    for (JsonElement item : values) {
      if (!item.isJsonPrimitive()) continue;
      if (result.length() > 0) result.append(separator);
      result.append(item.getAsString());
    }
    return result.toString();
  }

  private static MediaType parseType(String value) {
    if (value == null) throw new IllegalArgumentException("media type is required");
    String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
    if ("SHOW".equals(normalized) || "SERIES".equals(normalized)) normalized = "TV";
    return MediaType.valueOf(normalized);
  }
}
