# Frontend Studio Payouts Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `studio.payouts` (and the dashboard's balance/activity) from the mock `getPayouts()` + `useStudio()` store to the live WU-PAY-4/WU-PAY-7 endpoints, no visual change except the approved Add-Method modal expansion.

**Architecture:** TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX` for the read; mutations = async `apiFetch` fns + `queryClient.invalidateQueries` on success. New query module `lib/api/queries/payouts.ts`. `studio-context.tsx` (and `<StudioProvider>`) are deleted once both consumers move to the query.

**Tech Stack:** React 19 + TanStack Query v5 + TanStack Router, TypeScript, Vitest + RTL, Node 22 via nvm.

**Spec:** `docs/superpowers/specs/2026-07-22-frontend-studio-payouts-wiring-design.md`

## Global Constraints

- **No visual change**, except the deliberate Add-Method modal expansion (approved). Everything else is a data-source swap: JSX/classes preserved.
- Mapper outputs match `Frontend/src/lib/studio-payouts.ts` types exactly: `Payouts`, `PayoutMethod {id,label,detail,kind,isDefault}`, `PayoutTxn {id,date,source,type,gross:number|null,net,status}`. All money is a plain cedi `number` (wire is `BigDecimal` → `Number(...)`; `gross` stays nullable).
- `apiFetch` (`lib/api/client.ts`) prepends `/v1`; paths written WITHOUT `/v1`. It supports an `idempotencyKey` option and handles 204 + the JSON error envelope.
- **Withdraw is a money POST**: `POST /studio/payouts/withdraw`, body `{ amount: { amount, currency: 'GHS' }, methodId }`, with a fresh `Idempotency-Key`. The server computes the fee and gates KYC/floor/balance; the modal's client fee stays a preview. Mapped errors (KYC/floor/balance) surface as toasts.
- Add-method is structured (WU-PAY-7): momo → `network`(`mtn`|`telecel`|`airteltigo`) + `walletNumber`; bank → `bankName` + `bankCode`(GhanaBankCode token) + `accountName` + `accountNumber`. `label`/`detail` derived and sent too.
- There is **no GET for payout-methods** — methods come inside `PayoutsView`; method mutations invalidate `payoutsQuery`.
- No `@testing-library/jest-dom` → RTL uses `toBeTruthy()`.
- All commands from `Frontend/`. Real typecheck gate: `npm run build` (`tsc -b`), NOT `tsc --noEmit`. Test: `npx vitest run <path>`. Lint: `npm run lint` — add no NEW errors.
- Branch `feat/frontend-studio-payouts` (already created; spec committed). NEVER stage `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`.

---

## File Structure

- `Frontend/src/lib/api/mappers.ts` (modify) — `PayoutsWire` + nested wire types; `toPayouts`, `toPayoutMethod`, `toPayoutTxn`.
- `Frontend/src/lib/api/mappers.test.ts` (modify) — mapper cases.
- `Frontend/src/lib/api/queries/payouts.ts` (new) — `payoutsQuery`, `apiRequestWithdrawal`, `apiAddPayoutMethod`, `apiSetDefaultPayoutMethod`, `apiRemovePayoutMethod`, `NewPayoutMethodInput`, `GHANA_BANK_CODES`, `MOMO_NETWORKS`.
- `Frontend/src/lib/api/queries/payouts.test.ts` (new) — URLs, bodies, idempotency.
- `Frontend/src/routes/studio.payouts.tsx` (modify) — read via query; four mutations; Add-Method modal expansion.
- `Frontend/src/routes/studio.index.tsx` (modify) — balance + transactions from the query.
- `Frontend/src/features/studio/studio-context.tsx` (delete).
- `Frontend/src/routes/studio.tsx` (modify) — drop `<StudioProvider>`.

**Task order:** mappers → query layer → wire payouts page → wire dashboard → delete context. Each task compiles green: `studio-context.tsx` stays until Tasks 3–4 remove both `useStudio` consumers.

---

## Task 1: Payouts mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `Payouts`, `PayoutMethod`, `PayoutTxn`, `PayoutType`, `PayoutStatus`, `MethodKind` from `../../studio-payouts`.
- Produces: `PayoutsWire`, `PayoutMethodWire`, `PayoutTxnWire`, `toPayouts`, `toPayoutMethod`, `toPayoutTxn`.

**Prep:** Open `lib/studio-payouts.ts` to confirm the `Payouts`/`PayoutMethod`/`PayoutTxn` field names and unions. Match the existing mapper style in `mappers.ts` (see `toStudioEpisode`).

- [ ] **Step 1: Write failing tests** in `mappers.test.ts`:

```ts
import { toPayouts, toPayoutMethod, toPayoutTxn } from './mappers'

