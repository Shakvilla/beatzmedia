# Frontend Admin Trust, Compliance & Settings Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the last three mock-backed admin screens — Trust & safety, Compliance, and Settings — to their live endpoints, marking the unbacked controls as unavailable rather than leaving them looking functional.

**Architecture:** The established admin idiom — one `queries/admin-*.ts` per domain, wire types + `toX` mappers in the shared `lib/api/mappers.ts`, routes on `useQuery` with `AdminLoadError` + `isPending` branches, mutations awaiting then invalidating. Adds one date helper and one reason modal.

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **Node 22 via nvm** for all `npm`/`npx`: prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null &&`.
- **Real typecheck gate is `npm run build`** (`tsc -b`). Every task runs both it and vitest.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`. Frontend-only.
- **Use `isPending`, never `isLoading`** — a paused query reports `isLoading === false` with no data and would render a false empty state. No `isLoading` remains anywhere in the admin routes; do not reintroduce it.
- **Query keys:** `['admin','risk','board']`, `['admin','compliance','list']`, `['admin','settings']`.
- **Money and percentages on the settings contract:** `payoutMinimum` is **bare cedis** on the wire (the server converts to/from minor units) — do **no** minor-unit arithmetic client-side. `platformFeePct` is an **integer percent** (`30`, not `0.30`).
- **The settings `PUT` is a FULL REPLACE.** Every field including the nested `providers`/`flags` objects is required; a partial body is a 422, not a merge. Always send the complete object.
- **`POST /risk/{id}/ban` requires a non-blank `reason`** (422 otherwise) and also bans the subject's account.
- **New imports in the TOP import block; `import type`** for type-only imports.
- **A test named for a behavior must assert that behavior.**

### Unbacked surface — mark, do not wire

- **Provider toggles** (MoMo/Vodafone/AirtelTigo/Card/Bank): the PUT accepts and **silently discards** them; the GET hardcodes all five `true`. Render them **disabled** with a short note. Toggling them today would appear to save then revert on reload.
- **"Admin team & roles"** (invite / change role / remove): **no endpoint exists anywhere** in the admin package. Keep its local behaviour, add a label saying changes are local-only.
- **Trust KPIs** `chargebackRate`, `suspiciousSignups`, `botStreams` are hardcoded `"0%"`/`0`/`"0%"` server-side; only `fraudFlags` is real.
- **Compliance `/export`** is a documented stub (mints a job id, no worker). Wire it; keep the existing toast copy.
- `defaultCurrency` stays read-only. Tip fee / bundle discount / service fee are not on this contract.

---

### Task 1: `dueLabel` date helper

**Files:**
- Modify: `Frontend/src/lib/format.ts`
- Test: `Frontend/src/lib/format.test.ts`

**Interfaces:**
- Produces (used by Tasks 2 & 5): `dueLabel(iso: string | null, status: string, now?: number): string`.

**Why:** compliance `due` arrives as a nullable ISO instant, where the mock supplied prose (`"in 12 days"`, `"overdue 1 day"`, `"completed"`). This reproduces that wording from real data.

- [ ] **Step 1: Write the failing tests**

Extend the existing top import from `./format` with `dueLabel`, then append to `format.test.ts`:

```ts
describe('dueLabel', () => {
  const NOW = Date.parse('2026-07-31T12:00:00Z')

  it('reads "completed" for a completed request regardless of the date', () => {
    expect(dueLabel('2026-07-01T12:00:00Z', 'completed', NOW)).toBe('completed')
  })

  it('counts days forward for a future due date', () => {
    expect(dueLabel('2026-08-12T12:00:00Z', 'new', NOW)).toBe('in 12 days')
    expect(dueLabel('2026-08-01T12:00:00Z', 'new', NOW)).toBe('in 1 day')
  })

  it('says due today when it lands inside the current day', () => {
    expect(dueLabel('2026-07-31T18:00:00Z', 'new', NOW)).toBe('due today')
  })

  it('counts days back for an overdue date, singular at one', () => {
    expect(dueLabel('2026-07-30T12:00:00Z', 'overdue', NOW)).toBe('overdue 1 day')
    expect(dueLabel('2026-07-25T12:00:00Z', 'overdue', NOW)).toBe('overdue 6 days')
  })

  it('returns an em dash when there is no due date, rather than inventing one', () => {
    expect(dueLabel(null, 'new', NOW)).toBe('—')
    expect(dueLabel('not-a-date', 'new', NOW)).toBe('—')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/format.test.ts`
