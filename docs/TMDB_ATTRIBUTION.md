# TMDB attribution and use

This product uses the TMDB API but is not endorsed or certified by TMDB.

Every user-facing consumer that displays TMDB data or images must include that
notice in an About or Credits area and display an approved, unmodified TMDB
logo less prominently than the consumer's primary product logo. Approved logos
and brand colors are available from TMDB's official
[logos and attribution page](https://www.themoviedb.org/about/logos-attribution).

The plugin does not bundle a copied TMDB logo. This avoids distributing an
outdated or modified brand asset and requires each consumer UI to select the
appropriate current approved logo. Consumers must link references to TMDB to
<https://www.themoviedb.org/>.

The local SQLite cache enforces a maximum API-content retention of 180 days.
API keys and access tokens are user-supplied private configuration and must not
be included in source, logs, handoff archives, or releases. Commercial users
must independently obtain any agreement required by TMDB's current API terms.
