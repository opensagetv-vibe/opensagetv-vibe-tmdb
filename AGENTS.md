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

Release validation is impact-based: rerun only gates the release changes could
affect. Do not repeat unrelated completed gates. Run the full gate suite only
when the user explicitly requests it or a broad dependency/architecture change
requires it, and document that reason and scope.


## Stock-server test-control policy

- For any new testing, commissioning, diagnostic, or automation control, first
  implement or extend the stock-compatible `opensagetv-vibe-core-MCP-Plugin`
  using supported `sage.SageTV.api`/`apiUI` calls and verify it against an
  unmodified stock SageTV server.
- Do not patch `Sage.jar`, add private MiniClient events, or change Core merely
  to make a test easier. Existing public APIs, the bounded MCP bridge, and
  external test tooling are the required first option.
- Change Core only when the required production runtime behavior cannot be
  expressed through the stock plugin/API boundary. Document the proven API
  gap, keep the extension optional and negotiated with a safe stock fallback,
  and verify older clients and installations remain unaffected.
