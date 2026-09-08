# Third-party notices

## Xerial SQLite JDBC

The build uses `org.xerial:sqlite-jdbc:3.53.2.1`, distributed under the Apache
License 2.0. Source and notices: <https://github.com/xerial/sqlite-jdbc>.

The dependency remains a separate JAR in the output package; it is not copied
into this source repository.

## Gson

The build uses `com.google.code.gson:gson:2.14.0`, distributed under the Apache
License 2.0. Source and notices: <https://github.com/google/gson>.

## The Movie Database

This product uses the TMDB API but is not endorsed or certified by TMDB.
Consumers must display the attribution required by TMDB and use an approved,
unmodified TMDB logo in an About or Credits area. See
`docs/TMDB_ATTRIBUTION.md`. TMDB content is not included in the source release,
and cached API content is bounded to a maximum retention of 180 days.
