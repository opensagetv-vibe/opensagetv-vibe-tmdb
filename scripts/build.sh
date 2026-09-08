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
mkdir -p "$out/classes" "$out/test-classes" "$out/packages"
mapfile -t main_sources < <(find "$root/source/main/java" -name '*.java' -type f | sort)
mapfile -t test_sources < <(find "$root/source/test/java" -name '*.java' -type f | sort)
classpath="$sqlite_jar:$gson_jar"
javac -encoding UTF-8 -Xlint:all,-classfile --release 8 -classpath "$classpath" -d "$out/classes" "${main_sources[@]}"
javac -encoding UTF-8 -Xlint:all,-classfile --release 8 -classpath "$out/classes:$classpath" -d "$out/test-classes" "${test_sources[@]}"
java -classpath "$out/test-classes:$out/classes:$classpath" org.opensagetv.vibe.tmdb.TestRunner
jar --create --file "$out/packages/OpenSageTVVibeTMDB.jar" -C "$out/classes" .
cp "$sqlite_jar" "$out/packages/"
cp "$gson_jar" "$out/packages/"
echo "PASS: $out/packages/OpenSageTVVibeTMDB.jar"
