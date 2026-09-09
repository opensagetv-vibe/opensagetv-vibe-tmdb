package org.opensagetv.vibe.tmdb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Reusable, resumable library-enrichment worker. Consumers enumerate media and
 * own SageTV writes; this service owns matching, TMDB access, pacing, cache and
 * checkpoint state.
 */
public final class LibraryEnrichmentService implements Closeable {
  public enum Action {
    PREVIEW_ONLY,
    SAVE_METADATA,
    SAVE_ARTWORK,
    SAVE_METADATA_AND_ARTWORK;

    public boolean savesMetadata() {
      return this == SAVE_METADATA || this == SAVE_METADATA_AND_ARTWORK;
    }

    public boolean savesArtwork() {
      return this == SAVE_ARTWORK || this == SAVE_METADATA_AND_ARTWORK;
    }
  }

  public enum ResultStatus {
    PREVIEWED,
    SAVED,
    NO_MATCH,
    REVIEW_REQUIRED,
    OVERWRITE_CONFIRMATION_REQUIRED,
    FAILED
  }

  public enum JobState { RUNNING, COMPLETED, CANCELLED, FAILED }

  /** Consumer-owned write boundary. It is never called for preview-only work. */
  public interface Sink {
    void apply(Item item, long tmdbId, String detailsJson, Action action) throws Exception;
  }

  public interface Listener {
    void onProgress(Progress progress);
  }

  public static final class Item {
    private final String key;
    private final MediaType mediaType;
    private final String title;
    private final Integer year;
    private final boolean hasMetadata;
    private final boolean hasArtwork;
    private final IdentityEvidence identity;

    public Item(String key, MediaType mediaType, String title, Integer year,
        boolean hasMetadata, boolean hasArtwork) {
      this(key, mediaType, title, year, hasMetadata, hasArtwork,
          IdentityEvidence.empty());
    }

    public Item(String key, MediaType mediaType, String title, Integer year,
        boolean hasMetadata, boolean hasArtwork, IdentityEvidence identity) {
      this.key = requireText(key, "item key");
      if (mediaType == null) throw new IllegalArgumentException("mediaType is required");
      this.mediaType = mediaType;
      this.title = requireText(title, "title");
      this.year = year;
      this.hasMetadata = hasMetadata;
      this.hasArtwork = hasArtwork;
      this.identity = identity == null ? IdentityEvidence.empty() : identity;
    }

    public String getKey() { return key; }
    public MediaType getMediaType() { return mediaType; }
    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public boolean hasMetadata() { return hasMetadata; }
    public boolean hasArtwork() { return hasArtwork; }
    public IdentityEvidence getIdentity() { return identity; }
  }

  /** Optional consumer evidence; old callers can continue using Item's original constructor. */
  public static final class IdentityEvidence {
    private final Long tmdbId;
    private final String seriesTitle;
    private final String episodeTitle;
    private final Integer seasonNumber;
    private final Integer episodeNumber;
    private final String originalAirDate;
    private final String externalId;

    public IdentityEvidence(Long tmdbId, String seriesTitle, String episodeTitle,
        Integer seasonNumber, Integer episodeNumber, String originalAirDate,
        String externalId) {
      this.tmdbId = tmdbId != null && tmdbId.longValue() > 0L ? tmdbId : null;
      this.seriesTitle = safeText(seriesTitle);
      this.episodeTitle = safeText(episodeTitle);
      this.seasonNumber = positive(seasonNumber);
      this.episodeNumber = positive(episodeNumber);
      this.originalAirDate = safeText(originalAirDate);
      this.externalId = safeText(externalId);
    }

    public static IdentityEvidence empty() {
      return new IdentityEvidence(null, "", "", null, null, "", "");
    }

    public Long getTmdbId() { return tmdbId; }
    public String getSeriesTitle() { return seriesTitle; }
    public String getEpisodeTitle() { return episodeTitle; }
    public Integer getSeasonNumber() { return seasonNumber; }
    public Integer getEpisodeNumber() { return episodeNumber; }
    public String getOriginalAirDate() { return originalAirDate; }
    public String getExternalId() { return externalId; }
    public boolean hasEpisodeIdentity() {
      return !seriesTitle.isEmpty() && seasonNumber != null && episodeNumber != null;
    }

