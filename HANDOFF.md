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
has the common update and changed-files handoff launchers; plugin installation,
runtime component updates, release/SBOM assembly, and public CI are still open.

The public API now includes typed episode/artwork results, cache-only reads,
deduplicated batch lookup, and consistent SQLite backup. The stock-compatible
`OpenSageTVVibeTmdbPlugin` starts the service from a private TOML path and the
Studio facade exposes only safe scalar/array calls. The build runs a separate
probe against Core's actual `Sage.jar` and verifies that compile-only `sage.*`
API classes never enter `OpenSageTVVibeTMDB.jar`.
`output/packages/OpenSageTVVibeTMDB-plugin.zip` packages that JAR, pinned Gson
and SQLite JDBC dependencies, plugin metadata, and only the credential-free
TOML example in a stock SageTV layout. It has not yet been installed on a
server; component install/rollback and public plugin-repository XML remain
open gates.

Schema migration now preserves v1 rows while adding cache metadata in v2.
Four concurrent writers, cross-connection visibility, future-schema rejection,
corrupt-database failure, consistent backup, retention bounds, and five repeated
full cache/API/plugin runs pass. A child process also writes into WAL and exits
through `Runtime.halt()` without closing; the parent reopens and recovers the
committed row. Cross-consumer adapter stress remains pending until the SageMC
and XMLTV adapters exist.

## Next takeover

Run `dev.cmd all`, then implement thin SageMC and XMLTV adapters against the
public service interface. Do not let either consumer access SQLite directly.
Add the component-only install/update path once the plugin descriptor and exact
SageTV JAR deployment layout are defined.