Expected: FAIL — `dueLabel` is not exported.

- [ ] **Step 3: Implement at the end of `format.ts`**

```ts
/**
 * Renders a compliance request's due date the way the screen has always read it — `"in 12 days"`,
 * `"due today"`, `"overdue 1 day"` — from the nullable ISO instant the API actually sends.
 * A completed request shows `"completed"` whatever its date; a missing or unparseable date shows
 * an em dash rather than a fabricated interval.
 */
export function dueLabel(iso: string | null, status: string, now: number = Date.now()): string {
  if (status === 'completed') return 'completed'
  if (!iso) return '—'
  const ms = Date.parse(iso)
  if (!Number.isFinite(ms)) return '—'
  const days = Math.round((ms - now) / 86_400_000)
  if (days === 0) return 'due today'
  if (days > 0) return `in ${days} day${days === 1 ? '' : 's'}`
  const over = Math.abs(days)
  return `overdue ${over} day${over === 1 ? '' : 's'}`
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/format.test.ts`
Expected: PASS.

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/format.ts Frontend/src/lib/format.test.ts
git commit -m "feat(admin): dueLabel helper for compliance due dates"
```

---

### Task 2: Trust, compliance, and settings mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `RiskSignal`, `RiskLevel`, `RiskStatus`, `ComplianceRequest`, `ComplianceType`, `ComplianceStatus`, `PlatformSettings` from `../admin-data`; `relativeTimeAgo`, `dueLabel` from `../format`.
- Produces (used by Tasks 3–6): types `RiskSignalWire`, `RiskKpisWire`, `RiskBoardWire`, `RiskBoard`, `ComplianceRequestWire`, `PlatformSettingsWire`; functions `toRiskSignal`, `toRiskBoard`, `toComplianceRequest`, `toPlatformSettings`, `toSettingsRequest`.

- [ ] **Step 1: Write the failing tests**

Extend the top import from `./mappers` with the five functions and the wire types you assert on, then append:

```ts
describe('trust mappers', () => {
  it('maps a signal, narrowing unions and rendering the ISO time as relative', () => {
    const s = toRiskSignal(
      { id: 'r1', subject: '@kwabz', type: 'Chargeback', detail: 'Card · ₵180', level: 'high', time: '2026-07-31T10:00:00Z', status: 'open' },
      Date.parse('2026-07-31T12:00:00Z'),
    )
    expect(s).toEqual({ id: 'r1', subject: '@kwabz', type: 'Chargeback', detail: 'Card · ₵180', level: 'high', time: '2h ago', status: 'open' })
  })

  it('falls back safely for an unrecognised level or status rather than trusting the wire', () => {
    const s = toRiskSignal({ id: 'r2', subject: 'x', type: 't', detail: 'd', level: 'nonsense', time: null, status: 'nonsense' })
    expect(s.level).toBe('low')      // least-alarming level, never invents "high"
    expect(s.status).toBe('open')    // never reads as resolved
    expect(s.time).toBe('')
  })

  it('maps the board, carrying the honest-zero KPIs through untouched', () => {
    const b = toRiskBoard({ kpis: { chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 3, botStreams: '0%' }, signals: [] })
    expect(b.kpis).toEqual({ chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 3, botStreams: '0%' })
    expect(b.signals).toEqual([])
  })
})

describe('compliance mapper', () => {
  it('renders the due instant as prose and narrows the unions', () => {
    const c = toComplianceRequest(
      { id: 'c1', type: 'DSAR-export', subject: '@ama_b', detail: 'Data export', due: '2026-08-12T12:00:00Z', status: 'new' },
      Date.parse('2026-07-31T12:00:00Z'),
    )
    expect(c).toEqual({ id: 'c1', type: 'DSAR-export', subject: '@ama_b', detail: 'Data export', due: 'in 12 days', status: 'new' })
  })

  it('shows an em dash for a null due date instead of inventing one', () => {
    expect(toComplianceRequest({ id: 'c2', type: 'Tax', subject: 's', detail: 'd', due: null, status: 'new' }).due).toBe('—')
  })
})

