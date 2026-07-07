#!/usr/bin/env bash
# progress.sh — Report on, and update, the work-unit backlog.
#
# Reads backend/.project/backlog.yaml (the registry the autonomous loop reads).
# Default: a per-phase table of WU id | status | title.
#
# Flags:
#   --next            Print the next READY work unit(s): status 'todo' whose
#                     every depends_on is 'done', lowest phase first.
#   --set <ID> <st>   Set a WU's status in place (todo|in_progress|in_review|
#                     blocked|done). Uses yq if present, else a careful awk;
#                     writes to a temp file then moves it (never corrupts).
#   --gates           List WUs that carry a human_gate.
#   --help            Show this help.
#
# Prefers `yq` when installed; otherwise uses a minimal parser tailored to the
# known backlog structure (list of mappings under work_units:).
#
# Works from repo root or backend/.

set -uo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

# Backlog path — overridable via BEATZ_BACKLOG (used by the script self-tests
# to run against a fixed fixture instead of the live, ever-advancing backlog).
BACKLOG="${BEATZ_BACKLOG:-$BACKEND_DIR/.project/backlog.yaml}"

usage() {
  cat <<EOF
progress.sh — report on / update the work-unit backlog.

Usage:
  scripts/progress.sh                       Per-phase table (id | status | title)
  scripts/progress.sh --next                Next READY work unit(s)
  scripts/progress.sh --set <WU-ID> <st>    Update a WU's status in place
  scripts/progress.sh --gates               List WUs with a human_gate
  scripts/progress.sh --help                Show this help

Valid statuses: todo | in_progress | in_review | blocked | done
Backlog file: $BACKLOG
EOF
}

VALID_STATUSES="todo in_progress in_review blocked done"
is_valid_status() {
  for s in $VALID_STATUSES; do [ "$1" = "$s" ] && return 0; done
  return 1
}

[ -f "$BACKLOG" ] || die "Backlog not found at $BACKLOG"

USE_YQ=0
if have yq; then USE_YQ=1; fi

# ---------------------------------------------------------------------------
# Extraction. Emits TAB-separated rows: phase \t id \t status \t has_gate \t title
# Two implementations (yq / awk) producing the identical row format.
# ---------------------------------------------------------------------------
emit_rows() {
  if [ "$USE_YQ" -eq 1 ]; then
    yq -r '
      .work_units[]
      | [ (.phase // "?"), .id,
          ((.status // "?") | sub("\s*#.*$"; "")),   # defensive: strip any inline comment
          ((.human_gate // []) | length > 0), (.title // "") ]
      | @tsv
    ' "$BACKLOG" 2>/dev/null && return 0
    # If yq failed (e.g. older syntax), fall through to awk.
  fi
  awk '
    function flush() {
      if (id != "") {
        printf "%s\t%s\t%s\t%s\t%s\n",
               (phase=="" ? "?" : phase), id,
               (status=="" ? "?" : status),
               (gate=="" ? "false" : "true"), title
      }
      id=""; phase=""; status=""; title=""; gate=""
    }
    # New list item begins with "- id:" (optionally indented).
    /^[[:space:]]*-[[:space:]]+id:[[:space:]]*/ {
      flush()
      line=$0
      sub(/^[[:space:]]*-[[:space:]]+id:[[:space:]]*/, "", line)
      gsub(/[[:space:]]+$/, "", line)
      id=line
      next
    }
    /^[[:space:]]+phase:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]+phase:[[:space:]]*/, "", line); gsub(/[[:space:]]+$/,"",line); phase=line; next
    }
    /^[[:space:]]+status:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]+status:[[:space:]]*/, "", line)
      sub(/[[:space:]]*#.*$/, "", line)   # drop inline YAML comment (e.g. "done   # PR #63 merged")
      gsub(/[[:space:]]+$/,"",line); status=line; next
    }
    /^[[:space:]]+human_gate:[[:space:]]*/ { gate="true"; next }
    /^[[:space:]]+title:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]+title:[[:space:]]*/, "", line); gsub(/[[:space:]]+$/,"",line)
      gsub(/^"/,"",line); gsub(/"$/,"",line)
      title=line; next
    }
    # Stop scanning at a new top-level key after work_units (defensive).
    END { flush() }
  ' "$BACKLOG"
}

