#!/usr/bin/env bash
# progress-next.test.sh — regression test for progress.sh --next READY detection.
#
# Guards against the inline-comment parser bug: when a done WU's `status:` line
# carries a trailing YAML comment (e.g. `status: done   # PR #63 merged ...`),
# the status must still parse as `done` so that todo WUs depending on it are
# correctly reported as READY. If the comment leaks into the parsed status,
# dependents are silently dropped from `--next` and the autonomous loop stalls.
#
# Runs against the real backlog.yaml. Exits non-zero on any failure.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROGRESS="$SCRIPT_DIR/progress.sh"

# WUs whose every dependency is a done-with-inline-comment WU. These regressed
# under the parser bug — they MUST appear in --next.
EXPECT_READY="WU-ANA-1 WU-ADM-5 WU-STU-2"

fail=0

# Force plain output; capture --next.
out="$(NO_COLOR=1 bash "$PROGRESS" --next 2>&1)"
rc=$?
if [ "$rc" -ne 0 ]; then
  printf 'FAIL: progress.sh --next exited %s\n%s\n' "$rc" "$out"
  exit 1
fi

for wu in $EXPECT_READY; do
  if printf '%s\n' "$out" | grep -q "[[:space:]]$wu[[:space:]]"; then
    printf 'PASS: %s is READY\n' "$wu"
  else
    printf 'FAIL: %s missing from --next (dependency status likely mis-parsed)\n' "$wu"
    fail=1
  fi
done

# Guard against comment leakage in the default table: no status cell may contain '#'.
tbl="$(NO_COLOR=1 bash "$PROGRESS" 2>&1)"
if printf '%s\n' "$tbl" | grep -E '^  WU-[A-Z0-9-]+[[:space:]]+[a-z_]+[[:space:]]*#'; then
  printf 'FAIL: a parsed status contains an inline comment (leaked from YAML)\n'
  fail=1
else
  printf 'PASS: no inline comments leaked into parsed statuses\n'
fi

if [ "$fail" -ne 0 ]; then
  printf '\nprogress-next.test.sh: FAILED\n'
  exit 1
fi
printf '\nprogress-next.test.sh: OK\n'