    private static Integer positive(Integer value) {
      return value != null && value.intValue() > 0 ? value : null;
    }

    private static String safeText(String value) {
      return value == null ? "" : value.trim();
    }
  }

  public static final class Request {
    private final String jobId;
    private final List<Item> items;
    private final Action action;
    private final boolean autoApproveHighConfidence;
    private final boolean overwriteConfirmed;
    private final int maxConcurrency;
    private final Map<String, Long> approvedTmdbIds;

    public Request(String jobId, List<Item> items, Action action,
        boolean autoApproveHighConfidence, boolean overwriteConfirmed,
        int maxConcurrency, Map<String, Long> approvedTmdbIds) {
      this.jobId = requireText(jobId, "jobId");
      if (items == null || items.isEmpty()) throw new IllegalArgumentException("items are required");
      if (action == null) throw new IllegalArgumentException("action is required");
      if (maxConcurrency < 1 || maxConcurrency > 4) {
        throw new IllegalArgumentException("maxConcurrency must be between 1 and 4");
      }
      Set<String> keys = new LinkedHashSet<String>();
      List<Item> copy = new ArrayList<Item>();
      for (Item item : items) {
        if (item == null) throw new IllegalArgumentException("items must not contain null");
        if (!keys.add(item.getKey())) throw new IllegalArgumentException("duplicate item key");
        copy.add(item);
      }
      Map<String, Long> approvals = new LinkedHashMap<String, Long>();
      if (approvedTmdbIds != null) {
        for (Map.Entry<String, Long> entry : approvedTmdbIds.entrySet()) {
          if (!keys.contains(entry.getKey()) || entry.getValue() == null
              || entry.getValue().longValue() <= 0L) {
            throw new IllegalArgumentException("invalid approved TMDB mapping");
          }
          approvals.put(entry.getKey(), entry.getValue());
        }
      }
      this.items = Collections.unmodifiableList(copy);
      this.action = action;
      this.autoApproveHighConfidence = autoApproveHighConfidence;
      this.overwriteConfirmed = overwriteConfirmed;
      this.maxConcurrency = maxConcurrency;
      this.approvedTmdbIds = Collections.unmodifiableMap(approvals);
    }

    public String getJobId() { return jobId; }
    public List<Item> getItems() { return items; }
    public Action getAction() { return action; }
    public boolean isAutoApproveHighConfidence() { return autoApproveHighConfidence; }
    public boolean isOverwriteConfirmed() { return overwriteConfirmed; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public Map<String, Long> getApprovedTmdbIds() { return approvedTmdbIds; }
  }

  public static final class Result {
    private final Item item;
    private final ResultStatus status;
    private final Long tmdbId;
    private final String matchedTitle;
    private final String message;

    Result(Item item, ResultStatus status, Long tmdbId, String matchedTitle, String message) {
      this.item = item;
      this.status = status;
      this.tmdbId = tmdbId;
      this.matchedTitle = matchedTitle == null ? "" : matchedTitle;
      this.message = safeMessage(message);
    }

    public Item getItem() { return item; }
    public ResultStatus getStatus() { return status; }
    public Long getTmdbId() { return tmdbId; }
    public String getMatchedTitle() { return matchedTitle; }
    public String getMessage() { return message; }
  }

  public static final class Progress {
    private final String jobId;
    private final JobState state;
    private final int total;
    private final int completed;
    private final int reviewRequired;
    private final int failed;

    Progress(String jobId, JobState state, int total, int completed,
        int reviewRequired, int failed) {
      this.jobId = jobId;
      this.state = state;
      this.total = total;
      this.completed = completed;
      this.reviewRequired = reviewRequired;
      this.failed = failed;
    }

    public String getJobId() { return jobId; }
    public JobState getState() { return state; }
    public int getTotal() { return total; }
    public int getCompleted() { return completed; }
    public int getReviewRequired() { return reviewRequired; }
    public int getFailed() { return failed; }
  }

