# Architecture Design Doc — `payments` (`Payments & Payouts`)

> **Status:** Proposal (largely PROPOSAL per PRD §6.6) · **PRD source:** `BACKEND-PRD.md` §6.6, §9.2 ·
> **Owning context:** `payments` · **Package root:** `org.shakvilla.beatzmedia.payments`
>
> This ADD is consumed by Claude Code agents. It is the design contract for the most critical money
> module: an agent reads it, plans the listed work units, implements within the stated ports/adapters,
> writes the tests, and opens a PR. Do not invent endpoints or fields not traceable to the PRD /
> `API-CONTRACT.md`. Every monetary movement is double-entry and balanced (INV-6); ownership is granted
> only on settlement (INV-1) and revoked on refund (INV-9).

## 1. Purpose & responsibilities

The `payments` module owns the movement of money through BeatzClik end-to-end: **payment intents** and
provider integration (MTN MoMo, Telecel/Vodafone, AirtelTigo, card, bank), **asynchronous
webhooks/callbacks** with signature verification, **idempotency** on every money-moving operation, a
**timeout poll** and **daily reconciliation** that guarantee provider↔ledger convergence, a
**double-entry ledger** with derived **creator balance accrual** (70/30 sales & royalties, 90/10 tips —
percentages from `PlatformSettings`, INV-4), **payout methods**, **KYC-gated withdrawals**, **payout
batches** (weekly Friday run + single send), **refunds / chargebacks / disputes** with ownership
revocation and ledger clawback (INV-9), and **tips** (90/10). It explicitly does **not** own ownership
grants, orders, carts, catalog, KYC document capture, or notification delivery — it *requests* grant
revocation from `commerce`/`catalog` via output ports, *consumes* KYC verification state from `identity`
via `KycProvider`, and publishes domain events for other modules to react to. Persistence is private to
this module; cross-module references are by id only. Surfaces served: **Studio** (creator payouts),
**Admin** (finance/ledger/disputes), and the public **webhook receiver**.

**HLFRs covered:** PAYMENTS-01 (charging & async webhooks & timeout & reconciliation), PAYMENTS-02
(double-entry ledger & creator balance), PAYMENTS-03 (payout methods, withdrawals, KYC gating, payout
runs), PAYMENTS-04 (refunds/chargebacks/disputes), PAYMENTS-05 (tips). Work units **WU-PAY-1..5**.

## 2. Context & dependencies (C4 component view)

```mermaid
flowchart LR
  subgraph payments["payments module"]
    IN["Inbound REST adapters\n(webhook receiver, studio payouts,\nadmin finance)"]
    APP["Application use cases\n(InitiateCharge, HandleProviderWebhook,\nReconcile, RequestWithdrawal,\nRunWeeklyPayouts, RefundDispute, IssueTip ...)"]
    DOM["Domain\n(PaymentIntent, Ledger, CreatorBalance,\nWithdrawal, PayoutBatch, Dispute)"]
    SCHED["Schedulers\n(timeout poll, weekly-Friday,\ndaily reconciliation)"]
    subgraph OUT["Outbound adapters"]
      PG["PaymentGateway port"]
      MTN["MoMoMtnAdapter"]
      TEL["MoMoTelecelAdapter"]
      ATG["MoMoAirtelTigoAdapter"]
      CARD["CardAdapter"]
      BANK["BankAdapter"]
      LEDGERR["LedgerRepository"]
      PAYR["PayoutRepository"]
      KYCP["KycProvider"]
      EVP["EventPublisher"]
    end
  end
  IN --> APP --> DOM
  SCHED --> APP
  APP --> PG
  PG --> MTN & TEL & ATG & CARD & BANK
  MTN & TEL & ATG --> PROV[("MoMo provider APIs")]
  CARD --> CARDP[("Card PSP")]
  BANK --> BANKP[("Bank / payout rail")]
  APP --> LEDGERR --> DB[("Postgres (payments schema)")]
  APP --> PAYR --> DB
  APP --> KYCP -->|verification state| IDN["identity module (port)"]
  APP --> EVP -->|domain events| BUS["in-process event bus"]
  BUS --> COM["commerce/catalog\n(revoke OwnershipGrant on refund)"]
```

**Dependency rule.** The domain is framework-free; the application layer depends only on input/output
**ports**; inbound and outbound adapters depend inward. The module never imports another module's
persistence. It **calls out** only through ports: `KycProvider` (resolves to the `identity` module's KYC
read port) and `EventPublisher`. It **publishes** `PaymentSettled`, `PaymentFailed`, `TipReceived`,
`WithdrawalRequested`, `PayoutSent`, `DisputeOpened`, `OrderRefunded`; `commerce` consumes
`PaymentSettled` (to grant ownership, INV-1) and `OrderRefunded` (to revoke grants, INV-9). The provider
adapters sit behind one `PaymentGateway` port selected per `provider`/method kind, so the application
never knows which rail it is talking to.

## 3. Domain model

| Name | Kind | Key fields | Notes |
|---|---|---|---|
| `PaymentIntent` | Aggregate root | `id`, `orderRef`, `amount` (Money), `provider`, `providerRef`, `status`, `idempotencyKey` | Lifecycle pending→settled/failed/timeout (INV-1). |
| `PaymentEvent` | Entity (child) | `id`, `intentId`, `providerEventId`, `type`, `payload`, `receivedAt` | Idempotent on `providerEventId` (UNIQUE). |
| `LedgerAccount` | Entity | `id`, `kind`, `ownerAccountId?` | Kinds: `provider_clearing`, `creator_payable`, `platform_revenue`, `payout_clearing`. |
| `LedgerEntry` | Value object (within a txn) | `id`, `txnId`, `accountId`, `direction` (DEBIT/CREDIT), `amount` (Money), `refType`, `refId`, `clearedAt?`, `postedAt` | A `txnId` groups balanced rows (INV-6). |
| `CreatorBalance` | Aggregate (read-model projection) | `accountId`, `availableMinor`, `pendingMinor`, `lifetimeMinor` | Derived from cleared ledger entries (INV-6/INV-8). |
| `PayoutMethod` | Aggregate | `id`, `accountId`, `kind` (momo/bank), `label`, `detail`, `isDefault` | Exactly one default per account. |
| `WithdrawalRequest` | Aggregate root | `id`, `accountId`, `amount`, `fee`, `methodId`, `status`, `requestedAt` | KYC-gated, floor-gated (INV-8). |
| `PayoutBatch` | Aggregate root | `id`, `kind` (weekly/single), `runBy`, `status`, `totalMinor`, `runAt` | Groups paid `PayoutTxn`. |
| `PayoutTxn` | Entity (child of batch) | `id`, `batchId`, `withdrawalId`, `accountId`, `amount`, `status`, `providerRef` | One per executed withdrawal. |
| `KycRecord` | Value object (read via port) | `accountId`, `status`, `verifiedAt?` | Resolved from `identity`; mirrored read-only here. |
| `Dispute` | Aggregate root | `id`, `orderId`, `kind`, `status`, `amount`, `openedAt` | Lifecycle open→refunded/rejected/escalated. |
| `DisputeEvent` | Entity (child) | `id`, `disputeId`, `text`, `actor`, `at` | Timeline. |
| `Refund` | Entity | `id`, `disputeId`, `amount`, `at` | Triggers clawback + revoke (INV-9). |