describe('toPayoutTxn', () => {
  it('maps a sale (gross present) with money as numbers', () => {
    expect(toPayoutTxn({ id: 't1', date: 'May 02', source: 'Track sale', type: 'Sale',
      gross: 350, net: 245, status: 'cleared' })).toEqual({
      id: 't1', date: 'May 02', source: 'Track sale', type: 'Sale', gross: 350, net: 245, status: 'cleared' })
  })
  it('keeps gross null for a cash-out and coerces string money', () => {
    const r = toPayoutTxn({ id: 't4', date: 'Apr 28', source: 'Withdrawal', type: 'Cash-out',
      gross: null, net: '-5000', status: 'paid' })
    expect(r.gross).toBeNull()
    expect(r.net).toBe(-5000)
  })
})

describe('toPayoutMethod', () => {
  it('maps id/label/detail/kind/isDefault', () => {
    expect(toPayoutMethod({ id: 'm1', label: 'MTN MoMo', detail: '0244 ... 9210', kind: 'momo', isDefault: true }))
      .toEqual({ id: 'm1', label: 'MTN MoMo', detail: '0244 ... 9210', kind: 'momo', isDefault: true })
  })
})

describe('toPayouts', () => {
  it('maps the whole view, coercing money and nested lists', () => {
    const wire = {
      available: 18420.5, pending: 1240.8, thisMonth: 21680, thisMonthDelta: 24, lifetime: 142490,
      since: 'Jan 2024',
      earnings: [{ label: 'May', value: 21680 }],
      bySource: { sales: 12400, royalties: 6420, tips: 2860 },
      methods: [{ id: 'm1', label: 'MTN MoMo', detail: '0244', kind: 'momo', isDefault: true }],
      transactions: [{ id: 't1', date: 'May 02', source: 'Sale', type: 'Sale', gross: 350, net: 245, status: 'cleared' }],
    }
    const p = toPayouts(wire)
    expect(p.available).toBe(18420.5)
    expect(p.earnings[0]).toEqual({ label: 'May', value: 21680 })
    expect(p.bySource).toEqual({ sales: 12400, royalties: 6420, tips: 2860 })
    expect(p.methods[0].id).toBe('m1')
    expect(p.transactions[0].net).toBe(245)
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/mappers.test.ts` → FAIL.

- [ ] **Step 3: Add mappers** to `mappers.ts`:

```ts
import type { Payouts, PayoutMethod, PayoutTxn, PayoutType, PayoutStatus, MethodKind } from '../../studio-payouts'

export interface PayoutMethodWire { id: string; label: string; detail: string; kind: string; isDefault: boolean }
export function toPayoutMethod(w: PayoutMethodWire): PayoutMethod {
  return { id: w.id, label: w.label, detail: w.detail, kind: w.kind as MethodKind, isDefault: w.isDefault }
}

export interface PayoutTxnWire {
  id: string; date: string; source: string; type: string
  gross: number | string | null; net: number | string; status: string
}
export function toPayoutTxn(w: PayoutTxnWire): PayoutTxn {
  return {
    id: w.id, date: w.date, source: w.source, type: w.type as PayoutType,
    gross: w.gross === null ? null : Number(w.gross), net: Number(w.net), status: w.status as PayoutStatus,
  }
}

export interface PayoutsWire {
  available: number | string; pending: number | string; thisMonth: number | string
  thisMonthDelta: number; lifetime: number | string; since: string
  earnings: { label: string; value: number | string }[]
  bySource: { sales: number | string; royalties: number | string; tips: number | string }
  methods: PayoutMethodWire[]; transactions: PayoutTxnWire[]
}
export function toPayouts(w: PayoutsWire): Payouts {
  return {
    available: Number(w.available), pending: Number(w.pending), thisMonth: Number(w.thisMonth),
    thisMonthDelta: w.thisMonthDelta, lifetime: Number(w.lifetime), since: w.since,
    earnings: w.earnings.map((e) => ({ label: e.label, value: Number(e.value) })),
    bySource: { sales: Number(w.bySource.sales), royalties: Number(w.bySource.royalties), tips: Number(w.bySource.tips) },
    methods: w.methods.map(toPayoutMethod),
    transactions: w.transactions.map(toPayoutTxn),
  }
}
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/mappers.test.ts` → PASS. Also `npm run build` → 0 errors.

- [ ] **Step 5: Commit** — `feat(studio): payouts wire mappers`.

---

## Task 2: `payouts.ts` query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/payouts.ts`
- Test: `Frontend/src/lib/api/queries/payouts.test.ts`

**Interfaces:**
- Consumes: `apiFetch`; `toPayouts`/`PayoutsWire`, `toPayoutMethod`/`PayoutMethodWire` (Task 1); `Payouts`, `PayoutMethod` types.
- Produces:
  - `payoutsQuery()` → `queryOptions` for `Payouts`
  - `apiRequestWithdrawal(amountCedis: number, methodId: string): Promise<void>`
  - `interface NewPayoutMethodInput { kind: 'momo' | 'bank'; label: string; detail: string; network: string | null; walletNumber: string | null; bankName: string | null; bankCode: string | null; accountName: string | null; accountNumber: string | null }`
  - `apiAddPayoutMethod(input: NewPayoutMethodInput): Promise<PayoutMethod>`
  - `apiSetDefaultPayoutMethod(id: string): Promise<PayoutMethod>`
  - `apiRemovePayoutMethod(id: string): Promise<void>`
  - `const MOMO_NETWORKS: { value: string; label: string }[]`
  - `const GHANA_BANK_CODES: { code: string; name: string }[]`

**Prep:** Read `lib/api/queries/studio.ts` for the exact `queryOptions` + `apiFetch` + `.then(mapper)` idiom, the delete shape (`apiDeleteRelease`), and how `idempotencyKey` is passed (release-wizard upload). Confirm `crypto.randomUUID` usage.

- [ ] **Step 1: Write failing tests** `payouts.test.ts` (mock `../client`):

```ts
import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { payoutsQuery, apiRequestWithdrawal, apiAddPayoutMethod, apiSetDefaultPayoutMethod, apiRemovePayoutMethod } from './payouts'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)
const METHOD = { id: 'm1', label: 'MTN MoMo', detail: '0244', kind: 'momo', isDefault: true }

beforeEach(() => apiFetch.mockReset())

describe('payoutsQuery', () => {
  it('GETs /studio/payouts and maps', async () => {
    apiFetch.mockResolvedValue({ available: 10, pending: 0, thisMonth: 0, thisMonthDelta: 0, lifetime: 0,
      since: 'x', earnings: [], bySource: { sales: 0, royalties: 0, tips: 0 }, methods: [METHOD], transactions: [] })
    const res = await payoutsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/payouts')
    expect(res.available).toBe(10)
    expect(res.methods[0].id).toBe('m1')
  })
})

describe('apiRequestWithdrawal', () => {
  it('POSTs the money shape with an Idempotency-Key', async () => {
    apiFetch.mockResolvedValue({})
    await apiRequestWithdrawal(250, 'm1')
    const [path, opts] = apiFetch.mock.calls[0]!
    expect(path).toBe('/studio/payouts/withdraw')
    expect(opts.method).toBe('POST')
    expect(opts.body).toEqual({ amount: { amount: 250, currency: 'GHS' }, methodId: 'm1' })
    expect(typeof opts.idempotencyKey).toBe('string')
    expect((opts.idempotencyKey as string).length).toBeGreaterThan(0)
  })
})

describe('apiAddPayoutMethod', () => {
  it('POSTs a momo method with network + walletNumber', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiAddPayoutMethod({ kind: 'momo', label: 'MTN MoMo', detail: '0244000000',
      network: 'mtn', walletNumber: '0244000000', bankName: null, bankCode: null, accountName: null, accountNumber: null })
    const [path, opts] = apiFetch.mock.calls[0]!
    expect(path).toBe('/studio/payout-methods')
    expect(opts.method).toBe('POST')
    expect(opts.body).toMatchObject({ kind: 'momo', network: 'mtn', walletNumber: '0244000000', bankCode: null })
  })
  it('POSTs a bank method with the four bank fields', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiAddPayoutMethod({ kind: 'bank', label: 'GCB Bank', detail: '1234567890',
      network: null, walletNumber: null, bankName: 'GCB Bank', bankCode: 'GCB', accountName: 'Kojo', accountNumber: '1234567890' })
    expect(apiFetch.mock.calls[0]![1].body).toMatchObject({ kind: 'bank', bankCode: 'GCB', accountName: 'Kojo', network: null })
  })
})

describe('apiSetDefaultPayoutMethod / apiRemovePayoutMethod', () => {
  it('PATCHes default', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiSetDefaultPayoutMethod('m2')
    expect(apiFetch).toHaveBeenCalledWith('/studio/payout-methods/m2/default', { method: 'PATCH' })
  })
  it('DELETEs by id', async () => {
    apiFetch.mockResolvedValue(undefined)
    await apiRemovePayoutMethod('m2')
    expect(apiFetch).toHaveBeenCalledWith('/studio/payout-methods/m2', { method: 'DELETE' })
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/queries/payouts.test.ts` → FAIL.

- [ ] **Step 3: Implement** `payouts.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import type { Payouts, PayoutMethod } from '../../studio-payouts'
import { apiFetch } from '../client'
import { toPayouts, type PayoutsWire, toPayoutMethod, type PayoutMethodWire } from '../mappers'

/** `GET /v1/studio/payouts` — the signed-in creator's balance + stats + earnings + methods + ledger. */
export function payoutsQuery() {
  return queryOptions({
    queryKey: ['studio', 'payouts'],
    queryFn: async () => toPayouts(await apiFetch<PayoutsWire>('/studio/payouts')),
  })
}

/**
 * `POST /v1/studio/payouts/withdraw` — request a cash-out. Money POST: server computes the fee and
 * gates KYC/floor/balance. A fresh `Idempotency-Key` makes a double-click one withdrawal.
 */
export function apiRequestWithdrawal(amountCedis: number, methodId: string): Promise<void> {
  return apiFetch<void>('/studio/payouts/withdraw', {
    method: 'POST',
    body: { amount: { amount: amountCedis, currency: 'GHS' }, methodId },
    idempotencyKey: crypto.randomUUID(),
  })
}

export interface NewPayoutMethodInput {
  kind: 'momo' | 'bank'
  label: string
  detail: string
  network: string | null
  walletNumber: string | null
  bankName: string | null
  bankCode: string | null
  accountName: string | null
  accountNumber: string | null
}

/** `POST /v1/studio/payout-methods` — add a structured cash-out destination (WU-PAY-7). */
export function apiAddPayoutMethod(input: NewPayoutMethodInput): Promise<PayoutMethod> {
  return apiFetch<PayoutMethodWire>('/studio/payout-methods', { method: 'POST', body: input }).then(toPayoutMethod)
}

/** `PATCH /v1/studio/payout-methods/:id/default`. */
export function apiSetDefaultPayoutMethod(id: string): Promise<PayoutMethod> {
  return apiFetch<PayoutMethodWire>(`/studio/payout-methods/${id}/default`, { method: 'PATCH' }).then(toPayoutMethod)
}

/** `DELETE /v1/studio/payout-methods/:id`. */
export function apiRemovePayoutMethod(id: string): Promise<void> {
  return apiFetch<void>(`/studio/payout-methods/${id}`, { method: 'DELETE' })
}

/** MoMo networks the backend accepts (Provider wire tokens). */
export const MOMO_NETWORKS: { value: string; label: string }[] = [
  { value: 'mtn', label: 'MTN' },
  { value: 'telecel', label: 'Telecel' },
  { value: 'airteltigo', label: 'AirtelTigo' },
]

/** Ghana bank codes the backend accepts (GhanaBankCode tokens), mirrored — no endpoint lists them. */
export const GHANA_BANK_CODES: { code: string; name: string }[] = [
  { code: 'GCB', name: 'GCB Bank' }, { code: 'CAL', name: 'CalBank' }, { code: 'EBL', name: 'Ecobank Ghana' },
  { code: 'ABB', name: 'Absa Bank Ghana' }, { code: 'UBA', name: 'United Bank for Africa' },
  { code: 'ADB', name: 'Agricultural Development Bank' }, { code: 'APX', name: 'Apex Bank' },
  { code: 'BOA', name: 'Bank of Africa' }, { code: 'BBG', name: 'Bank of Baroda Ghana' },
  { code: 'FBN', name: 'First Bank of Nigeria' }, { code: 'FBL', name: 'Fidelity Bank Ghana' },
  { code: 'FAB', name: 'First Atlantic Bank' }, { code: 'FNB', name: 'First National Bank Ghana' },
  { code: 'GTB', name: 'Guaranty Trust Bank Ghana' }, { code: 'NIB', name: 'National Investment Bank' },
  { code: 'OMN', name: 'OmniBSIC Bank' }, { code: 'PBL', name: 'Prudential Bank' },
  { code: 'RBL', name: 'Republic Bank Ghana' }, { code: 'SGG', name: 'Societe Generale Ghana' },
  { code: 'CBG', name: 'Consolidated Bank Ghana' }, { code: 'SBL', name: 'Stanbic Bank Ghana' },
  { code: 'SCB', name: 'Standard Chartered Bank Ghana' }, { code: 'ZBL', name: 'Zenith Bank Ghana' },
]
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/queries/payouts.test.ts` → PASS. `npm run build` → 0 errors.

- [ ] **Step 5: Commit** — `feat(studio): payouts query + mutation layer`.

---

## Task 3: Wire `studio.payouts.tsx` (read + mutations + Add-Method expansion)

**Files:**
- Modify: `Frontend/src/routes/studio.payouts.tsx`

**Interfaces:**
- Consumes: `payoutsQuery`, `apiRequestWithdrawal`, `apiAddPayoutMethod`, `apiSetDefaultPayoutMethod`, `apiRemovePayoutMethod`, `NewPayoutMethodInput`, `MOMO_NETWORKS`, `GHANA_BANK_CODES` (Task 2); `useQuery`, `useQueryClient`.

**Prep:** Read the whole current file. Preserve every JSX element and class except inside `AddMethodModal`. Keep the helper functions (`cedis2`, `EarningsChart`, `SourceSplit`, `MethodCard`, `StatusPill`, `WithdrawModal`) and their markup. `MIN_PAYOUT`/`withdrawalFee`/`arrivalTime`/`daysUntilFriday` stay imported from `../lib/studio-payouts` (still used for display); only the `getPayouts` data import and `useStudio` usage go away. Keep the `PayoutType`/`PayoutStatus`/`PayoutMethod`/`MethodKind` type imports.

- [ ] **Step 1: Swap the data source.** Remove `getPayouts` from the `studio-payouts` import (keep the helpers + types) and remove the `useStudio` import. In `PayoutsComponent`:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { payoutsQuery, apiRequestWithdrawal, apiAddPayoutMethod, apiSetDefaultPayoutMethod, apiRemovePayoutMethod, type NewPayoutMethodInput } from '../lib/api/queries/payouts'

const EMPTY: Payouts = { available: 0, pending: 0, thisMonth: 0, thisMonthDelta: 0, lifetime: 0, since: '',
  earnings: [], bySource: { sales: 0, royalties: 0, tips: 0 }, methods: [], transactions: [] }
// ...
const queryClient = useQueryClient()
const { data: base = EMPTY } = useQuery(payoutsQuery())
const { available, transactions: txns, methods } = base   // all four now come from `base`
```

(Import the `Payouts` type from `../lib/studio-payouts`.) This makes `available`/`pending`/`thisMonth`/`lifetime`/`earnings`/`bySource`/`methods`/`transactions` all come from the one query — delete the separate `useMemo(() => getPayouts(), [])`.

- [ ] **Step 2: Rewire the four actions** as async + invalidate:

```ts
const invalidate = () => queryClient.invalidateQueries({ queryKey: payoutsQuery().queryKey })

const withdraw = async (amount: number, method: PayoutMethod) => {
  try {
    await apiRequestWithdrawal(amount, method.id)
    await invalidate()
    setWithdrawOpen(false)
    toast(`Withdrawal of ${cedis2(amount)} to ${method.label} requested`, 'success')
  } catch (e) {
    toast(e instanceof Error ? e.message : 'Withdrawal failed', 'error')
  }
}
const setDefaultMethod = async (id: string) => { try { await apiSetDefaultPayoutMethod(id); await invalidate() }
  catch (e) { toast(e instanceof Error ? e.message : 'Could not set default', 'error') } }
const removeMethod = async (id: string) => { try { await apiRemovePayoutMethod(id); await invalidate() }
  catch (e) { toast(e instanceof Error ? e.message : 'Could not remove method', 'error') } }
const addMethod = async (input: NewPayoutMethodInput) => {
  try { await apiAddPayoutMethod(input); await invalidate(); setAddOpen(false); toast(`${input.label} added`, 'success') }
  catch (e) { toast(e instanceof Error ? e.message : 'Could not add method', 'error') }
}
```

The `MethodCard` `onDefault`/`onRemove` and `WithdrawModal` `onConfirm` wiring stay the same (they call these handlers). The pending-clearance line now reads `base.pending`; stats read `base.thisMonth`/`base.thisMonthDelta`/`base.lifetime`/`base.since`; chart reads `base.earnings`; source-split reads `base.bySource` — all already reference `base`, so only the `base` source changed.

- [ ] **Step 3: Expand `AddMethodModal`** to collect the structured fields and build a `NewPayoutMethodInput`. Replace the modal body's two free-text inputs with kind-specific fields (keep the modal shell, the kind toggle, the `Add method` button styling):

```tsx
function AddMethodModal({ isOpen, onClose, onAdd }: {
  isOpen: boolean; onClose: () => void; onAdd: (input: NewPayoutMethodInput) => void
}) {
  const [kind, setKind] = useState<'momo' | 'bank'>('momo')
  const [network, setNetwork] = useState(MOMO_NETWORKS[0].value)
  const [wallet, setWallet] = useState('')
  const [bankCode, setBankCode] = useState(GHANA_BANK_CODES[0].code)
  const [accountName, setAccountName] = useState('')
  const [accountNumber, setAccountNumber] = useState('')

  const momoValid = wallet.trim() !== ''
  const bankValid = accountName.trim() !== '' && accountNumber.trim() !== ''
  const valid = kind === 'momo' ? momoValid : bankValid

  const reset = () => { setKind('momo'); setNetwork(MOMO_NETWORKS[0].value); setWallet(''); setBankCode(GHANA_BANK_CODES[0].code); setAccountName(''); setAccountNumber('') }

  const submit = () => {
    if (!valid) return
    if (kind === 'momo') {
      const label = `${MOMO_NETWORKS.find((n) => n.value === network)!.label} MoMo`
      onAdd({ kind, label, detail: wallet.trim(), network, walletNumber: wallet.trim(),
        bankName: null, bankCode: null, accountName: null, accountNumber: null })
    } else {
      const bank = GHANA_BANK_CODES.find((b) => b.code === bankCode)!
      onAdd({ kind, label: bank.name, detail: accountNumber.trim(), network: null, walletNumber: null,
        bankName: bank.name, bankCode, accountName: accountName.trim(), accountNumber: accountNumber.trim() })
    }
    reset()
  }
  // ... Modal shell + kind toggle unchanged; then kind-specific fields (reuse the existing input classes);
  // for momo: a <select> over MOMO_NETWORKS + a wallet <input>; for bank: a <select> over GHANA_BANK_CODES
  // + accountName <input> + accountNumber <input>. Submit button calls submit(), disabled={!valid}.
}
```

Use the same input class strings the modal already uses so styling is unchanged. `<select>` styling: match the existing `studio.podcasts.new.tsx` select (`appearance-none cursor-pointer`) with the modal's dark input classes.

- [ ] **Step 4: Typecheck + tests + lint.** `npm run build` → 0 errors; `npx vitest run` → whole suite green; `npm run lint` → no NEW errors.

- [ ] **Step 5: Commit** — `feat(studio): wire payouts page to live balance/methods/withdraw`.

---

## Task 4: Wire `studio.index.tsx` balance + activity

**Files:**
- Modify: `Frontend/src/routes/studio.index.tsx`

**Interfaces:**
- Consumes: `payoutsQuery` (Task 2); `useQuery`.

**Prep:** Read the current file. It does `const { balance, transactions } = useStudio()` and uses `balance` in a Stat card and `transactions.slice(0, 5)` in a recent-activity list. Preserve all JSX/classes.

- [ ] **Step 1: Swap the source.** Remove the `useStudio` import; add `import { useQuery } from '@tanstack/react-query'` and `import { payoutsQuery } from '../lib/api/queries/payouts'`. Replace the destructure with:

```ts
const { data: payouts } = useQuery(payoutsQuery())
const balance = payouts?.available ?? 0
const transactions = payouts?.transactions ?? []
```

Leave the rest (the Stat card, `ActivityRow`, `transactions.slice(0, 5)`) unchanged.

- [ ] **Step 2: Typecheck + tests + lint.** `npm run build` → 0 errors; `npx vitest run` green; `npm run lint` no NEW errors.

- [ ] **Step 3: Commit** — `feat(studio): dashboard balance + activity from payouts query`.

---

## Task 5: Delete `studio-context.tsx` + unwrap `StudioProvider`

**Files:**
- Delete: `Frontend/src/features/studio/studio-context.tsx`
- Modify: `Frontend/src/routes/studio.tsx`

**Interfaces:** After Tasks 3–4 there are NO remaining `useStudio` consumers.

**Prep:** Confirm `grep -rn "useStudio\|StudioProvider\|studio-context" Frontend/src` returns only `studio.tsx` (the provider mount) and the context file itself. If `initialsOf` (exported from `studio-context.tsx`) is used anywhere, relocate it first — check `grep -rn "initialsOf" Frontend/src`.

- [ ] **Step 1: Check for stragglers:**

```bash
grep -rn "useStudio\|StudioProvider\|studio-context\|initialsOf" Frontend/src
```
Expected: `studio.tsx` (StudioProvider import + JSX) and `studio-context.tsx` only. If `initialsOf` is imported elsewhere, move that tiny helper into the file that uses it (or `utils/`) as part of this task; if it's unused, it goes away with the file.

- [ ] **Step 2: Unwrap the provider** in `studio.tsx`: drop the `import { StudioProvider }` line and render `<StudioShell />` directly (remove the `<StudioProvider>` wrapper).

- [ ] **Step 3: Delete the file** `Frontend/src/features/studio/studio-context.tsx` (`git rm`).

- [ ] **Step 4: Typecheck + tests + lint.** `npm run build` → 0 errors (this is the real check that nothing still imports the deleted module); `npx vitest run` green; `npm run lint` no NEW errors.

- [ ] **Step 5: Commit** — `refactor(studio): delete now-empty studio-context + StudioProvider`.

---

## Task 6: Live QA + verification gate + PR

**Files:** none (verification only).

- [ ] **Step 1: Full frontend gate** (from `Frontend/`, Node 22 via nvm): `npm run build` clean; `npx vitest run` all green; `npm run lint` no NEW errors vs baseline.

- [ ] **Step 2: Live QA** against the running stack (beatzmedia backend + MinIO up; signed in as an artist):
  - `/studio/payouts` renders live balance / stats / earnings / methods / transactions (or zeros/empty for a fresh artist).
  - Add a **MoMo** method (network + wallet) → appears in the methods grid; add a **Bank** method (bank-code + account) → appears.
  - Set-default on the non-default method → the default badge moves; remove a method → it disappears.
  - Attempt a **withdraw** → either success toast + balance refresh, OR a mapped KYC/floor/balance error toast (a fresh un-KYC'd artist legitimately 4xx — that is correct, not a defect).
  - Dashboard `/studio` shows the same live balance + recent activity.
  - No visual difference except the expanded Add-Method modal.

- [ ] **Step 3: Push + PR.** Push `feat/frontend-studio-payouts`; open a PR titled `feat(studio): wire studio payouts to WU-PAY-4 (balance/methods/withdraw)`, body summarizing the data-swap, the approved Add-Method expansion, the context deletion, no-backend-change, and the test/QA evidence. NEVER stage `application.properties` / `docker-compose.yml`.

- [ ] **Step 4:** After CI green + review, squash-merge (strict branch protection — auto-merge only when all required checks pass).

---

## Self-Review notes

- **Spec coverage:** read whole page (T1/T2/T3), withdraw w/ idempotency (T2/T3), add-method structured + modal expansion (T2/T3), set-default + remove (T2/T3), dashboard balance/activity (T4), context teardown (T5), error handling on all mutations (T3). ✓
- **Type consistency:** `toPayouts`/`PayoutsWire` (T1) consumed by `payoutsQuery` (T2); `NewPayoutMethodInput` (T2) consumed by `AddMethodModal` (T3); `payoutsQuery().queryKey` reused for invalidation in T3 + T4. ✓
- **No placeholders:** every code step carries complete code; the only prose-described piece is the Add-Method modal's JSX layout, which reuses existing class strings (explicitly noted). ✓
