# Studio Slice 2 — Profile + Settings wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Studio **Profile** and **Settings** screens to the live backend (`GET/PUT /v1/studio/profile`, `GET/PUT /v1/studio/settings`), replacing the mock-seeded god-context, with no visual change.

**Architecture:** Follow the established query idiom (`queries/studio.ts` + `mappers.ts` + `apiFetch`), extending the module already created in Slice 1. This slice also begins the **incremental decomposition** of the `studio-context.tsx` god-context: `profile` and `settings` (plus `setProfile`/`setSettings`) are peeled out into query-backed hooks; the context shrinks to `releases`/`episodes`/payout state, which later slices (3–5) will peel out in turn until the context is empty and deletable.

**Tech Stack:** React 19 + React Compiler, TanStack Query v5 (`queryOptions`, `useSuspenseQuery`, `useQuery`), TanStack Router (file routes, `loader` + `ensureQueryData`), TypeScript, Vitest + RTL.

## Global Constraints

- **`apiFetch` prepends `BASE_URL` (`/v1`)** — query paths are written WITHOUT `/v1` (e.g. `/studio/profile`). It attaches the bearer token, sets `Content-Type: application/json`, supports `{ method, body }`, returns parsed JSON (or `undefined` on 204), and throws `ApiError(status, code, message, field?)` on non-2xx.
- **Mappers preserve the wire shape; never invent fields.** The backend view DTOs match the frontend types **field-for-field** (verified against `StudioProfileView`/`StudioSettingsView` and `studio-data.ts`), so `toStudioProfile`/`toStudioSettings` are thin pass-throughs — like Slice 1's `toAnalytics`.
- **`trackPrice` and `autoWithdrawThreshold` are plain decimal numbers, NOT the `Money` envelope.** Backend serializes them as `BigDecimal` → JSON number; frontend types are `number`. This is a deliberate exception (config defaults, not transactional money). Do not wrap them in `{ amount, currency }`.
- **React Compiler is enabled** — do NOT add manual `useMemo`/`useCallback` in components (it errors "Existing memoization could not be preserved"). The existing `useMemo` for the `dirty` flag in `studio.profile.tsx`/`studio.settings.tsx` predates this and lints clean today; leave it exactly as-is (do not add new ones).
- **No `@testing-library/jest-dom`** — assert with `toBeTruthy()` / `queryByText`, not `toBeInTheDocument()`.
- **Lint baseline is 214** (`npm run lint`). No new warnings.
- **Node 22.17.1** via nvm on PATH for all `npm`/`vitest` commands (default shell node is v10 and crashes Vitest/Vite).
- **`tsc --noEmit`** must stay clean.

---

## Backend contract (verified — ground truth for this slice)

**`StudioProfileResource` (`@Path("/v1/studio/profile")`, `@RolesAllowed("artist")`, artist id from `jwt.getSubject()` — never a path param):**
- `GET /v1/studio/profile` → 200 `StudioProfileView`; always the caller's own profile, never 404s.
- `PUT /v1/studio/profile` → 200 `StudioProfileView`; **409 `USERNAME_TAKEN`**; **422 `INVALID_GENRE`**. Whole shape is writable.
- `StudioProfileView` = `{ displayName, username, hometown, genres: string[], bio, avatar, banner, links: { instagram, twitter, youtube, website }, shows: { id, venue, date, city }[], featuredTrackId, bookingEmail, pressAssets: { id, name, url }[] }`. **Identical to the frontend `StudioProfile`.**
- Save body validation: `displayName` required (≤120); `username` required, regex `^@?[A-Za-z0-9_.-]{3,30}$` (optional leading `@`); `hometown` ≤120; `bio` ≤2000; `shows[].id` and `pressAssets[].id` required.