  public static final class Checkpoint {
    private final String jobId;
    private final String requestFingerprint;
    private final int total;
    private final int completed;
    private final JobState state;
    private final long updatedAtEpochSeconds;

    Checkpoint(String jobId, String requestFingerprint, int total, int completed,
        JobState state, long updatedAtEpochSeconds) {
      this.jobId = jobId;
      this.requestFingerprint = requestFingerprint;
      this.total = total;
      this.completed = completed;
      this.state = state;
      this.updatedAtEpochSeconds = updatedAtEpochSeconds;
    }

    public String getJobId() { return jobId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public int getTotal() { return total; }
    public int getCompleted() { return completed; }
    public JobState getState() { return state; }
    public long getUpdatedAtEpochSeconds() { return updatedAtEpochSeconds; }
  }

  public final class Job {
    private final Request request;
    private final List<Result> results = Collections.synchronizedList(new ArrayList<Result>());
    private volatile Future<?> future;
    private volatile JobState state = JobState.RUNNING;

    private Job(Request request) { this.request = request; }

    public String getJobId() { return request.getJobId(); }
    public JobState getState() { return state; }
    public List<Result> getResults() {
      synchronized (results) { return Collections.unmodifiableList(new ArrayList<Result>(results)); }
    }
    public void cancel() {
      Future<?> current = future;
      if (current != null) current.cancel(true);
    }
    public boolean await(long timeout, TimeUnit unit) throws Exception {
      Future<?> current = future;
      if (current == null) return false;
      try {
        current.get(timeout, unit);
        return true;
      } catch (CancellationException cancelled) {
        return true;
      }
    }
  }

  private final TmdbMetadataService metadata;
  private final TmdbCache cache;
  private final ExecutorService coordinator = Executors.newSingleThreadExecutor();
  private volatile boolean closed;

  LibraryEnrichmentService(TmdbMetadataService metadata, TmdbCache cache) {
    this.metadata = metadata;
    this.cache = cache;
  }

  public synchronized Job start(final Request request, final Sink sink, final Listener listener)
      throws SQLException {
    if (closed) throw new IllegalStateException("library enrichment service is closed");
    if (request == null) throw new IllegalArgumentException("request is required");
    if (request.getAction() != Action.PREVIEW_ONLY && sink == null) {
      throw new IllegalArgumentException("a sink is required for save actions");
    }
    final String fingerprint = fingerprint(request.getItems());
    Optional<Checkpoint> existing = checkpoint(request.getJobId());
    if (existing.isPresent()
        && !fingerprint.equals(existing.get().getRequestFingerprint())) {
      throw new IllegalArgumentException("jobId belongs to a different item set");
    }
    final Job job = new Job(request);
    cache.putEnrichmentJob(request.getJobId(), fingerprint, request.getItems().size(),
        existing.isPresent() ? existing.get().getCompleted() : 0,
        JobState.RUNNING.name(), now());
    job.future = coordinator.submit(new Runnable() {
      public void run() { runJob(job, fingerprint, sink, listener); }
    });
    return job;
  }

  public Optional<Checkpoint> checkpoint(String jobId) throws SQLException {
    String[] row = cache.getEnrichmentJob(requireText(jobId, "jobId"));
    if (row == null) return Optional.empty();
    return Optional.of(new Checkpoint(jobId, row[0], Integer.parseInt(row[1]),
        Integer.parseInt(row[2]), JobState.valueOf(row[3]), Long.parseLong(row[4])));
  }

