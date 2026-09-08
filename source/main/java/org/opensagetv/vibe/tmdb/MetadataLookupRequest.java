package org.opensagetv.vibe.tmdb;

import java.util.Objects;

/** Immutable lookup input suitable for deduplicated XMLTV batches. */
public final class MetadataLookupRequest {
  private final MediaType mediaType;
  private final String title;
  private final Integer year;

  public MetadataLookupRequest(MediaType mediaType, String title, Integer year) {
    if (mediaType == null) throw new IllegalArgumentException("mediaType is required");
    if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("title must not be blank");
    this.mediaType = mediaType;
    this.title = title.trim();
    this.year = year;
  }

  public MediaType getMediaType() { return mediaType; }
  public String getTitle() { return title; }
  public Integer getYear() { return year; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MetadataLookupRequest)) return false;
    MetadataLookupRequest that = (MetadataLookupRequest) other;
    return mediaType == that.mediaType && title.equals(that.title) && Objects.equals(year, that.year);
  }

  @Override
  public int hashCode() { return Objects.hash(mediaType, title, year); }
}