**`StudioSettingsResource` (`@Path("/v1/studio/settings")`, `@RolesAllowed("artist")`, IDOR-safe — id only from JWT):**
- `GET /v1/studio/settings` → 200 `StudioSettingsView` (the **full** shape, Category A + B).
- `PUT /v1/studio/settings` → 200 `StudioSettingsView`; 422 `VALIDATION`. **Accepts ONLY the Category-A writable subset**: `notifications`, `defaults`, `payouts`, `privacy`, `team`. Category B (`email`, `phone`, `country`, `language`, `timezone`, `twoFactor`, `sessions`, `connectedApps`, `verification`, `billing`) is **returned by GET but silently ignored on PUT** — there is no endpoint to persist it.
- `StudioSettingsView` = `{ email, phone, country, language, timezone, twoFactor, sessions: SessionInfo[], connectedApps: ConnectedApp[], verification: {artist,identity,payout,rights}, billing: {plan,price,renews}, notifications: {sales,tips,followers,payouts,weeklySummary,comments,marketing}, defaults: {trackPrice, releaseVisibility, autoExplicit, allowOffers}, payouts: {autoWithdraw, autoWithdrawThreshold, taxId}, privacy: {discoverable, showRealName, acceptBookings, allowDms}, team: TeamMember[] }`. **Identical to the frontend `StudioSettings`** (`SessionInfo`/`ConnectedApp`/`TeamMember` match too).
- Save body Category-A validation: `defaults.trackPrice` ≥ 0; `defaults.releaseVisibility` ∈ {`public`,`scheduled`}; `payouts.autoWithdrawThreshold` ≥ 0; `team[].id` required, `team[].role` ∈ {`Owner`,`Manager`,`Label`,`Invited`}.

### Two documented limitations for this slice (design decisions, flagged to the user)

1. **Category B settings are display-from-GET, session-local on edit.** The settings page renders email/phone/country/language/2FA/sessions/connected-apps/verification/billing from the GET response. Their controls still mutate the local draft (so the UI behaves), but **Save persists only Category A** — Category B edits revert on the next refetch. This exactly matches backend capability. The missing persistence endpoints (identity/security for 2FA + sessions, integrations for connected apps, subscription for billing) are **future backend work**, called out in the PR body and left as a TODO comment in the settings route.
2. **Avatar/banner/press-asset uploads produce `blob:` object URLs** that are meaningless server-side (no media-upload endpoint exists yet). The save path **strips `blob:` URLs before PUT** (reusing the existing `safeUrl` idea from `studio-context.tsx`), so we never persist a dead local URL. Real image upload is future backend work (media module). Text fields, links, genres, shows, featuredTrackId, bookingEmail persist normally.

---

## File Structure

- **Modify** `Frontend/src/lib/api/mappers.ts` — add `StudioProfileWire`/`toStudioProfile` + `SaveStudioProfileBody`/`toSaveProfileBody`; `StudioSettingsWire`/`toStudioSettings` + `SaveStudioSettingsBody`/`toSaveSettingsBody`. (Pass-throughs; body mappers narrow to the writable subset + strip `blob:`.)
- **Modify** `Frontend/src/lib/api/queries/studio.ts` — add `studioProfileQuery()`, `studioSettingsQuery()`, `apiSaveStudioProfile(body)`, `apiSaveStudioSettings(body)`.
- **Modify** `Frontend/src/lib/api/queries/studio.test.ts` — extend with profile/settings query + save-body tests.
- **Modify** `Frontend/src/routes/studio.profile.tsx` — loader prefetch + `useSuspenseQuery` + optimistic save mutation; drop `useStudio`.
- **Modify** `Frontend/src/routes/studio.settings.tsx` — loader prefetch + `useSuspenseQuery` + optimistic Category-A save; drop `useStudio`; keep Category-B controls local with a TODO.
- **Modify** `Frontend/src/routes/studio.index.tsx` — prefetch + `useSuspenseQuery` for profile/settings; keep `balance`/`transactions`/`releases` from context.
- **Modify** `Frontend/src/routes/studio.release.new.tsx` — prefetch + `useSuspenseQuery` for settings defaults; keep `addRelease` from context.
- **Modify** `Frontend/src/components/studio/studio-shell.tsx` — non-suspending `useQuery(studioProfileQuery())` with fallback to `studioArtist.name`/null avatar.
- **Modify** `Frontend/src/features/studio/studio-context.tsx` — remove `profile`/`settings`/`setProfile`/`setSettings` from state, value, seed, hydrate, and imports. Context now holds only `releases`/`episodes`/payout state.

---

