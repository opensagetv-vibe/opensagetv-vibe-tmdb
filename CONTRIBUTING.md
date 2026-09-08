# Contributing

Use the root `dev.cmd all` command on Windows or `./dev.sh all` on Linux. Keep
the public API Java 8 compatible, add deterministic tests for behavior changes,
and preserve fail-open behavior for optional consumers.

Never commit TMDB credentials, private configuration, SQLite cache files,
downloaded artwork, SageTV appdata, or private test data. Update `CHANGELOG.md`
and `HANDOFF.md` for durable behavior changes and remove completed work from
`TASKS.md`.
