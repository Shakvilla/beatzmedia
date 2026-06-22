/**
 * Shared Artist Studio store.
 *
 * Owns the persistent, mutable studio state — profile, settings, releases and
 * the payout balance/ledger/methods — so edits survive navigation and refresh
 * and the artist's identity is consistent across every studio screen.
 *
 * State is seeded from the mock `getX()` helpers, hydrated from localStorage on
 * load, and persisted on every change. When the real API lands, swap the seed
 * + persistence for fetches/mutations; call sites keep using `useStudio()`.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  getStudioProfile, getStudioSettings, getReleases, getStudioEpisodes,
  type StudioProfile, type StudioSettings, type StudioRelease, type StudioEpisode,
} from '../../lib/studio-data'
import { getPayouts, type PayoutTxn, type PayoutMethod } from '../../lib/studio-payouts'

const PERSIST_KEY = 'beatzclik-studio'

interface StudioState {
  profile: StudioProfile
  settings: StudioSettings
  releases: StudioRelease[]
  episodes: StudioEpisode[]
  balance: number
  transactions: PayoutTxn[]
  methods: PayoutMethod[]
}

interface StudioContextValue extends StudioState {
  setProfile: (p: StudioProfile) => void
  setSettings: (s: StudioSettings) => void
  addRelease: (r: StudioRelease) => void
  updateRelease: (id: string, patch: Partial<StudioRelease>) => void
  removeRelease: (id: string) => void
  addEpisode: (e: StudioEpisode) => void
  removeEpisode: (id: string) => void
  withdraw: (amount: number, method: PayoutMethod) => void
  setDefaultMethod: (id: string) => void
  removeMethod: (id: string) => void
  addMethod: (m: Omit<PayoutMethod, 'id' | 'isDefault'>) => void
}

const StudioContext = createContext<StudioContextValue | null>(null)

const round2 = (n: number) => Math.round(n * 100) / 100
const todayLabel = () => new Date().toLocaleDateString('en-US', { month: 'short', day: '2-digit' })
/** blob: URLs from a previous session can't be re-resolved, so drop them. */
const safeUrl = (u: string | null) => (u && u.startsWith('blob:') ? null : u)

function seed(): StudioState {
  const p = getPayouts()
  return {
    profile: getStudioProfile(),
    settings: getStudioSettings(),
    releases: getReleases(),
    episodes: getStudioEpisodes(),
    balance: p.available,
    transactions: p.transactions,
    methods: p.methods,
  }
}

function hydrate(): StudioState {
  const base = seed()
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    if (!raw) return base
    const saved = JSON.parse(raw) as Partial<StudioState>
    const profile: StudioProfile = { ...base.profile, ...(saved.profile ?? {}) }
    profile.avatar = safeUrl(profile.avatar)
    profile.banner = safeUrl(profile.banner)
    profile.pressAssets = (profile.pressAssets ?? []).filter((a) => !a.url.startsWith('blob:'))
    return {
      profile,
      settings: { ...base.settings, ...(saved.settings ?? {}) },
      releases: saved.releases ?? base.releases,
      episodes: saved.episodes ?? base.episodes,
      balance: saved.balance ?? base.balance,
      transactions: saved.transactions ?? base.transactions,
      methods: saved.methods ?? base.methods,
    }
  } catch {
    return base
  }
}

export function StudioProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<StudioState>(hydrate)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) { first.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(state)) } catch { /* ignore quota */ }
  }, [state])

  const value = useMemo<StudioContextValue>(() => ({
    ...state,
    setProfile: (profile) => setState((s) => ({ ...s, profile })),
    setSettings: (settings) => setState((s) => ({ ...s, settings })),
    addRelease: (r) => setState((s) => ({ ...s, releases: [r, ...s.releases] })),
    updateRelease: (id, patch) => setState((s) => ({ ...s, releases: s.releases.map((r) => (r.id === id ? { ...r, ...patch } : r)) })),
    removeRelease: (id) => setState((s) => ({ ...s, releases: s.releases.filter((r) => r.id !== id) })),
    addEpisode: (e) => setState((s) => ({ ...s, episodes: [e, ...s.episodes] })),
    removeEpisode: (id) => setState((s) => ({ ...s, episodes: s.episodes.filter((e) => e.id !== id) })),
    withdraw: (amount, method) => setState((s) => ({
      ...s,
      balance: round2(s.balance - amount),
      transactions: [
        { id: `w-${Date.now()}`, date: todayLabel(), source: `Withdrawal · ${method.label}`, type: 'Cash-out', gross: null, net: -amount, status: 'pending' },
        ...s.transactions,
      ],
    })),
    setDefaultMethod: (id) => setState((s) => ({ ...s, methods: s.methods.map((m) => ({ ...m, isDefault: m.id === id })) })),
    removeMethod: (id) => setState((s) => {
      const methods = s.methods.filter((m) => m.id !== id)
      if (methods.length && !methods.some((m) => m.isDefault)) methods[0] = { ...methods[0], isDefault: true }
      return { ...s, methods }
    }),
    addMethod: (m) => setState((s) => ({ ...s, methods: [...s.methods, { ...m, id: `m-${Date.now()}`, isDefault: s.methods.length === 0 }] })),
  }), [state])

  return <StudioContext.Provider value={value}>{children}</StudioContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useStudio(): StudioContextValue {
  const ctx = useContext(StudioContext)
  if (!ctx) throw new Error('useStudio must be used within a <StudioProvider>')
  return ctx
}

/** Two-letter monogram from a display name. */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}
