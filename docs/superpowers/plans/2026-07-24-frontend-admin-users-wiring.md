# Frontend Admin Users Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the admin Users list (`admin.users`) and detail (`admin.users.$userId`) screens from the `admin-data` mock to the live `AdminUsersResource` endpoints, with no visual change.

**Architecture:** Same idiom as the merged studio/support slices — a per-domain `queries/admin-users.ts` (TanStack `queryOptions` reads + free `api*` mutation functions), wire types + `toX` mappers in the shared `lib/api/mappers.ts`, and routes swapped from local `useState(mock)` to `useQuery(...)` + `useQueryClient().invalidateQueries` on mutation. Introduces one reusable `AdminLoadError` component (the new distinct load-error affordance).

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest + RTL, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **No visual change.** JSX, `className` strings, copy, and layout are preserved verbatim. Only the data source, the wired actions, and the added load-error affordance change.
- **Node 22 via nvm** for all `npm`/`npx` commands (default shell node is v10 and crashes the tooling): prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null`.
- **Real typecheck gate is `npm run build`** (`tsc -b`), NOT vitest (esbuild does not typecheck). Every task runs both.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`.
- **Mapper outputs match `admin-data.ts` types exactly** (`AdminUserRow`, `UserStatus`, `UserRole`, `UserActionLog`).
- **Query-key convention:** `['admin', <domain>, <resource>]` — here `['admin','users','list']` and `['admin','users','detail', id]`.
- **New imports go in the top import block** of each file (mappers.ts/test had a recurring mid-file-import nit in the prior slice — do not repeat it).
- **`import type`** for type-only imports (the repo uses `verbatimModuleSyntax`).
- **Toast variants** are `'success' | 'error' | 'info'`. Failures toast `'error'`.
- **Kept as-is (Category B, do NOT wire):** CSV export, bulk is wired but per-item; impersonate, reset-password, email-user, data-export, device sign-out stay toasts; the detail page's Activity/Orders/Devices sections stay on `getUserDetail()`.

---