**Enums** (lifted verbatim from frontend / PRD §3.2):

- `PaymentIntentStatus` = `pending | settled | failed | timeout`.
- `Provider` = `mtn | telecel | airteltigo | card | bank`.
- `LedgerAccountKind` = `provider_clearing | creator_payable | platform_revenue | payout_clearing`.
- `Direction` = `DEBIT | CREDIT`.
- `PayoutType` (frontend `studio-payouts.ts`) = `Sale | Royalty | Tip | Cash-out`.
- `PayoutStatus` = `cleared | paid | pending`.
- `MethodKind` = `momo | bank`.
- `LedgerType` (frontend `admin-data.ts`) = `Sale | Royalty | Tip | Payout | Refund | Fee`.
- `KycStatus` = `none | pending | verified | rejected`.
- `WithdrawalStatus` = `pending | ready | paid | failed | kyc_pending`.
- `DisputeStatus` = `open | refunded | rejected | escalated`.

**Invariants enforced here:**

- **INV-1 (ownership-on-payment).** No value (ownership grant) is requested before a `PaymentIntent`
  reaches `settled`; `PaymentSettled` is the only trigger for grant creation downstream.
- **INV-4 (revenue split).** Every settled sale/royalty credits creator 70% / platform 30%; tips 90% /
  10%. Percentages from `PlatformSettings.platformFeePct` / `tipFeePct` (OQ-2), never hard-coded.
- **INV-6 (ledger balance).** For every `txnId`, Σ DEBIT amounts = Σ CREDIT amounts (enforced in domain
  + DB check). Creator withdrawable balance = cleared `creator_payable` credits − cleared cash-outs.
- **INV-8 (withdrawal floor & KYC).** A withdrawal requires `amount ≥ MIN_PAYOUT (₵10)`, `amount ≤`
  cleared available balance, and `KycRecord.status == verified`.
- **INV-9 (refund revokes ownership).** A completed refund reverses the originating ledger entries
  (clawback of creator credit + platform fee) and emits `OrderRefunded` so grants are revoked.
- **INV-11 (money precision).** Money stored as integer minor units; cedis decimals only at the boundary.
- **INV-12 (split sum).** Multi-creator splits subdivide the creator share per `split_entry`, summing
  ≤ 100% of the creator pool; the originating creator holds the remainder.

```mermaid
erDiagram
  payment_intent ||--o{ payment_event : "has"
  payment_intent ||--o{ ledger_entry : "ref_type=intent"
  ledger_account ||--o{ ledger_entry : "posts to"
  ledger_entry }o--|| ledger_txn_group : "grouped by txn_id"
  creator_balance ||--o{ ledger_entry : "derived from cleared"
  payout_method ||--o{ withdrawal_request : "paid via"
  withdrawal_request ||--o| payout_txn : "executed as"
  payout_batch ||--o{ payout_txn : "contains"
  kyc_record ||--o{ withdrawal_request : "gates"
  dispute ||--o{ dispute_event : "timeline"
  dispute ||--o| refund : "resolved by"
  refund ||--o{ ledger_entry : "reverses (clawback)"

  payment_intent {
    uuid id PK
    string order_ref
    bigint amount_minor
    string provider
    string provider_ref
    string status
    string idempotency_key UK
    timestamptz created_at
  }
  payment_event {
    uuid id PK
    uuid intent_id FK
    string provider_event_id UK
    string type
    jsonb payload
    timestamptz received_at
  }
  ledger_account {
    uuid id PK
    string kind
    string owner_account_id
  }
  ledger_entry {
    uuid id PK
    uuid txn_id
    uuid account_id FK
    string direction
    bigint amount_minor
    string ref_type
    string ref_id
    timestamptz cleared_at
    timestamptz posted_at
  }
  creator_balance {
    string account_id PK
    bigint available_minor
    bigint pending_minor
    bigint lifetime_minor
  }
  payout_method {
    uuid id PK
    string account_id
    string kind
    string label
    string detail
    boolean is_default
  }
  withdrawal_request {
    uuid id PK
    string account_id
    bigint amount_minor
    bigint fee_minor
    uuid method_id FK
    string status
    string idempotency_key UK
    timestamptz requested_at
  }
  payout_batch {
    uuid id PK
    string kind
    string run_by
    string status
    bigint total_minor
    timestamptz run_at
  }
  payout_txn {
    uuid id PK
    uuid batch_id FK
    uuid withdrawal_id FK
    string account_id
    bigint amount_minor
    string status
    string provider_ref
  }
  kyc_record {
    string account_id PK
    string status
    timestamptz verified_at
  }
  dispute {
    uuid id PK
    string order_id
    string kind
    string status
    bigint amount_minor
    timestamptz opened_at
  }
  dispute_event {
    uuid id PK
    uuid dispute_id FK
    string text
    string actor
    timestamptz at
  }
  refund {
    uuid id PK
    uuid dispute_id FK
    bigint amount_minor
    timestamptz at
  }
```

### Double-entry ledger design

Accounts are partitioned by **kind**. Money flows in via `provider_clearing` (what the provider owes
us, cleared on settlement), accrues to `creator_payable` (per-creator) and `platform_revenue`, and flows
out via `payout_clearing` (cash-outs to creators).

