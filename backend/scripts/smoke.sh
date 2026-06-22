#!/usr/bin/env bash
# smoke.sh — Bring up the local stack and health-check it.
#
# Boots the Docker Compose stack, polls /q/health/ready, runs a couple of
# basic GET smoke checks, then tears the stack down on exit (trap). Mirrors
# the compose-smoke CI job (ci-cd-github-actions.md §5) and the local runbook
# (environments-and-deployment.md §2.2).
#
# If no docker-compose.yml exists yet (bootstrap/devops has not created it),
# the script prints guidance and exits 0 with a WARN — it does not fail the
# early project.
#
# Usage:
#   scripts/smoke.sh [--keep] [--timeout N] [--help]
#     --keep         Do not tear the stack down on exit (leave it running).
#     --timeout N    Seconds to wait for readiness (default 180).
#     --help         Show this help.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
smoke.sh — boot the Compose stack and health-check it.

Usage: scripts/smoke.sh [--keep] [--timeout N] [--help]

Options:
  --keep         Leave the stack running after the checks (skip teardown).
  --timeout N    Readiness wait budget in seconds (default 180).
  --help         Show this help and exit.
EOF
}

KEEP=0
TIMEOUT=180
HEALTH_URL="http://localhost:8080/q/health/ready"
LIVE_URL="http://localhost:8080/q/health/live"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --keep) KEEP=1 ;;
    --timeout)
      shift; [ "$#" -gt 0 ] || die "--timeout requires a value"
      case "$1" in (*[!0-9]*|'') die "--timeout must be a positive integer";; esac
      TIMEOUT="$1" ;;
    --timeout=*) TIMEOUT="${1#*=}"; case "$TIMEOUT" in (*[!0-9]*|'') die "--timeout must be a positive integer";; esac ;;
    -h|--help) usage; exit 0 ;;
    *) err "Unknown argument: $1"; usage; exit 2 ;;
  esac
  shift
done

# Locate the docker-compose.yml: repo root first, then backend/.
COMPOSE_DIR=""
if [ -f "$REPO_ROOT/docker-compose.yml" ] || [ -f "$REPO_ROOT/compose.yml" ]; then
  COMPOSE_DIR="$REPO_ROOT"
elif [ -f "$BACKEND_DIR/docker-compose.yml" ] || [ -f "$BACKEND_DIR/compose.yml" ]; then
  COMPOSE_DIR="$BACKEND_DIR"
fi

if [ -z "$COMPOSE_DIR" ]; then
  section "Compose smoke"
  status_row WARN "docker-compose.yml" "not found at repo root or backend/"
  warn "No Compose file yet. Once bootstrap/devops creates docker-compose.yml"
  warn "(see environments-and-deployment.md §2.1), re-run scripts/smoke.sh."
  exit 0
fi

# Compose CLI: prefer 'docker compose', fall back to 'docker-compose'.
if have docker && docker compose version >/dev/null 2>&1; then
  DC() { ( cd "$COMPOSE_DIR" && docker compose "$@" ); }
elif have docker-compose; then
  DC() { ( cd "$COMPOSE_DIR" && docker-compose "$@" ); }
else
  status_row WARN "docker compose" "not available"
  warn "Docker Compose is not installed/usable. Install Docker Desktop or the compose plugin."
  exit 0
fi

if ! docker info >/dev/null 2>&1; then
  status_row WARN "docker" "daemon not reachable"
  warn "The Docker daemon is not running. Start Docker and re-run."
  exit 0
fi

cleanup() {
  if [ "$KEEP" -eq 1 ]; then
    warn "--keep set: leaving the stack running. Tear down with: (cd $COMPOSE_DIR && docker compose down -v)"
    return
  fi
  section "Tearing down stack"
  DC down -v >/dev/null 2>&1 || true
  ok "Stack stopped."
}
trap cleanup EXIT INT TERM

section "Booting stack (compose dir: $COMPOSE_DIR)"
if ! DC up -d --build; then
  err "docker compose up failed. Recent logs:"
  DC logs --no-color 2>/dev/null | tail -n 60 || true
  exit 1
fi

section "Waiting for readiness ($HEALTH_URL, up to ${TIMEOUT}s)"
deadline=$(( $(date +%s) + TIMEOUT ))
ready=0
while [ "$(date +%s)" -lt "$deadline" ]; do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    ready=1; break
  fi
  printf '.'
  sleep 5
done
printf '\n'

if [ "$ready" -ne 1 ]; then
  err "App did not become ready within ${TIMEOUT}s. Recent logs:"
  DC logs --no-color 2>/dev/null | tail -n 80 || true
  exit 1
fi
ok "App is READY."

section "Smoke checks"
SMOKE_FAIL=0

if curl -fsS "$LIVE_URL" >/dev/null 2>&1; then
  status_row OK "GET /q/health/live"
else
  status_row FAIL "GET /q/health/live"
  SMOKE_FAIL=1
fi

if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
  status_row OK "GET /q/health/ready"
else
  status_row FAIL "GET /q/health/ready"
  SMOKE_FAIL=1
fi

# Optional representative read endpoint. Treated as best-effort: a 404 on an
# early project (endpoint not built yet) is a WARN, not a failure.
CATALOG_URL="http://localhost:8080/v1/catalog/releases?page=1&size=1"
code="$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: application/json' "$CATALOG_URL" 2>/dev/null || echo 000)"
case "$code" in
  2*) status_row OK   "GET /v1/catalog/releases" "HTTP $code" ;;
  000) status_row WARN "GET /v1/catalog/releases" "no response (endpoint not built yet?)" ;;
  *)  status_row WARN "GET /v1/catalog/releases" "HTTP $code (endpoint not built yet?)" ;;
esac

section "Smoke result"
if [ "$SMOKE_FAIL" -ne 0 ]; then
  err "Health checks FAILED."
  DC logs --no-color 2>/dev/null | tail -n 60 || true
  exit 1
fi
ok "Smoke PASSED — stack boots healthy."
exit 0
