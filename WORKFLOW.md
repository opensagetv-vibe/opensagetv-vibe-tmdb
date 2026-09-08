# Common project workflow

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `install`, or
`all`. The initial local workflow verifies the pinned SQLite JDBC checksum,
targets Java 8 bytecode using Java 11, runs the cache/configuration tests, and
packages the API JAR plus its runtime dependency under `output/packages`.

The project is not yet part of the unified development container. That active
integration task must replace this temporary local execution boundary with the
same location-independent component workflow used by the other Vibe projects.
Installation remains `SKIPPED` until the container component-update lifecycle
is added.
