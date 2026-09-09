# Handoff

## Current state

Library enrichment now supports optional `IdentityEvidence` without breaking
the original `Item` constructor. Existing TMDB/provider IDs take priority;
series identity plus season/episode coordinates is checked through the episode
endpoint; remaining non-exact search results use conservative title/year
scoring and require both a high score and a clear winner for automatic match.
Ambiguous evidence remains review-only. Identity participates in checkpoint
fingerprints so changed evidence cannot reuse stale completed work.

The complete Java 8, stock-API linkage, unit, deterministic-build, and package
gate passes. Current SHA-256 values are
`193c64b383c57d9ca7e16e2dddbd20e4ed65c5fed36e287c85f7d268f9ed6069`
(service JAR) and
`7a0b714e582ccf09354f14d6a0f73b4c2ed1db98be898fa543c46bcf13284a27`
(versioned/plugin ZIP).

Version 0.2.1 adds the shared `MediaTitleParser` and routes asynchronous
library enrichment through its ordered lookup candidates. The parser safely
handles noisy filenames such as `Honey.I.Shrunk.the.Kids.(1989).1080p...` and
`The.Big.Bang.Theory.S03E12...`, preserves numeric titles such as `1917` and
`Blade Runner 2049`, and retains the raw input as the final fallback. The full
test, stock-SageTV API linkage, build, and package-validation workflow passes.

The 0.2.0 development tree adds `LibraryEnrichmentService`, the reusable
selected-title/full-library worker requested by SageMC. The Java 8 API owns
bounded background execution, TMDB/cache calls, schema-3 checkpoint/resume,
review classification, durable manual approvals, overwrite protection, and
credential-safe progress. Consumers retain the final SageTV write boundary
through a narrow sink callback. Focused tests cover preview, save, overwrite
guard, resume, explicit approval, fingerprint mismatch, and v2-to-v3 migration.
Physical installation remains restricted to isolated `.232`; stock `.175`
must remain read-only. The exact v0.2.0 JAR is now commissioned on `.232` with
SHA-256
`4c41ab81344190e54d24093645cb93a29dc5d50b4e1fb5aee7ea6e17179cf216`.
SageMC on non-Pro Fire TV `.25` physically passed full-library Preview Only
start/progress/results/review/cancel and a selected-title Preview Only scan.
The selected Aladdin DVD created one completed schema-3 checkpoint with
`REVIEW_REQUIRED`, TMDB ID 420817, and no metadata or artwork writes.

SageTV's persisted plugin registry can still display the earlier 0.1.1
descriptor until the normal plugin manager updates it. The running JVM loads
the commissioned 0.2.0 classpath; do not hand-edit SageTV registry properties
solely to alter that displayed version.

Post-release packaging is now byte-reproducible. The build normalizes member
timestamps to `SOURCE_DATE_EPOCH` (or a fixed safe default), orders plugin ZIP
members, and omits `jar`'s generated current-time manifest. Two consecutive
complete local builds matched exactly: JAR SHA-256
`20479dd031aeaeb148c958addde74129c305f1ca4fdd47aa76c89de700949c5c`
and versioned plugin-ZIP SHA-256
`1da5da98c18622bc94271c517f67a481dc142500ccf2827608568ad521bdc5b6`.
CI repeats the build and compares the full package checksum set.

The plugin passed its binary-link probe against the exact read-only stock `.175`
`Sage.jar` on 2026-09-08 (SHA-256
`d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`).
This is a compatibility proof only; `.175` was not modified. The durable
boundary is recorded in `docs/STOCK_SAGETV_COMPATIBILITY.md`.

The standalone project and first cache/configuration foundation exist locally
and are integrated into the common ten-repository development/handoff workflow.
The Java 8-compatible sources implement strict `[tmdb]` TOML loading with
redacted summaries and SQLite schema version 3 with WAL, busy timeout,
transactional migration, positive/negative lookup cache, generic JSON
resource cache, manual mappings, expiry, and a 180-day maximum retention.

The dependency is pinned to Xerial SQLite JDBC 3.53.2.1 with SHA-256
`f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1`.
Gson 2.14.0 is independently pinned at SHA-256
`2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f`.
No API credential or cache database belongs in Git. The local reference
credential file is `C:\TMP_SAGETV_DOCKER\hdhr_atsc_epg\tmdb_config.toml` and
must only be passed into commissioning commands.

