# OpenSageTV Vibe TMDB

Reusable TMDB metadata service for OpenSageTV Vibe. It is intentionally
separate from SageMC and the XMLTV importer so one implementation owns API
credentials, request pacing, retry behavior, manual mappings, and a persistent
SQLite cache.

The service provides strict local TOML configuration and a
versioned SQLite schema 2 cache with WAL, a bounded busy timeout, transactional schema
migration, positive/negative lookup rows, manual mappings, expiry, and a hard
180-day retention ceiling. Its Java API supports movie/TV/person search,
exact and manual resolution, raw details, typed episode and artwork results,
cache-only offline reads, consistent cache backup, and deduplicated batch
lookups. A stock SageTV plugin wrapper owns the process-wide service. SageMC
uses the typed Java API, while XMLTV uses the small scalar
`getProgrammeMetadata` facade so neither consumer reads credentials or SQLite
directly. The XMLTV facade returns an empty result for no or ambiguous matches;
consumers retain their source metadata in that case.

Copy `tmdb_config.example.toml` to an ignored `tmdb_config.toml`. The existing
`C:\TMP_SAGETV_DOCKER\hdhr_atsc_epg\tmdb_config.toml` may be supplied locally
during commissioning, but it must never be copied into this project or a
release.

Run:

```text
dev.cmd all
```

Linux uses `./dev.sh all`. The command delegates to the existing unified Docker
build environment, targets Java 8 bytecode, and currently
produces `output/packages/OpenSageTVVibeTMDB.jar` plus the pinned SQLite JDBC
and Gson runtime dependencies. It also creates canonical and versioned plugin
ZIPs with the stock SageTV `JARs/` layout, a credential-free configuration
example, the standard SageTV V9 repository manifest
`opensagetv-vibe-tmdb.plugin.xml`, and `SHA256SUMS`. A real
`tmdb_config.toml` must be created in `plugins/opensagetv-vibe-tmdb/` after
installation; it is never included in the ZIP.

The repository-manifest version is deliberately dotted numeric because the
stock SageTV plugin manager parses each version segment as an integer. For
local development, copy the generated XML and versioned ZIP into
`SageTVPluginsDev.d/`, enable SageTV `devmode`, and restart the server. For a
public release, publish the versioned ZIP at the GitHub URL already recorded in
the generated XML, then submit that XML to the OpenSageTV plugin repository.

This product uses the TMDB API but is not endorsed or certified by TMDB.
Every UI that displays TMDB data or images must also follow
[`docs/TMDB_ATTRIBUTION.md`](docs/TMDB_ATTRIBUTION.md).

The plugin uses the public stock SageTV 9 lifecycle and does not require a
Vibe Core patch. See
[`docs/STOCK_SAGETV_COMPATIBILITY.md`](docs/STOCK_SAGETV_COMPATIBILITY.md) for
the exact binary-link evidence and the distinction between linkage and a
physical stock-server installation.