## Task 1: Profile + Settings mappers, queries, and save helpers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Modify: `Frontend/src/lib/api/queries/studio.ts`
- Test: `Frontend/src/lib/api/queries/studio.test.ts`

**Interfaces:**
- Consumes: `apiFetch` from `../client`; `StudioProfile`, `StudioSettings`, `TeamMember` from `../../studio-data`.
- Produces:
  - `toStudioProfile(w: StudioProfileWire): StudioProfile`, `toSaveProfileBody(p: StudioProfile): SaveStudioProfileBody`
  - `toStudioSettings(w: StudioSettingsWire): StudioSettings`, `toSaveSettingsBody(s: StudioSettings): SaveStudioSettingsBody`
  - `studioProfileQuery(): queryOptions` (key `['studio','profile']`), `studioSettingsQuery(): queryOptions` (key `['studio','settings']`)
  - `apiSaveStudioProfile(body: SaveStudioProfileBody): Promise<StudioProfile>`, `apiSaveStudioSettings(body: SaveStudioSettingsBody): Promise<StudioSettings>`

- [ ] **Step 1: Add the mappers.** In `mappers.ts`, extend the `../../studio-data` import to include `StudioProfile` and `StudioSettings` (and `TeamMember` if not already imported), then append:

```ts
// ── Studio profile ────────────────────────────────────────────────
// StudioProfileView matches StudioProfile field-for-field, so the read
// mapper is a pass-through. The save body is the whole (writable) shape
// with blob: object URLs stripped — those are local-only and meaningless
// server-side (no media-upload endpoint yet).
export type StudioProfileWire = StudioProfile
export function toStudioProfile(w: StudioProfileWire): StudioProfile {
  return w
}

export type SaveStudioProfileBody = StudioProfile
const dropBlob = (u: string | null): string | null => (u && u.startsWith('blob:') ? null : u)
export function toSaveProfileBody(p: StudioProfile): SaveStudioProfileBody {
  return {
    ...p,
    avatar: dropBlob(p.avatar),
    banner: dropBlob(p.banner),
    pressAssets: p.pressAssets.filter((a) => !a.url.startsWith('blob:')),
  }
}

// ── Studio settings ───────────────────────────────────────────────
// GET returns the full shape (Category A + B). PUT accepts ONLY the
// Category-A writable subset; Category B (email/phone/2FA/sessions/apps/
// verification/billing) has no persistence endpoint yet, so the save body
// narrows to what the backend will actually store.
export type StudioSettingsWire = StudioSettings
export function toStudioSettings(w: StudioSettingsWire): StudioSettings {
  return w
}

export interface SaveStudioSettingsBody {
  notifications: StudioSettings['notifications']
  defaults: StudioSettings['defaults']
  payouts: StudioSettings['payouts']
  privacy: StudioSettings['privacy']
  team: StudioSettings['team']
}
export function toSaveSettingsBody(s: StudioSettings): SaveStudioSettingsBody {
  return {
    notifications: s.notifications,
    defaults: s.defaults,
    payouts: s.payouts,
    privacy: s.privacy,
    team: s.team,
  }
}
```

- [ ] **Step 2: Add the queries + save helpers.** In `queries/studio.ts`, extend the imports and append:

```ts
import type { StudioProfile, StudioSettings } from '../../studio-data'
import {
  toStudioProfile, type StudioProfileWire, toSaveProfileBody, type SaveStudioProfileBody,
  toStudioSettings, type StudioSettingsWire, toSaveSettingsBody, type SaveStudioSettingsBody,
} from '../mappers'

/** `GET /v1/studio/profile` — the signed-in creator's own profile (never 404s). */
export function studioProfileQuery() {
  return queryOptions({
    queryKey: ['studio', 'profile'],
    queryFn: async () => toStudioProfile(await apiFetch<StudioProfileWire>('/studio/profile')),
  })
}

/** `GET /v1/studio/settings` — the signed-in creator's settings (full shape). */
export function studioSettingsQuery() {
  return queryOptions({
    queryKey: ['studio', 'settings'],
    queryFn: async () => toStudioSettings(await apiFetch<StudioSettingsWire>('/studio/settings')),
  })
}

/** `PUT /v1/studio/profile`. Throws ApiError (409 USERNAME_TAKEN / 422 INVALID_GENRE). */
export function apiSaveStudioProfile(profile: StudioProfile): Promise<StudioProfile> {
  const body: SaveStudioProfileBody = toSaveProfileBody(profile)
  return apiFetch<StudioProfileWire>('/studio/profile', { method: 'PUT', body }).then(toStudioProfile)
}

/** `PUT /v1/studio/settings`. Sends only the Category-A writable subset. */
export function apiSaveStudioSettings(settings: StudioSettings): Promise<StudioSettings> {
  const body: SaveStudioSettingsBody = toSaveSettingsBody(settings)
  return apiFetch<StudioSettingsWire>('/studio/settings', { method: 'PUT', body }).then(toStudioSettings)
}
```

