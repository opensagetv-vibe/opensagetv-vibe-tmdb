# Common project workflow

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

For an optional authenticated commissioning smoke test, set `TMDB_TEST_CONFIG`
to a private TOML file outside this repository before running the test gate.
The file and its credentials are never copied into output or handoff packages.
