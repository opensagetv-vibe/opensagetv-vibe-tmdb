# Handoff

## Current state

The standalone project and first cache/configuration foundation exist locally
and are integrated into the common ten-repository development/handoff workflow.
The Java 8-compatible sources implement strict `[tmdb]` TOML loading with
redacted summaries and SQLite schema version 2 with WAL, busy timeout,
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
installation/rollback and unified release/SBOM assembly pass; physical plugin
commissioning and public CI remain open.

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
package MD5. It has not yet been commissioned on a server. Targeted component
validation and atomic install/rollback pass in isolated appdata, including
preservation of a pre-existing private `tmdb_config.toml`.

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

The exact `e0f7d388cc05` component archive and checksum are staged, but not
activated, under isolated `.232` appdata at `.component-staging/remote/`. The
private ignored TOML is staged only as
`server/plugins/opensagetv-vibe-tmdb/tmdb_config.toml`; it was not copied into
source, output, or release media. No live JAR or Sage property was changed.
Activation requires executing the guarded installer on the Unraid host and a
controlled SageTV container/JVM restart. This workstation currently has SMB
appdata access but no authorized SSH/container-control route, and `.232` does
not expose web/Sagex control.

## Next takeover

Commission the plugin lifecycle on isolated server `.232`. Do not install or
modify anything on stock server `.175`. After that physical gate, publish the
versioned GitHub release and submit the generated manifest to the OpenSageTV
plugin repository.
