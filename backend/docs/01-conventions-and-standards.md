# BeatzClik Backend — Conventions & Standards

> Shared rules every module and every agent follows. **PRD source:** §3, §4, §9. Where a module ADD
> is silent, this document governs.

## 1. Package & file layout

Root package `org.shakvilla.beatzmedia`. Per-module layout is fixed (see
`00-system-architecture.md` §4). Shared, cross-module primitives live in
`org.shakvilla.beatzmedia.platform` (the **kernel**) and may be imported by any module's domain.

Kernel contents: `Money`, `Currency`, `IdempotencyKey`, typed ids (`AccountId`, `TrackId`, …),
`Page<T>`/`PageRequest`, `ApiError`/`ErrorCode`, `Clock` port, `IdGenerator` port, `DomainEvent`
marker, `PlatformSettings`/`FeatureFlags` access, and base exceptions.

## 2. Money & numeric handling

- **Storage:** integer **minor units (pesewas)**; column type `BIGINT`, suffix `_minor` (e.g.
  `price_minor`). Never store money as floating point.
- **API surface:** `{ "amount": <decimal cedis>, "currency": "GHS" }` (matches frontend `Money`).
- **Conversion:** exactly at the adapter boundary. `amount_cedis = minor / 100` (2 dp);
  `minor = round_half_up(amount_cedis * 100)`.
- **Arithmetic:** split/fee/discount math is done on minor units with half-up rounding; the sum of
  parts must equal the whole (reconcile remainder to the platform/creator residual). Constants
  (`platformFeePct=30`, `creatorSharePct=70`, `tipFeePct=10`, `bundleDiscountPct=24`,
  `serviceFee=₵0.50`, `minPayout=₵10`) come from `PlatformSettings`, never hard-coded.

```java
public record Money(long minor, Currency currency) {
    public static Money ofCedis(BigDecimal cedis) { /* half-up *100 */ }
    public BigDecimal toCedis() { /* minor/100, scale 2 */ }
    public Money plus(Money other) { /* same-currency guard */ }
    public Money percentage(int pct) { /* half-up */ }
}
```

## 3. Identifiers & timestamps

- **IDs:** opaque strings at the API (`type ID = string`). Internally use typed wrappers
  (`record TrackId(String value)`). Generation via `IdGenerator` (UUIDv7 or ULID for sortability).
- **Order reference:** human-facing `BZ-YYYY-NNNNN` (year + 5 digits), unique, generated server-side.
- **Timestamps:** persist `TIMESTAMPTZ` (UTC); serialize ISO-8601. **Durations** are whole **seconds**
  (`*_sec` columns), never pre-formatted strings (the frontend formats).

## 4. Error model

Uniform envelope (matches `API-CONTRACT.md`):

```json
{ "error": { "code": "STRING_CODE", "message": "human readable", "field": "optional.field" } }
```

- HTTP codes: `400` malformed, `401` unauthenticated, `403` unauthorized/feature-off, `404` not found
  (also used to hide existence of private resources), `409` conflict/illegal transition, `422`
  validation, `429` rate limited (+ `Retry-After`), `500` unexpected, `503` maintenance.
- `code` is a stable `SCREAMING_SNAKE_CASE` string an agent can assert on in tests (e.g.
  `EMAIL_TAKEN`, `KYC_REQUIRED`, `ALREADY_OWNED`, `ILLEGAL_TRANSITION`, `SPLIT_OVER_100`).
- A single Quarkus `ExceptionMapper` per exception family maps domain exceptions → envelope. Domain
  throws framework-free exceptions; the mapper lives in `adapter.in.rest`.
- Never leak stack traces, SQL, or PII in `message`.

## 5. REST conventions

- Base path `/v1`. JSON only, UTF-8. `Authorization: Bearer <jwt>`.
- **Pagination:** `?page=1&size=20` → `{ items, page, size, total }`. Default `size=20`, max `100`.
- **Filtering/sorting:** documented per endpoint (`?q=`, `?status=`, `?type=`, `?range=`, `?sort=`).
- **Idempotency:** money/side-effect POSTs require an `Idempotency-Key` header (or body field per
  contract); same key → same result, no repeated effect. See `cross-cutting/api-and-contract.md`.
- **Validation:** Hibernate Validator on request DTOs; violations → `422` with `error.field`.
- Resources are thin: map DTO → command, call input port, map result → DTO. **No business logic in
  resources.**

## 6. Persistence & migrations

- One Flyway migration set; files `src/main/resources/db/migration/V<n>__<snake_desc>.sql`, forward-only,
  never edited once merged. Repeatable seed `R__seed_dev_data.sql` (dev/test only).
- A module owns its tables; **no cross-module foreign keys** to another module's tables — reference by
  id and resolve via ports. (Within a module, FKs are expected.)
- Naming: `snake_case` tables/columns; PK `id`; FKs `<entity>_id`; money `*_minor`; durations `*_sec`;
  timestamps `*_at`; booleans `is_*`/`has_*`. Add indexes for every documented filter/lookup.
- Each entity maps domain ↔ JPA entity in the persistence adapter; domain objects carry no ORM
  annotations. Transaction boundary = the use case (`@Transactional` on the application service impl).

Full policy in `cross-cutting/data-and-migrations.md`.

## 7. Authorization

- JWT claims: `sub` (account id), `roles` (`fan`/`artist` + admin scopes). Enforce role/scope in the
  REST adapter (annotation/filter) **and** re-check resource ownership in the application layer (a
  creator touches only their releases; a fan only their own cart/collection). Detail in
  `cross-cutting/security-authz.md`.

## 8. Events & idempotency

- Publish domain events after a successful transaction (`AFTER_SUCCESS`). Handlers idempotent and
  side-effect-keyed (e.g. by `orderId`/`providerEventId`). Never pass JPA entities in events; pass ids
  + minimal snapshot.

## 9. Observability & logging

- Structured JSON logs; correlation/trace id on every request; **no PII or secrets** in logs.
- Every module exposes meaningful metrics (counts, latencies, queue depths) via Micrometer and spans
  via OpenTelemetry. Health contributions via SmallRye Health. See `cross-cutting/observability.md`.

## 10. Coding standards

- Java 25, records for value objects/DTOs, sealed types for closed hierarchies, `Optional` for
  nullable returns (never for fields), constructor injection only.
- Immutability by default in domain; no setters on aggregates — mutate through intention-revealing
  methods that enforce invariants.
- Null-safety: validate at boundaries; domain assumes valid inputs.
- Formatting: Spotless (google-java-format) enforced in CI; no warnings policy for `-Werror` on
  project code.
- Each public type carries a one-line Javadoc stating its responsibility.

## 11. Definition of done (global — every work unit)

A work unit is **done** only when all hold (mirrors PRD §8; CI enforces):

1. Unit tests (domain + use cases with fakes) and integration tests (Testcontainers Postgres/MinIO,
   REST-assured) pass.
2. **Contract conformance**: responses validate against the frontend types / `API-CONTRACT.md`
   (OpenAPI contract test green).
3. Flyway migration(s) included, forward-only, apply cleanly on an empty DB.
4. Runs under the Docker Compose stack (`docker compose up` boots healthy).
5. **Hexagonal dependency rule** holds (ArchUnit green).
6. Money/side-effect paths are idempotent; privileged mutations append an `AuditEntry` (INV-10).
7. Coverage ≥ the gate in `sdlc/testing-strategy.md`; Spotless clean; no new high/critical security
   findings.
8. The relevant module ADD is updated in the same PR if behavior changed.
