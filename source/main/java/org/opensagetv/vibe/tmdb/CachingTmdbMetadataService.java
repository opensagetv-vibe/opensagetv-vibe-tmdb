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
    if (type == null) throw new IllegalArgumentException("media type is required");
    String normalized = normalizeTitle(title);
    Optional<Long> manual = cache.getManualMapping(type.apiPath(), normalized, year);
    long now = Instant.now().getEpochSecond();
    if (manual.isPresent()) {
      return new LookupResult(LookupResult.Status.MATCHED, manual.get(), "manual", now, Long.MAX_VALUE);
    }
    String lookupKey = lookupKey(type, normalized, year);
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
  public Optional<LookupResult> resolveExactCached(MediaType type, String title, Integer year)
      throws SQLException {
    if (type == null) throw new IllegalArgumentException("media type is required");
    String normalized = normalizeTitle(requireText(title, "title"));
    Optional<Long> manual = cache.getManualMapping(type.apiPath(), normalized, year);
    long now = Instant.now().getEpochSecond();
    if (manual.isPresent()) {
      return Optional.of(new LookupResult(
          LookupResult.Status.MATCHED, manual.get(), "manual", now, Long.MAX_VALUE));
    }
    return cache.getLookup(lookupKey(type, normalized, year), now);
  }

  @Override
  public Map<MetadataLookupRequest, LookupResult> resolveExactBatch(
      List<MetadataLookupRequest> requests) throws IOException, SQLException {
    if (requests == null) throw new IllegalArgumentException("requests are required");
    Map<MetadataLookupRequest, LookupResult> resolved =
        new LinkedHashMap<MetadataLookupRequest, LookupResult>();
    for (MetadataLookupRequest request : requests) {
      if (request == null) throw new IllegalArgumentException("batch request must not be null");
      if (!resolved.containsKey(request)) {
        resolved.put(request, resolveExact(
            request.getMediaType(), request.getTitle(), request.getYear()));
      }
    }
    return Collections.unmodifiableMap(resolved);
  }

  @Override
  public String getDetailsJson(MediaType type, long tmdbId, String appendToResponse)
      throws IOException, SQLException {
    validateDetailsInput(type, tmdbId);
    String append = normalizedAppend(appendToResponse);
    String key = detailsKey(type, tmdbId, append);
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

  @Override
  public Optional<String> getCachedDetailsJson(
      MediaType type, long tmdbId, String appendToResponse) throws SQLException {
    validateDetailsInput(type, tmdbId);
    Optional<CachedResource> cached = cache.getResource(
        detailsKey(type, tmdbId, normalizedAppend(appendToResponse)),
        Instant.now().getEpochSecond());
    return cached.isPresent()
        ? Optional.of(cached.get().getPayloadJson()) : Optional.<String>empty();
  }

  @Override
  public TmdbEpisode getEpisode(long seriesId, int seasonNumber, int episodeNumber)
      throws IOException, SQLException {
    validateEpisodeInput(seriesId, seasonNumber, episodeNumber);
    long now = Instant.now().getEpochSecond();
    String key = episodeKey(seriesId, seasonNumber, episodeNumber);
    Optional<CachedResource> cached = cache.getResource(key, now);
    String json;
    if (cached.isPresent()) {
      json = cached.get().getPayloadJson();
    } else {
      json = api.getJson(
          "tv/" + seriesId + "/season/" + seasonNumber + "/episode/" + episodeNumber,
          commonParameters());
      cache.putResource(key, "tv", Long.valueOf(seriesId), "episode", language,
          region, seasonNumber + ":" + episodeNumber, json, now,
          CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
    }
    return parseEpisode(seriesId, seasonNumber, episodeNumber, json);
  }

  @Override
  public Optional<TmdbEpisode> getCachedEpisode(
      long seriesId, int seasonNumber, int episodeNumber) throws IOException, SQLException {
    validateEpisodeInput(seriesId, seasonNumber, episodeNumber);
    Optional<CachedResource> cached = cache.getResource(
        episodeKey(seriesId, seasonNumber, episodeNumber), Instant.now().getEpochSecond());
    return cached.isPresent()
        ? Optional.of(parseEpisode(seriesId, seasonNumber, episodeNumber,
            cached.get().getPayloadJson()))
        : Optional.<TmdbEpisode>empty();
  }

  @Override
  public TmdbArtworkConfiguration getArtworkConfiguration() throws IOException, SQLException {
    long now = Instant.now().getEpochSecond();
    Optional<CachedResource> cached = cache.getResource(artworkKey(), now);
    String json;
    if (cached.isPresent()) {
      json = cached.get().getPayloadJson();
    } else {
      json = api.getJson("configuration", Collections.<String, String>emptyMap());
      cache.putResource(artworkKey(), "system", null, "configuration", "", "", "",
          json, now, CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
    }
    return parseArtworkConfiguration(json);
  }

  @Override
  public Optional<TmdbArtworkConfiguration> getCachedArtworkConfiguration()
      throws IOException, SQLException {
    Optional<CachedResource> cached = cache.getResource(
        artworkKey(), Instant.now().getEpochSecond());
    return cached.isPresent()
        ? Optional.of(parseArtworkConfiguration(cached.get().getPayloadJson()))
        : Optional.<TmdbArtworkConfiguration>empty();
  }

  private Map<String, String> commonParameters() {
    Map<String, String> result = new LinkedHashMap<String, String>();
    result.put("language", language);
    if (!region.isEmpty()) result.put("region", region);
    return result;
  }

  private String lookupKey(MediaType type, String normalizedTitle, Integer year) {
    return type.apiPath() + ":" + normalizedTitle + ":" + yearValue(year)
        + ":" + language + ":" + region;
  }

  private String detailsKey(MediaType type, long tmdbId, String append) {
    return "details:" + type.apiPath() + ":" + tmdbId + ":" + language + ":" + region + ":" + append;
  }

  private String episodeKey(long seriesId, int seasonNumber, int episodeNumber) {
    return "episode:tv:" + seriesId + ":" + seasonNumber + ":" + episodeNumber
        + ":" + language + ":" + region;
  }

  private String artworkKey() { return "configuration:images"; }

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

  private static TmdbEpisode parseEpisode(
      long seriesId, int seasonNumber, int episodeNumber, String json) throws IOException {
    try {
      JsonObject item = JsonParser.parseString(json).getAsJsonObject();
      if (!item.has("id") || item.get("id").isJsonNull()) {
        throw new IOException("TMDB episode response has no ID");
      }
      return new TmdbEpisode(item.get("id").getAsLong(), seriesId,
          item.has("season_number") ? item.get("season_number").getAsInt() : seasonNumber,
          item.has("episode_number") ? item.get("episode_number").getAsInt() : episodeNumber,
          string(item, "name"), string(item, "overview"), string(item, "air_date"),
          string(item, "still_path"));
    } catch (RuntimeException error) {
      throw new IOException("TMDB returned malformed episode JSON", error);
    }
  }

  private static TmdbArtworkConfiguration parseArtworkConfiguration(String json)
      throws IOException {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonObject images = root.getAsJsonObject("images");
      if (images == null) throw new IOException("TMDB configuration has no images object");
      return new TmdbArtworkConfiguration(
          string(images, "base_url"), string(images, "secure_base_url"),
          strings(images, "backdrop_sizes"), strings(images, "poster_sizes"),
          strings(images, "profile_sizes"), strings(images, "still_sizes"));
    } catch (RuntimeException error) {
      throw new IOException("TMDB returned malformed configuration JSON", error);
    }
  }

  private static List<String> strings(JsonObject object, String name) {
    JsonArray values = object.getAsJsonArray(name);
    if (values == null) return Collections.emptyList();
    List<String> parsed = new ArrayList<String>();
    for (JsonElement value : values) {
      if (value.isJsonPrimitive()) parsed.add(value.getAsString());
    }
    return parsed;
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

  private static String normalizedAppend(String appendToResponse) {
    return appendToResponse == null ? "" : appendToResponse.trim();
  }

  private static void validateDetailsInput(MediaType type, long tmdbId) {
    if (type == null) throw new IllegalArgumentException("media type is required");
    if (tmdbId <= 0) throw new IllegalArgumentException("TMDB ID must be positive");
  }

  private static void validateEpisodeInput(long seriesId, int seasonNumber, int episodeNumber) {
    if (seriesId <= 0) throw new IllegalArgumentException("TMDB series ID must be positive");
    if (seasonNumber < 0) throw new IllegalArgumentException("season number must not be negative");
    if (episodeNumber <= 0) throw new IllegalArgumentException("episode number must be positive");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }

  @Override
  public void close() throws IOException { cache.close(); }
}
