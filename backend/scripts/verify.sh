#!/usr/bin/env bash
# verify.sh — The full LOCAL GATE run before opening a PR.
#
# Mirrors the CI required checks (sdlc/ci-cd-github-actions.md §3,
# branching-and-pr.md §4) so an agent can self-validate locally. Runs, in
# order, with clear section headers, failing fast:
#   (a) Spotless check         (skipped with --no-format / if plugin absent)
#   (b) compile
#   (c) unit tests (surefire)
#   (d) integration tests (failsafe / verify)   [skipped with --fast]
#   (e) coverage gate (jacoco:check)            [skipped if plugin absent]
#   (f) ArchUnit — note: runs inside the test suite (no separate step)
#
# The project may be early (little/no code). Each phase degrades gracefully:
# an absent plugin or a "nothing to do" build is reported, not fatal.
#
# Works from repo root or backend/. Uses backend/mvnw.
#
# Usage:
#   scripts/verify.sh [--fast] [--no-format] [--help]
#     --fast       Skip integration tests (failsafe) and the coverage gate.
#     --no-format  Skip the Spotless formatting check.
#     --help       Show this help.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
verify.sh — local pre-PR gate (Spotless, compile, unit, integration, coverage).

Usage: scripts/verify.sh [--fast] [--no-format] [--help]

Options:
  --fast       Skip integration tests and coverage gate (inner-loop speed).
  --no-format  Skip the Spotless check.
  --help       Show this help and exit.

Exits non-zero on the first failing phase.
EOF
}

FAST=0
NO_FORMAT=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    --no-format) NO_FORMAT=1 ;;
    -h|--help) usage; exit 0 ;;
    *) err "Unknown argument: $arg"; usage; exit 2 ;;
  esac
done

mvnw_cmd >/dev/null 2>&1 || die "Maven wrapper not found at $BACKEND_DIR/mvnw"

# Track outcomes for the final summary.
RESULTS=""
record() { RESULTS="${RESULTS}${1}|${2}\n"; }   # state|label

fail_phase() {
  record FAIL "$1"
  err "Phase failed: $1"
  print_summary
  exit 1
}

print_summary() {
  section "Summary"
  printf '%b' "$RESULTS" | while IFS='|' read -r st lbl; do
    [ -n "$st" ] && status_row "$st" "$lbl"
  done
}

section "Local gate for beatzmedia (backend: $BACKEND_DIR)"

# --- (a) Spotless ----------------------------------------------------------
section "(a) Spotless — formatting check"
if [ "$NO_FORMAT" -eq 1 ]; then
  warn "Skipped (--no-format)."
  record WARN "spotless (skipped)"
elif mvn_has_plugin "spotless"; then
  if run_mvn -q spotless:check; then
    ok "Spotless clean."
    record OK "spotless"
  else
    err "Spotless found violations. Fix with: (cd backend && ./mvnw spotless:apply)"
    fail_phase "spotless"
  fi
else
  warn "Spotless plugin not configured in pom.xml — skipping (add it to enforce §11.7)."
  record WARN "spotless (absent)"
fi

# --- (b) Compile -----------------------------------------------------------
section "(b) Compile"
if run_mvn -q clean compile; then
  ok "Compile OK."
  record OK "compile"
else
  fail_phase "compile"
fi

# --- (c) Unit tests (surefire) ---------------------------------------------
section "(c) Unit tests (surefire)"
# -DskipITs=true is the pom default, keeping failsafe out of this phase.
if run_mvn -q test -DskipITs=true; then
  # Distinguish "no tests" from "tests passed" by inspecting reports.
  if ls "$BACKEND_DIR"/target/surefire-reports/*.xml >/dev/null 2>&1; then
    ok "Unit tests passed."
    record OK "unit-tests"
  else
    warn "No unit tests found yet (early project) — phase is vacuously green."
    record WARN "unit-tests (none yet)"
  fi
else
  err "Unit tests failed — see backend/target/surefire-reports/."
  fail_phase "unit-tests"
fi

# --- (d) Integration tests (failsafe) --------------------------------------
section "(d) Integration tests (failsafe)"
if [ "$FAST" -eq 1 ]; then
  warn "Skipped (--fast). Run a full verify before opening the PR."
  record WARN "integration-tests (skipped)"
else
  if run_mvn -q verify -DskipITs=false -DskipUnitTests=true; then
    if ls "$BACKEND_DIR"/target/failsafe-reports/*.xml >/dev/null 2>&1; then
      ok "Integration tests passed."
      record OK "integration-tests"
    else
      warn "No integration tests (*IT) found yet — phase is vacuously green."
      record WARN "integration-tests (none yet)"
    fi
  else
    err "Integration tests failed — see backend/target/failsafe-reports/. (Docker required for Testcontainers.)"
    fail_phase "integration-tests"
  fi
fi

# --- (e) Coverage gate (jacoco) --------------------------------------------
section "(e) Coverage gate (JaCoCo)"
if [ "$FAST" -eq 1 ]; then
  warn "Skipped (--fast)."
  record WARN "coverage-gate (skipped)"
elif mvn_has_plugin "org.jacoco:jacoco-maven-plugin" || mvn_has_plugin "jacoco"; then
  if run_mvn -q verify -DskipITs=false -Pcoverage; then
    ok "Coverage gate satisfied."
    record OK "coverage-gate"
  else
    err "Coverage below gate (testing-strategy.md §10) — see backend/target/site/jacoco/."
    fail_phase "coverage-gate"
  fi
else
  warn "JaCoCo not configured — skipping coverage gate (wire jacoco-maven-plugin + -Pcoverage)."
  record WARN "coverage-gate (absent)"
fi

# --- (f) ArchUnit note ------------------------------------------------------
section "(f) ArchUnit (hexagonal dependency rule)"
say "ArchUnit rules run inside the JUnit suite (testing-strategy.md §6); they"
say "are exercised by phases (c)/(d) above and do not need a separate step here."
record OK "archunit (in-suite)"

# --- (g) Automation self-tests ---------------------------------------------
section "(g) Automation self-tests (backend/scripts/tests)"
_tests_dir="$(dirname "$0")/tests"
if ls "$_tests_dir"/*.test.sh >/dev/null 2>&1; then
  _st_fail=0
  for _t in "$_tests_dir"/*.test.sh; do
    if bash "$_t"; then
      ok "$(basename "$_t") passed."
    else
      err "$(basename "$_t") failed."
      _st_fail=1
    fi
  done
  if [ "$_st_fail" -eq 0 ]; then
    record OK "script-self-tests"
  else
    fail_phase "script-self-tests"
  fi
else
  warn "No script self-tests found — skipping."
  record WARN "script-self-tests (none)"
fi

print_summary
section "Local gate PASSED"
ok "Safe to open a PR. (Note WARN rows above for tooling not yet wired.)"
exit 0
