#!/usr/bin/env bash
# bootstrap.sh — Preflight readiness check for the BeatzClik backend toolchain.
#
# Verifies the tools the autonomous loop relies on (java 25, Maven wrapper,
# docker + docker compose, gh CLI + auth, git) and prints an OK/WARN/FAIL
# readiness table. Exits non-zero ONLY when a hard-essential is missing
# (a usable JDK or the Maven wrapper); everything else is a WARN.
#
# Works whether invoked from the repo root or from backend/.
#
# Usage:
#   scripts/bootstrap.sh [--fix] [--help]
#     --fix   git init the repo root if it is not yet a git repository.
#     --help  show this help.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
bootstrap.sh — preflight readiness check.

Usage: scripts/bootstrap.sh [--fix] [--help]

Options:
  --fix    Initialise a git repo at the repo root if absent.
  --help   Show this help and exit.

Exit status: non-zero only if java or the Maven wrapper is missing.
EOF
}

FIX=0
for arg in "$@"; do
  case "$arg" in
    --fix) FIX=1 ;;
    -h|--help) usage; exit 0 ;;
    *) err "Unknown argument: $arg"; usage; exit 2 ;;
  esac
done

HARD_FAIL=0

section "BeatzClik backend — preflight"
say "repo root : $REPO_ROOT"
say "backend   : $BACKEND_DIR"
echo
say "${C_BOLD}Tool             Status${C_RESET}"

# --- Java (essential; warn if not 25) ---------------------------------------
if have java; then
  jv_raw="$(java -version 2>&1 | head -n1)"
  # Extract major version: "21.0.2" -> 21, "1.8.0" -> 8.
  jv="$(printf '%s' "$jv_raw" | sed -n 's/.*version "\([0-9][0-9]*\)\(\.\|"\).*/\1/p')"
  [ "$jv" = "1" ] && jv="$(printf '%s' "$jv_raw" | sed -n 's/.*version "1\.\([0-9][0-9]*\).*/\1/p')"
  if [ "${jv:-0}" -ge 25 ] 2>/dev/null; then
    status_row OK "java" "$jv_raw"
  else
    status_row WARN "java" "found Java ${jv:-?}; project targets 25 ($jv_raw)"
  fi
else
  status_row FAIL "java" "not on PATH — install a JDK 25 (Temurin)"
  HARD_FAIL=1
fi

# --- Maven wrapper (essential) ----------------------------------------------
if mvnw_cmd >/dev/null 2>&1; then
  status_row OK "mvnw" "$BACKEND_DIR/mvnw"
else
  status_row FAIL "mvnw" "missing at $BACKEND_DIR/mvnw"
  HARD_FAIL=1
fi

# --- Docker -----------------------------------------------------------------
if have docker; then
  if docker info >/dev/null 2>&1; then
    status_row OK "docker" "$(docker --version 2>/dev/null)"
  else
    status_row WARN "docker" "installed but daemon not reachable — start Docker"
  fi
else
  status_row WARN "docker" "not found — needed for smoke/integration tests"
fi

# --- docker compose (v2 plugin preferred) -----------------------------------
if have docker && docker compose version >/dev/null 2>&1; then
  status_row OK "docker compose" "$(docker compose version --short 2>/dev/null || echo present)"
elif have docker-compose; then
  status_row WARN "docker compose" "only legacy docker-compose found; v2 plugin recommended"
else
  status_row WARN "docker compose" "not available — needed for the local stack"
fi

# --- gh CLI + auth ----------------------------------------------------------
if have gh; then
  if gh auth status >/dev/null 2>&1; then
    status_row OK "gh" "authenticated ($(gh --version 2>/dev/null | head -n1))"
  else
    status_row WARN "gh" "installed but not authenticated — run: gh auth login"
  fi
else
  status_row WARN "gh" "not found — PR automation (open-pr.sh) will be manual"
fi

# --- git repo ---------------------------------------------------------------
if git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  status_row OK "git" "repository present at $REPO_ROOT"
elif have git; then
  if [ "$FIX" -eq 1 ]; then
    if git -C "$REPO_ROOT" init >/dev/null 2>&1; then
      status_row OK "git" "initialised new repository at $REPO_ROOT"
    else
      status_row WARN "git" "git init failed at $REPO_ROOT"
    fi
  else
    status_row WARN "git" "not a git repo — re-run with --fix to 'git init'"
  fi
else
  status_row WARN "git" "git not installed"
fi

# --- Next steps -------------------------------------------------------------
section "Next steps"
if [ "$HARD_FAIL" -ne 0 ]; then
  err "Essential tooling missing. Install a JDK 25 and ensure backend/mvnw exists, then re-run."
else
  cat <<EOF
  1. Run the local gate before opening a PR:   scripts/verify.sh
  2. Boot + health-check the stack:            scripts/smoke.sh
  3. Find the next ready work unit:            scripts/progress.sh --next
  4. Open a PR for a work unit:                scripts/open-pr.sh <WU-ID> "<title>"
EOF
fi

exit "$HARD_FAIL"
