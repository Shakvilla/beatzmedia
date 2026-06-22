#!/usr/bin/env bash
# SessionStart — print a short orientation + the next READY work unit. Best-effort, never blocks.
set -uo pipefail
root="${CLAUDE_PROJECT_DIR:-.}"
echo "── BeatzClik autonomous team ─────────────────────────────────────────────"
echo "Loop: /run-loop (continuous) · /build-next-wu · /build-phase <n> · /status · /bootstrap"
echo "Truth: BACKEND-PRD.md · backend/docs/README.md · backend/.project/backlog.yaml"
if [ -x "$root/backend/scripts/progress.sh" ]; then
  echo "── Backlog ───────────────────────────────────────────────────────────────"
  bash "$root/backend/scripts/progress.sh" --next 2>/dev/null || true
fi
echo "──────────────────────────────────────────────────────────────────────────"
exit 0