describe('platform settings mappers', () => {
  const wire = {
    platformFeePct: 30, payoutDay: 'Friday', payoutMinimum: 10, defaultCurrency: 'GHS', maintenanceMode: false,
    providers: { momo: true, vodafone: true, airteltigo: true, card: true, bank: true },
    flags: { artistSignups: true, podcasts: true, events: false, tipping: true, fanMessaging: false },
  }

  it('passes the integer percent and bare-cedis minimum straight through', () => {
    const s = toPlatformSettings(wire)
    expect(s.platformFeePct).toBe(30)   // integer percent, not a fraction
    expect(s.payoutMinimum).toBe(10)    // bare cedis; the server owns the minor-unit conversion
    expect(s.flags.events).toBe(false)
  })

  it('toSettingsRequest sends the COMPLETE object (the PUT is a full replace, not a patch)', () => {
    const body = toSettingsRequest(toPlatformSettings(wire))
    expect(Object.keys(body).sort()).toEqual(
      ['defaultCurrency', 'flags', 'maintenanceMode', 'payoutDay', 'payoutMinimum', 'platformFeePct', 'providers'].sort(),
    )
    expect(body.providers).toEqual(wire.providers)
    expect(body.flags).toEqual(wire.flags)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — the five functions are not exported.

- [ ] **Step 3: Extend the existing top import lines of `mappers.ts`**

Add the admin-data types to the existing `../admin-data` type import, and `dueLabel` to the existing `../format` import. Do not add duplicate import lines from either module.

- [ ] **Step 4: Append at the END of `mappers.ts`**

```ts
// ── Admin trust & safety (AdminRiskResource) ──────────────────────────────────
export interface RiskSignalWire {
  id: string
  subject: string
  type: string
  detail: string
  level: string
  time: string | null
  status: string
}
export interface RiskKpisWire {
  chargebackRate: string
  suspiciousSignups: number
  fraudFlags: number
  botStreams: string
}
export interface RiskBoardWire { kpis: RiskKpisWire; signals: RiskSignalWire[] }
export interface RiskBoard { kpis: RiskKpisWire; signals: RiskSignal[] }

const RISK_LEVELS: RiskLevel[] = ['high', 'med', 'low']
const RISK_STATUSES: RiskStatus[] = ['open', 'cleared', 'banned']

/**
 * An unrecognised level falls back to `low` and an unrecognised status to `open` — the UI colours
 * rows by both, so a new wire value must never render as a louder alarm than it is, nor as
 * already-resolved work.
 */
export function toRiskSignal(w: RiskSignalWire, now?: number): RiskSignal {
  return {
    id: w.id,
    subject: w.subject,
    type: w.type,
    detail: w.detail,
    level: (RISK_LEVELS as string[]).includes(w.level) ? (w.level as RiskLevel) : 'low',
    time: w.time ? relativeTimeAgo(w.time, now) : '',
    status: (RISK_STATUSES as string[]).includes(w.status) ? (w.status as RiskStatus) : 'open',
  }
}

/**
 * `kpis` passes through as served. Only `fraudFlags` is real; `chargebackRate`,
 * `suspiciousSignups` and `botStreams` are documented Category-B zeros with no fraud-analytics
 * subsystem behind them, so they are shown as the honest zeros they are.
 */
export function toRiskBoard(w: RiskBoardWire, now?: number): RiskBoard {
  return { kpis: w.kpis, signals: w.signals.map((s) => toRiskSignal(s, now)) }
}

// ── Admin compliance (AdminComplianceResource) ────────────────────────────────
export interface ComplianceRequestWire {
  id: string
  type: string
  subject: string
  detail: string
  due: string | null
  status: string
}

const COMPLIANCE_TYPES: ComplianceType[] = ['DSAR-export', 'DSAR-delete', 'Takedown', 'Tax']
const COMPLIANCE_STATUSES: ComplianceStatus[] = ['new', 'in_progress', 'completed', 'overdue']

export function toComplianceRequest(w: ComplianceRequestWire, now?: number): ComplianceRequest {
  const status = (COMPLIANCE_STATUSES as string[]).includes(w.status) ? (w.status as ComplianceStatus) : 'new'
  return {
    id: w.id,
    type: (COMPLIANCE_TYPES as string[]).includes(w.type) ? (w.type as ComplianceType) : 'Tax',
    subject: w.subject,
    detail: w.detail,
    due: dueLabel(w.due, status, now),
    status,
  }
}

// ── Admin platform settings (AdminSettingsResource) ───────────────────────────
export interface PlatformSettingsWire {
  platformFeePct: number
  payoutDay: string
  payoutMinimum: number
  defaultCurrency: string
  maintenanceMode: boolean
  providers: { momo: boolean; vodafone: boolean; airteltigo: boolean; card: boolean; bank: boolean }
  flags: { artistSignups: boolean; podcasts: boolean; events: boolean; tipping: boolean; fanMessaging: boolean }
}

/**
 * `platformFeePct` is an integer percent and `payoutMinimum` is bare cedis — the server owns the
 * minor-unit conversion, so nothing here multiplies or divides by 100.
 */
export function toPlatformSettings(w: PlatformSettingsWire): PlatformSettings {
  return {
    platformFeePct: w.platformFeePct,
    payoutDay: w.payoutDay,
    payoutMinimum: w.payoutMinimum,
    defaultCurrency: w.defaultCurrency,
    maintenanceMode: w.maintenanceMode,
    providers: { ...w.providers },
    flags: { ...w.flags },
  }
}

/**
 * The `PUT` is a FULL REPLACE: every field, including the nested `providers` and `flags` objects,
 * is required server-side and a partial body is rejected as a 422 rather than merged.
 */
export function toSettingsRequest(s: PlatformSettings): PlatformSettingsWire {
  return {
    platformFeePct: s.platformFeePct,
    payoutDay: s.payoutDay,
    payoutMinimum: s.payoutMinimum,
    defaultCurrency: s.defaultCurrency,
    maintenanceMode: s.maintenanceMode,
    providers: { ...s.providers },
    flags: { ...s.flags },
  }
}
```

- [ ] **Step 5: Run the tests, then typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts && npm run build`
Expected: tests PASS; build 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): trust, compliance, and settings wire mappers"
```

---

### Task 3: Query + mutation layers

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-trust.ts` and `admin-trust.test.ts`
- Create: `Frontend/src/lib/api/queries/admin-compliance.ts` and `admin-compliance.test.ts`
- Create: `Frontend/src/lib/api/queries/admin-settings.ts` and `admin-settings.test.ts`

**Interfaces:**
- Consumes the Task 2 mappers and `apiFetch` from `../client`.
- Produces (used by Tasks 4–6): `riskBoardQuery()`, `apiReviewSignal`, `apiClearSignal`, `apiBanSignal(id, reason)`; `complianceQuery()`, `apiStartRequest`, `apiCompleteRequest`, `apiExportRequest`, `apiNoticeRequest`; `platformSettingsQuery()`, `apiSaveSettings(settings)`.

- [ ] **Step 1: Write the failing tests**

Create the three test files. Use the `mockFetch` helper shape already used by the sibling `admin-*.test.ts` files. The assertions that matter:

```ts
// admin-trust.test.ts
it('riskBoardQuery hits /v1/admin/risk', /* assert URL + key ['admin','risk','board'] */)
it('apiBanSignal POSTs the reason (the API rejects a blank one)', async () => {
  // assert URL /v1/admin/risk/r1/ban, method POST, and JSON.parse(body) === { reason: 'Fraud' }
})
it('apiClearSignal and apiReviewSignal POST with no body', /* assert URLs + method */)

// admin-compliance.test.ts
it('complianceQuery hits /v1/admin/compliance with NO type param (the DSAR chip spans two wire types)', async () => {
  // assert the URL is exactly '/v1/admin/compliance' — no query string
})
it('start / complete / export / notice POST to their own paths', /* assert each URL + method */)

// admin-settings.test.ts
it('platformSettingsQuery hits /v1/admin/settings', /* assert URL + key ['admin','settings'] */)
it('apiSaveSettings PUTs the COMPLETE object, including providers and flags', async () => {
  // assert method PUT and that JSON.parse(body) has all 7 keys with providers/flags nested objects
})
```

- [ ] **Step 2: Run them to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-trust.test.ts src/lib/api/queries/admin-compliance.test.ts src/lib/api/queries/admin-settings.test.ts`
Expected: FAIL — the three modules do not exist.

- [ ] **Step 3: Write the three modules**

`admin-trust.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toRiskBoard, type RiskBoardWire } from '../mappers'

/** `GET /v1/admin/risk` — the risk KPI strip and open signals. `moderator` or `super-admin`. */
export function riskBoardQuery() {
  return queryOptions({
    queryKey: ['admin', 'risk', 'board'],
    queryFn: async () => toRiskBoard(await apiFetch<RiskBoardWire>('/admin/risk')),
  })
}

/** `POST /v1/admin/risk/:id/review` — logs a review; deliberately does NOT change the status. */
export function apiReviewSignal(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/review`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/risk/:id/clear` — `open → cleared`. */
export function apiClearSignal(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/clear`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/risk/:id/ban` — `open → banned`, and bans the subject's account.
 * `reason` is `@NotBlank` server-side (422 otherwise) and is what lands in the audit trail.
 */
export function apiBanSignal(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/ban`, { method: 'POST', body: { reason } }).then(() => undefined)
}
```

`admin-compliance.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toComplianceRequest, type ComplianceRequestWire } from '../mappers'

/**
 * `GET /v1/admin/compliance` — the full request list, unfiltered.
 * The endpoint's `?type=` takes one exact wire value, but the UI's `DSAR` chip covers BOTH
 * `DSAR-export` and `DSAR-delete`, so filtering stays client-side over the whole list.
 */
export function complianceQuery() {
  return queryOptions({
    queryKey: ['admin', 'compliance', 'list'],
    queryFn: async () => (await apiFetch<ComplianceRequestWire[]>('/admin/compliance')).map((c) => toComplianceRequest(c)),
  })
}

/** `POST /v1/admin/compliance/:id/start` — `new|overdue → in_progress`. */
export function apiStartRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/start`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/compliance/:id/complete` — `→ completed`. */
export function apiCompleteRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/complete`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/compliance/:id/export` — queues a DSAR export. A documented Category-B stub:
 * it mints a job id and audits, but no worker exists and the request's status does not change.
 */
export function apiExportRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/export`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/compliance/:id/notice` — records a takedown notice; audit only, no status change. */
export function apiNoticeRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/notice`, { method: 'POST' }).then(() => undefined)
}
```

`admin-settings.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import type { PlatformSettings } from '../../admin-data'
import { apiFetch } from '../client'
import { toPlatformSettings, toSettingsRequest, type PlatformSettingsWire } from '../mappers'

/** `GET /v1/admin/settings` — the platform settings. `super-admin` only. */
export function platformSettingsQuery() {
  return queryOptions({
    queryKey: ['admin', 'settings'],
    queryFn: async () => toPlatformSettings(await apiFetch<PlatformSettingsWire>('/admin/settings')),
  })
}

/**
 * `PUT /v1/admin/settings` — a FULL REPLACE. Every field including the nested `providers` and
 * `flags` objects is required; a partial body is a 422, not a merge. `super-admin` only.
 *
 * Note the server accepts but does not persist `providers.*` (no per-provider subsystem), and it
 * preserves the tip-fee, bundle-discount and service-fee constants, which are not on this contract.
 */
export function apiSaveSettings(settings: PlatformSettings): Promise<PlatformSettings> {
  return apiFetch<PlatformSettingsWire>('/admin/settings', {
    method: 'PUT',
    body: toSettingsRequest(settings),
  }).then(toPlatformSettings)
}
```

- [ ] **Step 4: Run the three test files, then typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-trust.test.ts src/lib/api/queries/admin-compliance.test.ts src/lib/api/queries/admin-settings.test.ts && npm run build`
Expected: tests PASS; build 0 errors.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-trust.ts Frontend/src/lib/api/queries/admin-trust.test.ts Frontend/src/lib/api/queries/admin-compliance.ts Frontend/src/lib/api/queries/admin-compliance.test.ts Frontend/src/lib/api/queries/admin-settings.ts Frontend/src/lib/api/queries/admin-settings.test.ts
git commit -m "feat(admin): trust, compliance, and settings query layers"
```

---

### Task 4: Wire Trust & safety (with the ban-reason modal)

**Files:**
- Modify: `Frontend/src/routes/admin.trust.tsx`

**Interfaces:**
- Consumes: `riskBoardQuery`, `apiReviewSignal`, `apiClearSignal`, `apiBanSignal` from `../lib/api/queries/admin-trust`; `AdminLoadError`; `Modal` from `../components/ui/modal`; `useQuery`, `useQueryClient`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace

```ts
import { getRiskSignals, RISK_KPIS, type RiskSignal, type RiskLevel } from '../lib/admin-data'
```

with (`RiskSignal`/`RiskLevel` stay — they type the row and pill):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { RiskSignal, RiskLevel } from '../lib/admin-data'
import { riskBoardQuery, apiReviewSignal, apiClearSignal, apiBanSignal } from '../lib/api/queries/admin-trust'
import { AdminLoadError } from '../components/admin/load-error'
import { Modal } from '../components/ui/modal'
```

and add `useRef` to the react import.

Replace the component's state block with:

```tsx
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, isPending, refetch } = useQuery(riskBoardQuery())
  const signals = data?.signals ?? []
  const kpis = data?.kpis ?? { chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 0, botStreams: '0%' }
  const paged = usePaged(signals)

  const [banTarget, setBanTarget] = useState<RiskSignal | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string, tone: 'success' | 'info' = 'success') => {
    if (inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    let ok = false
    try { await fn(); ok = true } catch { toast(errMsg, 'error') }
    finally {
      inFlight.current = false
      setSubmitting(false)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'risk'] })
      if (ok) toast(okMsg, tone)
    }
  }

  const review = (s: RiskSignal) => runAction(() => apiReviewSignal(s.id), `Reviewing ${s.subject}`, 'Could not record the review', 'info')
  const clear = (s: RiskSignal) => runAction(() => apiClearSignal(s.id), `${s.subject} cleared`, 'Could not clear the signal')
  const ban = (s: RiskSignal, reason: string) => runAction(() => apiBanSignal(s.id, reason), `${s.subject} banned`, 'Could not ban the subject')
