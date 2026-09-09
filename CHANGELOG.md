# Changelog

## Unreleased

- Commissioned the exact v0.2.0 service JAR on isolated SageTV server `.232`
  and exercised it through SageMC on non-Pro Fire TV `.25`. Full-library and
  selected-title Preview Only flows passed start/progress/results, review,
  cancellation, and resumable SQLite checkpointing. The selected Aladdin DVD
  produced one `REVIEW_REQUIRED` result for TMDB ID 420817, and no metadata or
  artwork was written. The loaded JAR SHA-256 is
  `4c41ab81344190e54d24093645cb93a29dc5d50b4e1fb5aee7ea6e17179cf216`.
  SageTV's persisted plugin descriptor may continue to display 0.1.1 until its
  normal plugin-manager update; the running JVM uses the commissioned 0.2.0
  classpath, so registry metadata must not be hand-edited.
- Made JAR and plugin-ZIP packaging reproducible by normalizing archive member
  times, using stable member ordering, and omitting the generated current-time
  JAR manifest. Two complete consecutive builds now produce identical JAR
  SHA-256 `20479dd031aeaeb148c958addde74129c305f1ca4fdd47aa76c89de700949c5c`
  and plugin-ZIP SHA-256
  `1da5da98c18622bc94271c517f67a481dc142500ccf2827608568ad521bdc5b6`.
  GitHub CI repeats the build and compares the complete `SHA256SUMS` set.

## 0.2.0 - 2026-09-08

- Added the reusable asynchronous library-enrichment worker for full-library
  and selected-title consumers. It supports Preview Only, Save Metadata, Save
  Artwork, and Save Metadata + Artwork; bounded one-to-four worker concurrency;
  exact-match auto approval; ambiguous/manual review; explicit overwrite
  confirmation; cancellation; secret-safe progress; and durable manual match
  approvals.
- Migrated the SQLite cache from schema 2 to schema 3 with resumable job and
  per-item result checkpoints. Migration preserves v1/v2 cache data, completed
  items are not repeated after restart, and failed/review/overwrite-blocked
  items remain eligible for a later resume.

## 0.1.1 - 2026-09-08

- Apply the Enabled setting immediately so the shared TMDB service can be
  started, stopped, and re-enabled without a SageTV JVM restart.
- Reload the service immediately when its configuration-file path changes
  while the plugin is enabled.
- Commissioned the stock-compatible plugin update on isolated SageTV server
  `.232` through the normal SageTV plugin manager. The guarded restart loaded
  version 0.1.1 as Enabled/Running, SageMC returned live search results and
  details/artwork, and the shared SQLite WAL cache grew without exposing
  credentials.
- Re-ran the credentialed movie/API and XMLTV facade smoke tests plus the
  cache-only/offline and atomic rollback regression suite.
- Published the `v0.1.1` GitHub release with the versioned plugin ZIP,
  repository XML, and SHA-256 manifest. A clean re-download of the release ZIP
  matched SHA-256
  `6a84d363e9af3c92f715c24f70ec47ffe0518736a0bf9265a558ae0127a65c08`.
- Submitted the V9 plugin manifest to the upstream OpenSageTV plugin repository
  as pull request `OpenSageTV/sagetv-plugin-repo#123`.

- Published the Apache-2.0 source repository at
  `opensagetv-vibe/opensagetv-vibe-tmdb`.
  GitHub CI now passes the complete Java 8-compatible build/package suite and
  an exact credential-free example/config-data gate.
- Proved plugin binary linkage against the exact read-only `Sage.jar` used by
  stock test server `.175`, documented the stock compatibility boundary, and
  included that document in deterministic plugin packages. No stock server file
  was modified.

- Prepared numeric version 0.1.0 for stock SageTV version comparison. Builds
  now emit canonical/versioned plugin ZIPs, a standard V9 repository manifest
  with exact MD5, SHA-256 checksums, bundled notices, and current GitHub CI.
- Added durable TMDB attribution/logo/commercial-use guidance plus public
  contribution and security policies. No copied or modified TMDB logo is
  bundled; consuming UIs must use a current approved asset.
- Passed a dependency-empty independent-clone Windows build of the exact
  committed source, including tests, numeric/versioned packaging, repository
  XML generation, MD5 verification, and SHA-256 output generation.
- Staged the exact component archive/checksum and private runtime TOML in the
  isolated `.232` appdata tree without changing live JARs or stock `.175`.
  Host-side guarded install/restart and runtime verification remain pending.
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
