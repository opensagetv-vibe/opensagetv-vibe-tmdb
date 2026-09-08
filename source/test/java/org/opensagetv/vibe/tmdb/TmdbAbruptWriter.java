package org.opensagetv.vibe.tmdb;

import java.nio.file.Paths;

/** Child-process fixture that deliberately exits without closing SQLite. */
public final class TmdbAbruptWriter {
  private TmdbAbruptWriter() {}

  public static void main(String[] args) throws Exception {
    TmdbCache cache = new TmdbCache(Paths.get(args[0]));
    long now = Long.parseLong(args[1]);
    cache.putResource("abrupt", "tv", Long.valueOf(303), "details", "en-US", "US", "",
        "{\"id\":303}", now, CachePolicy.DEFAULT_DETAILS_TTL_SECONDS);
    Runtime.getRuntime().halt(0);
  }
}
