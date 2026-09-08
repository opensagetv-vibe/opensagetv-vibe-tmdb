package org.opensagetv.vibe.tmdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CachingTmdbMetadataService implements TmdbMetadataService {
  private final TmdbApiClient api;
  private final TmdbCache cache;
  private final String language;
  private final String region;

  public CachingTmdbMetadataService(TmdbConfiguration configuration) throws IOException, SQLException {
    this(new TmdbApiClient(configuration), new TmdbCache(configuration.getCachePath()),
        configuration.getLanguage(), configuration.getRegion());
  }

  CachingTmdbMetadataService(TmdbApiClient api, TmdbCache cache, String language, String region) {
    this.api = api;
    this.cache = cache;
    this.language = language;
    this.region = region;
  }

  @Override
  public List<TmdbSearchResult> search(MediaType type, String query, Integer year)
      throws IOException, SQLException {
    if (type == null) throw new IllegalArgumentException("media type is required");
    String title = requireText(query, "query");
    String normalized = normalizeTitle(title);
    String key = "search:" + type.apiPath() + ":" + normalized + ":" + yearValue(year)
        + ":" + language + ":" + region;
    long now = Instant.now().getEpochSecond();
    Optional<CachedResource> cached = cache.getResource(key, now);
    String json;
    if (cached.isPresent()) {
      json = cached.get().getPayloadJson();
    } else {
      Map<String, String> parameters = commonParameters();
      parameters.put("query", title);
      parameters.put("include_adult", "false");
      if (year != null && type == MediaType.MOVIE) parameters.put("primary_release_year", year.toString());
      if (year != null && type == MediaType.TV) parameters.put("first_air_date_year", year.toString());
      json = api.getJson("search/" + type.apiPath(), parameters);
      cache.putResource(key, type.apiPath(), null, "search", language, region,
          yearValue(year), json, now, CachePolicy.DEFAULT_SEARCH_TTL_SECONDS);
    }
    return parseSearchResults(type, json);
  }

  @Override
  public LookupResult resolveExact(MediaType type, String title, Integer year)
      throws IOException, SQLException {
    String normalized = normalizeTitle(title);
    Optional<Long> manual = cache.getManualMapping(type.apiPath(), normalized, year);
    long now = Instant.now().getEpochSecond();
    if (manual.isPresent()) {
      return new LookupResult(LookupResult.Status.MATCHED, manual.get(), "manual", now, Long.MAX_VALUE);
    }
    String lookupKey = type.apiPath() + ":" + normalized + ":" + yearValue(year)
        + ":" + language + ":" + region;
    Optional<LookupResult> cached = cache.getLookup(lookupKey, now);
    if (cached.isPresent()) return cached.get();

    List<TmdbSearchResult> exact = new ArrayList<TmdbSearchResult>();
    for (TmdbSearchResult candidate : search(type, title, year)) {
      if (normalized.equals(normalizeTitle(candidate.getTitle()))
          || normalized.equals(normalizeTitle(candidate.getOriginalTitle()))) {
        exact.add(candidate);
      }
    }
    if (exact.size() > 1 && type == MediaType.TV) {
      List<TmdbSearchResult> regional = new ArrayList<TmdbSearchResult>();
      for (TmdbSearchResult candidate : exact) {
        if (candidate.getOriginCountries().contains(region)) regional.add(candidate);
      }
      if (regional.size() == 1) exact = regional;
    }

    LookupResult.Status status;
    Long id = null;
    String matchedTitle = "";
    if (exact.size() == 1) {
      status = LookupResult.Status.MATCHED;
      id = Long.valueOf(exact.get(0).getId());
      matchedTitle = exact.get(0).getTitle();
    } else {
      status = exact.isEmpty() ? LookupResult.Status.NO_MATCH : LookupResult.Status.AMBIGUOUS;
    }
    long ttl = status == LookupResult.Status.MATCHED
        ? CachePolicy.DEFAULT_SEARCH_TTL_SECONDS : CachePolicy.DEFAULT_NEGATIVE_TTL_SECONDS;
    cache.putLookup(lookupKey, type.apiPath(), normalized, year, language, region,
        status, id, matchedTitle, now, ttl);
    return cache.getLookup(lookupKey, now).get();
  }

  @Override
  public String getDetailsJson(MediaType type, long tmdbId, String appendToResponse)
      throws IOException, SQLException {
    if (type == null) throw new IllegalArgumentException("media type is required");
    if (tmdbId <= 0) throw new IllegalArgumentException("TMDB ID must be positive");
    String append = appendToResponse == null ? "" : appendToResponse.trim();
    String key = "details:" + type.apiPath() + ":" + tmdbId + ":" + language + ":" + region + ":" + append;
    long now = Instant.now().getEpochSecond();
    Optional<CachedResource> cached = cache.getResource(key, now);
    if (cached.isPresent()) return cached.get().getPayloadJson();
    Map<String, String> parameters = commonParameters();
    if (!append.isEmpty()) parameters.put("append_to_response", append);
    String json = api.getJson(type.apiPath() + "/" + tmdbId, parameters);
    cache.putResource(key, type.apiPath(), Long.valueOf(tmdbId), "details", language,
        region, append, json, now, CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
    return json;
  }

  private Map<String, String> commonParameters() {
    Map<String, String> result = new LinkedHashMap<String, String>();
    result.put("language", language);
    if (!region.isEmpty()) result.put("region", region);
    return result;
  }

  private static List<TmdbSearchResult> parseSearchResults(MediaType type, String json)
      throws IOException {
    try {
      JsonElement rootElement = JsonParser.parseString(json);
      if (!rootElement.isJsonObject()) throw new IOException("TMDB response root is not an object");
      JsonArray results = rootElement.getAsJsonObject().getAsJsonArray("results");
      if (results == null) return Collections.emptyList();
      List<TmdbSearchResult> parsed = new ArrayList<TmdbSearchResult>();
      for (JsonElement itemElement : results) {
        if (!itemElement.isJsonObject()) continue;
        JsonObject item = itemElement.getAsJsonObject();
        if (!item.has("id") || item.get("id").isJsonNull()) continue;
        String title = string(item, type == MediaType.MOVIE ? "title" : "name");
        String original = type == MediaType.PERSON
            ? title : string(item, type == MediaType.MOVIE ? "original_title" : "original_name");
        String date = type == MediaType.PERSON
            ? "" : string(item, type == MediaType.MOVIE ? "release_date" : "first_air_date");
        List<String> countries = new ArrayList<String>();
        JsonArray origin = item.getAsJsonArray("origin_country");
        if (origin != null) {
          for (JsonElement country : origin) if (country.isJsonPrimitive()) countries.add(country.getAsString().toUpperCase(Locale.ROOT));
        }
        parsed.add(new TmdbSearchResult(item.get("id").getAsLong(), type, title, original,
            string(item, "overview"), date,
            string(item, type == MediaType.PERSON ? "profile_path" : "poster_path"), countries));
      }
      return Collections.unmodifiableList(parsed);
    } catch (RuntimeException error) {
      throw new IOException("TMDB returned malformed JSON", error);
    }
  }

  private static String string(JsonObject object, String name) {
    JsonElement value = object.get(name);
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  public static String normalizeTitle(String value) {
    if (value == null) return "";
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    return normalized.replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll("\\s+", " ");
  }

  private static String yearValue(Integer year) { return year == null ? "" : year.toString(); }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }

  @Override
  public void close() throws IOException { cache.close(); }
}
