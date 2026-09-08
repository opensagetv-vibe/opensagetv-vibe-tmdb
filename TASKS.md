# OpenSageTV Vibe TMDB tasks

This is the only active TMDB backlog. Completed work is removed and recorded
in `CHANGELOG.md` and `HANDOFF.md`.

- [ ] Extend the stable immutable `TmdbMetadataService` foundation with
  episode-specific typed results, artwork configuration, cache-only/offline
  calls, and batch XMLTV requests.
- [ ] Complete the Java 8 HTTPS client's failure regressions for auth, 404,
  timeout, oversized/malformed JSON, exhausted 429/5xx retries, and prove every
  exception/log path remains credential-safe.
- [ ] Complete SQLite migrations and concurrency tests for simultaneous
  SageMC/XMLTV requests, crash recovery, corrupt databases, cleanup, backup,
  and all cache-retention boundaries.
- [ ] Add a SageTV `SageTVPlugin` lifecycle/configuration wrapper and a small
  STV-callable facade while retaining stock SageTV 9 compatibility.
- [ ] Add the SageMC adapter, migrate useful `sagemc/imdb_*` preferences, and
  replace broken SageIMDb screens without coupling the STV to HTTP or SQLite.
- [ ] Add opt-in XMLTV enrichment that preserves existing feed metadata,
  stable Show IDs, and successful imports when TMDB is absent/offline.
- [ ] Commission with the ignored local `hdhr_atsc_epg/tmdb_config.toml` and
  synthetic fixtures; test auth failure, 404, 429, timeout, ambiguity,
  negative cache, offline restart, and no-credential behavior.
- [ ] Integrate `tmdb-test|validate|build|install|all`, component updates,
  release packaging, SBOMs, and the ten-repository handoff into the unified
  build environment without rebuilding the runtime image for plugin updates.
- [ ] Add public plugin metadata, required TMDB attribution/logo guidance,
  third-party notices, GitHub CI, clean-checkout proof, and release packaging.