- [ ] **Step 3: Write the failing tests.** Append to `studio.test.ts` (import the new symbols at top: `studioProfileQuery, studioSettingsQuery, apiSaveStudioProfile, apiSaveStudioSettings`):

```ts
const profileWire = {
  displayName: 'Kojo Beats', username: '@kojo', hometown: 'Accra, Ghana',
  genres: ['Afrobeats'], bio: 'Producer.', avatar: null, banner: null,
  links: { instagram: 'kojo', twitter: '', youtube: '', website: '' },
  shows: [{ id: 's1', venue: 'Alliance', date: 'Aug 1', city: 'Accra' }],
  featuredTrackId: 't1', bookingEmail: 'book@kojo.com',
  pressAssets: [{ id: 'a1', name: 'kit.pdf', url: 'https://x/kit.pdf' }],
}

describe('studioProfileQuery', () => {
  it('requests /studio/profile and maps the view through', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(profileWire)
    const p = await studioProfileQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/profile')
    expect(p.displayName).toBe('Kojo Beats')
    expect(p.shows[0]).toEqual({ id: 's1', venue: 'Alliance', date: 'Aug 1', city: 'Accra' })
  })
})

describe('apiSaveStudioProfile', () => {
  it('PUTs the profile and strips blob: avatar/banner/press assets', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(profileWire)
    await apiSaveStudioProfile({
      ...profileWire, avatar: 'blob:local', banner: 'https://x/b.jpg',
      pressAssets: [{ id: 'a1', name: 'k', url: 'blob:x' }, { id: 'a2', name: 'ok', url: 'https://x/ok' }],
    } as never)
    const [path, opts] = vi.mocked(client.apiFetch).mock.calls[0]
    expect(path).toBe('/studio/profile')
    expect(opts).toMatchObject({ method: 'PUT' })
    expect(opts!.body).toMatchObject({ avatar: null, banner: 'https://x/b.jpg' })
    expect((opts!.body as { pressAssets: unknown[] }).pressAssets).toHaveLength(1)
  })
})

describe('apiSaveStudioSettings', () => {
  it('PUTs ONLY the Category-A subset (drops email/twoFactor/sessions/etc.)', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({})
    await apiSaveStudioSettings({
      email: 'x@y.com', phone: '024', country: 'GH', language: 'English', timezone: 'GMT',
      twoFactor: true, sessions: [{ id: 's' }], connectedApps: [{ id: 'a' }],
      verification: { artist: true }, billing: { plan: 'Pro' },
      notifications: { sales: true, tips: false, followers: false, payouts: false, weeklySummary: false, comments: false, marketing: false },
      defaults: { trackPrice: 2, releaseVisibility: 'public', autoExplicit: false, allowOffers: true },
      payouts: { autoWithdraw: false, autoWithdrawThreshold: 0, taxId: '' },
      privacy: { discoverable: true, showRealName: false, acceptBookings: true, allowDms: false },
      team: [{ id: 'u1', name: 'A', email: 'a@b.com', role: 'Manager' }],
    } as never)
    const body = vi.mocked(client.apiFetch).mock.calls[0][1]!.body as Record<string, unknown>
    expect(Object.keys(body).sort()).toEqual(['defaults', 'notifications', 'payouts', 'privacy', 'team'])
    expect(body.notifications).toMatchObject({ sales: true })
  })
})
```

