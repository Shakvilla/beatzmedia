#!/usr/bin/env bash
# next-migration-version.sh — Print the next free Flyway version number.
#
# Scans backend/src/main/resources/db/migration/ for V<n>__*.sql, finds the
# highest integer <n>, and prints <n>+1 (or 1 if there are none). Robust to a
# missing migration directory. See cross-cutting/data-and-migrations.md §4/§7.
#
# NOTE on bands: data-and-migrations.md §4.1 recommends module-prefixed version
# BANDS (V1xx platform, V2xx identity, ...). This script reports the next free
# number across the WHOLE sequence by default; pass --module to also surface the
# band hint for that module (informational — it does not change the printed
# next-free unless you also want band-local numbering, see --module behaviour).
#
# Works from repo root or backend/. Creates nothing — --create only ECHOES the
# target path.
#
# Usage:
#   scripts/next-migration-version.sh [--module <name>] [--create "<snake_desc>"] [--help]
#     (no args)            Print the next free global version number.
#     --module <name>      Informational: print the module's band hint too.
#     --create "<desc>"    Echo the full target filename path (does NOT create).
#     --help               Show this help.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
next-migration-version.sh — next free Flyway version number.

Usage: scripts/next-migration-version.sh [--module <name>] [--create "<snake_desc>"] [--help]

Options:
  --module <name>     Informational band hint for the module (see data-and-migrations §4.1).
  --create "<desc>"   Echo the full target V<n>__<desc>.sql path (does not create it).
  --help              Show this help and exit.

Examples:
  scripts/next-migration-version.sh
  scripts/next-migration-version.sh --module payments
  scripts/next-migration-version.sh --create "add_ownership_grant"
EOF
}

MODULE=""
CREATE_DESC=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --module) shift; [ "$#" -gt 0 ] || die "--module requires a value"; MODULE="$1" ;;
    --module=*) MODULE="${1#*=}" ;;
    --create) shift; [ "$#" -gt 0 ] || die "--create requires a value"; CREATE_DESC="$1" ;;
    --create=*) CREATE_DESC="${1#*=}" ;;
    -h|--help) usage; exit 0 ;;
    *) err "Unknown argument: $1"; usage; exit 2 ;;
  esac
  shift
done

MIG_DIR="$BACKEND_DIR/src/main/resources/db/migration"

# Find the highest integer version across V<n>__*.sql (n may be multi-digit).
max=0
if [ -d "$MIG_DIR" ]; then
  for f in "$MIG_DIR"/V*__*.sql; do
    [ -e "$f" ] || continue                     # no matches -> skip the literal glob
    base="$(basename "$f")"
    # Strip leading V, take digits up to the first non-digit (handles V12__, V100__).
    n="$(printf '%s' "$base" | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p')"
    [ -n "$n" ] || continue
    if [ "$n" -gt "$max" ] 2>/dev/null; then max="$n"; fi
  done
fi

if [ "$max" -eq 0 ]; then
  next=1
else
  next=$(( max + 1 ))
fi

# --module is informational: surface the documented band so an agent can choose
# a band-local number if following the band convention.
if [ -n "$MODULE" ]; then
  case "$MODULE" in
    platform|bootstrap) band="1xx" ;;
    identity)           band="2xx" ;;
    catalog)            band="3xx" ;;
    playback)           band="4xx" ;;
    library)            band="5xx" ;;
    commerce)           band="6xx" ;;
    payments)           band="7xx" ;;
    store|podcasts|events) band="8xx" ;;
    notifications|studio|admin|analytics|audit) band="9xx" ;;
    *)                  band="(no documented band; see data-and-migrations §4.1)" ;;
  esac
  warn "module '$MODULE' -> documented Flyway band V${band} (data-and-migrations §4.1)"
fi

if [ -n "$CREATE_DESC" ]; then
  # Normalise the description to snake_case-ish (lowercase, spaces/dashes -> _).
  desc="$(printf '%s' "$CREATE_DESC" \
    | tr '[:upper:]' '[:lower:]' \
    | sed -e 's/[^a-z0-9]\{1,\}/_/g' -e 's/^_//' -e 's/_$//')"
  [ -n "$desc" ] || die "--create description reduced to empty after normalisation"
  target="$MIG_DIR/V${next}__${desc}.sql"
  # Echo the full target path (creation is intentionally NOT performed).
  printf '%s\n' "$target"
  warn "Target migration path above (NOT created). Author it forward-only; never edit a merged migration."
else
  printf '%s\n' "$next"
fi

exit 0
