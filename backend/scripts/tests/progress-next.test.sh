#!/usr/bin/env bash
# progress-next.test.sh — regression test for progress.sh --next READY detection.
#
# Guards against the inline-comment parser bug: when a done WU's `status:` line
# carries a trailing YAML comment (e.g. `status: done   # PR #63 merged ...`),
# the status must still parse as `done` so that todo WUs depending on it are
# correctly reported as READY. If the comment leaks into the parsed status,
# dependents are silently dropped from `--next` and the autonomous build loop
# stalls.
#
# Runs against a SELF-CONTAINED fixture (via BEATZ_BACKLOG) so it never goes
# stale as the real backlog advances. Exits non-zero on any failure.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROGRESS="$SCRIPT_DIR/progress.sh"

fixture="$(mktemp 2>/dev/null || echo "${TMPDIR:-/tmp}/backlog-fixture.$$.yaml")"
cleanup() { rm -f "$fixture"; }
trap cleanup EXIT

# A minimal backlog exercising the bug:
#   - WU-DONE-CMT  : done, with an inline comment that itself contains '#'
#   - WU-READY     : todo, depends only on the commented-done WU  -> MUST be READY
#   - WU-BLOCKED   : todo, depends on a not-done WU               -> MUST NOT be READY
#   - WU-OPEN-DEP  : todo dependency of WU-BLOCKED
cat > "$fixture" <<'YAML'
work_units:
  - id: WU-DONE-CMT
    title: Done dependency carrying an inline comment
    phase: 9
    status: done   # PR #999 merged 2026-01-01 (squash abc1234); note with # hash
    depends_on: []
  - id: WU-READY
    title: Todo depending on the commented-done WU
    phase: 9
    status: todo
    depends_on: [WU-DONE-CMT]
  - id: WU-OPEN-DEP
    title: An undone dependency
    phase: 9
    status: todo
    depends_on: []
  - id: WU-BLOCKED
    title: Todo whose dependency is not done
    phase: 9
    status: todo
    depends_on: [WU-OPEN-DEP]
YAML

fail=0
pass() { printf 'PASS: %s\n' "$1"; }
bad()  { printf 'FAIL: %s\n' "$1"; fail=1; }

out="$(NO_COLOR=1 BEATZ_BACKLOG="$fixture" bash "$PROGRESS" --next 2>&1)"
rc=$?
if [ "$rc" -ne 0 ]; then
  printf 'FAIL: progress.sh --next exited %s\n%s\n' "$rc" "$out"
  exit 1
fi

# The core regression: a todo whose only dep is done-with-comment is READY.
if printf '%s\n' "$out" | grep -q '[[:space:]]WU-READY[[:space:]]'; then
  pass "WU-READY is READY (done-with-comment dependency parsed as done)"
else
  bad "WU-READY missing from --next (dependency status mis-parsed)"
fi

# Negative control: a todo with a not-done dep must NOT be reported READY.
if printf '%s\n' "$out" | grep -q '[[:space:]]WU-BLOCKED[[:space:]]'; then
  bad "WU-BLOCKED wrongly reported READY (its dependency is not done)"
else
  pass "WU-BLOCKED correctly excluded (dependency not done)"
fi

# Guard against comment leakage in the default table: no status cell may hold '#'.
tbl="$(NO_COLOR=1 BEATZ_BACKLOG="$fixture" bash "$PROGRESS" 2>&1)"
if printf '%s\n' "$tbl" | grep -qE '^  WU-[A-Z0-9-]+[[:space:]]+[a-z_]+[[:space:]]*#'; then
  bad "a parsed status contains an inline comment (leaked from YAML)"
else
  pass "no inline comments leaked into parsed statuses"
fi

# Smoke: the fix works against the REAL backlog too (parses without error,
# and no status leaks a comment there either).
real="$(NO_COLOR=1 bash "$PROGRESS" 2>&1)"
if printf '%s\n' "$real" | grep -qE '^  WU-[A-Z0-9-]+[[:space:]]+[a-z_]+[[:space:]]*#'; then
  bad "live backlog: a parsed status leaked an inline comment"
else
  pass "live backlog parses with no comment leakage"
fi

if [ "$fail" -ne 0 ]; then
  printf '\nprogress-next.test.sh: FAILED\n'
  exit 1
fi
printf '\nprogress-next.test.sh: OK\n'