- [ ] **Step 4: Run tests, expect pass.** Run: `cd Frontend && npx vitest run src/lib/api/queries/studio.test.ts`. Expected: all studio tests pass (4 pre-existing + 3 new = 7).

- [ ] **Step 5: Typecheck + commit.** Run `cd Frontend && npx tsc --noEmit` (clean), then:

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/queries/studio.ts Frontend/src/lib/api/queries/studio.test.ts
git commit -m "feat(frontend): studio profile + settings mappers, queries, save helpers"
```

---

## Task 2: Rewire `studio.profile.tsx` to the live profile query

**Files:**
- Modify: `Frontend/src/routes/studio.profile.tsx`

**Interfaces:**
- Consumes: `studioProfileQuery`, `apiSaveStudioProfile` (Task 1); `useSuspenseQuery`, `useQueryClient` from `@tanstack/react-query`; `ApiError` from `../lib/api/errors`.
- Produces: nothing downstream (leaf route).

- [ ] **Step 1: Add the loader + swap the data source.** Replace the route definition and the top of `ProfileComponent`:

```tsx
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { studioProfileQuery, apiSaveStudioProfile } from '../lib/api/queries/studio'
import { ApiError } from '../lib/api/errors'
// remove: import { useStudio } from '../features/studio/studio-context'

export const Route = createFileRoute('/studio/profile')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioProfileQuery()),
  component: ProfileComponent,
})
```

Inside `ProfileComponent`, replace `const { profile: storeProfile, setProfile } = useStudio()` with:

```tsx
const queryClient = useQueryClient()
const { data: storeProfile } = useSuspenseQuery(studioProfileQuery())
```

- [ ] **Step 2: Rewrite `save` as an optimistic mutation.** Replace `const save = () => { setProfile(p); toast('Profile changes saved', 'success') }` with:

```tsx
const save = async () => {
  const key = studioProfileQuery().queryKey
  const previous = queryClient.getQueryData(key)
  queryClient.setQueryData(key, p)          // optimistic — updates editor baseline + sidebar
  try {
    const saved = await apiSaveStudioProfile(p)
    queryClient.setQueryData(key, saved)    // adopt server-canonical (e.g. normalized username)
    setP(saved)
    toast('Profile changes saved', 'success')
  } catch (err) {
    queryClient.setQueryData(key, previous) // rollback
    const code = err instanceof ApiError ? err.code : null
    toast(
      code === 'USERNAME_TAKEN' ? 'That username is already taken.'
      : code === 'INVALID_GENRE' ? 'One of your genres isn’t recognised.'
      : 'Could not save your profile. Please try again.',
      'error',
    )
  }
}
```

Update the Save button to `onClick={save}` (it already calls `save`; keep `disabled={!dirty}`).

- [ ] **Step 3: Verify no other `useStudio`/`setProfile` references remain.** Run: `grep -n "useStudio\|setProfile" Frontend/src/routes/studio.profile.tsx`. Expected: no matches.

- [ ] **Step 4: Typecheck + build the route.** Run `cd Frontend && npx tsc --noEmit`. Expected: clean.

- [ ] **Step 5: Commit.**

```bash
git add Frontend/src/routes/studio.profile.tsx
git commit -m "feat(frontend): wire studio profile to /v1/studio/profile with optimistic save"
```

---

## Task 3: Rewire `studio.settings.tsx` to the live settings query (Category-A save)

**Files:**
- Modify: `Frontend/src/routes/studio.settings.tsx`

**Interfaces:**
- Consumes: `studioSettingsQuery`, `apiSaveStudioSettings` (Task 1); `useSuspenseQuery`, `useQueryClient`.
- Produces: nothing downstream (leaf route).

- [ ] **Step 1: Add loader + swap data source.** Add imports and route loader mirroring Task 2:

```tsx
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { studioSettingsQuery, apiSaveStudioSettings } from '../lib/api/queries/studio'
// remove: import { useStudio } from '../features/studio/studio-context'

