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
