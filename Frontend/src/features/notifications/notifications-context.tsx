/**
 * Notifications store (mock).
 *
 * Holds the notification feed + read state, persisted to localStorage. Seeded
 * with a realistic mixed feed so the bell + inbox have content. Swap the seed
 * for a fetch/subscription later; consumers keep using `useNotifications()`.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { AppNotification, NotificationType } from '../../types'

export type { AppNotification, NotificationType }

const PERSIST_KEY = 'beatzclik-notifications'

const seed: AppNotification[] = [
  { id: 'n1', type: 'sale', title: 'New sale', body: '“Soja” sold to @ama_b · +₵2.50', time: '12m ago', read: false, to: '/studio/payouts' },
  { id: 'n2', type: 'tip', title: 'You got a tip', body: '@kwesi_ tipped you ₵5.00', time: '1h ago', read: false, to: '/studio/payouts' },
  { id: 'n3', type: 'follower', title: 'New follower', body: '@yaw_g started following you', time: '3h ago', read: false, to: '/studio/audience' },
  { id: 'n4', type: 'release', title: 'Release approved', body: '“Iron Boy” passed review and is scheduled', time: 'Yesterday', read: true, to: '/studio/releases' },
  { id: 'n5', type: 'payout', title: 'Payout sent', body: '₵5,000.00 to MTN MoMo · 0244 ··· 9210', time: '2 days ago', read: true, to: '/studio/payouts' },
  { id: 'n6', type: 'release', title: 'New from an artist you follow', body: 'Amaarae released “Fountain Baby II”', time: '3 days ago', read: true, to: '/' },
  { id: 'n7', type: 'system', title: 'Verify your rights', body: 'Confirm catalog ownership to keep payouts clearing', time: '5 days ago', read: true, to: '/studio/settings' },
]

interface NotificationsContextValue {
  notifications: AppNotification[]
  unread: number
  markRead: (id: string) => void
  markAllRead: () => void
  clearAll: () => void
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null)

function load(): AppNotification[] {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    return raw ? (JSON.parse(raw) as AppNotification[]) : seed
  } catch {
    return seed
  }
}

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<AppNotification[]>(load)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) { first.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(notifications)) } catch { /* ignore */ }
  }, [notifications])

  const value = useMemo<NotificationsContextValue>(() => ({
    notifications,
    unread: notifications.filter((n) => !n.read).length,
    markRead: (id) => setNotifications((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n))),
    markAllRead: () => setNotifications((list) => list.map((n) => ({ ...n, read: true }))),
    clearAll: () => setNotifications([]),
  }), [notifications])

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useNotifications(): NotificationsContextValue {
  const ctx = useContext(NotificationsContext)
  if (!ctx) throw new Error('useNotifications must be used within a <NotificationsProvider>')
  return ctx
}