```

- [ ] **Step 2: Point the KPI strip at live data and add the error/loading branches**

The four `Kpi` values become `kpis.chargebackRate`, `kpis.suspiciousSignups.toLocaleString()`, `kpis.fraudFlags.toLocaleString()`, `kpis.botStreams` — the same expressions as today, sourced from the query. Their `sub` labels are unchanged.

In the table body, replace the bare `paged.pageItems.map(...)` with the standard ladder:

```tsx
            {isError ? (
              <AdminLoadError label="Couldn't load risk signals." onRetry={() => refetch()} />
            ) : isPending ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : signals.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No risk signals.</div>
            ) : (
              paged.pageItems.map((s) => (
                <SignalRow key={s.id} signal={s} disabled={submitting}
                  onReview={() => review(s)}
                  onBan={() => setBanTarget(s)}
                  onClear={() => clear(s)}
                />
              ))
            )}
```

`SignalRow` gains a `disabled?: boolean` prop forwarded to its three menu items (`MenuItem` already supports `disabled`).

- [ ] **Step 3: Add the ban-reason modal**

`POST /ban` rejects a blank reason and bans the subject's account, so the reason must be collected. Add this component at the end of the file, mirroring the catalog takedown modal's structure and classes:

```tsx
function BanModal({ signal, onClose, onConfirm }: { signal: RiskSignal | null; onClose: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState('')
  const REASONS = ['Payment fraud', 'Chargeback abuse', 'Bot activity', 'Impersonation', 'Other']
  return (
    <Modal isOpen={signal !== null} onClose={onClose} title={`Ban ${signal?.subject ?? ''}`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The subject loses access immediately and the account is banned. A reason is required and is recorded in the audit trail.</p>
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button key={r} onClick={() => setReason(r)} className={cn('h-9 px-3.5 rounded-full text-xs font-bold border transition-colors', reason === r ? 'border-beatz-red bg-beatz-red/10 text-beatz-red' : 'border-white/10 text-white/70 hover:border-white/20')}>{r}</button>
          ))}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Add a note…" className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim()} className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">Ban</button>
        </div>
      </div>
    </Modal>
  )
}
```

and render it at the end of the page's root element:

```tsx
      <BanModal signal={banTarget} onClose={() => setBanTarget(null)}
        onConfirm={(reason) => { const s = banTarget; setBanTarget(null); if (s) ban(s, reason) }} />