export const Route = createFileRoute('/studio/settings')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioSettingsQuery()),
  component: SettingsComponent,
})
```

Inside `SettingsComponent`, replace `const { settings: storeSettings, setSettings } = useStudio()` with:

```tsx
const queryClient = useQueryClient()
const { data: storeSettings } = useSuspenseQuery(studioSettingsQuery())
```

- [ ] **Step 2: Rewrite the Save handler as an optimistic Category-A mutation.** Replace the inline `onClick={() => { setSettings(s); toast('Settings saved', 'success') }}` on the Save button with `onClick={save}`, and add above the `return`:

```tsx
// NOTE: PUT /v1/studio/settings persists only Category-A settings
// (notifications, sales defaults, payouts, privacy, team). Category-B
// controls below — account email/phone/country/language, 2FA, active
// sessions, connected apps, verification, billing — have no persistence
// endpoint yet; their edits are session-local and revert on refetch.
// TODO(studio-slice-later): wire 2FA/sessions to identity, connected
// apps to integrations, billing to subscription when those land.
const save = async () => {
  const key = studioSettingsQuery().queryKey
  const previous = queryClient.getQueryData(key)
  queryClient.setQueryData(key, s)
  try {
    const saved = await apiSaveStudioSettings(s)
    // The PUT echoes the full shape but RESETS Category-B fields to fixed
    // defaults, so adopt only the five Category-A slices from `saved`;
    // Category-B local edits in `s` are kept (spreading `...saved` wholesale
    // would clobber them on every save — it must be the five slices).
    queryClient.setQueryData(key, {
      ...s,
      notifications: saved.notifications,
      defaults: saved.defaults,
      payouts: saved.payouts,
      privacy: saved.privacy,
      team: saved.team,
    })
    toast('Settings saved', 'success')
  } catch {
    queryClient.setQueryData(key, previous)
    toast('Could not save your settings. Please try again.', 'error')
  }
}
```

- [ ] **Step 3: Verify no `useStudio`/`setSettings` references remain.** Run: `grep -n "useStudio\|setSettings" Frontend/src/routes/studio.settings.tsx`. Expected: no matches.

- [ ] **Step 4: Typecheck.** Run `cd Frontend && npx tsc --noEmit`. Expected: clean.

- [ ] **Step 5: Commit.**

```bash
git add Frontend/src/routes/studio.settings.tsx
git commit -m "feat(frontend): wire studio settings to /v1/studio/settings (Category-A persist)"
```

---

## Task 4: Rewire the read-only consumers (dashboard, release wizard, sidebar)

**Files:**
- Modify: `Frontend/src/routes/studio.index.tsx`
- Modify: `Frontend/src/routes/studio.release.new.tsx`
- Modify: `Frontend/src/components/studio/studio-shell.tsx`

**Interfaces:**
- Consumes: `studioProfileQuery`, `studioSettingsQuery` (Task 1).
- Produces: nothing.

- [ ] **Step 1: `studio.index.tsx` — prefetch + suspense for profile/settings, keep context for the rest.** The current line is `const { balance, transactions, profile, settings, releases } = useStudio()`. Split it:

```tsx
import { useSuspenseQuery } from '@tanstack/react-query'
import { studioProfileQuery, studioSettingsQuery } from '../lib/api/queries/studio'

