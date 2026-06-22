---
name: contract-conformance
description: Verify every BeatzClik endpoint's response validates against Frontend/src/types/index.ts and API-CONTRACT.md — inspect /q/openapi, run the contract test suite, confirm the 1:1 mock getX()→endpoint mapping, and check money/duration/timestamp serialization. Use when adding/changing an endpoint or proving a WU's contract DoD.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep
---

# Contract conformance

The hard acceptance gate (PRD §1.5): the frontend runs against the API with its mock `getX()`
functions removed and the rendered UI does not change. That is only provable if every response
validates against the frontend types. Source: `api-and-contract.md §8`.

## 1. Inspect the as-built OpenAPI
Boot the app (`@QuarkusTest`/`@QuarkusIntegrationTest` or `./mvnw quarkus:dev`) and fetch the contract:
```bash
curl -fsS http://localhost:8080/q/openapi?format=json | tee target/openapi.json
```
Confirm the endpoint exists under `/v1`, with the documented verb, params (enum values), and response schema. Diff `target/openapi.json` against the committed baseline — a removed/renamed field, changed type, or removed endpoint is a **breaking change to `/v1`** and is forbidden unless the baseline is intentionally updated with sign-off.

## 2. Run the contract test suite
```bash
./mvnw -B test -Dtest='*ContractTest'
```
Each test hits a real (seeded) endpoint with REST-assured and validates the body against the matching JSON Schema generated from `Frontend/src/types/index.ts` (under `backend/src/test/resources/contract/schema/`):
```java
given().auth().oauth2(fanToken())
  .when().get("/v1/tracks/{id}", seededTrackId)
  .then().statusCode(200)
  .body(matchesJsonSchemaInClasspath("contract/schema/Track.json"));
```
For list/envelope endpoints, validate `{ items, page, size, total }` and each `items[*]` against the element schema. For composite payloads (`/home`, `/studio/analytics`, `/admin/overview`) that don't map cleanly to one TS type, assert against a committed JSON snapshot of the expected shape. Every endpoint in the §10 inventory needs at least one conformance test asserting status code, shape, serialization rules, and — for failure paths — the exact `error.code` from the §2.3 catalog.

## 3. Serialization rules to assert (§4)
- **Money** = `{ amount:number, currency:"GHS" }`; assert no field is a pre-formatted string (regression guard against display-string leakage).
- **Duration** = integer seconds; **Timestamp** = ISO-8601 string; **Counts** = raw integers; **percentages/splits** = numbers, not strings.

## 4. Confirm the 1:1 mock → GET mapping (§15)
For every mock `getX()` in `Frontend/src/lib/*` there must be exactly one GET endpoint returning the same shape (e.g. `getHomeFeed()`→`/v1/home`, `getStoreItem(id)`→`/v1/store/:id`, `getAnalytics(range)`→`/v1/studio/analytics?range=`). Walk the §8.4 table; any `getX()` without a matching, shape-conformant endpoint is a gap to close.

## 5. Keep schemas from drifting
Re-generate TS→JSON schemas (`ts-json-schema-generator`/`typescript-json-schema`) whenever `index.ts` changes; commit them. The CI gate fails on a schema mismatch or an OpenAPI breaking diff. When green, this satisfies the contract line of the DoD — feed it into run-verification-gate.
