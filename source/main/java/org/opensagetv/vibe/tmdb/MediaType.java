package org.opensagetv.vibe.tmdb;

public enum MediaType {
  MOVIE("movie"),
  TV("tv"),
  PERSON("person");

  private final String apiPath;

  MediaType(String apiPath) {
    this.apiPath = apiPath;
  }

  public String apiPath() {
    return apiPath;
  }
}
