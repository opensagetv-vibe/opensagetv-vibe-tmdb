# OpenSageTV Vibe TMDB tasks

This is the only active TMDB backlog. Completed work is removed and recorded
in `CHANGELOG.md` and `HANDOFF.md`.

- [ ] Complete SQLite migrations and concurrency tests for simultaneous
  SageMC/XMLTV requests, crash recovery, corrupt databases, cleanup, backup,
  and all cache-retention boundaries.
- [ ] Add the SageMC adapter, migrate useful `sagemc/imdb_*` preferences, and
  replace broken SageIMDb screens without coupling the STV to HTTP or SQLite.
- [ ] Add opt-in XMLTV enrichment that preserves existing feed metadata,
  stable Show IDs, and successful imports when TMDB is absent/offline.
- [ ] Commission with the ignored local `hdhr_atsc_epg/tmdb_config.toml` and
  synthetic fixtures; test auth failure, 404, 429, timeout, ambiguity,
  negative cache, offline restart, and no-credential behavior.
- [ ] Add the SageTV plugin artifact to component-only install/update handling,
  release packaging, and SBOM generation without rebuilding a development or
  runtime image for plugin updates.
- [ ] Add public plugin metadata, required TMDB attribution/logo guidance,
  third-party notices, GitHub CI, clean-checkout proof, and release packaging.
