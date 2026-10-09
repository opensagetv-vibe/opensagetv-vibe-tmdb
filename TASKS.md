# OpenSageTV Vibe TMDB tasks

> **Pre-commit task maintenance:** Immediately before every repository commit, move
> completed `[x]` items out of active sections and into
> `## Checklist change ledger`. Preserve IDs, evidence, and context; never
> discard completion history. Active sections contain unchecked work only.

This is the only active TMDB backlog. Completed work moves to the checklist
change ledger; release evidence is also recorded in `CHANGELOG.md` and
`HANDOFF.md`.

- [ ] Publish the approved v0.2.1 plugin release, verify deterministic public
  artifacts and hashes, confirm repository checks, and update the SageTV plugin
  catalog proposal without overstating physical metadata-write validation.

## Checklist change ledger

- 2026-10-08 pre-commit source-sync review: task-fix/server-boundary workflow
  only; TMDB code, cache schema and package inputs are unchanged. Existing
  plugin publication remains its own task, not an Android-release checkoff.
  Completed entries ledger-only and workspace order reviewed305.


### Archived completed checklist items (2026-09-30)

These completed items were moved from active task sections immediately
before commit. Stable IDs, acceptance evidence, and source context are
preserved; active sections contain unchecked work only.

#### From `# OpenSageTV Vibe TMDB tasks`

- [x] Add reusable evidence-scored library matching for non-exact titles while
  preserving the existing API constructors and exact/manual mapping behavior.
  Provider IDs and episode identity must outrank title similarity; automatic
  approval requires one uniquely strong candidate and ambiguity remains
  review-only.