### Task 1: User mappers + wire types

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts` (add imports at top block; add types + functions at end)
- Test: `Frontend/src/lib/api/mappers.test.ts` (add cases; imports at top block)

**Interfaces:**
- Consumes: `AdminUserRow`, `UserRole`, `UserStatus`, `UserActionLog` from `../../admin-data`.
- Produces (used by Task 2):
  - types `AdminUserRowWire`, `UserCountsWire`, `PagedUsersWire`, `UserActionLogWire`, `UserDetailWire`, `UserCounts`, `AdminUsersList`, `AdminUserDetailData`
  - functions `toAdminUserRow(w: AdminUserRowWire): AdminUserRow`, `toUserCounts(w: UserCountsWire): UserCounts`, `toUsersList(w: PagedUsersWire): AdminUsersList`, `toUserActionLog(w: UserActionLogWire): UserActionLog`, `toUserDetail(w: UserDetailWire): AdminUserDetailData`

- [ ] **Step 1: Write the failing tests**

Add to the top import block of `Frontend/src/lib/api/mappers.test.ts` (alongside the existing imports from `./mappers`):

```ts
import {
  toAdminUserRow, toUsersList, toUserDetail,
  type PagedUsersWire, type UserDetailWire,
} from './mappers'
```

Append these tests to `Frontend/src/lib/api/mappers.test.ts`:

```ts
describe('admin users mappers', () => {
  const rowWire = {
    id: 'u1', name: 'Ama Boateng', initial: 'AB', email: 'ama@x.com',
    role: 'artist', verified: true, joined: 'Jan 2025', lastActive: '2h', status: 'active',
  }

  it('toAdminUserRow maps 1:1 with narrowed unions', () => {
    const r = toAdminUserRow(rowWire)
    expect(r).toEqual({
      id: 'u1', name: 'Ama Boateng', initial: 'AB', email: 'ama@x.com',
      role: 'artist', verified: true, joined: 'Jan 2025', lastActive: '2h', status: 'active',
    })
  })

  it('toUsersList maps items + counts', () => {
    const wire: PagedUsersWire = {
      items: [rowWire], page: 1, size: 100, total: 1,
      counts: { all: 10, fans: 7, artists: 3, verified: 2, suspended: 1 },
    }
    const list = toUsersList(wire)
    expect(list.users).toHaveLength(1)
    expect(list.users[0].name).toBe('Ama Boateng')
    expect(list.counts).toEqual({ all: 10, fans: 7, artists: 3, verified: 2, suspended: 1 })
  })

  it('toUserDetail projects summary + actionLog, ignoring activity/orders/devices', () => {
    const wire: UserDetailWire = {
      summary: rowWire,
      activity: [], orders: [], devices: [],
      actionLog: [{ id: 'l1', action: 'Verified artist', by: 'Admin', time: '1h ago' }],
    }
    const d = toUserDetail(wire)
    expect(d.summary.id).toBe('u1')
    expect(d.actionLog).toEqual([{ id: 'l1', action: 'Verified artist', by: 'Admin', time: '1h ago' }])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — `toAdminUserRow`/`toUsersList`/`toUserDetail` are not exported.

- [ ] **Step 3: Add the admin-data type import to the top import block**

In `Frontend/src/lib/api/mappers.ts`, add to the existing top import block (do NOT place mid-file):

```ts
import type { AdminUserRow, UserRole, UserStatus, UserActionLog } from '../../admin-data'
```

- [ ] **Step 4: Add wire types + result types + mapper functions at the END of `mappers.ts`**

```ts
// ── Admin users (AdminUsersResource) ──────────────────────────────────────────
export interface AdminUserRowWire {
  id: string
  name: string
  initial: string
  email: string
  role: string
  verified: boolean
  joined: string
  lastActive: string
  status: string
}
export interface UserCountsWire { all: number; fans: number; artists: number; verified: number; suspended: number }
export interface PagedUsersWire {
  items: AdminUserRowWire[]
  page: number
  size: number
  total: number
  counts: UserCountsWire
}
export interface UserActionLogWire { id: string; action: string; by: string; time: string }
export interface UserDetailWire {
  summary: AdminUserRowWire
  activity: unknown[]
  orders: unknown[]
  devices: unknown[]
  actionLog: UserActionLogWire[]
}

export interface UserCounts { all: number; fans: number; artists: number; verified: number; suspended: number }
export interface AdminUsersList { users: AdminUserRow[]; counts: UserCounts }
export interface AdminUserDetailData { summary: AdminUserRow; actionLog: UserActionLog[] }

export function toAdminUserRow(w: AdminUserRowWire): AdminUserRow {
  return {
    id: w.id,
    name: w.name,
    initial: w.initial,
    email: w.email,
    role: w.role as UserRole,
    verified: w.verified,
    joined: w.joined,
    lastActive: w.lastActive,
    status: w.status as UserStatus,
  }
}

export function toUserCounts(w: UserCountsWire): UserCounts {
  return { all: w.all, fans: w.fans, artists: w.artists, verified: w.verified, suspended: w.suspended }
}

export function toUsersList(w: PagedUsersWire): AdminUsersList {
  return { users: w.items.map(toAdminUserRow), counts: toUserCounts(w.counts) }
}

export function toUserActionLog(w: UserActionLogWire): UserActionLog {
  return { id: w.id, action: w.action, by: w.by, time: w.time }
}

export function toUserDetail(w: UserDetailWire): AdminUserDetailData {
  return { summary: toAdminUserRow(w.summary), actionLog: w.actionLog.map(toUserActionLog) }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS (all new cases green; existing cases unaffected).

- [ ] **Step 6: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): user wire mappers (list, counts, detail)"
```

---

### Task 2: Admin users query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-users.ts`
- Test: `Frontend/src/lib/api/queries/admin-users.test.ts`

**Interfaces:**
- Consumes: `toUsersList`, `toUserDetail`, `PagedUsersWire`, `UserDetailWire`, `AdminUsersList`, `AdminUserDetailData` from `../mappers`; `apiFetch` from `../client`.
- Produces (used by Tasks 3 & 4):
  - `usersQuery(): queryOptions` (key `['admin','users','list']`, returns `AdminUsersList`)
  - `userDetailQuery(id: string): queryOptions` (key `['admin','users','detail', id]`, returns `AdminUserDetailData`)
  - `apiVerifyUser(id: string): Promise<void>`
  - `apiSuspendUser(id: string, reason: string): Promise<void>`
  - `apiReactivateUser(id: string): Promise<void>`

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-users.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { usersQuery, userDetailQuery, apiVerifyUser, apiSuspendUser, apiReactivateUser } from './admin-users'

const pagedWire = {
  items: [{ id: 'u1', name: 'Ama', initial: 'A', email: 'a@x.com', role: 'fan', verified: false, joined: 'Jan 2025', lastActive: '2h', status: 'active' }],
  page: 1, size: 100, total: 1,
  counts: { all: 1, fans: 1, artists: 0, verified: 0, suspended: 0 },
}
const detailWire = {
  summary: pagedWire.items[0],
  activity: [], orders: [], devices: [],
  actionLog: [{ id: 'l1', action: 'Joined', by: 'system', time: 'Jan 2025' }],
}

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json,
    text: async () => JSON.stringify(json),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('admin-users queries', () => {
  it('usersQuery hits /v1/admin/users and maps items + counts', async () => {
    const f = mockFetch(200, pagedWire)
    vi.stubGlobal('fetch', f)
    const result = await usersQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/users', expect.objectContaining({ method: 'GET' }))
    expect(result.users[0].name).toBe('Ama')
    expect(result.counts.all).toBe(1)
    expect(usersQuery().queryKey).toEqual(['admin', 'users', 'list'])
  })

  it('userDetailQuery hits /v1/admin/users/:id and keys by id', async () => {
    const f = mockFetch(200, detailWire)
    vi.stubGlobal('fetch', f)
    const result = await userDetailQuery('u1').queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/users/u1', expect.objectContaining({ method: 'GET' }))
    expect(result.summary.id).toBe('u1')
    expect(result.actionLog).toHaveLength(1)
    expect(userDetailQuery('u1').queryKey).toEqual(['admin', 'users', 'detail', 'u1'])
  })

  it('apiVerifyUser POSTs to /verify', async () => {
    const f = mockFetch(200, pagedWire.items[0])
    vi.stubGlobal('fetch', f)
    await apiVerifyUser('u1')
    expect(f).toHaveBeenCalledWith('/v1/admin/users/u1/verify', expect.objectContaining({ method: 'POST' }))
  })

  it('apiSuspendUser POSTs reason to /suspend', async () => {
    const f = mockFetch(200, pagedWire.items[0])
    vi.stubGlobal('fetch', f)
    await apiSuspendUser('u1', 'Spam')
    const [, opts] = f.mock.calls[0]
    expect(f.mock.calls[0][0]).toBe('/v1/admin/users/u1/suspend')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Spam' })
  })

  it('apiReactivateUser POSTs to /reactivate', async () => {
    const f = mockFetch(200, pagedWire.items[0])
    vi.stubGlobal('fetch', f)
    await apiReactivateUser('u1')
    expect(f).toHaveBeenCalledWith('/v1/admin/users/u1/reactivate', expect.objectContaining({ method: 'POST' }))
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-users.test.ts`
Expected: FAIL — `./admin-users` module does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-users.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toUsersList, toUserDetail,
  type PagedUsersWire, type UserDetailWire,
} from '../mappers'

/** `GET /v1/admin/users` — the full admin user list plus the filter-pill counts. */
export function usersQuery() {
  return queryOptions({
    queryKey: ['admin', 'users', 'list'],
    queryFn: async () => toUsersList(await apiFetch<PagedUsersWire>('/admin/users')),
  })
}

/** `GET /v1/admin/users/:id` — one user's header summary + server action log. */
export function userDetailQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'users', 'detail', id],
    queryFn: async () => toUserDetail(await apiFetch<UserDetailWire>(`/admin/users/${id}`)),
  })
}

/** `POST /v1/admin/users/:id/verify` — mark an artist verified. */
export function apiVerifyUser(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/verify`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/users/:id/suspend { reason }` — reason is required (non-blank). */
export function apiSuspendUser(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/suspend`, { method: 'POST', body: { reason } }).then(() => undefined)
}

/** `POST /v1/admin/users/:id/reactivate` — lift a suspension. */
export function apiReactivateUser(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/reactivate`, { method: 'POST' }).then(() => undefined)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-users.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-users.ts Frontend/src/lib/api/queries/admin-users.test.ts
git commit -m "feat(admin): users query + mutation layer"
```

---

### Task 3: Shared load-error affordance + wire the Users list

**Files:**
- Create: `Frontend/src/components/admin/load-error.tsx`
- Modify: `Frontend/src/routes/admin.users.tsx`

**Interfaces:**
- Consumes: `usersQuery`, `apiVerifyUser`, `apiSuspendUser`, `apiReactivateUser` from `../lib/api/queries/admin-users`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.
- Produces (used by Task 4): `AdminLoadError` component — `AdminLoadError({ label, onRetry }: { label: string; onRetry: () => void })`.

- [ ] **Step 1: Create the reusable error affordance**

Create `Frontend/src/components/admin/load-error.tsx`:

```tsx
/** The distinct admin load-error affordance — shown when a query fails, so an outage
 * on a role-gated screen never looks identical to a genuinely empty result. */
export function AdminLoadError({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div className="py-12 text-center flex flex-col items-center gap-3">
      <p className="text-sm text-gray-400 dark:text-gray-500">{label}</p>
      <button
        onClick={onRetry}
        className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
      >
        Retry
      </button>
    </div>
  )
}
```

- [ ] **Step 2: Replace the imports + mock seed in `admin.users.tsx`**

Change the import block (lines 1-7). Replace the mock import line

```ts
import { getAdminUsers, USER_COUNTS, type AdminUserRow, type UserStatus } from '../lib/admin-data'
```

with:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminUserRow, UserStatus } from '../lib/admin-data'
import { usersQuery, apiVerifyUser, apiSuspendUser, apiReactivateUser } from '../lib/api/queries/admin-users'
import { AdminLoadError } from '../components/admin/load-error'
```

(Keep the existing `useMemo, useState` import; `USER_COUNTS` and `getAdminUsers` are no longer imported.)

- [ ] **Step 3: Move FILTERS inside the component (counts now come from the query)**

Delete the module-level `FILTERS` array (lines 16-22). Keep the `FilterKey` type and `matchesFilter`. Inside `AdminUsers()`, replace the state/derivation block (lines 28-53) with:

```ts
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data, isError, refetch } = useQuery(usersQuery())
  const users = data?.users ?? []
  const counts = data?.counts ?? { all: 0, fans: 0, artists: 0, verified: 0, suspended: 0 }

  const [filter, setFilter] = useState<FilterKey>('all')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const FILTERS: { key: FilterKey; label: string; count: number }[] = [
    { key: 'all', label: 'All', count: counts.all },
    { key: 'fans', label: 'Fans', count: counts.fans },
    { key: 'artists', label: 'Artists', count: counts.artists },
    { key: 'verified', label: 'Verified', count: counts.verified },
    { key: 'suspended', label: 'Suspended', count: counts.suspended },
  ]

  const q = query.trim().toLowerCase()
  const rows = useMemo(
    () => users.filter((u) => matchesFilter(u, filter) && (!q || `${u.name} ${u.email}`.toLowerCase().includes(q))),
    [users, filter, q],
  )
  const paged = usePaged(rows)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: usersQuery().queryKey })

  const handleVerify = async (u: AdminUserRow) => {
    try { await apiVerifyUser(u.id); await invalidate(); toast(`${u.name} verified`, 'success') }
    catch { toast('Could not verify user', 'error') }
  }
  const handleSuspend = async (u: AdminUserRow) => {
    try { await apiSuspendUser(u.id, 'Suspended from user list'); await invalidate(); toast(`${u.name} suspended`, 'success') }
    catch { toast('Could not suspend user', 'error') }
  }
  const handleReactivate = async (u: AdminUserRow) => {
    try { await apiReactivateUser(u.id); await invalidate(); toast(`${u.name} reactivated`, 'success') }
    catch { toast('Could not reactivate user', 'error') }
  }

  const allShownSelected = rows.length > 0 && rows.every((u) => selected.has(u.id))
  const toggleAll = () => setSelected(allShownSelected ? new Set() : new Set(rows.map((u) => u.id)))
  const toggleOne = (id: string) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })

  const bulkSuspend = async () => {
    const ids = [...selected]
    try {
      await Promise.all(ids.map((id) => apiSuspendUser(id, 'Suspended from user list')))
      await invalidate()
      toast(`${ids.length} user${ids.length > 1 ? 's' : ''} suspended`, 'success')
      setSelected(new Set())
    } catch { toast('Could not suspend the selected users', 'error') }
  }
```

- [ ] **Step 4: Wire the row action props + the error state in the table body**

In the JSX, the `<UserRow>` handlers (lines 115-117) change from the local mutators to the async handlers:

```tsx
                  onVerify={() => handleVerify(u)}
                  onSuspend={() => handleSuspend(u)}
                  onReactivate={() => handleReactivate(u)}
```

In the table body, replace the `rows.length === 0 ? (...empty...) : (...rows...)` block (lines 109-120) so an error renders the distinct affordance:

```tsx
            {isError ? (
              <AdminLoadError label="Couldn't load users." onRetry={() => refetch()} />
            ) : rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching users.</div>
            ) : (
              paged.pageItems.map((u) => (
                <UserRow key={u.id} user={u} selected={selected.has(u.id)} onSelect={() => toggleOne(u.id)}
                  onView={() => navigate({ to: '/admin/users/$userId', params: { userId: u.id } })}
                  onVerify={() => handleVerify(u)}
                  onSuspend={() => handleSuspend(u)}
                  onReactivate={() => handleReactivate(u)}
                />
              ))
            )}