export const Route = createFileRoute('/studio/')({
  loader: ({ context: { queryClient } }) => Promise.all([
    queryClient.ensureQueryData(studioProfileQuery()),
    queryClient.ensureQueryData(studioSettingsQuery()),
  ]),
  component: StudioIndexComponent,   // keep the existing component name
})
```

Inside the component:

```tsx
const { balance, transactions, releases } = useStudio()
const { data: profile } = useSuspenseQuery(studioProfileQuery())
const { data: settings } = useSuspenseQuery(studioSettingsQuery())
```

(If `/studio/` already has a `loader`, merge the two `ensureQueryData` calls into its `Promise.all`.) Do not change any JSX that reads `profile`/`settings`/`balance`/`transactions`/`releases`.

- [ ] **Step 2: `studio.release.new.tsx` — prefetch + suspense for settings defaults.** Current: `const { settings } = useStudio()` (line ~33) and `const { addRelease } = useStudio()` (line ~53, a different component). For the component that reads `settings`, add a loader on its route (`/studio/release/new`) and swap to the query:

```tsx
import { useSuspenseQuery } from '@tanstack/react-query'
import { studioSettingsQuery } from '../lib/api/queries/studio'
// in the route definition, add/merge:
//   loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioSettingsQuery()),
```

Replace `const { settings } = useStudio()` with `const { data: settings } = useSuspenseQuery(studioSettingsQuery())`. Leave the `const { addRelease } = useStudio()` in the other component untouched (that's Slice 3 territory).

- [ ] **Step 3: `studio-shell.tsx` — non-suspending profile for the sidebar footer.** The shell renders on every studio screen and reads `profile.displayName`/`profile.avatar`. Use a plain `useQuery` (not suspense — the shell must paint immediately, and the child route's loader will have populated the cache anyway):

```tsx
import { useQuery } from '@tanstack/react-query'
import { studioProfileQuery } from '../../lib/api/queries/studio'
import { studioArtist } from '../../lib/studio-data'
// remove `useStudio` from the import; keep `initialsOf`:
import { initialsOf } from '../../features/studio/studio-context'
```

Replace `const { profile } = useStudio()` / `const name = profile.displayName.trim() || studioArtist.name` with:

```tsx
const { data: profile } = useQuery(studioProfileQuery())
const name = profile?.displayName.trim() || studioArtist.name
```

Update the two `<CreatorFooter ... avatar={profile.avatar} />` usages to `avatar={profile?.avatar ?? null}`.

- [ ] **Step 4: Verify.** Run: `grep -rn "useStudio" Frontend/src/routes/studio.index.tsx Frontend/src/components/studio/studio-shell.tsx`. Expected: `studio.index.tsx` still imports `useStudio` (for balance/transactions/releases); `studio-shell.tsx` has **no** `useStudio` value usage (only `initialsOf` imported). Confirm `studio.release.new.tsx` still uses `useStudio` only for `addRelease`.

- [ ] **Step 5: Typecheck + commit.** Run `cd Frontend && npx tsc --noEmit` (clean), then:

```bash
git add Frontend/src/routes/studio.index.tsx Frontend/src/routes/studio.release.new.tsx Frontend/src/components/studio/studio-shell.tsx
git commit -m "feat(frontend): point studio dashboard/wizard/shell at profile+settings queries"
```

---

## Task 5: Shrink `studio-context.tsx` — remove profile + settings

**Files:**
- Modify: `Frontend/src/features/studio/studio-context.tsx`

**Interfaces:**
- Consumes: nothing new.
- Produces: a smaller `StudioContextValue` (no `profile`/`settings`/`setProfile`/`setSettings`). Remaining: `releases`, `episodes`, `balance`, `transactions`, `methods` + their mutators, and the exported `initialsOf`.

- [ ] **Step 1: Confirm all profile/settings consumers are migrated.** Run: `grep -rn "\.profile\b\|\.settings\b\|setProfile\|setSettings" Frontend/src/routes/studio*.tsx Frontend/src/components/studio/` (excluding `studio-context.tsx`). Expected: no consumer reads `profile`/`settings` from `useStudio()` any more (matches are only local `p`/`s` draft state, `storeProfile`/`storeSettings` from the queries, or unrelated identifiers). If any real consumer remains, migrate it before proceeding.

- [ ] **Step 2: Remove profile/settings from the context.** In `studio-context.tsx`:
  - Drop `getStudioProfile`, `getStudioSettings`, `StudioProfile`, `StudioSettings` from the `../../lib/studio-data` import (keep `getReleases`, `getStudioEpisodes`, `StudioRelease`, `StudioEpisode`).
  - Remove `profile` and `settings` from the `StudioState` interface.
  - Remove `setProfile` and `setSettings` from `StudioContextValue`.
  - In `seed()`, drop the `profile`/`settings` fields.
  - In `hydrate()`, drop the `profile`/`settings` handling (the `safeUrl` helper and the `profile.pressAssets` filter go too, unless `safeUrl` is used elsewhere — it is not).
  - In the `value` object, drop `setProfile` and `setSettings`.

- [ ] **Step 3: Typecheck — this is the real test.** Run `cd Frontend && npx tsc --noEmit`. Expected: clean. A type error here means a consumer still reads the removed fields — fix that consumer (do not re-add the field).

- [ ] **Step 4: Run the full frontend test + lint gate.** Run:

```bash
cd Frontend && npx vitest run && npm run lint
```

Expected: tests green (the 3 pre-existing `localStorage.clear` failures in `token.test.ts`/`client.test.ts`/`cart-context.test.tsx` remain red — unrelated, red on master too); lint at the **214** baseline, no new warnings.

- [ ] **Step 5: Commit.**

```bash
git add Frontend/src/features/studio/studio-context.tsx
git commit -m "refactor(frontend): drop profile+settings from studio god-context (now query-backed)"
```

---

## Task 6: Verification gate, live QA, and PR

**Files:** none (verification + PR).

- [ ] **Step 1: Ask the user to run the gate.** Per project convention, the user runs `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` (IntelliJ JPS races the build if the agent runs it) and reports results. This is a frontend-only slice, so the backend gate should be unaffected; the relevant checks are the frontend `tsc`/`lint`/`vitest` already run in Task 5.

- [ ] **Step 2: Live QA (logged in as a creator on the running app).** Verify in the browser network tab:
  - `/studio/profile` → `GET /v1/studio/profile → 200`; the form renders the live profile (name, bio, genres, links, shows, press). Edit the bio, Save → `PUT /v1/studio/profile → 200`, toast "Profile changes saved", the sidebar footer name updates, and a refresh shows the persisted bio.
  - Change the username to one already taken (if a second artist exists) → `PUT → 409`, toast "That username is already taken.", the form reverts. (If no conflicting account exists, note this as not-exercised.)
  - `/studio/settings` → `GET /v1/studio/settings → 200`; sections render. Toggle a notification + change the default track price, Save → `PUT /v1/studio/settings → 200`, toast "Settings saved", refresh shows Category-A changes persisted. Confirm a Category-B edit (e.g. email) does **not** persist across refresh (expected — documented limitation).
  - `/studio` dashboard renders (reads profile+settings from cache, no extra requests if prefetched by navigation).
  - `/studio/release/new` renders and the default price/visibility reflect the saved settings defaults.
  - Sidebar footer shows the creator name/avatar on every studio screen.

- [ ] **Step 3: Push + open the PR.** Do NOT use `open-pr.sh` (it runs `git add -A`, which would stage the local-only `application.properties`, and targets `main`). Open manually:

```bash
git push -u origin feat/frontend-studio-profile-settings
gh pr create --base master --head feat/frontend-studio-profile-settings \
  --title "feat(frontend): wire studio profile + settings to /v1/studio" \
  --body "<summary: what, changes, the two documented limitations (Category-B session-local, blob: URL stripping / no upload endpoint), tests, live QA evidence>"
