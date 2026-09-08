package org.opensagetv.vibe.tmdb;

/** Typed episode metadata returned without exposing a JSON implementation. */
public final class TmdbEpisode {
  private final long id;
  private final long seriesId;
  private final int seasonNumber;
  private final int episodeNumber;
  private final String name;
  private final String overview;
  private final String airDate;
  private final String stillPath;

  public TmdbEpisode(long id, long seriesId, int seasonNumber, int episodeNumber,
      String name, String overview, String airDate, String stillPath) {
    this.id = id;
    this.seriesId = seriesId;
    this.seasonNumber = seasonNumber;
    this.episodeNumber = episodeNumber;
    this.name = safe(name);
    this.overview = safe(overview);
    this.airDate = safe(airDate);
    this.stillPath = safe(stillPath);
  }

  public long getId() { return id; }
  public long getSeriesId() { return seriesId; }
  public int getSeasonNumber() { return seasonNumber; }
  public int getEpisodeNumber() { return episodeNumber; }
  public String getName() { return name; }
  public String getOverview() { return overview; }
  public String getAirDate() { return airDate; }
  public String getStillPath() { return stillPath; }

  private static String safe(String value) { return value == null ? "" : value; }
}
