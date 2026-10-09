# Common project workflow

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `install`, or
`all`. The initial local workflow verifies the pinned SQLite JDBC checksum,
targets Java 8 bytecode using Java 11, runs the cache/configuration tests, and
packages the API JAR plus its runtime dependency under `output/packages`.

The project is mounted into the existing unified development container and the
root wrappers delegate to the same location-independent component workflow used
by the other Vibe projects. `tmdb-test`, `tmdb-validate`, `tmdb-build`, and
`tmdb-all` are also available from the build-environment root. The targeted
runtime component workflow validates atomic install and rollback without a
Docker image rebuild. Physical server activation still requires a controlled
SageTV restart because JVM plugin classes cannot be replaced safely in place.

The build creates a canonical component ZIP, a versioned public-release ZIP,
a stock SageTV V9 repository manifest with the exact legacy MD5 required by the
plugin manager, and a SHA-256 checksum set for modern artifact verification.
Archive member timestamps and ordering are normalized, so identical source and
dependencies produce byte-identical JAR and ZIP outputs. Repository CI builds
twice and compares the complete checksum set.

For an optional authenticated commissioning smoke test, set `TMDB_TEST_CONFIG`
to a private TOML file outside this repository before running the test gate.
The file and its credentials are never copied into output or handoff packages.