  private void runJob(Job job, String fingerprint, Sink sink, Listener listener) {
    Request request = job.request;
    ExecutorService workers = Executors.newFixedThreadPool(request.getMaxConcurrency());
    List<Future<Result>> pending = new ArrayList<Future<Result>>();
    int completed = 0;
    int review = 0;
    int failed = 0;
    try {
      Set<String> alreadyComplete = cache.getCompletedEnrichmentKeys(request.getJobId());
      completed = alreadyComplete.size();
      notifyProgress(listener, new Progress(request.getJobId(), JobState.RUNNING,
          request.getItems().size(), completed, 0, 0));
      for (final Item item : request.getItems()) {
        if (alreadyComplete.contains(item.getKey())) continue;
        pending.add(workers.submit(new java.util.concurrent.Callable<Result>() {
          public Result call() { return process(request, item, sink); }
        }));
      }
      for (Future<Result> work : pending) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Result result = work.get();
        job.results.add(result);
        if (result.getStatus() == ResultStatus.REVIEW_REQUIRED
            || result.getStatus() == ResultStatus.OVERWRITE_CONFIRMATION_REQUIRED) review++;
        if (result.getStatus() == ResultStatus.FAILED) failed++;
        if (isComplete(result.getStatus())) completed++;
        cache.putEnrichmentResult(request.getJobId(), result.getItem().getKey(),
            result.getStatus().name(), result.getTmdbId(), result.getMatchedTitle(),
            result.getMessage(), now());
        cache.putEnrichmentJob(request.getJobId(), fingerprint, request.getItems().size(),
            completed, JobState.RUNNING.name(), now());
        notifyProgress(listener, new Progress(request.getJobId(), JobState.RUNNING,
            request.getItems().size(), completed, review, failed));
      }
      job.state = JobState.COMPLETED;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      job.state = JobState.CANCELLED;
    } catch (Exception error) {
      failed++;
      job.state = JobState.FAILED;
    } finally {
      for (Future<Result> work : pending) if (!work.isDone()) work.cancel(true);
      workers.shutdownNow();
      try {
        cache.putEnrichmentJob(request.getJobId(), fingerprint, request.getItems().size(),
            completed, job.state.name(), now());
      } catch (SQLException ignored) {
        job.state = JobState.FAILED;
      }
      notifyProgress(listener, new Progress(request.getJobId(), job.state,
          request.getItems().size(), completed, review, failed));
    }
  }

  private Result process(Request request, Item item, Sink sink) {
    try {
      Long approved = request.getApprovedTmdbIds().get(item.getKey());
      LookupResult lookup;
      if (approved != null) {
        cache.putManualMapping(item.getMediaType().apiPath(),
            CachingTmdbMetadataService.normalizeTitle(item.getTitle()), item.getYear(),
            approved.longValue(), item.getTitle(), now());
        lookup = new LookupResult(LookupResult.Status.MATCHED, approved,
            "approved", now(), Long.MAX_VALUE);
      } else if (item.getIdentity().getTmdbId() != null) {
        lookup = new LookupResult(LookupResult.Status.MATCHED,
            item.getIdentity().getTmdbId(), "existing TMDB ID", now(), Long.MAX_VALUE);
      } else {
        lookup = resolveTitleCandidates(item);
      }
      if (lookup.getStatus() == LookupResult.Status.NO_MATCH) {
        return new Result(item, ResultStatus.NO_MATCH, null, "", "No confident match");
      }
      if (lookup.getStatus() == LookupResult.Status.AMBIGUOUS
          || (approved == null && !request.isAutoApproveHighConfidence())) {
        return new Result(item, ResultStatus.REVIEW_REQUIRED, lookup.getTmdbId(),
            lookup.getMatchedTitle(), "Match requires review");
      }
      long tmdbId = lookup.getTmdbId().longValue();
      String details = metadata.getDetailsJson(item.getMediaType(), tmdbId, "images");
      if (request.getAction().savesArtwork()) metadata.getArtworkConfiguration();
      if (request.getAction() == Action.PREVIEW_ONLY) {
        return new Result(item, ResultStatus.PREVIEWED, Long.valueOf(tmdbId),
            lookup.getMatchedTitle(), "");
      }
      boolean overwrites = (request.getAction().savesMetadata() && item.hasMetadata())
          || (request.getAction().savesArtwork() && item.hasArtwork());
      if (overwrites && !request.isOverwriteConfirmed()) {
        return new Result(item, ResultStatus.OVERWRITE_CONFIRMATION_REQUIRED,
            Long.valueOf(tmdbId), lookup.getMatchedTitle(), "Overwrite confirmation required");
      }
      sink.apply(item, tmdbId, details, request.getAction());
      return new Result(item, ResultStatus.SAVED, Long.valueOf(tmdbId),
          lookup.getMatchedTitle(), "");
    } catch (Exception error) {
      return new Result(item, ResultStatus.FAILED, null, "", safeMessage(error.getMessage()));
    }
  }

