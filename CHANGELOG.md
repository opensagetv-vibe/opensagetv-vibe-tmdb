# Changelog

## Unreleased

- Created the standalone reusable TMDB project and its cross-consumer
  ownership boundary.
- Added strict secret-safe TOML configuration and SQLite schema version 1 with
  WAL concurrency, bounded lock waits, transactional initialization,
  positive/negative lookup caching, manual mappings, expiry, and a hard
  180-day retention ceiling.
- Added pinned dependency acquisition, Java 8 compilation, unit tests, and the
  common root `dev` command surface.
- Added the stable cache-backed service foundation for movie, television, and
  person search, exact/manual resolution, and raw details. The Java 8 HTTP
  client prefers bearer authentication, bounds responses/timeouts/retries,
  paces requests, honors HTTP 429 `Retry-After`, and never retains a URL-bearing
  low-level exception that could expose a v3 API key.
- Passed fake-server search/details/429/negative-cache tests and a live
  authentication/configuration smoke test using the ignored local TOML file.
- Integrated the repository into the existing unified Docker development
  container, common component command surface, checkout helpers, and complete
  ten-repository handoff workflow without rebuilding the toolchain image.
- Added the standard resumable update and changed-files handoff launchers.
- Extended the consumer API with typed episode and artwork configuration,
  cache-only/offline reads, deduplicated batch lookups, and consistent SQLite
  backup. Added bounded HTTP auth/404/429/5xx/timeout/malformed/oversized and
  credential-redaction regressions plus cache retention/corruption/WAL tests.
- Added a stock SageTV 9 `SageTVPlugin` lifecycle/configuration wrapper and a
  small scalar/array facade for Studio consumers. The build proves binary
  linkage against the actual Core `Sage.jar` while excluding compile-only API
  stubs from the packaged plugin JAR.
- Added a credential-free stock SageTV plugin archive containing the service,
  pinned runtime dependency JARs, plugin metadata, and example TOML in the
  standard `JARs/` and `plugins/` layout.
- Migrated the cache to schema 2 with preserved v1 data and metadata ownership.
  Added concurrent-writer, cross-connection, future-schema, corrupt-database,
  backup, and hard-retention tests; five repeated full runs pass.
