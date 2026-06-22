# shellcheck shell=bash
# _common.sh — shared helpers for BeatzClik backend automation scripts.
# Sourced by the other scripts; not meant to be run directly.
#
# Provides: repo-root / backend-dir resolution, the Maven wrapper locator,
# colourised status helpers, and an OK/WARN/FAIL readiness-row printer.
# POSIX-friendly bash. No `set -euo pipefail` here — the caller owns that.

# ---------------------------------------------------------------------------
# Colours (auto-disabled when not a TTY or NO_COLOR is set).
# ---------------------------------------------------------------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET="$(printf '\033[0m')"
  C_RED="$(printf '\033[31m')"
  C_GREEN="$(printf '\033[32m')"
  C_YELLOW="$(printf '\033[33m')"
  C_BLUE="$(printf '\033[34m')"
  C_BOLD="$(printf '\033[1m')"
else
  C_RESET=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_BOLD=""
fi

say()   { printf '%s\n' "$*"; }
info()  { printf '%s%s%s\n' "$C_BLUE"  "$*" "$C_RESET"; }
ok()    { printf '%s%s%s\n' "$C_GREEN" "$*" "$C_RESET"; }
warn()  { printf '%s%s%s\n' "$C_YELLOW" "$*" "$C_RESET" >&2; }
err()   { printf '%s%s%s\n' "$C_RED"   "$*" "$C_RESET" >&2; }
section() { printf '\n%s==> %s%s\n' "$C_BOLD" "$*" "$C_RESET"; }
die()   { err "FATAL: $*"; exit 1; }

# A single OK/WARN/FAIL table row: status_row <STATE> <tool> <detail>
status_row() {
  _state="$1"; _tool="$2"; _detail="${3:-}"
  case "$_state" in
    OK)   _c="$C_GREEN" ;;
    WARN) _c="$C_YELLOW" ;;
    FAIL) _c="$C_RED" ;;
    *)    _c="$C_RESET" ;;
  esac
  printf '  %s%-5s%s %-22s %s\n' "$_c" "$_state" "$C_RESET" "$_tool" "$_detail"
}

# ---------------------------------------------------------------------------
# Resolve repo root + backend dir regardless of invocation cwd.
# Repo root is assumed to be the parent of backend/. These scripts live in
# backend/scripts/, so we anchor off this file's own location.
# ---------------------------------------------------------------------------
_resolve_dirs() {
  # Directory containing this _common.sh (i.e. backend/scripts).
  _src="${BASH_SOURCE[0]:-$0}"
  while [ -h "$_src" ]; do
    _dir="$(cd -P "$(dirname "$_src")" >/dev/null 2>&1 && pwd)"
    _src="$(readlink "$_src")"
    case "$_src" in /*) ;; *) _src="$_dir/$_src" ;; esac
  done
  SCRIPTS_DIR="$(cd -P "$(dirname "$_src")" >/dev/null 2>&1 && pwd)"
  BACKEND_DIR="$(cd -P "$SCRIPTS_DIR/.." >/dev/null 2>&1 && pwd)"
  REPO_ROOT="$(cd -P "$BACKEND_DIR/.." >/dev/null 2>&1 && pwd)"
  export SCRIPTS_DIR BACKEND_DIR REPO_ROOT
}
_resolve_dirs

# Locate the Maven wrapper. Prefer backend/mvnw; fall back to ./mvnw if the
# script happens to be run from inside backend.
mvnw_cmd() {
  if [ -x "$BACKEND_DIR/mvnw" ]; then
    printf '%s' "$BACKEND_DIR/mvnw"
  elif [ -f "$BACKEND_DIR/mvnw" ]; then
    printf 'sh %s' "$BACKEND_DIR/mvnw"
  else
    return 1
  fi
}

# Run a maven goal in the backend dir. Usage: run_mvn <args...>
run_mvn() {
  _mvn="$(mvnw_cmd)" || die "Maven wrapper not found at $BACKEND_DIR/mvnw"
  ( cd "$BACKEND_DIR" && eval "$_mvn" -B -ntp "$@" )
}

# True if a maven plugin/goal prefix is resolvable (used to skip absent plugins).
mvn_has_plugin() {
  _prefix="$1"
  _mvn="$(mvnw_cmd)" || return 1
  ( cd "$BACKEND_DIR" && eval "$_mvn" -B -ntp -q help:describe "-Dplugin=$_prefix" >/dev/null 2>&1 )
}

have() { command -v "$1" >/dev/null 2>&1; }
