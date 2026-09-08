# OpenSageTV Vibe TMDB contributor rules

Read `README.md`, `HANDOFF.md`, `TASKS.md`, `WORKFLOW.md`, and
`docs/ARCHITECTURE.md` before changing this repository. `TASKS.md` is the sole
local backlog; completed work moves to `CHANGELOG.md` and `HANDOFF.md`.

The plugin owns all TMDB HTTP traffic, authentication, rate limiting, and its
SQLite cache. SageMC, XMLTV, and other consumers use the public Java service
API and must not read or write its database directly. Preserve Java 8 bytecode
compatibility for stock SageTV while building with the unified Java 11 image.

Never commit API credentials, `tmdb_config.toml`, SQLite databases/WAL files,
downloaded artwork, SageTV appdata, or private test data. Logs, exceptions,
diagnostics, manifests, and handoff packages must redact credentials and URL
query parameters. Keep cache retention within TMDB's terms and preserve the
required attribution notice in every consuming UI.

Do not create prompt, review, session, or per-version Markdown/text files.
