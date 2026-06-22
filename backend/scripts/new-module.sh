#!/usr/bin/env bash
# new-module.sh — Scaffold a hexagonal bounded-context package tree.
#
# Creates the standard ports-and-adapters directory layout for a new module
# under org.shakvilla.beatzmedia, in both main and test source roots, and drops
# a package-info.java in the domain package stating the dependency rule
# (00-system-architecture.md §4 / testing-strategy.md §6). Idempotent: existing
# files/dirs are never clobbered.
#
# Works from repo root or backend/.
#
# Usage:
#   scripts/new-module.sh <module> [--help]
#     <module>   lowercase bounded-context name (e.g. payments, catalog).
#     --help     Show this help.

set -euo pipefail
# shellcheck source=_common.sh
. "$(dirname "$0")/_common.sh"

usage() {
  cat <<EOF
new-module.sh — scaffold a hexagonal module package tree.

Usage: scripts/new-module.sh <module> [--help]

Creates under backend/src/{main,test}/java/org/shakvilla/beatzmedia/<module>/:
  domain
  application/port/in
  application/port/out
  adapter/in/rest
  adapter/in/job
  adapter/out/persistence
  adapter/out/integration
plus a domain/package-info.java stating the dependency rule. Idempotent.
EOF
}

MODULE=""
for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    -*) err "Unknown option: $arg"; usage; exit 2 ;;
    *) if [ -z "$MODULE" ]; then MODULE="$arg"; else die "Only one module name expected"; fi ;;
  esac
done

[ -n "$MODULE" ] || { err "Missing <module> argument."; usage; exit 2; }

# Validate the module name: lowercase letters/digits/underscores, must start with a letter.
case "$MODULE" in
  [a-z]*) ;;
  *) die "Module name must start with a lowercase letter: '$MODULE'" ;;
esac
case "$MODULE" in
  *[!a-z0-9_]*) die "Module name may only contain [a-z0-9_]: '$MODULE'" ;;
esac

BASE_PKG="org/shakvilla/beatzmedia"
MAIN_ROOT="$BACKEND_DIR/src/main/java/$BASE_PKG/$MODULE"
TEST_ROOT="$BACKEND_DIR/src/test/java/$BASE_PKG/$MODULE"

SUBDIRS="
domain
application/port/in
application/port/out
adapter/in/rest
adapter/in/job
adapter/out/persistence
adapter/out/integration
"

section "Scaffolding module '$MODULE'"
created=0
for sub in $SUBDIRS; do
  for root in "$MAIN_ROOT" "$TEST_ROOT"; do
    d="$root/$sub"
    if [ -d "$d" ]; then
      :
    else
      mkdir -p "$d"
      created=$(( created + 1 ))
    fi
  done
done

# package-info.java in the domain package — states the dependency rule.
PKG_INFO="$MAIN_ROOT/domain/package-info.java"
if [ -f "$PKG_INFO" ]; then
  warn "domain/package-info.java already exists — left untouched."
else
  cat > "$PKG_INFO" <<EOF
/**
 * Domain layer for the <strong>${MODULE}</strong> bounded context.
 *
 * <p><b>Dependency rule (hexagonal — 00-system-architecture.md §4):</b>
 * the domain is framework-free pure Java. It must NOT depend on Jakarta,
 * Quarkus, Hibernate, REST, the AWS SDK, or any adapter. Dependencies point
 * inward only: {@code adapter -> application -> domain}. The application layer
 * may depend on this package; adapters may not reach past the application
 * ports. Cross-module access is forbidden — talk to another context only
 * through its {@code application.port.in} (ArchUnit enforces this).
 *
 * <p>No wall-clock or random ids in the core: inject the platform {@code Clock}
 * and {@code IdGenerator} ports instead of {@code Instant.now()} /
 * {@code UUID.randomUUID()} (testing-strategy.md §6).
 */
package org.shakvilla.beatzmedia.${MODULE}.domain;
EOF
  created=$(( created + 1 ))
fi

# Print the resulting tree (use `tree` if available, else a portable find).
section "Module tree"
if have tree; then
  ( cd "$BACKEND_DIR" && tree -a --noreport "src/main/java/$BASE_PKG/$MODULE" "src/test/java/$BASE_PKG/$MODULE" )
else
  say "main: src/main/java/$BASE_PKG/$MODULE"
  ( cd "$MAIN_ROOT" && find . -type d | sort | sed 's/^\./  /' )
  [ -f "$PKG_INFO" ] && say "  ./domain/package-info.java"
  say "test: src/test/java/$BASE_PKG/$MODULE"
  ( cd "$TEST_ROOT" && find . -type d | sort | sed 's/^\./  /' )
fi

section "Done"
if [ "$created" -eq 0 ]; then
  ok "Nothing to do — module '$MODULE' already scaffolded (idempotent)."
else
  ok "Created $created new path(s) for module '$MODULE'."
fi
say "Next: add a V<n> migration in its band (scripts/next-migration-version.sh --module $MODULE)"
say "      and the module ADD under backend/docs/architecture/."
exit 0