| Account kind | Owner | Normal balance | Role |
|---|---|---|---|
| `provider_clearing` | — (one per provider) | debit | Funds in transit from the rail; cleared on settlement. |
| `creator_payable` | creator account | credit | What the platform owes the creator. |
| `platform_revenue` | — (singleton) | credit | Platform's fee take. |
| `payout_clearing` | — | debit | Funds reserved/in-flight for withdrawals. |

**Example postings (each row balanced per `txn_id`, INV-6):**

*₵10 sale settled* (creator 70%, platform 30%):

| txn_id | account | direction | amount_minor | ref |
|---|---|---|---|---|
| T1 | provider_clearing | DEBIT | 1000 | intent |
| T1 | creator_payable (creator) | CREDIT | 700 | intent |
| T1 | platform_revenue | CREDIT | 300 | intent |

Σdebits = 1000 = Σcredits. Result: creator +₵7.00, platform +₵3.00.

*₵10 tip settled* (creator 90%, platform 10% — OQ-2 `tipFeePct=10`):

| txn_id | account | direction | amount_minor | ref |
|---|---|---|---|---|
| T2 | provider_clearing | DEBIT | 1000 | tip |
| T2 | creator_payable (creator) | CREDIT | 900 | tip |
| T2 | platform_revenue | CREDIT | 100 | tip |

Result: creator +₵9.00, platform +₵1.00.

*Refund reversal of the ₵10 sale* (clawback, INV-9) — every original row reversed under a new `txn_id`:

| txn_id | account | direction | amount_minor | ref |
|---|---|---|---|---|
| T3 | provider_clearing | CREDIT | 1000 | refund |
| T3 | creator_payable (creator) | DEBIT | 700 | refund |
| T3 | platform_revenue | DEBIT | 300 | refund |

Σdebits = 1000 = Σcredits. Creator credit clawed back ₵7.00, platform fee reversed ₵3.00; emits
`OrderRefunded` → grant revoked.

**Multi-creator splits (INV-12):** the 700 creator credit is subdivided per `split_entry` (e.g. 60/40 →
420 / 280) with the remainder reconciled to the originating creator so the sub-rows still sum to 700.

**Available balance derivation (INV-8):**
`available_minor = Σ(cleared creator_payable CREDIT for account) − Σ(cleared cash-out DEBIT for account)`;
`pending_minor = Σ(uncleared creator_payable CREDIT)`. `creator_balance` is a projection refreshed on
each posting/clear inside the same transaction.

## 4. Application layer (ports)

### 4.1 Input ports (use cases)

```java
public interface InitiateCharge {
    // WU-PAY-1 returns a PaymentIntentView (read-model DTO), not the aggregate, so no domain type
    // leaks across the port boundary; the aggregate stays inside the application layer. The acting
    // AccountId is threaded in so the AuditEntry records WHO acted (INV-10) and the intent is bound
    // to the authenticated caller. Order/cart-ownership authz (orderRef+amount belong to the
    // caller's own pending order) is NOT done here — the order table lands in WU-COM-2 and the
    // intended caller is the commerce checkout orchestration, which owns that check (§8a).
    PaymentIntentView charge(AccountId actor, OrderRef orderRef, Money amount, PaymentMethodRef method, IdempotencyKey key);
}
public interface HandleProviderWebhook {
    WebhookResult handle(Provider provider, String signature, byte[] rawBody);
}
public interface Reconcile {
    void pollPendingTimeouts(Duration olderThan, Duration maxWindow); // timeout/retry
    ReconciliationReport reconcileDaily(LocalDate day);               // provider vs ledger
}
public interface GetPayouts {
    PayoutsView get(AccountId creator);
}
public interface RequestWithdrawal {
    WithdrawalRequest request(AccountId creator, WithdrawCommand cmd, IdempotencyKey key);
}
public interface AddPayoutMethod {
    PayoutMethod add(AccountId creator, AddPayoutMethodCommand cmd);
}
public interface RemovePayoutMethod {
    void remove(AccountId creator, PayoutMethodId id);
}
public interface SetDefaultPayoutMethod {
    PayoutMethod setDefault(AccountId creator, PayoutMethodId id);
}
public interface RunWeeklyPayouts {
    PayoutBatch runWeekly(AdminId actor, IdempotencyKey key);
}
public interface SendSinglePayout {
    PayoutTxn send(AdminId actor, WithdrawalId id, IdempotencyKey key);
}
public interface GetLedger {
    Page<LedgerEntryView> list(LedgerType type, String q, PageRequest page);
}
public interface GetDispute {
    DisputeDetail get(DisputeId id);
}
public interface RefundDispute {
    Refund refund(AdminId actor, DisputeId id, RefundCommand cmd, IdempotencyKey key);
}
public interface RejectDispute {
    Dispute reject(AdminId actor, DisputeId id, String reason);
}
public interface EscalateDispute {
    Dispute escalate(AdminId actor, DisputeId id);
}
public interface IssueTip {
    PaymentIntent tip(AccountId fan, AccountId creator, Money amount, PaymentMethodRef method, IdempotencyKey key);
}
```

**Commands (records):**

```java
public record WithdrawCommand(Money amount, PayoutMethodId methodId) {}
public record AddPayoutMethodCommand(String label, String detail, MethodKind kind) {}
public record RefundCommand(Optional<Money> amount, String reason) {}
public record PaymentMethodRef(Provider provider, MethodKind kind, String token) {}
```

