---
description: One-time project setup — verify toolchain, ensure the repo/remote, materialize CI + Compose, add Phase 0 Quarkus extensions, produce a baseline green build, and set branch protection.
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

You are bootstrapping the BeatzClik `beatzmedia` backend for autonomous development. Drive this by
delegating to the **devops-engineer** and **tech-lead** agents. Ground every step in
`backend/docs/sdlc/agent-workflow.md`, the root `CLAUDE.md`, and `backend/.project/backlog.yaml`.

Execute in order; stop for a human ONLY if a remote or required secret is genuinely unavailable.

1. **Verify toolchain.** Run `bash backend/scripts/bootstrap.sh`. Confirm: Java 25, the Maven wrapper
   `backend/mvnw`, Docker + Docker Compose, and the `gh` CLI (authenticated). Report each version.
2. **Repo + remote.** Ensure a git repo exists and a GitHub remote is configured. If absent, create the
   repo via `gh repo create` and push `main`. If `gh` is unauthenticated or the org/remote cannot be
   created autonomously, STOP and tell the human EXACTLY what to provide (auth, org name, repo name).
3. **Materialize infra.** If missing, create the `.github/workflows/` CI pipelines (build, unit +
   integration, ArchUnit, contract, migration-validate, Compose smoke, image build/publish, deploy) and
   the root `docker-compose.yml` per `backend/docs/sdlc/ci-cd-github-actions.md` and
   `environments-and-deployment.md`. Keep `deploy.yml` paused pending prod secrets.
4. **Phase 0 extensions.** Add the required Quarkus extensions to `backend/pom.xml` for Phase 0
   foundations (REST/Jackson, Hibernate ORM + Panache, Flyway, JDBC Postgres, SmallRye Health,
   SmallRye OpenAPI, Scheduler, Security/JWT, validator). Reconcile against the platform/media ADDs.
5. **Baseline green build.** Run `./backend/mvnw -q -DskipTests package`, then a passing smoke
   (`bash backend/scripts/smoke.sh`). Iterate until both are green.
6. **Branch protection.** On `main`, require these checks before merge: build, unit+integration,
   ArchUnit, contract test, migration validate, and Compose smoke; require PR review and linear history.
   Apply via `gh api`. If permissions are insufficient, instruct the human precisely.
7. **Report readiness.** Summarize toolchain status, remote URL, materialized files, extensions added,
   build/smoke result, branch-protection checks, and the next command to run (`/build-next-wu`).
