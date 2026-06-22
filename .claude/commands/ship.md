---
description: Cut a release of main and run the release + deploy workflows; pause deploy if prod secrets are missing.
argument-hint: "[version]"
allowed-tools: Bash, Read, Glob, Grep, Task
---

Ship a release of the BeatzClik `beatzmedia` backend. Drive this via the **devops-engineer** agent.
Ground in `backend/docs/sdlc/environments-and-deployment.md`, `ci-cd-github-actions.md`,
`branching-and-pr.md`, and root `CLAUDE.md`.

1. **Preconditions.** Confirm `main` is green (latest CI passing) and the working tree is clean. If
   `main` is red, stop and report.
2. **Version.** Use SemVer `$1` if provided; otherwise auto-derive the next version from the latest tag
   and the Conventional Commits since it (patch/minor/major). Create and push the `v<version>` tag.
3. **Release + build.** Trigger the release workflow and the image build/publish workflow. Always
   build and publish the container image for `v<version>` to the registry.
4. **Deploy (gated).** Run the deploy workflow IF the required production GitHub Environment secrets are
   present. If ANY are absent, PAUSE the deploy after publishing the image and tell the human EXACTLY
   which Environment secrets to add (payment provider prod keys, DB/infra credentials, registry/host
   creds) and where. Do not deploy with missing secrets.
5. **Verify + rollback note.** After a successful deploy, confirm health (`/q/health/ready` green) and
   smoke the key endpoints. Document rollback: redeploy the previous tag (`v<previous>`). Report the
   version shipped, image ref, deploy state (deployed or paused-pending-secrets), and health result.
