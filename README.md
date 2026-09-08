# OpenSageTV Vibe TMDB

Reusable TMDB metadata service for OpenSageTV Vibe. It is intentionally
separate from SageMC and the XMLTV importer so one implementation owns API
credentials, request pacing, retry behavior, manual mappings, and a persistent
SQLite cache.

The service provides strict local TOML configuration and a
versioned SQLite cache with WAL, a bounded busy timeout, transactional schema
migration, positive/negative lookup rows, manual mappings, expiry, and a hard
180-day retention ceiling. Its Java API supports movie/TV/person search,
exact and manual resolution, raw details, typed episode and artwork results,
cache-only offline reads, consistent cache backup, and deduplicated batch
lookups for XMLTV. The SageTV plugin wrapper, SageMC adapter, and XMLTV adapter
remain tracked in `TASKS.md`; consumers must never read SQLite directly.

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
and Gson runtime dependencies.

This product uses the TMDB API but is not endorsed or certified by TMDB.