```

- [ ] **Step 4: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.trust.tsx
git commit -m "feat(admin): wire trust & safety board with a ban-reason modal"
```

---

### Task 5: Wire Compliance

**Files:**
- Modify: `Frontend/src/routes/admin.compliance.tsx`

**Interfaces:**
- Consumes: `complianceQuery`, `apiStartRequest`, `apiCompleteRequest`, `apiExportRequest`, `apiNoticeRequest` from `../lib/api/queries/admin-compliance`; `AdminLoadError`; `useQuery`, `useQueryClient`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace the `getCompliance` import with the query imports (keeping the `ComplianceRequest`/type imports the sub-components need), add `useRef` to the react import, and replace the component's state block with:

```tsx
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, isPending, refetch } = useQuery(complianceQuery())
  const items = data ?? []
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string) => {
    if (inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    let ok = false
    try { await fn(); ok = true } catch { toast(errMsg, 'error') }
    finally {
      inFlight.current = false
      setSubmitting(false)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'compliance'] })
      if (ok) toast(okMsg, 'success')
    }
  }
```

Keep the existing `filter` state and the client-side `inFilter` bucketing — the `DSAR` chip spans two wire types, so filtering must stay client-side over the full list.

- [ ] **Step 2: Wire the four row actions**

```tsx
  const start = (id: string) => runAction(() => apiStartRequest(id), 'Request started', 'Could not start the request')
  const complete = (id: string) => runAction(() => apiCompleteRequest(id), 'Request completed', 'Could not complete the request')
  const download = (id: string) => runAction(() => apiExportRequest(id), 'Preparing data export', 'Could not queue the export')
  const notice = (id: string) => runAction(() => apiNoticeRequest(id), 'Generating takedown notice', 'Could not record the notice')
```

