package org.opensagetv.vibe.tmdb;

public final class CachedResource {
  private final String cacheKey;
  private final String payloadJson;
  private final long fetchedAtEpochSeconds;
  private final long expiresAtEpochSeconds;

  CachedResource(
      String cacheKey, String payloadJson, long fetchedAtEpochSeconds, long expiresAtEpochSeconds) {
    this.cacheKey = cacheKey;
    this.payloadJson = payloadJson;
    this.fetchedAtEpochSeconds = fetchedAtEpochSeconds;
    this.expiresAtEpochSeconds = expiresAtEpochSeconds;
  }

  public String getCacheKey() {
    return cacheKey;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public long getFetchedAtEpochSeconds() {
    return fetchedAtEpochSeconds;
  }

  public long getExpiresAtEpochSeconds() {
    return expiresAtEpochSeconds;
  }
}