```

The PR body must call out the two documented limitations and flag the missing Category-B backend endpoints (identity/2FA + sessions, integrations, subscription/billing) as future work. Leave auto-merge off for the user to handle.

---

## Self-Review (author checklist — done at plan-write time)

- **Spec coverage:** Profile GET/PUT wired (Task 2), Settings GET/PUT-Category-A wired (Task 3), all read-only consumers migrated (Task 4), god-context shrunk (Task 5), verified + shipped (Task 6). ✓
- **Type consistency:** `studioProfileQuery`/`studioSettingsQuery`/`apiSaveStudioProfile`/`apiSaveStudioSettings` names are identical across Tasks 1–4. `SaveStudioSettingsBody` keys (`notifications`,`defaults`,`payouts`,`privacy`,`team`) match the backend `SaveStudioSettingsBody` writable subset exactly. ✓
- **No placeholders:** every code step shows real code; the only intentional prose is the PR body summary in Task 6 Step 3. ✓
- **Decomposition boundary:** context retains `releases`/`episodes`/payout state and `initialsOf`; only profile/settings removed — matches the incremental-peel strategy. ✓

## Studio roadmap position

Slice 1 (analytics + audience) — shipped (PR #138). **Slice 2 (this plan) — profile + settings.** Remaining: Slice 3 (releases + 5-step wizard), Slice 4 (podcasts), Slice 5 (payouts — money, needs security review). Each subsequent slice peels its domain out of `studio-context.tsx`; after Slice 5 the context is empty and deletable.
