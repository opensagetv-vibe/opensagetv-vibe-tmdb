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

    public Item(String key, MediaType mediaType, String title, Integer year,
        boolean hasMetadata, boolean hasArtwork) {
      this.key = requireText(key, "item key");
      if (mediaType == null) throw new IllegalArgumentException("mediaType is required");
      this.mediaType = mediaType;
      this.title = requireText(title, "title");
      this.year = year;
      this.hasMetadata = hasMetadata;
      this.hasArtwork = hasArtwork;
    }

    public String getKey() { return key; }
    public MediaType getMediaType() { return mediaType; }
    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public boolean hasMetadata() { return hasMetadata; }
    public boolean hasArtwork() { return hasArtwork; }
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
      } else {
        lookup = resolveTitleCandidates(item);
      }
      if (lookup.getStatus() == LookupResult.Status.NO_MATCH) {
        return new Result(item, ResultStatus.NO_MATCH, null, "", "No exact match");
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
    return ambiguous == null
        ? new LookupResult(LookupResult.Status.NO_MATCH, null, "", now(), Long.MAX_VALUE)
        : ambiguous;
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
