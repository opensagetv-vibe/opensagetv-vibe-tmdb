package org.opensagetv.vibe.tmdb;

public final class LookupResult {
  public enum Status {
    MATCHED,
    NO_MATCH,
    AMBIGUOUS
  }

  private final Status status;
  private final Long tmdbId;
  private final String matchedTitle;
  private final long fetchedAtEpochSeconds;
  private final long expiresAtEpochSeconds;

  LookupResult(
      Status status,
      Long tmdbId,
      String matchedTitle,
      long fetchedAtEpochSeconds,
      long expiresAtEpochSeconds) {
    this.status = status;
    this.tmdbId = tmdbId;
    this.matchedTitle = matchedTitle;
    this.fetchedAtEpochSeconds = fetchedAtEpochSeconds;
    this.expiresAtEpochSeconds = expiresAtEpochSeconds;
  }

  public Status getStatus() {
    return status;
  }

  public Long getTmdbId() {
    return tmdbId;
  }

  public String getMatchedTitle() {
    return matchedTitle;
  }

  public long getFetchedAtEpochSeconds() {
    return fetchedAtEpochSeconds;
  }

  public long getExpiresAtEpochSeconds() {
    return expiresAtEpochSeconds;
  }
}
