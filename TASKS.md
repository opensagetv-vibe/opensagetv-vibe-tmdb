# OpenSageTV Vibe TMDB tasks

This is the only active TMDB backlog. Completed work is removed and recorded
in `CHANGELOG.md` and `HANDOFF.md`.

## Planned reusable features

- [ ] Add a resumable background library-enrichment service that supports both
  an entire-library scan and a selected-title scan for imported DVDs and other
  video-library media. Resolve candidates through TMDB and populate the shared
  SQLite cache. Offer Preview Only, Save Metadata, Save Artwork, and Save
  Metadata + Artwork outcomes. Allow high-confidence matches to be approved in
  bulk, route unmatched/ambiguous results to a review queue, and require an
  explicit confirmation before overwriting existing SageTV data. Provide
  bounded concurrency, TMDB rate-limit handling, checkpoint/resume,
  credential-safe progress reporting, and a stable API so SageMC, XMLTV, and
  future STVs use the same worker instead of implementing their own scanners.
