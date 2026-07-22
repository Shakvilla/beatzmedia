# Frontend Studio Payouts Wiring — Design

**Date:** 2026-07-22
**Slice:** Studio surface completion — payouts (balance / methods / withdrawals / ledger)
**Branch:** `feat/frontend-studio-payouts` off `master`
**Backend:** WU-PAY-4 (payout methods + withdrawals) and WU-PAY-7 (structured payout methods) — already merged. No backend change.

## Goal

Wire the studio payouts surface, and the studio dashboard's balance/recent-activity, from the mock
`getPayouts()` + `useStudio()` store to the live WU-PAY-4 endpoints. Data-swap with **no visual
change**, plus one **backend-driven form expansion** (the Add-Method modal) that the user approved.
`GET /v1/studio/payouts` returns the whole page's data 1:1, so mappers are thin.

## Context

- `Frontend/src/routes/studio.payouts.tsx` — the full payouts page: balance hero (available +
  pending) with a Withdraw button (disabled below `MIN_PAYOUT`), this-month/lifetime stats, an
  earnings bar chart, a by-source split, a payout-methods grid (add / set-default / remove), a
  filterable/grouped transactions ledger, an Export-CSV button (toast only), a **Withdraw** modal
  (amount + method select + client-side fee/net preview), and an **Add-Method** modal.
  Data today: `getPayouts()` (the `base`: pending, thisMonth, lifetime, earnings, bySource, since)
  **and** `useStudio()` (balance, transactions, methods + `withdraw`/`setDefaultMethod`/
  `removeMethod`/`addMethod`).
- `Frontend/src/routes/studio.index.tsx` (dashboard) — its "Available balance" stat and the recent-
  activity list also read `useStudio().balance` and `.transactions`.
- `Frontend/src/features/studio/studio-context.tsx` — holds only payout state now (episodes left in
  the podcasts slice). Removing payouts empties it.
- `Frontend/src/routes/studio.tsx` — mounts `<StudioProvider><StudioShell/></StudioProvider>`.

### Backend surface (WU-PAY-4 / WU-PAY-7, payments module, `@RolesAllowed("artist")`)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/v1/studio/payouts` | — | `PayoutsView` (the whole page) |
| POST | `/v1/studio/payouts/withdraw` | `{ amount: { amount, currency }, methodId }`; **requires `Idempotency-Key`** | `WithdrawalView` |
| POST | `/v1/studio/payout-methods` | `AddBody` (structured, below) | `PayoutMethodView` (201) |
| PATCH | `/v1/studio/payout-methods/{id}/default` | — | `PayoutMethodView` |
| DELETE | `/v1/studio/payout-methods/{id}` | — | 204 |

There is **no GET for payout-methods** — methods are served inside `PayoutsView.methods`; the three
method mutations just invalidate `payoutsQuery`.

`PayoutsView` (1:1 with the frontend `Payouts` type; all money `BigDecimal`):
```
available, pending, thisMonth, thisMonthDelta(int), lifetime, since,
earnings: [{ label, value }], bySource: { sales, royalties, tips },
methods: [{ id, label, detail, kind, isDefault }],
transactions: [{ id, date, source, type, gross(nullable), net, status }]
```

`AddBody` (WU-PAY-7): `{ label, detail, kind, network, walletNumber, bankName, bankCode,
accountName, accountNumber }`. The application service **validates**: `kind=momo` requires
`network` + `walletNumber`; `kind=bank` requires `bankName` + `bankCode` + `accountName` +
`accountNumber`. Unused fields for a kind are null. `network` is the `Provider` wire token
(`mtn` | `telecel` | `airteltigo`). `bankCode` is a `GhanaBankCode` token (invalid → 422).

`apiFetch` (`lib/api/client.ts`) already prepends `/v1`, supports an `idempotencyKey` option, and
handles the 204 (DELETE) and JSON error envelope. This is the same idiom as the merged studio slices.

## Architecture

- **New `Frontend/src/lib/api/queries/payouts.ts`**:
  - `payoutsQuery()` → `GET /studio/payouts` → `toPayouts`.
  - `apiRequestWithdrawal(amountCedis: number, methodId: string)` → `POST /studio/payouts/withdraw`,
    body `{ amount: { amount: amountCedis, currency: 'GHS' }, methodId }`, `idempotencyKey:
    crypto.randomUUID()`.
  - `apiAddPayoutMethod(input: NewPayoutMethodInput)` → `POST /studio/payout-methods`.
  - `apiSetDefaultPayoutMethod(id)` → `PATCH /studio/payout-methods/{id}/default`.
  - `apiRemovePayoutMethod(id)` → `DELETE /studio/payout-methods/{id}`.