Pass these to the row in place of the current local handlers, keeping each toast's existing copy. The header's `open`/`overdue` counts keep their existing client-side derivation from `items`.

- [ ] **Step 3: Add the error/loading branches**

Replace the table body's bare rows expression with the standard ladder (`isError` → `AdminLoadError` with label `"Couldn't load compliance requests."`, then `isPending` → `Loading…`, then the existing empty state, then the rows).

- [ ] **Step 4: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.compliance.tsx
git commit -m "feat(admin): wire compliance queue (start/complete/export/notice)"
```

---

### Task 6: Wire Settings, and mark the unbacked controls

**Files:**
- Modify: `Frontend/src/routes/admin.settings.tsx`

**Interfaces:**
- Consumes: `platformSettingsQuery`, `apiSaveSettings` from `../lib/api/queries/admin-settings`; `AdminLoadError`; `useQuery`, `useQueryClient`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace the `getPlatformSettings` import with the query imports (keep `getAdminTeam`, `ADMIN_ROLES`, and the three types — the team section stays local), and replace the component's state block with:

```tsx
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, isPending, refetch } = useQuery(platformSettingsQuery())
  const [draft, setDraft] = useState<PlatformSettings | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)

  // The draft is seeded from the server copy on first load and after each save.
  const s = draft ?? data ?? null
  const dirty = useMemo(() => (s && data ? JSON.stringify(s) !== JSON.stringify(data) : false), [s, data])

  const setS = (fn: (p: PlatformSettings) => PlatformSettings) => setDraft((p) => (p ? fn(p) : data ? fn(data) : null))
