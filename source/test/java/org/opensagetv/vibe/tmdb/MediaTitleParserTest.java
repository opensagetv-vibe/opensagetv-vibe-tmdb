package org.opensagetv.vibe.tmdb;

final class MediaTitleParserTest {
  private MediaTitleParserTest() {}

  static void run() {
    require("The Big Bang Theory".equals(MediaTitleParser.parse(
        "The.Big.Bang.Theory.S03E12.1080p.WEB-DL.x264.mkv", MediaType.TV).getTitle()),
        "TV episode/release suffix cleanup");
    MediaTitleParser.ParsedTitle movie = MediaTitleParser.parse(
        "001 - The Matrix (1999) 1080p BluRay DTS.mkv", MediaType.MOVIE);
    require("The Matrix".equals(movie.getTitle()), "movie index/year/release suffix cleanup");
    require(Integer.valueOf(1999).equals(movie.getYear()), "movie year extraction");
    require(movie.getSearchCandidates().contains("001 - The Matrix (1999) 1080p BluRay DTS.mkv"),
        "raw title fallback retained");
    String meet = MediaTitleParser.parse(
        "/var/media/tv/MeetthePress-65149351-0.ts", MediaType.TV).getTitle();
    require("Meet the Press".equals(meet),
        "SageTV recording ID and conservative camel-case cleanup: " + meet);
    require("1917".equals(MediaTitleParser.parse("1917.mkv", MediaType.MOVIE).getTitle()),
        "year-only movie title preserved");
    require("Blade Runner 2049".equals(MediaTitleParser.parse(
        "Blade Runner 2049.mkv", MediaType.MOVIE).getTitle()),
        "year-like title number preserved when it is not a release suffix");
    require("Honey I Shrunk the Kids".equals(MediaTitleParser.parse(
        "Honey I Shrunk the Kids", MediaType.MOVIE).getTitle()),
        "already-clean natural title preserved exactly");
    MediaTitleParser.ParsedTitle honey = MediaTitleParser.parse(
        "Honey.I.Shrunk.the.Kids.(1989).1080p.BluRay.mkv", MediaType.MOVIE);
    require("Honey I Shrunk the Kids".equals(honey.getTitle()),
        "Honey I Shrunk the Kids filename cleanup");
    require(Integer.valueOf(1989).equals(honey.getYear()), "Honey year hint");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