# Depends_on list for a given id (space separated), used by --next.
deps_of() {
  _wid="$1"
  if [ "$USE_YQ" -eq 1 ]; then
    yq -r ".work_units[] | select(.id == \"$_wid\") | (.depends_on // []) | .[]" "$BACKLOG" 2>/dev/null | tr '\n' ' '
    return 0
  fi
  awk -v want="$_wid" '
    /^[[:space:]]*-[[:space:]]+id:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]*-[[:space:]]+id:[[:space:]]*/, "", line); gsub(/[[:space:]]+$/,"",line)
      cur=line
    }
    cur==want && /^[[:space:]]+depends_on:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]+depends_on:[[:space:]]*/, "", line)
      gsub(/[\[\]]/, "", line); gsub(/,/, " ", line); gsub(/[[:space:]]+/, " ", line)
      gsub(/^ /,"",line); gsub(/ $/,"",line)
      print line
    }
  ' "$BACKLOG" | tr '\n' ' '
}

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------
cmd_table() {
  section "BeatzClik backlog — $(basename "$BACKLOG")"
  rows="$(emit_rows)"
  [ -n "$rows" ] || { warn "No work units parsed."; return 0; }

  # Counts.
  total=0; done_n=0
  # Sort by phase then id; group with phase headers.
  printf '%s\n' "$rows" | sort -t"$(printf '\t')" -k1,1 -k2,2 | {
    last_phase=""
    while IFS="$(printf '\t')" read -r phase id status gate title; do
      [ -n "$id" ] || continue
      if [ "$phase" != "$last_phase" ]; then
        printf '\n%sPhase %s%s\n' "$C_BOLD" "$phase" "$C_RESET"
        last_phase="$phase"
      fi
      case "$status" in
        done)        c="$C_GREEN" ;;
        in_progress) c="$C_BLUE" ;;
        in_review)   c="$C_BLUE" ;;
        blocked)     c="$C_RED" ;;
        *)           c="$C_YELLOW" ;;
      esac
      g=""; [ "$gate" = "true" ] && g=" ${C_YELLOW}[human_gate]${C_RESET}"
      printf '  %-10s %s%-12s%s %s%s\n' "$id" "$c" "$status" "$C_RESET" "$title" "$g"
    done
  }

  # Summary counts (recompute outside the subshell-friendly loop).
  total="$(printf '%s\n' "$rows" | grep -c . || true)"
  done_n="$(printf '%s\n' "$rows" | awk -F"\t" '$3=="done"{c++} END{print c+0}')"
  section "Summary"
  say "  $done_n / $total work units done"
}

cmd_next() {
  rows="$(emit_rows)"
  [ -n "$rows" ] || { warn "No work units parsed."; return 0; }

  # Build a lookup of id -> status into a temp file (POSIX-friendly).
  statusmap="$(mktemp 2>/dev/null || echo "${TMPDIR:-/tmp}/pmap.$$")"
  printf '%s\n' "$rows" | awk -F"\t" '{print $2"="$3}' > "$statusmap"
  status_of() { grep -m1 "^$1=" "$statusmap" 2>/dev/null | cut -d= -f2; }

  section "Next READY work unit(s)"
  found=0
  # Iterate phase-ascending, then id, over todo WUs with all deps done.
  printf '%s\n' "$rows" | sort -t"$(printf '\t')" -k1,1 -k2,2 \
  | while IFS="$(printf '\t')" read -r phase id status gate title; do
      [ "$status" = "todo" ] || continue
      deps="$(deps_of "$id")"
      ready=1
      for d in $deps; do
        [ -n "$d" ] || continue
        ds="$(status_of "$d")"
        if [ "$ds" != "done" ]; then ready=0; break; fi
      done
      if [ "$ready" -eq 1 ]; then
        g=""; [ "$gate" = "true" ] && g=" ${C_YELLOW}[human_gate]${C_RESET}"
        printf '  %sP%s%s  %-10s %s%s\n' "$C_GREEN" "$phase" "$C_RESET" "$id" "$title" "$g"
        echo READY >> "$statusmap.flag"
      fi
    done

  if [ -f "$statusmap.flag" ]; then found=1; rm -f "$statusmap.flag"; fi
  rm -f "$statusmap"
  if [ "$found" -eq 0 ]; then
    warn "No READY work units (all todo WUs have unmet dependencies, or none are todo)."
  fi
}