```

with `[team, setTeam]`, `inviteEmail`, `inviteRole` unchanged.

**Do not** seed the draft in an effect — deriving it as `draft ?? data` keeps the server copy authoritative until the user edits, and makes `dirty` a true comparison against what the server actually holds.

- [ ] **Step 2: Wire the Save button**

```tsx
  const save = async () => {
    if (!s || inFlight.current) return
    inFlight.current = true
    setSaving(true)
    let ok = false
    try { await apiSaveSettings(s); ok = true } catch { toast('Could not save platform settings', 'error') }
    finally {
      inFlight.current = false
      setSaving(false)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'settings'] })
      setDraft(null)   // fall back to the refetched server copy, so the form shows what was really saved
      if (ok) toast('Platform settings saved', 'success')
    }
  }
```

and the button becomes `onClick={save} disabled={!dirty || saving}` (existing classes unchanged).

Clearing the draft on both outcomes matters: on success the form shows what the server actually stored (including the silently-discarded provider values reverting to `true`), and on failure it snaps back to server truth rather than displaying an edit that never landed.

- [ ] **Step 3: Gate the page on error/loading**

Wrap the four `Section`s (Platform, Payment providers, Feature flags, Admin team) so they render only with data:

```tsx
      {isError ? (
        <AdminLoadError label="Couldn't load platform settings." onRetry={() => refetch()} />
      ) : isPending || !s ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* the existing four Sections, unchanged apart from Steps 4 and 5 */}
        </>
      )}
