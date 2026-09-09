# OpenSageTV Vibe TMDB tasks

This is the only active TMDB backlog. Completed work is removed and recorded
in `CHANGELOG.md` and `HANDOFF.md`.

- [x] Add reusable evidence-scored library matching for non-exact titles while
  preserving the existing API constructors and exact/manual mapping behavior.
  Provider IDs and episode identity must outrank title similarity; automatic
  approval requires one uniquely strong candidate and ambiguity remains
  review-only.
