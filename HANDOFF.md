# Handoff

## Current state

The standalone project and first cache/configuration foundation exist locally.
The Java 8-compatible sources implement strict `[tmdb]` TOML loading with
redacted summaries and SQLite schema version 1 with WAL, busy timeout,
transactional initialization, positive/negative lookup cache, generic JSON
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

## Next takeover

Run `dev.cmd all`, then implement the public service interface and fake-server
HTTP tests before connecting SageMC or XMLTV. Do not let either consumer access
SQLite directly. After the local API is stable, add this tenth repository to
the unified build environment and component-only container update path.