```

(The CSV **Export** button keeps its existing `toast('Exporting users as CSV', 'success')` — do not wire.)

- [ ] **Step 5: Typecheck + run the full unit suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest all green (no route test exists for this file; the wiring is covered by the query/mapper tests + live QA).

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/components/admin/load-error.tsx Frontend/src/routes/admin.users.tsx
git commit -m "feat(admin): wire users list to live query (verify/suspend/reactivate + error state)"
```

---

### Task 4: Wire the Users detail page

**Files:**
- Modify: `Frontend/src/routes/admin.users.$userId.tsx`

**Interfaces:**
- Consumes: `userDetailQuery`, `usersQuery`, `apiVerifyUser`, `apiSuspendUser`, `apiReactivateUser` from `../lib/api/queries/admin-users`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

- [ ] **Step 1: Replace the imports + mock seed**

Replace the mock import line (line 10)

```ts
import { getAdminUsers, getUserDetail, type AdminUserRow, type UserStatus, type UserActionLog } from '../lib/admin-data'
```

with (note: `getUserDetail` stays — Activity/Orders/Devices remain on the mock; `UserStatus` stays — it types the local `StatusPill`; `getAdminUsers`, `AdminUserRow`, `UserActionLog` are no longer used here):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getUserDetail, type UserStatus } from '../lib/admin-data'
import { userDetailQuery, usersQuery, apiVerifyUser, apiSuspendUser, apiReactivateUser } from '../lib/api/queries/admin-users'
import { AdminLoadError } from '../components/admin/load-error'
```

- [ ] **Step 2: Replace the component's state/seed + guards (lines 20-49)**

Replace the block from `function AdminUserDetail() {` through the `stats` definition with:

```tsx
function AdminUserDetail() {
  const { userId } = Route.useParams()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, isError, refetch } = useQuery(userDetailQuery(userId))
  const detail = useMemo(() => getUserDetail(), [])

  const [suspendOpen, setSuspendOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  if (isError) {
    return (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this user." onRetry={() => refetch()} />
      </div>
    )
  }

  const user = data?.summary
  const log = data?.actionLog ?? []

  if (!user) {
    return isLoading ? (
      <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
    ) : (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">User not found.</p>
        <Link to="/admin/users" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to users</Link>
      </div>
    )
  }

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: userDetailQuery(userId).queryKey })
    await queryClient.invalidateQueries({ queryKey: usersQuery().queryKey })
  }
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string) => {
    try { await fn(); await invalidate(); toast(okMsg, 'success') }
    catch { toast(errMsg, 'error') }
  }
  const verify = () => runAction(() => apiVerifyUser(user.id), `${user.name} verified`, 'Could not verify user')
  const reactivate = () => runAction(() => apiReactivateUser(user.id), 'Reactivated account', 'Could not reactivate user')
  const suspend = (reason: string) => runAction(() => apiSuspendUser(user.id, reason), `Suspended · ${reason}`, 'Could not suspend user')

  const isArtist = user.role === 'artist'
  const stats = isArtist
    ? [{ label: 'Releases', value: '12' }, { label: 'Revenue', value: '₵42K' }, { label: 'Followers', value: '412K' }]
    : [{ label: 'Purchases', value: `${detail.orders.length}` }, { label: 'Lifetime spend', value: '₵312' }, { label: 'Playlists', value: '7' }]
