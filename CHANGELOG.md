# Changelog

## Unreleased

- Prepared numeric version 0.1.0 for stock SageTV version comparison. Builds
  now emit canonical/versioned plugin ZIPs, a standard V9 repository manifest
  with exact MD5, SHA-256 checksums, bundled notices, and current GitHub CI.
- Added durable TMDB attribution/logo/commercial-use guidance plus public
  contribution and security policies. No copied or modified TMDB logo is
  bundled; consuming UIs must use a current approved asset.
- Passed a dependency-empty independent-clone Windows build of the exact
  committed source, including tests, numeric/versioned packaging, repository
  XML generation, MD5 verification, and SHA-256 output generation.
- Added the standalone plugin ZIP, service/dependency JARs, documentation,
  exact source revision, and component version to unified release assembly and
  its release-artifact SPDX SBOM.
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
- Added a child-process regression that commits into WAL and terminates via
  `Runtime.halt()` without closing SQLite; the parent successfully reopens and
  recovers the committed cache row.
- Added the TMDB artifact to the verified component-only appdata update path.
  Targeted package validation and atomic install/rollback pass while preserving
  an existing private configuration file and avoiding Docker image rebuilds.
- Added a consumer-neutral programme metadata facade for XMLTV and deterministic
  coverage for title/details/episode/country/genre mapping. The opt-in XMLTV
  adapter and all 39 SageMC presentation-call migrations now consume the shared
  service without owning credentials, HTTP transport, or SQLite access. A live
  credentialed exact-movie facade smoke test passes without exposing the key.
- Passed a unified simultaneous-consumer stress using four real SageMC workers
  and four real XMLTV workers: 4,000 adapter operations and 12,000 calls through
  one shared service completed without failure or registry-state leakage.
