package org.opensagetv.vibe.tmdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative search-title cleanup shared by TMDB consumers.
 *
 * <p>The original value is always retained as the final fallback. Cleanup is
 * intentionally limited to common media filenames, SageTV recording suffixes,
 * episode markers and technical release tags; it does not guess at arbitrary
 * words in a real title.</p>
 */
public final class MediaTitleParser {
  private static final Pattern EXTENSION = Pattern.compile(
      "(?i)\\.(?:3g2|3gp|asf|avi|divx|flv|iso|m2ts|m4v|mkv|mov|mp4|mpeg|mpg|mts|ogm|ts|vob|webm|wmv)$");
  private static final Pattern SAGE_RECORDING_ID = Pattern.compile("[-_ ]\\d{5,}(?:[-_ ]\\d+)?$");
  private static final Pattern EPISODE = Pattern.compile(
      "(?i)(?:[ ._\\-]+)(?:s\\d{1,2}[ ._\\-]*e\\d{1,3}(?:[ ._\\-]*e\\d{1,3})*|\\d{1,2}x\\d{1,3})(?:\\b|[ ._\\-])");
  private static final Pattern YEAR = Pattern.compile(
      "(?:[ ._\\-]*[\\[(]?)((?:19|20)\\d{2})(?:[\\])]?[ ._\\-]*)");
  private static final Pattern DISC_PART = Pattern.compile(
      "(?i)[ ._\\-]+(?:cd|disc|disk|part|pt)[ ._\\-]*\\d{1,2}$");
  private static final Pattern TECHNICAL_SUFFIX = Pattern.compile(
      "(?i)(?:[ ._\\-]+)(?:(?:360|480|576|720|1080|1440|2160)[pi]|4k|8k|"
          + "(?:x|h)[ ._\\-]*26[45]|hevc|avc|mpeg[ ._\\-]*[24]|divx|xvid|"
          + "web[ ._\\-]*dl|webrip|bluray|blu[ ._\\-]*ray|bdrip|brip|dvdrip|hdtv|remux|"
          + "hdr10|hdr|dolby[ ._\\-]*vision|dv|sdr|proper|repack|extended|uncut|"
          + "aac|ac3|eac3|ddp|dts(?:[ ._\\-]*hd)?|truehd|atmos|flac|opus|"
          + "[257][ ._\\-]*1|10bit|8bit|multi)(?:[ ._\\-].*)?$\\s*");
  private static final Pattern LEADING_INDEX = Pattern.compile("^\\s*(?:\\[?\\d{1,4}\\]?)[ ._\\-]+(?=\\p{L})");
  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])");
  private static final Pattern COMPACT_CONNECTOR = Pattern.compile(
      "(?<=[a-z])(?=(?:the|and|of|to|in|for)[A-Z])");

  private MediaTitleParser() {}

  public static ParsedTitle parse(String value, MediaType type) {
    String original = value == null ? "" : value.trim();
    String base = filename(original);
    base = EXTENSION.matcher(base).replaceFirst("");
    base = SAGE_RECORDING_ID.matcher(base).replaceFirst("");
    base = LEADING_INDEX.matcher(base).replaceFirst("");

    Integer inferredYear = null;
    String cleaned = base;
    Matcher episode = EPISODE.matcher(cleaned);
    if (type == MediaType.TV && episode.find()) cleaned = cleaned.substring(0, episode.start());

    Matcher year = YEAR.matcher(cleaned);
    if (year.find() && hasTitleBefore(cleaned, year.start())) {
      inferredYear = Integer.valueOf(year.group(1));
      String after = cleaned.substring(year.end());
      String yearToken = year.group();
      boolean bracketed = yearToken.indexOf('(') >= 0 || yearToken.indexOf('[') >= 0;
      boolean followedByTechnicalSuffix = !after.trim().isEmpty()
          && TECHNICAL_SUFFIX.matcher(" " + after).matches();
      if (bracketed || followedByTechnicalSuffix) {
        cleaned = cleaned.substring(0, year.start());
      } else {
        inferredYear = null;
      }
    }
    cleaned = TECHNICAL_SUFFIX.matcher(cleaned).replaceFirst("");
    cleaned = DISC_PART.matcher(cleaned).replaceFirst("");
    cleaned = displaySeparators(cleaned);

    Set<String> candidates = new LinkedHashSet<String>();
    add(candidates, cleaned);
    String compactSplit = splitCompactTitle(cleaned);
    if (!compactSplit.equals(cleaned)) {
      candidates.remove(cleaned);
      add(candidates, compactSplit);
      add(candidates, cleaned);
      cleaned = compactSplit;
    }
    add(candidates, displaySeparators(base));
    add(candidates, original);
    return new ParsedTitle(cleaned.isEmpty() ? displaySeparators(base) : cleaned,
        inferredYear, new ArrayList<String>(candidates));
  }

  private static String filename(String value) {
    int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
    return slash < 0 ? value : value.substring(slash + 1);
  }

  private static boolean hasTitleBefore(String value, int end) {
    return end > 0 && value.substring(0, end).matches(".*\\p{L}.*");
  }

  private static String displaySeparators(String value) {
    return value.replaceAll("[._]+", " ").replaceAll("\\s*-\\s*", " ")
        .trim().replaceAll("\\s+", " ");
  }

  private static String splitCompactTitle(String value) {
    if (value.indexOf(' ') >= 0) return value;
    String split = COMPACT_CONNECTOR.matcher(value).replaceAll(" ");
    split = CAMEL_BOUNDARY.matcher(split).replaceAll(" ");
    return split.equals(value) ? value : split.trim().replaceAll("\\s+", " ");
  }

  private static void add(Set<String> values, String value) {
    if (value != null && !value.trim().isEmpty()) values.add(value.trim());
  }

  public static final class ParsedTitle {
    private final String title;
    private final Integer year;
    private final List<String> searchCandidates;

    private ParsedTitle(String title, Integer year, List<String> searchCandidates) {
      this.title = title;
      this.year = year;
      this.searchCandidates = Collections.unmodifiableList(searchCandidates);
    }

    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public List<String> getSearchCandidates() { return searchCandidates; }

    @Override
    public String toString() {
      return String.format(Locale.ROOT, "%s (%s)", title, year == null ? "no year" : year);
    }
  }
}
