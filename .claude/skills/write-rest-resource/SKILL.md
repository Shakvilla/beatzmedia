---
name: write-rest-resource
description: Author a thin Quarkus REST resource (quarkus-rest) that maps HTTP to an application input port — DTO records, validation, the uniform error envelope, pagination, money/duration serialization, auth/scope, idempotency, and OpenAPI annotations. Use whenever a WU exposes or changes a /v1 endpoint.
allowed-tools: Read, Write, Edit, Glob, Grep
---

# Write a REST resource

Resources are **thin**: map DTO → command, call the input port, map result → DTO. **No business logic
in resources.** Governing doc: `backend/docs/cross-cutting/api-and-contract.md`.

## 1. Locate the contract
Find the endpoint in `api-and-contract.md §10` and `API-CONTRACT.md`; confirm the response shape against the matching interface in `Frontend/src/types/index.ts`. Do not invent endpoints/fields the UI does not need. Where ADD and contract disagree, the contract + TS types win for the API surface.

## 2. DTOs (records)
Request and response DTOs are Java records in `adapter/in/rest/dto/`, matching the TS type field names exactly. Map DTO ↔ domain in the resource/mapper — never expose domain aggregates or JPA entities. Serialization rules (§4):
- **Money** → `{ "amount": <decimal cedis>, "currency": "GHS" }`. Convert at this boundary only: `amount = minor/100` (2dp), `minor = round_half_up(amount×100)`. Never a float in storage, never a `"₵2.50"` string on the wire.
- **Duration** → plain integer seconds (`duration: 213`). **Timestamp** → ISO-8601 string. **Counts** → raw integers. Never pre-format display strings — the frontend formats.

## 3. Validation
Hibernate Validator annotations on request DTOs (`@NotBlank`, `@Min`, `@Email`, …). Violations → `422` with `error.field` (dotted path). Domain-level checks throw framework-free domain exceptions.

## 4. Error envelope
All non-2xx use the uniform envelope `{ "error": { "code", "message", "field?" } }`. The domain throws framework-free exceptions carrying an `ErrorCode`; one shared `ExceptionMapper` family in `platform`/`adapter.in.rest` maps them to the declared HTTP status. New failure mode → add an `ErrorCode` enum value + a catalog row (api-and-contract §2.3) in the same PR; route through the single mapper. Never leak stack traces/SQL/PII.

## 5. Status codes, pagination, filters
- Verbs/status per §1.1: `201` create, `202` async settlement, `204` no-body, idempotent `PUT`/`DELETE` toggles, action sub-paths (`POST .../suspend`).
- Lists documented with `?page=&size=` use the envelope `{ items, page, size, total }` (default size 20, max 100, clamp silently); map domain `Page<T>`. Endpoints the contract returns as a bare array stay bare arrays.
- Filters/sort: `?q= ?status= ?type= ?range= ?sort= ?filter=`. Unknown enum value → `422 VALIDATION_FAILED` naming the param.

## 6. Auth & scope
`Authorization: Bearer <jwt>` (`sub` + `roles`). Annotate the role/scope in the resource (studio = `artist`, admin = the specific scope) **and** re-check resource ownership in the application layer. Private resources hide existence with `404`, not `403`.

## 7. Idempotency on money POSTs
Money / irreversible side-effect POSTs (`/checkout`, `/studio/payouts/withdraw`, `/podcasts/:id/tip`, finance payouts/refunds) require an `Idempotency-Key` header → missing = `400 IDEMPOTENCY_KEY_REQUIRED`; same key+body replays the stored result (effect at most once); same key+different body = `409 IDEMPOTENCY_KEY_REUSED`. Webhooks dedupe on provider event id instead.

## 8. OpenAPI annotations
Annotate with `@Operation`, `@APIResponse(s)` (response schema), `@Parameter` (document accepted enum values), `@RequestBody`, `@Tag` (module), `@SecurityRequirement` (Bearer) on protected endpoints; `@Schema` on DTOs for examples/defaults. The spec serves at `/q/openapi`.

## 9. Finish
Write the contract test (see contract-conformance), update the §10 inventory if the path/verb set changed, then run run-verification-gate.
