# Stock SageTV compatibility

OpenSageTV Vibe TMDB is a standalone Standard SageTV plugin. It implements the
public SageTV 9 `SageTVPlugin` lifecycle and does not require a patched Vibe
Core.

The plugin is compiled for Java 8. Its package deliberately excludes the
compile-only `sage.*` API stubs, allowing the installed SageTV runtime to supply
the real interfaces. On 2026-09-08 the complete plugin linked successfully
against the exact read-only `Sage.jar` used by stock test server `.175`, with
SHA-256 `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.

This proves binary linkage, not a physical install. Stock `.175` was not
modified. Final runtime commissioning remains isolated to Vibe server `.232`
unless a stock install is separately authorized.

Consumers must treat the service as optional. If it is absent, disabled,
unconfigured, offline, or rate-limited, they must retain their core behavior
and provide a bounded error or unavailable state.

