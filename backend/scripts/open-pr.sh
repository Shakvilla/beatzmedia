#!/usr/bin/env bash
# open-pr.sh — Branch + commit + push + PR helper for one work unit.
#
# Implements the branching-and-pr.md flow: branch off main as
# feat/<WU-ID>-<slug>, stage + Conventional-Commit, push, open a PR with a
# DoD-checklist body (or the repo PR template), apply labels, and enable
# auto-merge (squash). Degrades gracefully when gh is missing/unauthenticated
# by printing the exact manual commands.
#
# Works from repo root or backend/.
#
# Usage:
#   scripts/open-pr.sh <WU-ID> "<title>" [--label <name> ...] [--module <name>] [--no-merge] [--help]
#     <WU-ID>          e.g. WU-PAY-1 (literal id from .project/backlog.yaml / PRD §8).
#     "<title>"        short subject for branch slug, commit, and PR title.
#     --label <name>   add a label (repeatable), e.g. --label area:payments.
#     --module <name>  Conventional-Commit scope; auto-guessed from WU id if omitted.
#     --no-merge       open the PR but do NOT enable auto-merge.
#     --help           Show this help.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
open-pr.sh — branch, commit, push, and open a PR for a work unit.

Usage: scripts/open-pr.sh <WU-ID> "<title>" [--label <name> ...] [--module <name>] [--no-merge]

Options:
  --label <name>    Add a label to the PR (repeatable).
  --module <name>   Commit scope; guessed from the WU id (e.g. WU-PAY-* -> payments) if omitted.
  --no-merge        Do not enable auto-merge after opening.
  --help            Show this help and exit.

Examples:
  scripts/open-pr.sh WU-IDN-1 "account registration & authentication" --label area:security
  scripts/open-pr.sh WU-PAY-1 "PaymentIntent + InitiateCharge" --label area:payments
EOF
}

WU=""
TITLE=""
MODULE=""
NO_MERGE=0
LABELS=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --label) shift; [ "$#" -gt 0 ] || die "--label requires a value"; LABELS="$LABELS $1" ;;
    --label=*) LABELS="$LABELS ${1#*=}" ;;
    --module) shift; [ "$#" -gt 0 ] || die "--module requires a value"; MODULE="$1" ;;
    --module=*) MODULE="${1#*=}" ;;
    --no-merge) NO_MERGE=1 ;;
    -h|--help) usage; exit 0 ;;
    -*) err "Unknown option: $1"; usage; exit 2 ;;
    *)
      if [ -z "$WU" ]; then WU="$1"
      elif [ -z "$TITLE" ]; then TITLE="$1"
      else die "Unexpected extra argument: $1"
      fi ;;
  esac
  shift
done

[ -n "$WU" ] || { err "Missing <WU-ID>."; usage; exit 2; }
[ -n "$TITLE" ] || { err "Missing <title>."; usage; exit 2; }

have git || die "git is required."
git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "Not a git repository at $REPO_ROOT. Run scripts/bootstrap.sh --fix first."

GIT() { git -C "$REPO_ROOT" "$@"; }

# Normalise WU id to uppercase for consistency.
WU_UC="$(printf '%s' "$WU" | tr '[:lower:]' '[:upper:]')"

# Guess module/scope from the WU id (WU-PAY-1 -> payments) if not supplied.
if [ -z "$MODULE" ]; then
  code="$(printf '%s' "$WU_UC" | sed -n 's/^WU-\([A-Z]*\)-.*/\1/p')"
  case "$code" in
    PLT) MODULE="platform" ;;
    IDN) MODULE="identity" ;;
    CAT) MODULE="catalog" ;;
    PAY) MODULE="payments" ;;
    COM) MODULE="commerce" ;;
    PLY) MODULE="playback" ;;
    LIB) MODULE="library" ;;
    MED) MODULE="media" ;;
    SRCH) MODULE="search" ;;
    STO) MODULE="store" ;;
    POD) MODULE="podcasts" ;;
    EVT) MODULE="events" ;;
    NOT) MODULE="notifications" ;;
    STU) MODULE="studio" ;;
    ANA) MODULE="analytics" ;;
    AUD) MODULE="audit" ;;
    ADM) MODULE="admin" ;;
    *)   MODULE="$(printf '%s' "${code:-$WU_UC}" | tr '[:upper:]' '[:lower:]')" ;;
  esac
fi

# kebab slug from the title (2-5 words is the convention; we keep it short).
slug="$(printf '%s' "$TITLE" \
  | tr '[:upper:]' '[:lower:]' \
  | sed -e 's/[^a-z0-9]\{1,\}/-/g' -e 's/^-//' -e 's/-$//' \
  | cut -c1-40 | sed 's/-$//')"
[ -n "$slug" ] || slug="change"

BRANCH="feat/${WU_UC}-${slug}"
COMMIT_MSG="feat(${MODULE}): ${WU_UC} ${TITLE}"

section "open-pr for ${WU_UC}"
say "module/scope : $MODULE"
say "branch       : $BRANCH"
say "commit       : $COMMIT_MSG"

