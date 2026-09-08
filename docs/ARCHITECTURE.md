# Architecture

## Ownership boundary

```text
SageMC adapter ----\
                    -> TmdbMetadataService -> HTTP client -> TMDB API
XMLTV adapter -----/             |
                                  -> TmdbCache -> SQLite/WAL
```

Consumers receive immutable Java result objects. They do not know the SQLite
schema, credentials, HTTP endpoints, or retry policy. Plugin absence and
network failure are normal bounded outcomes: existing SageMC/XMLTV metadata is
kept and SageTV remains usable.

## Cache contract

The database is owned by one service instance. SQLite uses WAL, foreign keys,
`busy_timeout=5000`, normal synchronous mode, short transactions, and numbered
schema migrations. Generic API JSON resources and resolved title lookups are
separate. Manual title-to-TMDB mappings are durable and are never removed by
automatic expiry.

Successful details default to 30 days, searches to 7 days, and negative or
ambiguous results to 1 day. No API-derived row may remain longer than 180
days. Expiry is checked on read and cleanup is bounded. TVTV-specific rows from
the Python reference are not part of this generic schema.

## Reference behavior

`hdhr_atsc_epg/tvtv_xmltv.py` is a behavioral reference for request pacing,
`Retry-After`, exponential retry, exact title matching, regional
disambiguation, negative caching, manual mappings, episode refresh, and XMLTV
enrichment. The Java implementation remains independently structured and adds
multi-consumer SQLite concurrency and the explicit hard-retention policy.

## Planned service API

The stable service will support movie, TV-series, episode, and person search;
details and artwork metadata; exact/manual resolution; cache-only/offline
operation; bounded background refresh; diagnostics with no secrets; and batch
lookups for XMLTV. The SageTV plugin wrapper will expose configuration and a
small callable facade suitable for STV use. XMLTV will use a compile-time Java
adapter and preserve feed-supplied fields unless enrichment is explicitly
enabled.
