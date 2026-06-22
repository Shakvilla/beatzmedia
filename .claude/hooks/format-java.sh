#!/usr/bin/env bash
# PostToolUse(Edit|Write) — best-effort format of a changed .java file. Never blocks (always exit 0).
set -uo pipefail
payload="$(cat || true)"
file="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0
# Prefer a project Spotless apply scoped to the file if available; else google-java-format jar; else no-op.
if command -v google-java-format >/dev/null 2>&1; then
  google-java-format --replace "$file" >/dev/null 2>&1 || true
fi
exit 0
