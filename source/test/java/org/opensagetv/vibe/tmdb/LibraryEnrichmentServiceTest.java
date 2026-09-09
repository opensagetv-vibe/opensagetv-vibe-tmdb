package org.opensagetv.vibe.tmdb;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class LibraryEnrichmentServiceTest {
  static void run() throws Exception {
    TmdbCache cache = new TmdbCache(
        Files.createTempDirectory("tmdb-enrichment-test").resolve("cache.sqlite3"));
    FakeMetadata metadata = new FakeMetadata();
    LibraryEnrichmentService service = new LibraryEnrichmentService(metadata, cache);
    try {
      LibraryEnrichmentService.Item existing = new LibraryEnrichmentService.Item(
          "media:1", MediaType.MOVIE, "Sample Movie", Integer.valueOf(2026), true, true);
      LibraryEnrichmentService.Request preview = request("preview", existing,
          LibraryEnrichmentService.Action.PREVIEW_ONLY, true, false, null);
      LibraryEnrichmentService.Job previewJob = service.start(preview, null, null);
      require(previewJob.await(5, TimeUnit.SECONDS), "preview completion");
      require(previewJob.getState() == LibraryEnrichmentService.JobState.COMPLETED,
          "preview state");
      require(previewJob.getResults().get(0).getStatus()
          == LibraryEnrichmentService.ResultStatus.PREVIEWED, "preview result");
      require(service.checkpoint("preview").get().getCompleted() == 1,
          "preview checkpoint");

      final AtomicInteger writes = new AtomicInteger();
      LibraryEnrichmentService.Sink sink = new LibraryEnrichmentService.Sink() {
        public void apply(LibraryEnrichmentService.Item item, long tmdbId,
            String detailsJson, LibraryEnrichmentService.Action action) {
          require(tmdbId == 101L, "sink TMDB ID");
          require(detailsJson.contains("Sample Movie"), "sink details");
          require(action.savesMetadata(), "sink metadata action");
          writes.incrementAndGet();
        }
      };
      LibraryEnrichmentService.Request guarded = request("save", existing,
          LibraryEnrichmentService.Action.SAVE_METADATA_AND_ARTWORK, true, false, null);
      LibraryEnrichmentService.Job guardedJob = service.start(guarded, sink, null);
      guardedJob.await(5, TimeUnit.SECONDS);
      require(guardedJob.getResults().get(0).getStatus()
          == LibraryEnrichmentService.ResultStatus.OVERWRITE_CONFIRMATION_REQUIRED,
          "overwrite guard");
      require(writes.get() == 0, "guarded write not invoked");

      LibraryEnrichmentService.Request confirmed = request("save", existing,
          LibraryEnrichmentService.Action.SAVE_METADATA_AND_ARTWORK, true, true, null);
      LibraryEnrichmentService.Job resumed = service.start(confirmed, sink, null);
      resumed.await(5, TimeUnit.SECONDS);
      require(resumed.getResults().get(0).getStatus()
          == LibraryEnrichmentService.ResultStatus.SAVED, "confirmed resume result");
      require(writes.get() == 1, "confirmed write invoked once");
      require(service.checkpoint("save").get().getCompleted() == 1,
          "resumed checkpoint completed");

      LibraryEnrichmentService.Request manualReview = request("review", existing,
          LibraryEnrichmentService.Action.PREVIEW_ONLY, false, false, null);
      LibraryEnrichmentService.Job reviewJob = service.start(manualReview, null, null);
      reviewJob.await(5, TimeUnit.SECONDS);
      require(reviewJob.getResults().get(0).getStatus()
          == LibraryEnrichmentService.ResultStatus.REVIEW_REQUIRED,
          "manual review queue");

      Map<String, Long> approval = Collections.singletonMap("media:1", Long.valueOf(101L));
      LibraryEnrichmentService.Request approved = request("approved", existing,
          LibraryEnrichmentService.Action.SAVE_METADATA, false, true, approval);
      LibraryEnrichmentService.Job approvedJob = service.start(approved, sink, null);
      approvedJob.await(5, TimeUnit.SECONDS);
      require(approvedJob.getResults().get(0).getStatus()
          == LibraryEnrichmentService.ResultStatus.SAVED, "explicit match approval");
      require(Long.valueOf(101L).equals(cache.getManualMapping(
          "movie", CachingTmdbMetadataService.normalizeTitle("Sample Movie"),
          Integer.valueOf(2026)).get()), "explicit approval persists manual mapping");

      try {
        LibraryEnrichmentService.Item other = new LibraryEnrichmentService.Item(
            "media:2", MediaType.MOVIE, "Other", null, false, false);
        service.start(request("preview", other, LibraryEnrichmentService.Action.PREVIEW_ONLY,
            true, false, null), null, null);
        throw new AssertionError("checkpoint fingerprint mismatch should fail");
      } catch (IllegalArgumentException expected) {
        require(expected.getMessage().contains("different item set"),
            "fingerprint mismatch diagnostic");
      }

      TmdbCache cleanCache = new TmdbCache(
          Files.createTempDirectory("tmdb-clean-title-test").resolve("cache.sqlite3"));
      FakeMetadata cleanMetadata = new FakeMetadata("Honey I Shrunk the Kids");
      LibraryEnrichmentService cleanService = new LibraryEnrichmentService(cleanMetadata, cleanCache);
      try {
        LibraryEnrichmentService.Item noisy = new LibraryEnrichmentService.Item(
            "media:noisy", MediaType.MOVIE,
            "001 - Honey.I.Shrunk.the.Kids.(1989).1080p.BluRay.mkv", null, false, false);
        LibraryEnrichmentService.Job cleanJob = cleanService.start(request("clean-title", noisy,
            LibraryEnrichmentService.Action.PREVIEW_ONLY, true, false, null), null, null);
        cleanJob.await(5, TimeUnit.SECONDS);
        require(cleanJob.getResults().get(0).getStatus()
            == LibraryEnrichmentService.ResultStatus.PREVIEWED,
            "cleaned title resolves before raw fallback");
        require("Honey I Shrunk the Kids".equals(cleanMetadata.resolvedTitles.get(0)),
            "cleaned title is first lookup candidate");
        require(Integer.valueOf(1989).equals(cleanMetadata.resolvedYears.get(0)),
            "parsed year is supplied as a lookup hint");
      } finally {
        cleanService.close();
        cleanCache.close();
      }

      TmdbCache fuzzyCache = new TmdbCache(
          Files.createTempDirectory("tmdb-fuzzy-title-test").resolve("cache.sqlite3"));
      FakeMetadata fuzzyMetadata = new FakeMetadata("no exact title");
      fuzzyMetadata.searchResults = Arrays.asList(new TmdbSearchResult(202L, MediaType.TV,
          "The Big Bang Theory", "The Big Bang Theory", "", "2007-09-24", "/poster.jpg",
          Arrays.asList("US")));
      LibraryEnrichmentService fuzzyService = new LibraryEnrichmentService(
          fuzzyMetadata, fuzzyCache);
      try {
        LibraryEnrichmentService.Item typo = new LibraryEnrichmentService.Item(
            "media:typo", MediaType.TV, "The Big Bang Theroy", null, false, false);
        LibraryEnrichmentService.Job fuzzyJob = fuzzyService.start(request("fuzzy-title", typo,
            LibraryEnrichmentService.Action.PREVIEW_ONLY, true, false, null), null, null);
        fuzzyJob.await(5, TimeUnit.SECONDS);
        require(fuzzyJob.getResults().get(0).getStatus()
            == LibraryEnrichmentService.ResultStatus.PREVIEWED,
            "unique strong non-exact title is accepted");
        require(Long.valueOf(202L).equals(fuzzyJob.getResults().get(0).getTmdbId()),
            "fuzzy title keeps TMDB identity");
      } finally {
        fuzzyService.close();
        fuzzyCache.close();
      }

      TmdbCache episodeCache = new TmdbCache(
          Files.createTempDirectory("tmdb-episode-identity-test").resolve("cache.sqlite3"));
      FakeMetadata episodeMetadata = new FakeMetadata();
      episodeMetadata.episodeResult = new TmdbEpisode(305L, 101L, 11, 5,
          "The Collaboration Contamination", "Episode overview", "2017-10-23", "/still.jpg");
      LibraryEnrichmentService episodeService = new LibraryEnrichmentService(
          episodeMetadata, episodeCache);
      try {
        LibraryEnrichmentService.IdentityEvidence evidence =
            new LibraryEnrichmentService.IdentityEvidence(null, "The Big Bang Theory",
                "The Collaboration Contamination", Integer.valueOf(11), Integer.valueOf(5),
                "2017-10-23", "EP000000110005");
        LibraryEnrichmentService.Item episodeItem = new LibraryEnrichmentService.Item(
            "media:episode", MediaType.TV, "The Big Bang Theory", null, false, false, evidence);
        LibraryEnrichmentService.Job episodeJob = episodeService.start(request("episode", episodeItem,
            LibraryEnrichmentService.Action.PREVIEW_ONLY, true, false, null), null, null);
        episodeJob.await(5, TimeUnit.SECONDS);
        require(episodeJob.getResults().get(0).getStatus()
            == LibraryEnrichmentService.ResultStatus.PREVIEWED,
            "series plus episode identity resolves");
        require(episodeJob.getResults().get(0).getMatchedTitle().contains("S11E05"),
            "episode match is identified in review output");
      } finally {
        episodeService.close();
        episodeCache.close();
      }
    } finally {
      service.close();
      cache.close();
    }
  }

  private static LibraryEnrichmentService.Request request(String jobId,
      LibraryEnrichmentService.Item item, LibraryEnrichmentService.Action action,
      boolean autoApprove, boolean overwrite, Map<String, Long> approvals) {
    return new LibraryEnrichmentService.Request(jobId, Arrays.asList(item), action,
        autoApprove, overwrite, 2, approvals);
  }

  private static final class FakeMetadata implements TmdbMetadataService {
    private final String requiredTitle;
    private final List<String> resolvedTitles = new java.util.ArrayList<String>();
    private final List<Integer> resolvedYears = new java.util.ArrayList<Integer>();
    private List<TmdbSearchResult> searchResults = Collections.emptyList();
    private TmdbEpisode episodeResult;

    FakeMetadata() { this(null); }
    FakeMetadata(String requiredTitle) { this.requiredTitle = requiredTitle; }

    public List<TmdbSearchResult> search(MediaType type, String query, Integer year) {
      return searchResults;
    }
    public LookupResult resolveExact(MediaType type, String title, Integer year) {
      resolvedTitles.add(title);
      resolvedYears.add(year);
      if (requiredTitle != null && !requiredTitle.equals(title)) {
        return new LookupResult(LookupResult.Status.NO_MATCH, null, "", 1L, Long.MAX_VALUE);
      }
      return new LookupResult(LookupResult.Status.MATCHED, Long.valueOf(101L),
          "Sample Movie", 1L, Long.MAX_VALUE);
    }
    public Optional<LookupResult> resolveExactCached(MediaType type, String title, Integer year) {
      return Optional.empty();
    }
    public Map<MetadataLookupRequest, LookupResult> resolveExactBatch(
        List<MetadataLookupRequest> requests) {
      return Collections.emptyMap();
    }
    public String getDetailsJson(MediaType type, long tmdbId, String append) {
      return "{\"id\":101,\"title\":\"Sample Movie\"}";
    }
    public Optional<String> getCachedDetailsJson(MediaType type, long tmdbId, String append) {
      return Optional.empty();
    }
    public TmdbEpisode getEpisode(long seriesId, int seasonNumber, int episodeNumber)
        throws IOException, SQLException {
      if (episodeResult == null) throw new UnsupportedOperationException();
      return episodeResult;
    }
    public Optional<TmdbEpisode> getCachedEpisode(long seriesId, int seasonNumber,
        int episodeNumber) { return Optional.empty(); }
    public TmdbArtworkConfiguration getArtworkConfiguration() {
      return new TmdbArtworkConfiguration("http://image/", "https://image/",
          Collections.<String>emptyList(), Arrays.asList("w500"),
          Collections.<String>emptyList(), Collections.<String>emptyList());
    }
    public Optional<TmdbArtworkConfiguration> getCachedArtworkConfiguration() {
      return Optional.empty();
    }
    public LibraryEnrichmentService getLibraryEnrichmentService() { return null; }
    public void close() { }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
