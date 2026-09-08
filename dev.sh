#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
command="${1:-all}"
case "$command" in
  test|build) exec bash "$root/scripts/build.sh" ;;
  validate)
    find "$root" -type f \( -name 'tmdb_config.toml' -o -name '*.sqlite3' -o -name '*.sqlite3-wal' -o -name '*.sqlite3-shm' \) \
      -not -path "$root/.deps/*" -not -path "$root/output/*" -print -quit | grep -q . && {
        echo 'ERROR: private TMDB configuration/cache in publishable source' >&2; exit 1; }
    echo 'PASS: no private TMDB configuration/cache in publishable source'
    ;;
  install) echo 'SKIPPED: installation will be owned by the container component-update workflow' ;;
  all) "$root/dev.sh" validate; "$root/dev.sh" build; "$root/dev.sh" install ;;
  *) echo 'Usage: ./dev.sh test|validate|build|install|all' >&2; exit 2 ;;
esac
