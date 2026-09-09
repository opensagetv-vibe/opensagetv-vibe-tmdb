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
    public List<TmdbSearchResult> search(MediaType type, String query, Integer year) {
      return Collections.emptyList();
    }
    public LookupResult resolveExact(MediaType type, String title, Integer year) {
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
        throws IOException, SQLException { throw new UnsupportedOperationException(); }
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
