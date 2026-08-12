# GAP-13 — Per-provider payment enablement

**Status:** design, awaiting decisions. No code written.
**Date:** 2026-08-11
**Gap:** GAP-13 — Settings renders MTN MoMo / Vodafone Cash / AirtelTigo / Card / Bank transfer with
the caption *"Not yet configurable — every method is currently enabled platform-wide."*

---

## What exists today

`PUT /v1/admin/settings` accepts a `providers` object and **throws it away**. `SaveSettingsService`
says so plainly:

> *`providers.*` is accepted but not persisted (no per-provider enablement subsystem) — documented
> in `PlatformSettingsView`.*

The toggles are rendered `disabled` in the UI, so nothing lies to the operator today. That is why
this was filed Low.

## What "make them real" actually requires

Four things need deciding before any of it is safe to write. Each is a genuine fork, not a
formality.

---

### Decision 1 — the two vocabularies do not match

| Admin settings key | UI label | Payments domain `Provider` |
|---|---|---|
| `momo` | "MTN MoMo" | `mtn` |
| `vodafone` | "Vodafone Cash" | `telecel` |
| `airteltigo` | "AirtelTigo Money" | `airteltigo` |
| `card` | "Card" | `card` |
| `bank` | "Bank transfer" | `bank` |

The mapping *is* 1:1 — the label "MTN MoMo" on key `momo` confirms `momo` means MTN specifically,
not "all mobile money". But **two keys carry stale names, and one names a brand that no longer
exists**: Vodafone Ghana became Telecel in 2023. The rest of the product already knows this —
`checkout.index.tsx` offers "Telecel Cash" and `payouts.ts` lists `{ value: 'telecel' }`. Only the
admin console still says Vodafone.

- **(a) Rename the wire keys** to `mtn` / `telecel` so settings, checkout, payouts and the domain
  all agree. A contract change to `PlatformSettingsView.Providers` and the SPA's
  `PlatformSettings`, and the admin ADD's §11 table. One vocabulary everywhere afterwards.
- **(b) Keep `momo`/`vodafone`** and translate at the boundary. No contract change; a permanent
  mapping layer, and an operator toggling "Vodafone Cash" to disable a rail the rest of the system
  calls Telecel.

**Recommendation: (a).** A mapping layer that exists only to preserve a defunct brand name is the
kind of thing nobody remembers in six months, and this is the money path.

---

### Decision 2 — where the flags live

- **(a) Extend `FeatureKey`** with `PROVIDER_MTN`, `PROVIDER_TELECEL`, `PROVIDER_AIRTELTIGO`,
  `PROVIDER_CARD`, `PROVIDER_BANK`. Reuses `feature_flag`, its cache, and the after-commit
  invalidation fixed in GAP-07. Cheapest by a distance. Cost: the enum grows to 11 and mixes product
  flags (`PODCASTS`) with payment rails, alongside the existing out-of-band `PSP_REDDE`.
- **(b) A new `payment_provider` table** owned by payments, with its own port. Cleaner separation —
  payments owns its rails rather than reading platform's flag bag. Cost: a migration, a repository,
  a cache, and re-solving the invalidation problem GAP-07 already solved once.

**Recommendation: (a)**, with the provider keys grouped and commented in the enum. The precedent
already exists: `PSP_REDDE` is an operational payments toggle living in `FeatureKey` today.

---

### Decision 3 — fail-open or fail-closed *(the one that matters)*

`FeatureFlagsAdapter.isEnabled` currently does:

```java
// Default to true for unknown flags (fail-open for non-security features).
return flagCache.getOrDefault(key.name(), true);
```

**Fail-open is the wrong default for a payment rail, and the right one for platform availability.**
That tension is the whole risk in this gap:

- **Fail-open:** a provider with no row charges normally. A failed migration, a typo'd key, or a
  cache miss means a rail the operator believes is *off* keeps taking money. The operator's promise
  is silently broken and nothing alerts.
- **Fail-closed:** a missing row blocks the rail. The operator's promise holds — but a botched
  migration halts **all payments**, and this failure is loud, immediate and total.

Options:
- **(a) Fail-closed for provider keys only**, leaving product flags fail-open. Requires
  `isEnabled` to distinguish the two classes, or a separate `isProviderEnabled` that defaults false.
- **(b) Fail-open throughout**, and rely on the migration seeding all five rows `true`. Simplest;
  bets the promise on a migration never being wrong.
- **(c) Fail-closed with a startup assertion** — refuse to boot if any provider row is missing, so
  the failure surfaces at deploy rather than at a customer's checkout.

**Recommendation: (c).** It converts "silently takes money on a disabled rail" and "silently
declines every payment" into "does not start", which is the only one of the three an operator finds
out about immediately. This is the reason I asked for a design pass before writing the migration.

---

### Decision 4 — enforcement scope

`Provider` is used on both sides of the money flow.

- **Charges** — `InitiateChargeService` calls `gateway.initiate(method.provider(), …)`. Natural
  gate: reject before the gateway call with a mapped 4xx, so a disabled rail never reaches the PSP.
- **Payouts** — `PayoutDisburser` pays creators out over the same rails. **Disabling a rail here
  strands creator balances**: money already earned cannot be withdrawn, through no fault of the
  creator.
- **Checkout UI** — the fan-facing method picker should not offer a rail that will be refused.
  Needs a public read of enabled providers; there is no such endpoint today.

Options:
- **(a) Charges only.** Disabling stops new money in; existing balances still pay out. Smallest
  blast radius, and the semantics an operator most likely expects from "stop accepting MoMo".
- **(b) Charges + payouts.** Consistent, but a rail disabled for a PSP outage would freeze payouts
  to creators who have no other method on file.
- **(c) Charges + payouts, with payouts warning instead of blocking** — surface the disabled rail in
  the payout run's output and skip those payees, rather than failing them.

**Recommendation: (a) for this change**, with the port shaped so payouts can adopt it later. "Stop
accepting payments on this rail" and "stop paying out on this rail" are different operator
intentions and deserve to be separate switches, not one flag with two meanings.

---

## Scope if all four recommendations are taken

1. Migration: seed five `feature_flag` rows, one per provider, `true`.
2. `FeatureKey`: five provider keys, grouped and commented.
3. `Provider.fromWire` unchanged; `PlatformSettingsView.Providers` renamed `momo`→`mtn`,
   `vodafone`→`telecel`.
4. New payments outbound port `PaymentProviderPolicy.isEnabled(Provider)`, fail-closed, adapter over
   `FeatureFlags`.
5. `InitiateChargeService`: reject a disabled provider before the gateway call —
   `409 PROVIDER_DISABLED` (not 422; the request is well-formed, the platform state refuses it).
6. `SaveSettingsService`: actually persist `providers`, audited like the fee change.
7. Startup assertion: fail to boot if any provider row is absent.
8. Public `GET /v1/payments/providers` so checkout can hide disabled rails.
9. Frontend: enable the toggles; checkout reads the enabled set.
10. Tests: unit (policy, fail-closed), IT (charge refused end to end, settings round-trip),
    contract (renamed keys), plus a mutation check that the refusal is load-bearing.
11. Docs: payments ADD §3, admin ADD §11 table, and an ADR for the fail-closed decision.

**Security review required** before merge — this is `area:payments` and changes who can take money.

## Out of scope

Per-provider *credentials* or routing config. This is enablement only: which rails the platform will
use, not how it authenticates to them.
