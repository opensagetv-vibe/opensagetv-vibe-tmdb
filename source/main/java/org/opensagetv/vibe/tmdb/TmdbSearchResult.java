package org.opensagetv.vibe.tmdb;

import java.util.Collections;
import java.util.List;

public final class TmdbSearchResult {
  private final long id;
  private final MediaType mediaType;
  private final String title;
  private final String originalTitle;
  private final String overview;
  private final String date;
  private final String posterPath;
  private final List<String> originCountries;

  TmdbSearchResult(
      long id,
      MediaType mediaType,
      String title,
      String originalTitle,
      String overview,
      String date,
      String posterPath,
      List<String> originCountries) {
    this.id = id;
    this.mediaType = mediaType;
    this.title = title;
    this.originalTitle = originalTitle;
    this.overview = overview;
    this.date = date;
    this.posterPath = posterPath;
    this.originCountries = Collections.unmodifiableList(originCountries);
  }

  public long getId() { return id; }
  public MediaType getMediaType() { return mediaType; }
  public String getTitle() { return title; }
  public String getOriginalTitle() { return originalTitle; }
  public String getOverview() { return overview; }
  public String getDate() { return date; }
  public String getPosterPath() { return posterPath; }
  public List<String> getOriginCountries() { return originCountries; }
}