cmd_gates() {
  rows="$(emit_rows)"
  section "Work units with a human_gate"
  any=0
  printf '%s\n' "$rows" | sort -t"$(printf '\t')" -k1,1 -k2,2 \
  | while IFS="$(printf '\t')" read -r phase id status gate title; do
      [ "$gate" = "true" ] || continue
      printf '  %sP%s%s  %-10s %-12s %s\n' "$C_BOLD" "$phase" "$C_RESET" "$id" "$status" "$title"
    done
  # Detect emptiness.
  if ! printf '%s\n' "$rows" | awk -F"\t" '$4=="true"{found=1} END{exit found?0:1}'; then
    warn "No work units carry a human_gate."
  fi
}

cmd_set() {
  _id="$1"; _new="$2"
  is_valid_status "$_new" || die "Invalid status '$_new'. Valid: $VALID_STATUSES"

  # Confirm the id exists.
  if ! emit_rows | awk -F"\t" -v id="$_id" '$2==id{f=1} END{exit f?0:1}'; then
    die "Work unit '$_id' not found in $BACKLOG"
  fi

  tmp="$(mktemp 2>/dev/null || echo "${BACKLOG}.tmp.$$")"

  if [ "$USE_YQ" -eq 1 ]; then
    if yq -i "(.work_units[] | select(.id == \"$_id\") | .status) = \"$_new\"" "$BACKLOG" 2>/dev/null; then
      ok "Set $_id -> $_new (via yq)."
      rm -f "$tmp"
      return 0
    fi
    warn "yq update failed; falling back to awk."
  fi

  # awk: rewrite the status line belonging to the matching WU block only.
  awk -v want="$_id" -v newst="$_new" '
    BEGIN { cur=""; }
    /^[[:space:]]*-[[:space:]]+id:[[:space:]]*/ {
      line=$0; sub(/^[[:space:]]*-[[:space:]]+id:[[:space:]]*/, "", line); gsub(/[[:space:]]+$/,"",line)
      cur=line
      print; next
    }
    {
      if (cur==want && $0 ~ /^[[:space:]]+status:[[:space:]]*/) {
        # Preserve the original leading indentation.
        match($0, /^[[:space:]]+/); indent=substr($0, 1, RLENGTH)
        print indent "status: " newst
        cur=""   # only the first status line in the block is replaced
        next
      }
      print
    }
  ' "$BACKLOG" > "$tmp" || { rm -f "$tmp"; die "awk rewrite failed"; }

  # Sanity: file must be non-empty and still contain the id.
  if [ ! -s "$tmp" ] || ! grep -q "id:[[:space:]]*$_id" "$tmp"; then
    rm -f "$tmp"
    die "Refusing to write: rewritten backlog failed sanity check."
  fi

  mv "$tmp" "$BACKLOG"
  ok "Set $_id -> $_new."
}

# ---------------------------------------------------------------------------
# Argument dispatch
# ---------------------------------------------------------------------------
if [ "$#" -eq 0 ]; then
  cmd_table
  exit 0
fi

case "$1" in
  --next)  cmd_next ;;
  --gates) cmd_gates ;;
  --set)
    shift
    [ "$#" -ge 2 ] || { err "--set requires <WU-ID> <status>"; usage; exit 2; }
    cmd_set "$1" "$2" ;;
  -h|--help) usage ;;
  *) err "Unknown argument: $1"; usage; exit 2 ;;
esac
exit 0