  private LookupResult resolveTitleCandidates(Item item) throws IOException, SQLException {
    IdentityEvidence identity = item.getIdentity();
    if (item.getMediaType() == MediaType.TV && !identity.getSeriesTitle().isEmpty()) {
      LookupResult series = metadata.resolveExact(MediaType.TV, identity.getSeriesTitle(),
          item.getYear());
      if (series.getStatus() == LookupResult.Status.MATCHED && identity.hasEpisodeIdentity()) {
        TmdbEpisode episode = metadata.getEpisode(series.getTmdbId().longValue(),
            identity.getSeasonNumber().intValue(), identity.getEpisodeNumber().intValue());
        double episodeScore = titleSimilarity(identity.getEpisodeTitle(), episode.getName());
        if (identity.getEpisodeTitle().isEmpty() || episodeScore >= 0.72d) {
          return new LookupResult(LookupResult.Status.MATCHED, series.getTmdbId(),
              series.getMatchedTitle() + " " + String.format("S%02dE%02d",
                  identity.getSeasonNumber(), identity.getEpisodeNumber()),
              now(), Long.MAX_VALUE);
        }
        return new LookupResult(LookupResult.Status.AMBIGUOUS, series.getTmdbId(),
            series.getMatchedTitle(), now(), Long.MAX_VALUE);
      }
      if (series.getStatus() != LookupResult.Status.NO_MATCH) return series;
    }
    MediaTitleParser.ParsedTitle parsed = MediaTitleParser.parse(
        item.getTitle(), item.getMediaType());
    Integer year = item.getYear() == null ? parsed.getYear() : item.getYear();
    LookupResult ambiguous = null;
    for (String candidate : parsed.getSearchCandidates()) {
      LookupResult lookup = metadata.resolveExact(item.getMediaType(), candidate, year);
      if (lookup.getStatus() == LookupResult.Status.MATCHED) return lookup;
      if (lookup.getStatus() == LookupResult.Status.AMBIGUOUS && ambiguous == null) {
        ambiguous = lookup;
      }
    }
    if (ambiguous != null) return ambiguous;
    return resolveStrongNonExact(item.getMediaType(), parsed.getSearchCandidates(), year);
  }

  private LookupResult resolveStrongNonExact(MediaType type, List<String> queries, Integer year)
      throws IOException, SQLException {
    Map<Long, ScoredCandidate> byId = new LinkedHashMap<Long, ScoredCandidate>();
    for (String query : queries) {
      for (TmdbSearchResult candidate : metadata.search(type, query, year)) {
        double score = Math.max(titleSimilarity(query, candidate.getTitle()),
            titleSimilarity(query, candidate.getOriginalTitle())) * 100.0d;
        Integer candidateYear = yearFromDate(candidate.getDate());
        if (year != null && candidateYear != null) {
          score += year.equals(candidateYear) ? 12.0d : -18.0d;
        }
        ScoredCandidate previous = byId.get(Long.valueOf(candidate.getId()));
        if (previous == null || score > previous.score) {
          byId.put(Long.valueOf(candidate.getId()), new ScoredCandidate(candidate, score));
        }
      }
    }
    List<ScoredCandidate> ranked = new ArrayList<ScoredCandidate>(byId.values());
    Collections.sort(ranked, new java.util.Comparator<ScoredCandidate>() {
      public int compare(ScoredCandidate left, ScoredCandidate right) {
        return Double.compare(right.score, left.score);
      }
    });
    if (ranked.isEmpty() || ranked.get(0).score < 62.0d) {
      return new LookupResult(LookupResult.Status.NO_MATCH, null, "", now(), Long.MAX_VALUE);
    }
    ScoredCandidate best = ranked.get(0);
    double margin = ranked.size() == 1 ? 100.0d : best.score - ranked.get(1).score;
    LookupResult.Status status = best.score >= 84.0d && margin >= 10.0d
        ? LookupResult.Status.MATCHED : LookupResult.Status.AMBIGUOUS;
    return new LookupResult(status, Long.valueOf(best.result.getId()),
        best.result.getTitle(), now(), Long.MAX_VALUE);
  }