The current service foundation supports cached movie/TV/person search,
exact/manual title resolution, and details. Fake-server tests cover 429 retry,
details, persistent positive cache, and negative-cache reuse. A live call to
TMDB's configuration endpoint passed with the local ignored credential file;
the output contained only a redacted configuration summary.

The root `dev.cmd all` passes through the installed unified Docker image without
rebuilding it. The build-environment workflow contract and isolated complete
ten-repository handoff test also pass with this project included. The project
has the common update and changed-files handoff launchers. Targeted component
installation/rollback and unified release/SBOM assembly pass. The Apache-2.0
source is published at `opensagetv-vibe/opensagetv-vibe-tmdb`, and its GitHub
CI passes. Physical plugin commissioning and the versioned v0.2.0 GitHub
release now pass. The V9 manifest is awaiting upstream review in
`OpenSageTV/sagetv-plugin-repo#123`.

The public API now includes typed episode/artwork results, cache-only reads,
deduplicated batch lookup, and consistent SQLite backup. The stock-compatible
`OpenSageTVVibeTmdbPlugin` starts the service from a private TOML path and the
Studio facade exposes only safe scalar/array calls. The build runs a separate
probe against Core's actual `Sage.jar` and verifies that compile-only `sage.*`
API classes never enter `OpenSageTVVibeTMDB.jar`.
`output/packages/OpenSageTVVibeTMDB-plugin.zip` packages that JAR, pinned Gson
and SQLite JDBC dependencies, plugin metadata, and only the credential-free
TOML example in a stock SageTV layout. The build also emits a numeric-versioned
ZIP, verified SHA-256 set, and standard SageTV V9 repository XML with its exact
package MD5. Version 0.1.1 was installed through SageTV's normal plugin-update
path on isolated server `.232`; the required restart completed, the plugin
loaded Enabled/Running, and the pre-existing private `tmdb_config.toml` was
preserved.

Unified release assembly stages the plugin ZIP, service and dependency JARs,
`release.properties`, and durable documentation under `components/tmdb` and
`docs/tmdb`. The resolved manifest records this repository's exact commit and
component version, while the release-artifact SPDX SBOM hashes every staged
TMDB file.

Schema migration now preserves v1 rows while adding cache metadata in v2.
Four concurrent writers, cross-connection visibility, future-schema rejection,
corrupt-database failure, consistent backup, retention bounds, and five repeated
full cache/API/plugin runs pass. A child process also writes into WAL and exits
through `Runtime.halt()` without closing; the parent reopens and recovers the
committed row. Both consumer adapters now exist: SageMC's 39 historical
SageIMDb call families use the typed shared service, and XMLTV has a bounded,
deduplicated, fill-only, fail-open facade consumer whose opt-in/off imports
retain identical Show IDs. The facade has deterministic fake-server coverage
and passed a live exact-movie metadata probe through the ignored local config.
The unified integration gate also ran four SageMC and four XMLTV workers for
4,000 adapter operations and 12,000 shared-service calls without failure.
The numeric 0.1.0 public package also passed a dependency-empty independent
clone build on Windows; generated outputs did not dirty the checkout.

On 2026-09-08 the local development manifest exposed version 0.1.1 only to the
isolated `.232` plugin repository. SageTV staged the JAR replacement, presented
its standard restart prompt, and restarted the JVM safely. The plugin screen
then reported `Enabled=True` and `Status=Running`. A physical SageMC lookup for
`Enough (2002)` returned 40 results, detailed metadata, and poster artwork;
the SQLite WAL grew from 45,352 to 193,672 bytes. Evidence screenshots are
`20260908-222816_tmdb-config-011-running.png`,
`20260908-223351_tmdb-search-dialog.png`, and
`20260908-223432_tmdb-enough-details-artwork.png` in the Android project's
ignored `artifacts/firetv` directory. The credentialed API/XMLTV facade smoke
test and deterministic cache-only/offline/rollback tests also pass. No source,
output, log, or release artifact contains the private credentials, and stock
server `.175` was not modified.

## Next takeover

Monitor `OpenSageTV/sagetv-plugin-repo#123` for upstream review. There is no
open reusable-service implementation task in `TASKS.md`; consumer-specific UI
work remains in each consumer repository. Do not install or modify anything on
stock server `.175`.