- **Mappers** in `lib/api/mappers.ts`: `toPayouts` (+ `PayoutsWire` and nested wire types), coercing
  every money `BigDecimal` via `Number(...)`; `gross` stays `number | null`; `type`/`status`/`kind`
  string → the existing frontend unions.
- **`studio.payouts.tsx`**: replace both data sources with `useQuery(payoutsQuery())` (default to a
  zero/empty `Payouts` while loading so the existing JSX renders unchanged). The four actions become
  async calls that invalidate `payoutsQuery().queryKey` and toast on success/failure. Withdraw and
  Add-Method modals close on success. JSX/classes preserved except the Add-Method modal (below).
- **`studio.index.tsx`**: read `balance` + `transactions` from `useQuery(payoutsQuery())` instead of
  `useStudio()`; drop the `useStudio` import. No JSX change.
- **Add-Method modal expansion** (approved — backend-driven, same modal styling):
  - **MoMo**: `network` select (**MTN**→`mtn`, **Telecel**→`telecel`, **AirtelTigo**→`airteltigo`) +
    `walletNumber` input.
  - **Bank**: `bankName` input + `bankCode` select (the 23-entry `GhanaBankCode` list mirrored as a
    frontend `{ code, name }[]` constant — GCB "GCB Bank", CAL "CalBank", EBL "Ecobank Ghana", ABB
    "Absa Bank Ghana", UBA "United Bank for Africa", ADB "Agricultural Development Bank", APX "Apex
    Bank", BOA "Bank of Africa", BBG "Bank of Baroda Ghana", FBN "First Bank of Nigeria", FBL
    "Fidelity Bank Ghana", FAB "First Atlantic Bank", FNB "First National Bank Ghana", GTB "Guaranty
    Trust Bank Ghana", NIB "National Investment Bank", OMN "OmniBSIC Bank", PBL "Prudential Bank",
    RBL "Republic Bank Ghana", SGG "Societe Generale Ghana", CBG "Consolidated Bank Ghana", SBL
    "Stanbic Bank Ghana", SCB "Standard Chartered Bank Ghana", ZBL "Zenith Bank Ghana" — since no
    endpoint lists them) + `accountName` + `accountNumber` inputs.
  - `label`/`detail` (shown on the method card) are derived from the entries: MoMo → label the
    network display name (e.g. "MTN MoMo"), detail the wallet number; Bank → label the bank name,
    detail the account number. Sent explicitly in `AddBody` alongside the structured fields.
- **Context teardown**: remove all payout state from `studio-context.tsx`; it is then empty, so
  **delete `studio-context.tsx`** and unwrap `<StudioProvider>` in `studio.tsx` (render
  `<StudioShell/>` directly). `useStudio` no longer exists anywhere.

## Money / caution notes

- **Withdraw is a real money POST.** The server computes the authoritative fee (INV-4) and enforces
  KYC / floor / balance; the modal's client-side `withdrawalFee` stays a **preview only**. KYC-not-
  verified, insufficient-balance, and below-floor responses surface as error toasts (mapped error
  envelope). A fresh dev artist may be un-KYC'd, so a live withdraw may legitimately 4xx — that is
  correct behavior, not a wiring defect.
- `MIN_PAYOUT`, `withdrawalFee`, `arrivalTime`, `daysUntilFriday` stay as pre-existing client display
  helpers (the backend is authoritative on the actual floor/fee). Not changed in this slice.

## Out of scope

- **Export CSV** — stays a toast (no endpoint).
- **Withdrawal history detail / cancel** — not in the mock UI; not added.
- Any change to `MIN_PAYOUT`/fee constants (they should ultimately come from PlatformSettings — a
  pre-existing carryover, not this slice).

## Testing & gate

- Co-located Vitest `payouts.test.ts`: `payoutsQuery` URL + mapping; `apiRequestWithdrawal` body
  shape (`{ amount:{ amount, currency:'GHS' }, methodId }`) + `Idempotency-Key` presence;
  `apiAddPayoutMethod` momo vs bank body shapes; set-default/remove URLs+methods. Mapper cases in
  `mappers.test.ts`.
- No visual change outside the deliberate Add-Method modal expansion; JSX/classes preserved.
- Mapper outputs match `Frontend/src/lib/studio-payouts.ts` types; money is a plain cedi `number`.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + `npx vitest run` green; no
  NEW lint errors. Live QA against the running stack: view live balance/methods/txns, add a MoMo
  method (see it appear), set-default, remove, attempt a withdraw (observe success or the mapped
  KYC/floor/balance error). One PR: `feat/frontend-studio-payouts`.