| Use case | Trigger | Auth | Idempotency | Emits | LLFR |
|---|---|---|---|---|---|
| `InitiateCharge` | commerce checkout | internal (fan via commerce) | `idempotencyKey` → same intent | — | 01.1 |
| `HandleProviderWebhook` | provider POST | public, signature-verified | `providerEventId` UNIQUE | `PaymentSettled`/`PaymentFailed` | 01.2 |
| `Reconcile` (poll) | scheduler | system | safe re-run | `PaymentSettled`/`PaymentFailed` | 01.3 |
| `Reconcile` (daily) | scheduler | system | safe re-run | discrepancy AttentionItem | 01.4 |
| `GetPayouts` | studio read | artist (own) | n/a | — | 02.2 |
| `GetLedger` | admin read | finance/super-admin | n/a | — | 02.3 |
| `RequestWithdrawal` | studio | artist (own) | `idempotencyKey` | `WithdrawalRequested` | 03.2 |
| `AddPayoutMethod`/`Remove`/`SetDefault` | studio | artist (own) | n/a | — | 03.1 |
| `RunWeeklyPayouts` | admin | finance/super-admin | `idempotencyKey` | `PayoutSent` (per txn) | 03.3 |
| `SendSinglePayout` | admin | finance/super-admin | `idempotencyKey` | `PayoutSent` | 03.4 |
| `GetDispute` | admin read | finance/super-admin | n/a | — | 04.1 |
| `RefundDispute` | admin | finance/super-admin | `idempotencyKey` | `OrderRefunded` | 04.2 |
| `RejectDispute`/`EscalateDispute` | admin | finance/super-admin | n/a | — | 04.3 |
| `IssueTip` | commerce/podcast | fan | `idempotencyKey` | `TipReceived`→`PaymentSettled` | 05 / 02.1 |

### 4.2 Output ports

```java
public interface PaymentGateway {           // selected per provider/method kind
    ChargeHandle initiate(Provider provider, OrderRef ref, Money amount, PaymentMethodRef method);
    ProviderStatus queryStatus(Provider provider, String providerRef);   // timeout poll
    boolean verifySignature(Provider provider, String signature, byte[] rawBody);
    PayoutHandle disburse(Provider provider, AccountId creator, Money amount, PayoutMethod method);
    interface ProviderClient {               // per-provider sub-interface (one impl each)
        ChargeHandle initiate(OrderRef ref, Money amount, PaymentMethodRef method);
        ProviderStatus queryStatus(String providerRef);
        boolean verifySignature(String signature, byte[] rawBody);
        PayoutHandle disburse(AccountId creator, Money amount, PayoutMethod method);
    }
}
public interface LedgerRepository {
    void postBalanced(TxnId txn, List<LedgerEntry> entries);   // throws if Σdebits != Σcredits
    void clear(TxnId txn, Instant at);
    CreatorBalance balanceOf(AccountId creator);
    Page<LedgerEntry> find(LedgerType type, String q, PageRequest page);
}
// WU-PAY-1 implements payment-intent persistence behind PaymentRepository (below); the remaining
// methods (methods/withdrawals/batches/disputes) stay under PayoutRepository as later WUs land them.
public interface PaymentRepository {                           // WU-PAY-1
    void lockForIdempotencyKey(IdempotencyKey key);            // txn-scoped advisory lock (concurrency)
    PaymentIntent save(PaymentIntent intent);
    Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key);
}
public interface PayoutRepository {
    boolean recordEvent(PaymentEvent event);                   // false if providerEventId seen
    PayoutMethod saveMethod(PayoutMethod m);
    WithdrawalRequest saveWithdrawal(WithdrawalRequest w);
    List<WithdrawalRequest> findReadyWithdrawals();
    PayoutBatch saveBatch(PayoutBatch b);
    Dispute saveDispute(Dispute d);
    Refund saveRefund(Refund r);
}
public interface KycProvider {
    KycStatus statusOf(AccountId creator);
}
public interface EventPublisher {
    void publish(DomainEvent event);   // AFTER_SUCCESS
}
public interface Clock { Instant now(); }
public interface IdGenerator { String newId(); }
```

| Output port | Implementing outbound adapter |
|---|---|
| `PaymentGateway` + per-provider `ProviderClient` | `MoMoMtnAdapter`, `MoMoTelecelAdapter`, `MoMoAirtelTigoAdapter`, `CardAdapter`, `BankAdapter` (REST clients). |
| `LedgerRepository` | JPA ledger adapter with per-`txn_id` balance assertion + DB check. |
| `PayoutRepository` | JPA persistence adapter (intents, methods, withdrawals, batches, disputes, refunds). |
| `KycProvider` | Calls `identity` module KYC read port. |
| `EventPublisher` | In-process event bus (`AFTER_SUCCESS`). |
| `Clock` / `IdGenerator` | Kernel adapters (UTC clock; UUIDv7/ULID). |

## 5. Adapters

### 5.1 Inbound — REST resources

| Method | Path | Auth/scope | Request DTO | Response DTO | Success | Error codes | LLFR |
|---|---|---|---|---|---|---|---|
| POST | `/v1/payments/intents` | authenticated (fan / internal commerce); `Idempotency-Key` | `{ orderRef, amount: Money, provider, methodKind, paymentToken }` | `PaymentIntent` | 200 | 400 `MISSING_IDEMPOTENCY_KEY`, 409 `IDEMPOTENCY_KEY_CONFLICT`, 422 `VALIDATION`, 502 `PROVIDER_ERROR` | 01.1 |
| POST | `/v1/payments/webhooks/:provider` | public, **signature-verified** (`BEATZ_PAYMENT_WEBHOOK_SECRET`) | raw provider payload + signature header | — | 200 (handled), 202 (unknown ref, ignored) | 401 invalid signature | 01.2 |

