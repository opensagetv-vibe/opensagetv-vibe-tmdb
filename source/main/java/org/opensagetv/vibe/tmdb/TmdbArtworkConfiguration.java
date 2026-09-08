package org.opensagetv.vibe.tmdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed subset of TMDB image configuration needed by reusable consumers. */
public final class TmdbArtworkConfiguration {
  private final String baseUrl;
  private final String secureBaseUrl;
  private final List<String> backdropSizes;
  private final List<String> posterSizes;
  private final List<String> profileSizes;
  private final List<String> stillSizes;

  public TmdbArtworkConfiguration(String baseUrl, String secureBaseUrl,
      List<String> backdropSizes, List<String> posterSizes,
      List<String> profileSizes, List<String> stillSizes) {
    this.baseUrl = safe(baseUrl);
    this.secureBaseUrl = safe(secureBaseUrl);
    this.backdropSizes = immutable(backdropSizes);
    this.posterSizes = immutable(posterSizes);
    this.profileSizes = immutable(profileSizes);
    this.stillSizes = immutable(stillSizes);
  }

  public String getBaseUrl() { return baseUrl; }
  public String getSecureBaseUrl() { return secureBaseUrl; }
  public List<String> getBackdropSizes() { return backdropSizes; }
  public List<String> getPosterSizes() { return posterSizes; }
  public List<String> getProfileSizes() { return profileSizes; }
  public List<String> getStillSizes() { return stillSizes; }

  private static List<String> immutable(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  private static String safe(String value) { return value == null ? "" : value; }
}
