#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deps="$root/.deps"
out="$root/output"
sqlite_version=3.53.2.1
sqlite_sha256=f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1
sqlite_jar="$deps/sqlite-jdbc-$sqlite_version.jar"
sqlite_url="https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/$sqlite_version/sqlite-jdbc-$sqlite_version.jar"
gson_version=2.14.0
gson_sha256=2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f
gson_jar="$deps/gson-$gson_version.jar"
gson_url="https://repo.maven.apache.org/maven2/com/google/code/gson/gson/$gson_version/gson-$gson_version.jar"
version="$(sed -n 's/^VERSION=//p' "$root/release.properties" | tr -d '\r')"
archive_epoch="${SOURCE_DATE_EPOCH:-946684800}"
[[ "$version" =~ ^[0-9]+([.][0-9]+){1,3}$ ]] || {
  echo "ERROR: SageTV plugin VERSION must be dotted numeric: $version" >&2
  exit 1
}

mkdir -p "$deps"
if [[ ! -f "$sqlite_jar" ]]; then
  curl --fail --location --retry 3 --output "$sqlite_jar" "$sqlite_url"
fi
echo "$sqlite_sha256  $sqlite_jar" | sha256sum --check --status
if [[ ! -f "$gson_jar" ]]; then
  curl --fail --location --retry 3 --output "$gson_jar" "$gson_url"
fi
echo "$gson_sha256  $gson_jar" | sha256sum --check --status
rm -rf "$out"
mkdir -p "$out/api-classes" "$out/classes" "$out/test-classes" "$out/packages"
mapfile -t api_sources < <(find "$root/source/compileOnly/java" -name '*.java' -type f | sort)
mapfile -t main_sources < <(find "$root/source/main/java" -name '*.java' -type f | sort)
mapfile -t test_sources < <(find "$root/source/test/java" -name '*.java' -type f | sort)
classpath="$sqlite_jar:$gson_jar"
javac -encoding UTF-8 -Xlint:all --release 8 -d "$out/api-classes" "${api_sources[@]}"
javac -encoding UTF-8 -Xlint:all,-classfile --release 8 -classpath "$out/api-classes:$classpath" -d "$out/classes" "${main_sources[@]}"
javac -encoding UTF-8 -Xlint:all,-classfile --release 8 -classpath "$out/api-classes:$out/classes:$classpath" -d "$out/test-classes" "${test_sources[@]}"
java -classpath "$out/test-classes:$out/api-classes:$out/classes:$classpath" org.opensagetv.vibe.tmdb.TestRunner
sage_jar="${SAGETV_COMPILE_JAR:-}"
if [[ -z "$sage_jar" && -f /work/sagetv/build/release/Sage.jar ]]; then
  sage_jar=/work/sagetv/build/release/Sage.jar
fi
if [[ -n "$sage_jar" ]]; then
  test -f "$sage_jar" || { echo "ERROR: SAGETV_COMPILE_JAR does not exist: $sage_jar" >&2; exit 1; }
  java -classpath "$out/test-classes:$out/classes:$sage_jar:$classpath" \
    org.opensagetv.vibe.tmdb.SageTvBinaryCompatibilityProbe
else
  echo 'SKIPPED: actual Sage.jar binary-compatibility probe (set SAGETV_COMPILE_JAR)'
fi
# javac and cp use the current time. Normalize archive members and omit jar's
# generated, current-time manifest so identical sources produce identical JARs.
find "$out/classes" -exec touch -h -d "@$archive_epoch" {} +
jar --create --no-manifest --file "$out/packages/OpenSageTVVibeTMDB.jar" -C "$out/classes" .
if jar tf "$out/packages/OpenSageTVVibeTMDB.jar" | grep -q '^sage/'; then
  echo 'ERROR: compile-only SageTV API classes leaked into the plugin JAR' >&2
  exit 1
fi
cp "$sqlite_jar" "$out/packages/"
cp "$gson_jar" "$out/packages/"
plugin_stage="$out/plugin-stage"
mkdir -p "$plugin_stage/JARs" "$plugin_stage/plugins/opensagetv-vibe-tmdb" \
  "$plugin_stage/docs/opensagetv-vibe-tmdb"
cp "$out/packages/OpenSageTVVibeTMDB.jar" "$sqlite_jar" "$gson_jar" "$plugin_stage/JARs/"
cp "$root/tmdb_config.example.toml" "$root/plugin.properties" \
  "$plugin_stage/plugins/opensagetv-vibe-tmdb/"
cp "$root/LICENSE" "$root/THIRD_PARTY_NOTICES.md" "$root/docs/TMDB_ATTRIBUTION.md" \
  "$root/docs/STOCK_SAGETV_COMPATIBILITY.md" \
  "$plugin_stage/docs/opensagetv-vibe-tmdb/"
find "$plugin_stage" -exec touch -h -d "@$archive_epoch" {} +
(cd "$plugin_stage" && find . -mindepth 1 -printf '%P\n' | LC_ALL=C sort | \
  zip -X -q "$out/packages/OpenSageTVVibeTMDB-plugin.zip" -@)
unzip -Z1 "$out/packages/OpenSageTVVibeTMDB-plugin.zip" | \
  grep -Fx 'docs/opensagetv-vibe-tmdb/STOCK_SAGETV_COMPATIBILITY.md' >/dev/null || {
    echo 'ERROR: stock compatibility contract missing from plugin ZIP' >&2
    exit 1
  }
cp "$out/packages/OpenSageTVVibeTMDB-plugin.zip" \
  "$out/packages/OpenSageTVVibeTMDB-plugin-$version.zip"
python3 "$root/scripts/generate-plugin-manifest.py" \
  --version "$version" \
  --package "$out/packages/OpenSageTVVibeTMDB-plugin-$version.zip" \
  --output "$out/packages/opensagetv-vibe-tmdb.plugin.xml"
(cd "$out/packages" && find . -maxdepth 1 -type f ! -name SHA256SUMS -printf '%P\0' | \
  sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$out/packages" && sha256sum -c SHA256SUMS >/dev/null)
echo "PASS: $out/packages/OpenSageTVVibeTMDB.jar"
echo "PASS: $out/packages/OpenSageTVVibeTMDB-plugin.zip"
echo "PASS: $out/packages/OpenSageTVVibeTMDB-plugin-$version.zip"
echo "PASS: $out/packages/opensagetv-vibe-tmdb.plugin.xml"