```

Note: the old local `setUser`/`addLog`/`setStatus`/`verify` helpers are gone — the log and status now come from the server via `data` after invalidation.

- [ ] **Step 3: Rewire the header buttons + overflow menu + suspend modal**

The Verify button `onClick={verify}` stays as-is (now calls the new `verify`). The Reactivate/Suspend buttons (lines 75-77) become:

```tsx
            {user.status === 'suspended'
              ? <button onClick={reactivate} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><RotateCcw size={15} /> Reactivate</button>
              : <button onClick={() => setSuspendOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><Ban size={15} /> Suspend</button>}
```

The overflow-menu items (lines 84-87) drop the removed `addLog` calls but keep their toasts (Category B):

```tsx
                    <MenuItem icon={LogIn} label="Log in as user" onClick={() => { toast('Opening an impersonation session', 'info'); setMenuOpen(false) }} />
                    <MenuItem icon={KeyRound} label="Reset password" onClick={() => { toast('Password reset link sent', 'success'); setMenuOpen(false) }} />
                    <MenuItem icon={Mail} label="Email user" onClick={() => { toast(`Compose email to ${user.email}`, 'info'); setMenuOpen(false) }} />
                    <MenuItem icon={Download} label="Export data (GDPR)" onClick={() => { toast('Preparing data export', 'success'); setMenuOpen(false) }} />
```

The SuspendModal `onConfirm` (line 171) calls the wired `suspend`:

```tsx
      <SuspendModal isOpen={suspendOpen} name={user.name} onClose={() => setSuspendOpen(false)}
        onConfirm={(reason) => { setSuspendOpen(false); suspend(reason) }} />
```

The `log.map(...)` action-history section (lines 157-163) is unchanged — `log` now holds the server `actionLog`. Activity/Orders/Devices sections (`detail.activity`/`detail.orders`/`detail.devices`) are unchanged (mock). The device Sign-out button keeps its toast.

- [ ] **Step 4: Typecheck + run the full unit suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest all green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.users.$userId.tsx
git commit -m "feat(admin): wire users detail to live query (summary + action log + actions)"
```

---

### Task 5: Live QA + PR (USER-run gate)

**Files:** none (verification only).

This task is the human live-QA gate — the controller does NOT run `verify.sh` (IntelliJ JPS races); CI is authoritative.

- [ ] **Step 1: Final full unit + build gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full vitest suite green.

- [ ] **Step 2: Live QA against the running stack** (backend on :18080, Vite proxy pointed at :18080, signed in as the seeded `support`/admin account)

  - List: counts on the filter pills match the backend; rows render; filters + search work client-side.
  - Verify an unverified artist → row shows verified, persists after refetch.
  - Suspend a user (row menu) → status → suspended (default reason `Suspended from user list`), persists.
  - Reactivate → status → active, persists.
  - Bulk-select + Suspend → all selected suspended, persists.
  - Open a detail page → header + action log are live; Activity/Orders/Devices still render (mock).
  - Suspend via the modal → reason is sent; action log shows the server entry after refetch.
  - Force a load error (stop the backend, refetch) → the distinct "Couldn't load users." + Retry affordance appears (not the empty state).

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-users
gh pr create --base master --title "feat(admin): wire Users list + detail to live endpoints" --body "<DoD checklist + no-visual-change note + Category-B list>"
```

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-users` (already created off `master`; the spec commit is `f39774a`). BASE for the first review package is `f39774a`.
- **Do NOT** run `backend` builds or stage backend secrets. This is a frontend-only branch.
- **Category B (leave as toasts, do not wire):** CSV export, impersonate, reset-password, email-user, data-export, device sign-out; the detail page's Activity/Orders/Devices stay on `getUserDetail()`.
- **`AdminLoadError`** is the new shared standard — later admin slices reuse it.