> **WU-PAY-1 note.** `/v1/payments/intents` is the inbound surface for `InitiateCharge`. It is an
> internal money endpoint the commerce checkout (WU-COM-2) drives; it is exposed directly so the
> idempotency contract (§9.2) is independently testable. Every money POST **must** carry an
> `Idempotency-Key` header: same key + same request → the same intent (no second provider charge);
> same key + different request → `409 IDEMPOTENCY_KEY_CONFLICT` (matched via a SHA-256
> `request_fingerprint` over `orderRef|amount_minor|currency|provider|method_kind`, excluding the raw
> payment token so a token re-issue does not spuriously conflict). Decimal cedis → minor units
> conversion happens only at this boundary (INV-11).
>
> The resource is `@Authenticated`; the JWT subject is bound to the intent as `account_id` and used as
> the `AuditEntry` actor (INV-10 — the audit records WHO acted). **Order/cart-ownership authorization**
> (verifying `orderRef` + `amount` belong to the caller's own pending order) is **not** performed here —
> the order table lands in WU-COM-2, and per §8(a) the intended caller of `InitiateCharge` is the
> commerce **checkout** orchestration, which owns that check before calling in. Concurrent same-key
> requests are serialised by a transaction-scoped advisory lock (see §9 Idempotency) so a race cannot
> double-charge the provider or surface a 500.
| GET | `/v1/studio/payouts` | artist (own) | — | `Payouts` | 200 | 401, 403 | 02.2 |
| POST | `/v1/studio/payouts/withdraw` | artist (own); `Idempotency-Key` | `{ amount, methodId }` | `WithdrawalRequest` `{ status, fee, arrival }` | 202 | 422 `BELOW_MIN_PAYOUT`, 409 `INSUFFICIENT_BALANCE`, 403 `KYC_REQUIRED` | 03.2 |
| POST | `/v1/studio/payout-methods` | artist (own) | `{ label, detail, kind }` | `PayoutMethod` | 201 | 422 | 03.1 |
| DELETE | `/v1/studio/payout-methods/:id` | artist (own) | — | — | 204 | 409 (in-flight withdrawal), 404 | 03.1 |
| PATCH | `/v1/studio/payout-methods/:id/default` | artist (own) | — | `PayoutMethod` | 200 | 404 | 03.1 |
| GET | `/v1/admin/finance` | finance/super-admin | `?range=` | `Finance` | 200 | 403 | 05.* (read) |
| POST | `/v1/admin/finance/payouts/run-weekly` | finance/super-admin; `Idempotency-Key` | — | `PayoutBatch` | 200 | 403 | 03.3 |
| POST | `/v1/admin/finance/payouts/:id/send` | finance/super-admin; `Idempotency-Key` | — | `PayoutTxn` | 200 | 409 `KYC_BLOCKED`, 403, 404 | 03.4 |
| GET | `/v1/admin/finance/ledger` | finance/super-admin | `?type=&q=&page=` | `Page<LedgerEntry>` | 200 | 403 | 02.3 |
| GET | `/v1/admin/finance/disputes/:id` | finance/super-admin | — | `Dispute` + timeline | 200 | 403, 404 | 04.1 |
| POST | `/v1/admin/finance/disputes/:id/refund` | finance/super-admin; `Idempotency-Key` | `{ amount?, reason }` | `Dispute` | 200 | 409 `ILLEGAL_TRANSITION`, 403 | 04.2 |
| POST | `/v1/admin/finance/disputes/:id/reject` | finance/super-admin | `{ reason }` | `Dispute` | 200 | 409, 403 | 04.3 |
| POST | `/v1/admin/finance/disputes/:id/escalate` | finance/super-admin | — | `Dispute` | 200 | 409, 403 | 04.3 |

Scopes per PRD §14: **super-admin** = all; **finance** = payouts/ledger/disputes. Resources are thin
(DTO → command → input port → DTO); no business logic in resources.

### 5.2 Outbound — persistence & integrations

The JPA persistence adapter maps domain ↔ entity (domain carries no ORM annotations). The
`LedgerRepository` impl asserts Σdebits = Σcredits before flushing each `txn_id` and relies on a DB
constraint as a backstop. External clients: five `ProviderClient` REST adapters (MTN, Telecel,
AirtelTigo, card PSP, bank rail) behind `PaymentGateway`, each implementing initiate/queryStatus/
verifySignature/disburse. Transaction boundary = the use case (`@Transactional` on the application
service impl); events publish `AFTER_SUCCESS`. Webhook bodies are read **raw** (pre-deserialization) so
the signature is verified over the exact bytes.

## 6. DTOs & API shapes

Money is `{ amount: <decimal cedis>, currency: "GHS" }`; timestamps ISO-8601. Traceable to
`Frontend/src/lib/studio-payouts.ts` and `admin-data.ts`.

- **PaymentIntent** `{ id, orderRef, amount: Money, provider, providerRef, status: pending|settled|failed|timeout, createdAt }`
- **Payouts** `{ available, pending, thisMonth, thisMonthDelta, lifetime, since, earnings: { label, value }[], bySource: { sales, royalties, tips }, methods: PayoutMethod[], transactions: PayoutTxn[] }`
- **PayoutTxn** `{ id, date, source, type: Sale|Royalty|Tip|Cash-out, gross: number|null, net, status: cleared|paid|pending }`
- **PayoutMethod** `{ id, label, detail, kind: momo|bank, isDefault }`
- **WithdrawalRequest** `{ id, amount: Money, fee: Money, arrival: string, status: pending|ready|paid|failed }` (`fee` per `withdrawalFee(kind, amount)`; `arrival` per `arrivalTime(kind)`)
- **LedgerEntry** `{ id, date, type: Sale|Royalty|Tip|Payout|Refund|Fee, party, ref, amount: number }`
- **Dispute** `{ id, kind, subject, detail, amount?, opened?, status: open|refunded|rejected|escalated, timeline: { id, text, time }[] }`
- **Finance** `{ kpis: { gmvMtd, gmvDelta, platformFee, feeTakePct, payoutsDue, payoutsArtists, momoFloat }, pendingPayouts: PendingPayout[], providerMix: { name, value }[], disputes: Dispute[] }`

## 7. Persistence schema & migrations

```sql
CREATE TYPE payment_status AS ENUM ('pending','settled','failed','timeout');
CREATE TYPE ledger_direction AS ENUM ('DEBIT','CREDIT');
CREATE TYPE kyc_status AS ENUM ('none','pending','verified','rejected');
CREATE TYPE dispute_status AS ENUM ('open','refunded','rejected','escalated');

-- Implemented in V701 (WU-PAY-1). Ids are UUIDv7 strings (TEXT, matching the rest of the codebase),
-- not the UUID type; status/provider/method_kind use TEXT + CHECK rather than PG enums for additive
-- evolution. account_id, currency, method_kind, failure_reason, request_fingerprint, updated_at
-- added for WU-PAY-1's actor-binding + idempotency + failure semantics.
CREATE TABLE payment_intent (
  id                  TEXT PRIMARY KEY,
  account_id          TEXT NOT NULL,   -- authenticated principal that initiated the charge (INV-10)
  order_ref           TEXT NOT NULL,
  amount_minor        BIGINT NOT NULL CHECK (amount_minor >= 0),
  currency            TEXT NOT NULL DEFAULT 'GHS',
  provider            TEXT NOT NULL CHECK (provider IN ('mtn','telecel','airteltigo','card','bank')),
  method_kind         TEXT NOT NULL CHECK (method_kind IN ('momo','bank','card')),
  provider_ref        TEXT,
  status              TEXT NOT NULL DEFAULT 'pending'
                          CHECK (status IN ('pending','settled','failed','timeout')),
  failure_reason      TEXT,
  idempotency_key     TEXT NOT NULL,
  request_fingerprint TEXT NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_payment_intent_idem UNIQUE (idempotency_key)
);
CREATE INDEX idx_payment_intent_status_created ON payment_intent(status, created_at);
CREATE INDEX idx_payment_intent_order_ref ON payment_intent(order_ref);
CREATE INDEX idx_payment_intent_account ON payment_intent(account_id);

CREATE TABLE payment_event (
  id                UUID PRIMARY KEY,
  intent_id         UUID NOT NULL REFERENCES payment_intent(id),
  provider_event_id TEXT NOT NULL,
  type              TEXT NOT NULL,
  payload           JSONB NOT NULL,
  received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_event_provider UNIQUE (provider_event_id)
);

CREATE TABLE ledger_account (
  id               UUID PRIMARY KEY,
  kind             TEXT NOT NULL,   -- provider_clearing|creator_payable|platform_revenue|payout_clearing
  owner_account_id TEXT
);
CREATE INDEX ix_ledger_account_owner ON ledger_account(owner_account_id);

CREATE TABLE ledger_entry (
  id           UUID PRIMARY KEY,
  txn_id       UUID NOT NULL,
  account_id   UUID NOT NULL REFERENCES ledger_account(id),
  direction    ledger_direction NOT NULL,
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  ref_type     TEXT NOT NULL,
  ref_id       TEXT NOT NULL,
  cleared_at   TIMESTAMPTZ,
  posted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ledger_txn ON ledger_entry(txn_id);
CREATE INDEX ix_ledger_account ON ledger_entry(account_id, cleared_at);

-- Per-txn balance backstop: signed sum per txn_id must be zero (Σdebits = Σcredits, INV-6).
-- Enforced at COMMIT via a deferred constraint trigger.
CREATE OR REPLACE FUNCTION assert_txn_balanced() RETURNS trigger AS $$
BEGIN
  IF (SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE -amount_minor END),0)
        FROM ledger_entry WHERE txn_id = NEW.txn_id) <> 0 THEN
    RAISE EXCEPTION 'ledger txn % not balanced', NEW.txn_id;
  END IF;
  RETURN NULL;
END; $$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER trg_ledger_balanced AFTER INSERT ON ledger_entry
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced();

CREATE TABLE creator_balance (
  account_id     TEXT PRIMARY KEY,
  available_minor BIGINT NOT NULL DEFAULT 0,
  pending_minor   BIGINT NOT NULL DEFAULT 0,
  lifetime_minor  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE payout_method (
  id         UUID PRIMARY KEY,
  account_id TEXT NOT NULL,
  kind       TEXT NOT NULL CHECK (kind IN ('momo','bank')),
  label      TEXT NOT NULL,
  detail     TEXT NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX uq_default_method ON payout_method(account_id) WHERE is_default;

CREATE TABLE withdrawal_request (
  id              UUID PRIMARY KEY,
  account_id      TEXT NOT NULL,
  amount_minor    BIGINT NOT NULL CHECK (amount_minor >= 1000), -- ≥ ₵10 (INV-8)
  fee_minor       BIGINT NOT NULL CHECK (fee_minor >= 0),
  method_id       UUID NOT NULL REFERENCES payout_method(id),
  status          TEXT NOT NULL DEFAULT 'pending',
  idempotency_key TEXT NOT NULL,
  requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_withdrawal_idem UNIQUE (idempotency_key)
);

CREATE TABLE payout_batch (
  id          UUID PRIMARY KEY,
  kind        TEXT NOT NULL CHECK (kind IN ('weekly','single')),
  run_by      TEXT NOT NULL,
  status      TEXT NOT NULL,
  total_minor BIGINT NOT NULL DEFAULT 0,
  run_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payout_txn (
  id            UUID PRIMARY KEY,
  batch_id      UUID NOT NULL REFERENCES payout_batch(id),
  withdrawal_id UUID NOT NULL REFERENCES withdrawal_request(id),
  account_id    TEXT NOT NULL,
  amount_minor  BIGINT NOT NULL,
  status        TEXT NOT NULL,
  provider_ref  TEXT,
  CONSTRAINT uq_payout_per_withdrawal UNIQUE (withdrawal_id)
);

CREATE TABLE kyc_record (
  account_id  TEXT PRIMARY KEY,
  status      kyc_status NOT NULL DEFAULT 'none',
  verified_at TIMESTAMPTZ
);

CREATE TABLE dispute (
  id           UUID PRIMARY KEY,
  order_id     TEXT NOT NULL,
  kind         TEXT NOT NULL,
  status       dispute_status NOT NULL DEFAULT 'open',
  amount_minor BIGINT NOT NULL,
  opened_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dispute_event (
  id         UUID PRIMARY KEY,
  dispute_id UUID NOT NULL REFERENCES dispute(id),
  text       TEXT NOT NULL,
  actor      TEXT,
  at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refund (
  id           UUID PRIMARY KEY,
  dispute_id   UUID NOT NULL REFERENCES dispute(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Flyway migration list (forward-only):**

- `V701__create_payment_intent.sql` (WU-PAY-1) — **implemented**; payments band is `V7xx`
  (data-and-migrations §4.1), not `V2x`.
- `V702__payments_payment_event.sql` (WU-PAY-2)
- `V703__payments_ledger.sql` (ledger_account, ledger_entry, balance trigger, creator_balance — WU-PAY-3)
- `V704__payments_payouts.sql` (payout_method, withdrawal_request, payout_batch, payout_txn, kyc_record — WU-PAY-4)
- `V705__payments_disputes.sql` (dispute, dispute_event, refund — WU-PAY-5)
- `R__seed_dev_data.sql` (dev only): seed `ledger_account` singletons (platform_revenue, payout_clearing, provider_clearing per provider) and sample KYC records.

## 8. Key flows

**(a) Initiate charge → provider → async webhook → PaymentSettled; with timeout/reconciliation poll.**

```mermaid
sequenceDiagram
  participant C as commerce (checkout)
  participant IC as InitiateCharge
  participant PG as PaymentGateway (provider adapter)
  participant DB as payments DB
  participant W as Webhook receiver
  participant L as LedgerRepository
  participant EV as EventPublisher
  participant S as Reconcile scheduler

  C->>IC: charge(orderRef, amount, method, idemKey) [trace-id]
  IC->>DB: findByIdempotencyKey(idemKey)
  alt key seen
    DB-->>IC: existing intent
    IC-->>C: same PaymentIntent (no new charge)
  else new
    IC->>PG: initiate(provider, ref, amount, method)
    PG-->>IC: providerRef
    IC->>DB: save intent (status=pending)
    IC-->>C: PaymentIntent(pending)
  end
  Note over W,EV: async — provider calls back
  PG-->>W: POST /v1/payments/webhooks/:provider (signed)
  W->>PG: verifySignature(sig, rawBody)
  alt invalid
    W-->>PG: 401
  else valid
    W->>DB: recordEvent(providerEventId)  %% UNIQUE
    alt duplicate event
      DB-->>W: false → 200 no-op
    else first time
      W->>DB: intent pending→settled
      W->>L: postBalanced(T, [debit clearing, credit creator 70%, credit platform 30%])
      W->>EV: publish PaymentSettled
      W-->>PG: 200
    end
  end
  Note over S: if webhook never arrives
  S->>PG: queryStatus(providerRef) for pending > N min
  PG-->>S: settled|failed|stillPending
  S->>DB: settle/fail; after maxWindow → failed(timeout)
  S->>EV: publish PaymentSettled/PaymentFailed
```

**(b) Withdrawal request (KYC gate) → admin run-weekly → PayoutSent.**

```mermaid
sequenceDiagram
  participant A as Artist (studio)
  participant RW as RequestWithdrawal
  participant K as KycProvider
  participant L as LedgerRepository
  participant Adm as Admin (finance)
  participant RWk as RunWeeklyPayouts
  participant PG as PaymentGateway
  participant EV as EventPublisher

  A->>RW: withdraw(amount, methodId, idemKey)
  RW->>RW: amount ≥ ₵10? else 422 BELOW_MIN_PAYOUT
  RW->>L: available ≥ amount? else 409 INSUFFICIENT_BALANCE
  RW->>K: statusOf(creator)
  alt not verified
    K-->>RW: not verified → 403 KYC_REQUIRED (no ledger move)
  else verified
    RW->>L: post Cash-out (reserve funds, debit creator_payable→payout_clearing)
    RW->>EV: publish WithdrawalRequested
    RW-->>A: 202 WithdrawalRequest(pending, fee, arrival)
  end
  Adm->>RWk: runWeekly(actor, idemKey)  %% Friday
  RWk->>L: findReadyWithdrawals() (KYC-verified, ≥ min)
  loop each ready
    RWk->>PG: disburse(provider, creator, amount, method)
    PG-->>RWk: payoutRef
    RWk->>L: clear cash-out
    RWk->>EV: publish PayoutSent + AuditEntry
  end
  RWk-->>Adm: PayoutBatch (KYC-blocked skipped)
```

**(c) Dispute refund → ownership revoke + ledger clawback.**

```mermaid
sequenceDiagram
  participant Adm as Admin (finance)
  participant RD as RefundDispute
  participant L as LedgerRepository
  participant EV as EventPublisher
  participant CM as commerce/catalog

  Adm->>RD: refund(disputeId, {amount?, reason}, idemKey)
  RD->>RD: dispute open? else 409 ILLEGAL_TRANSITION
  RD->>L: postBalanced(T3, reverse sale: credit clearing, debit creator 70%, debit platform 30%)
  RD->>RD: save Refund; dispute open→refunded; AuditEntry
  RD->>EV: publish OrderRefunded
  EV->>CM: revoke OwnershipGrant(s) (INV-9) → track preview-gated again
  RD-->>Adm: Dispute(refunded)
```

**State machines:**

```mermaid
stateDiagram-v2
  [*] --> pending
  pending --> settled : webhook/poll confirms
  pending --> failed : webhook/poll rejects
  pending --> timeout : maxWindow elapsed (poll)
  settled --> [*]
  failed --> [*]
  timeout --> [*]
```

```mermaid
stateDiagram-v2
  [*] --> open
  open --> refunded : RefundDispute (revoke + clawback)
  open --> rejected : RejectDispute (reason)
  open --> escalated : EscalateDispute
  refunded --> [*]
  rejected --> [*]
  escalated --> open : re-open after review
```

## 9. Cross-cutting hooks

- **Idempotency** is mandatory on every path that moves money: `InitiateCharge`/`IssueTip`
  (`idempotency_key` UNIQUE on `payment_intent`), `HandleProviderWebhook` (`provider_event_id` UNIQUE on
  `payment_event` — replays are no-ops), `RequestWithdrawal` (`idempotency_key` UNIQUE on
  `withdrawal_request`), `RunWeeklyPayouts`/`SendSinglePayout`/`RefundDispute` (`Idempotency-Key` header,
  one effect per key). §9.2.
  - **Concurrency (WU-PAY-1).** `InitiateCharge` takes a **transaction-scoped Postgres advisory lock**
    (`pg_advisory_xact_lock` keyed on the idempotency key) before the read→provider→save window, so two
    truly-simultaneous same-key requests serialise: only one thread ever reaches `PaymentGateway.initiate`
    (no double charge), the loser blocks then returns the winner's intent (or 409 on a different body) —
    never a raw unique-violation 500. The `idempotency_key` UNIQUE constraint remains the durable
    backstop. Idempotency is matched via a SHA-256 `request_fingerprint` over
    `orderRef|amount_minor|currency|provider|method_kind` (the raw payment token is excluded so a token
    re-issue for the same charge is not a spurious conflict).
- **Webhook signature verification** over the **raw** request bytes using `BEATZ_PAYMENT_WEBHOOK_SECRET`
  (per-provider scheme via `PaymentGateway.verifySignature`); invalid → 401; unknown/untrusted ref →
  202 (accept-and-ignore to avoid a provider retry storm).
- **Audit (INV-10):** every privileged money mutation appends exactly one `AuditEntry` — `run-weekly`,
  single `send`, `refund`, `reject`, `escalate`, and any settings change (platform fee, payout day/min).
- **Error codes:** `BELOW_MIN_PAYOUT` (422), `INSUFFICIENT_BALANCE` (409), `KYC_REQUIRED` (403),
  `KYC_BLOCKED` (409), `ILLEGAL_TRANSITION` (409), plus the kernel envelope `{ error: { code, message, field? } }`.
- **Domain events (AFTER_SUCCESS):** `PaymentSettled`, `PaymentFailed`, `TipReceived`,
  `WithdrawalRequested`, `PayoutSent`, `DisputeOpened`, `OrderRefunded` (ids + minimal snapshot, never
  JPA entities).
- **Configurability:** `platformFeePct`/`creatorSharePct`, `tipFeePct` (OQ-2), royalty accrual model
  (OQ-4 — periodic platform-funded pool, may be ₵0 initially), `payoutMinimum`, `payoutDay`,
  per-provider enable flags all come from `PlatformSettings` — never hard-coded.
- **Rate limiting** on checkout/tip/withdraw (token-bucket per account/IP) → 429 + `Retry-After`.
- **Observability:** Micrometer payment-success-rate, payout volume, reconciliation-discrepancy count,
  webhook latency; OpenTelemetry spans inbound→use case→provider/DB; correlation/trace id on every
  request; **no PII or secrets in logs**.

## 10. Work units & build order

| WU | Scope | LLFR | Owned tables | Depends on | Order |
|---|---|---|---|---|---|
| **WU-PAY-1** | `PaymentIntent` + `InitiateCharge` + idempotency; `PaymentGateway` port | 01.1 | payment_intent | WU-PLT-1 | 1 |
| **WU-PAY-2** | Provider webhooks + timeout poll + reconciliation | 01.2–01.4 | payment_event | WU-PAY-1, WU-PLT-2 | 2 |
| **WU-PAY-3** | Double-entry ledger + creator balance + split posting + tips | 02.*, 05 | ledger_account, ledger_entry, creator_balance | WU-PAY-1 | 3 |
| **WU-PAY-4** | Payout methods + KYC withdrawals + admin payout runs | 03.* | payout_method, withdrawal_request, payout_batch, payout_txn, kyc_record | WU-PAY-3, WU-IDN-4 | 4 |
| **WU-PAY-5** | Refunds/chargebacks/disputes + ownership revocation + clawback | 04.* | dispute, dispute_event, refund | WU-PAY-3, WU-COM-2 | 5 |

Cross-reference PRD §8: Phase 2 — WU-PAY-1 → WU-PAY-2, WU-PAY-3; Phase 3 — WU-PAY-4, WU-PAY-5.
`commerce` WU-COM-2 depends on WU-PAY-1/3; podcast tipping WU-POD-2 depends on WU-PAY-3.

## 11. Testing plan

**Unit (domain/use-case with fakes):** balanced-posting math (₵10 sale → 700/300; ₵10 tip → 900/100;
half-up rounding & remainder reconciliation); split subdivision (INV-12); available-balance derivation
(INV-8); state-machine transitions.

**Integration (Testcontainers Postgres, REST-assured):** the DB balance trigger rejects an unbalanced
txn; idempotency uniqueness; webhook signature verification; scheduler timeout transition.

**Contract:** responses validate against `studio-payouts.ts` (`Payouts`, `PayoutTxn`, `PayoutMethod`)
and `admin-data.ts` (`Finance`, `LedgerTxn`, `Dispute`).

**Acceptance (PRD §6.6 Given/When/Then):**

- **Idempotent double charge** — *Given* the same idempotency key twice *When* `InitiateCharge` *Then*
  exactly one provider charge and one `PaymentIntent`. (01.1)
- **Duplicate webhook** — *Given* a duplicate webhook (same `providerEventId`) *When* received *Then* the
  intent transitions at most once and exactly one `PaymentSettled` is emitted. (01.2)
- **Timeout** — *Given* a never-delivered webhook *When* the poll runs *Then* the intent eventually
  settles or fails(timeout). (01.3)
- **Sale split** — *Given* a ₵10 settled sale *Then* ledger shows creator_payable +₵7.00 and
  platform_revenue +₵3.00, balanced (Σdebits=Σcredits). (02.1)
- **KYC gating** — *Given* unverified KYC *When* withdraw *Then* 403 `KYC_REQUIRED` and **no ledger
  movement**. (03.2)
- **Below min** — *Given* a ₵5 withdrawal request *Then* 422 `BELOW_MIN_PAYOUT`. (03.2)
- **Single send blocks on KYC** — *Given* a KYC-blocked withdrawal *When* admin send *Then* 409
  `KYC_BLOCKED`. (03.4)
- **Weekly run skips blocked** — *Given* mixed-eligibility creators *Then* only KYC-verified, ≥ min are
  paid and each appends an audit entry. (03.3)
- **Refund revokes ownership** — *Given* a refunded purchase *Then* the buyer no longer owns the track
  (preview-gated again) and the creator's balance is reduced by the clawed-back share. (04.2)

**Coverage:** ≥ the gate in `sdlc/testing-strategy.md`; all money paths covered by both unit and
integration tests.

## 12. Definition of done (module-specific)

Global DoD (PRD §8 / conventions §11) **plus**:

1. **The ledger is always balanced** — every `txn_id` satisfies Σdebits = Σcredits (INV-6), enforced in
   domain and by the DB deferred constraint trigger; an integration test proves an unbalanced posting is
   rejected.
2. **No value without settlement** — no `OwnershipGrant` request (`PaymentSettled`) is emitted on
   `pending`/`failed`/`timeout` (INV-1).
3. **Payouts blocked on KYC** — withdrawals require `verified` KYC and `amount ≥ ₵10 ≤` cleared balance
   (INV-8); weekly run skips, single send returns `KYC_BLOCKED`.
4. **Refund integrity** — a refund reverses the originating entries (clawback) and emits `OrderRefunded`
   so grants are revoked (INV-9).
5. **Idempotency proven** — duplicate charge, duplicate webhook, and duplicate withdrawal/payout/refund
   keys produce exactly one effect.
6. **Audit completeness** — every payout run, single send, and refund/reject/escalate appends exactly
   one `AuditEntry` (INV-10).
7. All splits/fees sourced from `PlatformSettings` (OQ-2 tip fee, OQ-4 royalty model); none hard-coded.
