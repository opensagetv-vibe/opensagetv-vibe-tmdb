package org.opensagetv.vibe.tmdb;

/** Central cache durations for all TMDB consumers. */
public final class CachePolicy {
  public static final long DAY_SECONDS = 24L * 60L * 60L;
  public static final long DEFAULT_DETAILS_TTL_SECONDS = 30L * DAY_SECONDS;
  public static final long DEFAULT_SEARCH_TTL_SECONDS = 7L * DAY_SECONDS;
  public static final long DEFAULT_NEGATIVE_TTL_SECONDS = DAY_SECONDS;
  public static final long MAX_RETENTION_SECONDS = 180L * DAY_SECONDS;

  private CachePolicy() {}

  public static long boundedTtl(long requestedSeconds) {
    if (requestedSeconds <= 0) {
      throw new IllegalArgumentException("Cache TTL must be positive");
    }
    return Math.min(requestedSeconds, MAX_RETENTION_SECONDS);
  }
}
