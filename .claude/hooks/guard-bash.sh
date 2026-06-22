#!/usr/bin/env bash
# PreToolUse(Bash) guard. Reads the hook JSON on stdin, blocks destructive commands.
# Exit 2 = block (stderr shown to the model). Exit 0 = allow.
set -euo pipefail
payload="$(cat || true)"
cmd="$(printf '%s' "$payload" | jq -r '.tool_input.command // .tool_input.cmd // empty' 2>/dev/null || true)"
[ -z "$cmd" ] && exit 0
deny_patterns=(
  'git[[:space:]]+push[[:space:]].*--force'
  'git[[:space:]]+push[[:space:]]+-f'
  'rm[[:space:]]+-rf[[:space:]]+/'
  'rm[[:space:]]+-rf[[:space:]]+~'
  ':\(\)\{'                     # fork bomb
  '>[[:space:]]*/dev/sda'
  'mkfs'
  'chmod[[:space:]]+-R[[:space:]]+777[[:space:]]+/'
)
for p in "${deny_patterns[@]}"; do
  if printf '%s' "$cmd" | grep -Eq "$p"; then
    echo "BLOCKED by guard-bash hook: command matches forbidden pattern /$p/. Refusing to run: $cmd" >&2
    exit 2
  fi
done
# Protect main branch from accidental direct commits (loop should use feature branches).
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+commit' ; then
  branch="$(git -C "${CLAUDE_PROJECT_DIR:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
  if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
    echo "BLOCKED: direct commit to '$branch'. Create a feature branch (feat/<WU-ID>-slug) first." >&2
    exit 2
  fi
fi
exit 0
