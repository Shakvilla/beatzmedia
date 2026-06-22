---
name: Bug report
about: Report a defect in the BeatzClik backend
title: "bug: <short description>"
labels: ["type:fix"]
assignees: []
---

## Summary
<!-- One or two sentences: what is wrong. -->

## Affected area
- **Module(s):** <identity | catalog | payments | commerce | platform | …>
- **Related WU / LLFR (if known):** WU-XXX-N · LLFR-XXX-NN.M
- **Endpoint / component:** <e.g. POST /v1/payments/intents>

## Steps to reproduce
1.
2.
3.

## Expected behavior
<!-- What should have happened (cite the contract/invariant if relevant). -->

## Actual behavior
<!-- What actually happened. Include the error envelope `code` and message if any. -->

## Evidence
<!-- Logs (redact secrets/PII), stack traces, request/response, screenshots. -->
```
<paste here>
```

## Environment
- **Version / image tag:** <e.g. ghcr.io/.../beatzmedia:1.2.3 or commit sha>
- **Environment:** <local | staging | production>
- **DB / dependencies:** <PostgreSQL 16, MinIO, …>

## Impact / severity
- [ ] Data integrity / money path (INV-*) — treat as high
- [ ] Security / auth
- [ ] Functional defect
- [ ] Cosmetic / minor