# --- Ensure we are on a feature branch (not main / not detached) -----------
CURRENT="$(GIT rev-parse --abbrev-ref HEAD 2>/dev/null || echo HEAD)"
if [ "$CURRENT" = "main" ] || [ "$CURRENT" = "master" ] || [ "$CURRENT" = "HEAD" ]; then
  if GIT show-ref --verify --quiet "refs/heads/$BRANCH"; then
    info "Switching to existing branch $BRANCH"
    GIT switch "$BRANCH" || GIT checkout "$BRANCH"
  else
    info "Creating branch $BRANCH from current HEAD ($CURRENT)"
    GIT switch -c "$BRANCH" 2>/dev/null || GIT checkout -b "$BRANCH"
  fi
else
  info "Already on feature branch '$CURRENT' — using it."
  BRANCH="$CURRENT"
fi

# --- Stage + commit (skip if nothing to commit) ----------------------------
GIT add -A
if GIT diff --cached --quiet; then
  warn "Nothing staged to commit — skipping commit step."
else
  GIT commit -m "$COMMIT_MSG"
  ok "Committed: $COMMIT_MSG"
fi

# --- Push -------------------------------------------------------------------
if GIT remote get-url origin >/dev/null 2>&1; then
  if GIT push -u origin "$BRANCH"; then
    ok "Pushed $BRANCH to origin."
  else
    warn "git push failed. Push manually: git push -u origin $BRANCH"
  fi
else
  warn "No 'origin' remote configured — cannot push or open a PR automatically."
  warn "Add a remote (git remote add origin <url>) then re-run."
fi

# --- PR body ----------------------------------------------------------------
PR_TEMPLATE=""
for cand in "$REPO_ROOT/.github/PULL_REQUEST_TEMPLATE.md" "$REPO_ROOT/.github/pull_request_template.md"; do
  [ -f "$cand" ] && { PR_TEMPLATE="$cand"; break; }
done

BODY_FILE="$(mktemp 2>/dev/null || echo "${TMPDIR:-/tmp}/openpr.$$.md")"
if [ -n "$PR_TEMPLATE" ]; then
  cp "$PR_TEMPLATE" "$BODY_FILE"
  { printf '\n\n<!-- auto-prefilled by open-pr.sh -->\nWork unit: %s\n' "$WU_UC"; } >> "$BODY_FILE"
else
  cat > "$BODY_FILE" <<EOF
## Work unit
- **WU:** ${WU_UC}
- **Module(s):** ${MODULE}

## Summary
${TITLE}

## Definition of Done (01-conventions §11 — CI enforces; tick what holds)
- [ ] Unit tests pass (domain + use cases with fakes)
- [ ] Integration tests pass (Testcontainers Postgres/MinIO, REST-assured)
- [ ] Contract conformance green (responses validate against API-CONTRACT.md / frontend types)
- [ ] Flyway migration(s) forward-only, apply cleanly on an empty DB
- [ ] Boots under \`docker compose up\` (healthy)
- [ ] Hexagonal dependency rule holds (ArchUnit green)
- [ ] Money/side-effect paths idempotent; privileged mutations append an AuditEntry (INV-10)
- [ ] Coverage >= gate (testing-strategy.md); Spotless clean; no new high/critical security findings
- [ ] Relevant module ADD updated in this PR (if behavior changed)

## Migrations
- <list each new V<n>__*.sql, or "none">

## Breaking changes
- [ ] None
EOF
fi

# --- Open PR + labels + auto-merge via gh, or print manual fallback --------
section "Pull request"
if have gh && gh auth status >/dev/null 2>&1; then
  LABEL_ARGS=""
  for l in $LABELS; do LABEL_ARGS="$LABEL_ARGS --label $l"; done

  if ( cd "$REPO_ROOT" && eval gh pr create --title "\"$COMMIT_MSG\"" --body-file "\"$BODY_FILE\"" --base main --head "\"$BRANCH\"" $LABEL_ARGS ); then
    ok "PR opened."
    if [ "$NO_MERGE" -eq 0 ]; then
      if ( cd "$REPO_ROOT" && gh pr merge --squash --auto --delete-branch ); then
        ok "Auto-merge (squash) enabled — merges when checks + reviews are green."
      else
        warn "Could not enable auto-merge (branch protection / permissions?). Enable manually:"
        warn "  gh pr merge --squash --auto --delete-branch"
      fi
    fi
  else
    warn "gh pr create failed (PR may already exist). Inspect with: gh pr view --web"
  fi
else
  warn "gh CLI not available/authenticated — printing manual instructions."
  cat <<EOF

  Manual PR steps:
    1. Push (if not already):   git push -u origin $BRANCH
    2. Open a PR to 'main' with title:
         $COMMIT_MSG
       Body saved at: $BODY_FILE
    3. Apply labels:${LABELS:- (none)}
    4. Enable auto-merge:       gh pr merge --squash --auto --delete-branch
       (after: gh auth login)
EOF
fi

say ""
info "PR body kept at: $BODY_FILE"
exit 0
