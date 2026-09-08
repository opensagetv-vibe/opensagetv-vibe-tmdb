# OpenSageTV Vibe TMDB tasks

This is the only active TMDB backlog. Completed work is removed and recorded
in `CHANGELOG.md` and `HANDOFF.md`.

- [ ] Add a longer simultaneous SageMC/XMLTV adapter stress run after both
  consumers exist. Transactional v1-to-v2 migration, concurrent writers,
  abrupt external-process recovery, cross-connection visibility,
  corrupt/future database rejection, cleanup, backup, and retention boundaries
  already pass.
- [ ] Add the SageMC adapter, migrate useful `sagemc/imdb_*` preferences, and
  replace broken SageIMDb screens without coupling the STV to HTTP or SQLite.
- [ ] Add opt-in XMLTV enrichment that preserves existing feed metadata,
  stable Show IDs, and successful imports when TMDB is absent/offline.
- [ ] Commission with the ignored local `hdhr_atsc_epg/tmdb_config.toml` and
  synthetic fixtures; test auth failure, 404, 429, timeout, ambiguity,
  negative cache, offline restart, and no-credential behavior.
- [ ] Add the SageTV plugin artifact to unified release packaging and SBOM
  generation. Component-only install/update/rollback already passes without
  rebuilding a development or runtime image.
- [ ] Add public plugin metadata, required TMDB attribution/logo guidance,
  third-party notices, GitHub CI, clean-checkout proof, and release packaging.
