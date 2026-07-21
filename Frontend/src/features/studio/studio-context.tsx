/**
 * Shared Artist Studio store.
 *
 * Owns the persistent, mutable studio state — episodes and the payout
 * balance/ledger/methods — so edits survive navigation and refresh.
 * (Profile and settings are query-backed via `studioProfileQuery()` /
 * `studioSettingsQuery()`, and releases via `studioReleasesQuery()` /
 * `studioReleaseQuery()`, and no longer live here.)
 *
 * State is seeded from the mock `getX()` helpers, hydrated from localStorage on
 * load, and persisted on every change. When the real API lands, swap the seed
 * + persistence for fetches/mutations; call sites keep using `useStudio()`.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { getPayouts, type PayoutTxn, type PayoutMethod } from '../../lib/studio-payouts'

const PERSIST_KEY = 'beatzclik-studio'

interface StudioState {
  balance: number
  transactions: PayoutTxn[]
  methods: PayoutMethod[]
}

interface StudioContextValue extends StudioState {
  withdraw: (amount: number, method: PayoutMethod) => void
  setDefaultMethod: (id: string) => void
  removeMethod: (id: string) => void
  addMethod: (m: Omit<PayoutMethod, 'id' | 'isDefault'>) => void
}

const StudioContext = createContext<StudioContextValue | null>(null)

const round2 = (n: number) => Math.round(n * 100) / 100
const todayLabel = () => new Date().toLocaleDateString('en-US', { month: 'short', day: '2-digit' })

function seed(): StudioState {
  const p = getPayouts()
  return {
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
    return {
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