  static double titleSimilarity(String first, String second) {
    String left = CachingTmdbMetadataService.normalizeTitle(first == null ? "" : first);
    String right = CachingTmdbMetadataService.normalizeTitle(second == null ? "" : second);
    if (left.isEmpty() || right.isEmpty()) return 0.0d;
    if (left.equals(right)) return 1.0d;
    Set<String> a = new LinkedHashSet<String>();
    Set<String> b = new LinkedHashSet<String>();
    Collections.addAll(a, left.split(" "));
    Collections.addAll(b, right.split(" "));
    Set<String> intersection = new LinkedHashSet<String>(a);
    intersection.retainAll(b);
    Set<String> union = new LinkedHashSet<String>(a);
    union.addAll(b);
    double jaccard = union.isEmpty() ? 0.0d
        : ((double) intersection.size()) / ((double) union.size());
    if (left.contains(right) || right.contains(left)) jaccard = Math.max(jaccard, 0.82d);
    int longest = Math.max(left.length(), right.length());
    double editSimilarity = longest == 0 ? 0.0d
        : 1.0d - (((double) editDistance(left, right)) / ((double) longest));
    return Math.max(jaccard, editSimilarity);
  }

  private static int editDistance(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int column = 0; column <= right.length(); column++) previous[column] = column;
    for (int row = 1; row <= left.length(); row++) {
      current[0] = row;
      for (int column = 1; column <= right.length(); column++) {
        int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
        current[column] = Math.min(Math.min(current[column - 1] + 1,
            previous[column] + 1), previous[column - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length()];
  }

  private static Integer yearFromDate(String date) {
    if (date == null || date.length() < 4) return null;
    try { return Integer.valueOf(Integer.parseInt(date.substring(0, 4))); }
    catch (NumberFormatException ignored) { return null; }
  }

  private static final class ScoredCandidate {
    private final TmdbSearchResult result;
    private final double score;
    private ScoredCandidate(TmdbSearchResult result, double score) {
      this.result = result;
      this.score = score;
    }
  }

  private static boolean isComplete(ResultStatus status) {
    return status == ResultStatus.PREVIEWED || status == ResultStatus.SAVED
        || status == ResultStatus.NO_MATCH;
  }

  private static void notifyProgress(Listener listener, Progress progress) {
    if (listener == null) return;
    try { listener.onProgress(progress); } catch (RuntimeException ignored) { }
  }

  private static String fingerprint(List<Item> items) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Item item : items) {
        String row = item.getKey() + "\u001f" + item.getMediaType().name() + "\u001f"
            + item.getTitle() + "\u001f" + (item.getYear() == null ? "" : item.getYear()) + "\n";
        IdentityEvidence identity = item.getIdentity();
        row += (identity.getTmdbId() == null ? "" : identity.getTmdbId()) + "\u001f"
            + identity.getSeriesTitle() + "\u001f" + identity.getEpisodeTitle() + "\u001f"
            + (identity.getSeasonNumber() == null ? "" : identity.getSeasonNumber()) + "\u001f"
            + (identity.getEpisodeNumber() == null ? "" : identity.getEpisodeNumber()) + "\n";
        digest.update(row.getBytes(StandardCharsets.UTF_8));
      }
      StringBuilder value = new StringBuilder();
      for (byte octet : digest.digest()) value.append(String.format("%02x", octet & 0xff));
      return value.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static long now() { return Instant.now().getEpochSecond(); }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static String safeMessage(String value) {
    if (value == null) return "";
    return value.replaceAll("(?i)(api[_ -]?key|token|bearer)\\s*[=:]\\s*\\S+", "$1=[redacted]");
  }

  @Override
  public synchronized void close() throws IOException {
    closed = true;
    coordinator.shutdownNow();
    try {
      if (!coordinator.awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IOException("Timed out stopping library enrichment service");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while stopping library enrichment service", interrupted);
    }
  }
}