```

- [ ] **Step 4: Mark the provider toggles unavailable**

The `PUT` accepts `providers.*` and discards them; the `GET` always returns all five `true`. Toggling one would appear to save and silently revert on reload. Change that section's `desc` and disable its five toggles:

```tsx
      <Section title="Payment providers" desc="Which methods fans can pay with. Not yet configurable — every method is currently enabled platform-wide.">
        <ToggleRow label="MTN MoMo" checked={s.providers.momo} onChange={() => {}} disabled />
        <ToggleRow label="Vodafone Cash" checked={s.providers.vodafone} onChange={() => {}} disabled />
        <ToggleRow label="AirtelTigo Money" checked={s.providers.airteltigo} onChange={() => {}} disabled />
        <ToggleRow label="Card" checked={s.providers.card} onChange={() => {}} disabled />
        <ToggleRow label="Bank transfer" checked={s.providers.bank} onChange={() => {}} disabled last />
      </Section>
```

`ToggleRow` and `Toggle` gain a `disabled?: boolean` prop: `ToggleRow` forwards it to `Toggle`, and `Toggle` applies `disabled` to its button plus `disabled:opacity-40 disabled:cursor-not-allowed`. Read `Frontend/src/components/ui/toggle.tsx` first and extend it minimally — do not restyle the enabled state.

The `setProvider` helper becomes unused; delete it.

- [ ] **Step 5: Label the admin-team section local-only**

No admin-team endpoint exists anywhere in the backend, and this section now sits beside controls that genuinely persist. Change only its `desc`:

```tsx
      <Section title="Admin team & roles" desc="Who can access the console and what they can do. Changes here are not saved yet — team management has no backend.">
```

Its local invite/role-change/remove behaviour is unchanged.

- [ ] **Step 6: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/routes/admin.settings.tsx Frontend/src/components/ui/toggle.tsx
git commit -m "feat(admin): wire platform settings save, mark unbacked controls unavailable"
```

---

### Task 7: Live QA + PR (USER-run gate)

**Files:** none. The controller does NOT run `verify.sh`; CI is authoritative.

- [ ] **Step 1: Final full gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full suite green.

- [ ] **Step 2: Live QA** (backend on :18080, Vite proxy → :18080)

**Compliance and Settings are `super-admin` only**; Risk also accepts `moderator`. Revert the Vite proxy only at the very END of the session — reverting mid-session makes Vite hot-reload it back to `:8080` and every call fails with `TENANT_MISSING`.

  - Trust: the board loads; the KPI strip shows the honest zeros with only `fraudFlags` real; signal rows show relative times; **Ban opens the modal, requires a reason, and the row moves to `banned`**; Clear moves it to `cleared`; Review records without changing the status.
  - Compliance: requests load with `"in N days"` / `"overdue N days"` / `"completed"` due labels; the DSAR chip matches both DSAR types; Start and Complete persist; Download and Generate notice fire without error.
  - Settings: values load from the server; **change the platform fee, Save, reload — the new value persists**; the provider toggles are visibly disabled with the explanatory note; the team section shows its local-only note; a failed save snaps the form back to server truth.
  - Force a load error on each screen → the distinct "Couldn't load …" affordance rather than an empty state.

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-trust-compliance-settings
gh pr create --base master --title "feat(admin): wire Trust & safety, Compliance, and Settings" --body "<DoD checklist; the full-replace PUT semantics; the two deliberate UI additions; the unbacked-control list; note this completes the admin console>"
```

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-trust-compliance-settings` off `master` (spec `fdd31c5`). BASE for the first review package is the plan commit.
- **Do NOT** touch backend or stage backend secrets.
- **Already on master — import, do not recreate:** `AdminLoadError`, `Modal`, `Toggle`, and `format.ts`'s `relativeTime` / `relativeTimeAgo` / `monthDay`.
- **Use `isPending`, never `isLoading`.**
- This is the final admin-console slice; after it, every admin route is wired.
