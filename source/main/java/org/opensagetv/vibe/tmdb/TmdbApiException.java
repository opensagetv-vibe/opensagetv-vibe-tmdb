package org.opensagetv.vibe.tmdb;

import java.io.IOException;

public class TmdbApiException extends IOException {
  private static final long serialVersionUID = 1L;
  private final int statusCode;

  public TmdbApiException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public TmdbApiException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
