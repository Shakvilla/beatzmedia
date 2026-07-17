/**
 * Notifications store.
 *
 * Backed by `GET /v1/me/notifications` (the feed plus the server's unread count, which the
 * bell badge renders). Mark-read writes go to `POST /v1/me/notifications/read` and
 * `/{id}/read`, applied optimistically so the badge updates instantly and then reconciles
 * with the server. Consumers keep using `useNotifications()`.
 */

import { createContext, useContext, useEffect, useMemo, useRef, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { AppNotification, NotificationType } from '../../types'
import { useAuth } from '../auth/auth-context'
import { useToast } from '../../components/ui/toast-provider'
import {
  notificationsQuery,
  apiMarkAllRead,
  apiMarkOneRead,
} from '../../lib/api/queries/notifications'

export type { AppNotification, NotificationType }

/** Cached feed shape — mirrors what `notificationsQuery` returns. */
interface NotificationsFeed {
  items: AppNotification[]
  unread: number
}

interface NotificationsContextValue {
  notifications: AppNotification[]
  unread: number
  markRead: (id: string) => void
  markAllRead: () => void
}

const NOTIFICATIONS_KEY = ['notifications']
const EMPTY_FEED: NotificationsFeed = { items: [], unread: 0 }

const NotificationsContext = createContext<NotificationsContextValue | null>(null)

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const { data } = useQuery({ ...notificationsQuery(), enabled: isAuthenticated })
  const feed = data ?? EMPTY_FEED

  // Drop the feed cache on logout (the server is the source of truth). Only on an actual
  // authed→unauthed transition — not on first mount, where auth hasn't hydrated yet.
  const wasAuthed = useRef(isAuthenticated)
  useEffect(() => {
    if (wasAuthed.current && !isAuthenticated) {
      queryClient.removeQueries({ queryKey: NOTIFICATIONS_KEY })
    }
    wasAuthed.current = isAuthenticated
  }, [isAuthenticated, queryClient])

  // Optimistic-cache helper: apply `transform` immediately, run `call`, roll back on error.
  // Mirrors CollectionProvider — no cancelQueries (it would surface as a CancelledError in
  // loaders); the onSettled invalidate reconciles with the server instead.
  async function optimistic(
    transform: (f: NotificationsFeed) => NotificationsFeed,
    call: () => Promise<unknown>,
  ) {
    const prev = queryClient.getQueryData<NotificationsFeed>(NOTIFICATIONS_KEY) ?? EMPTY_FEED
    queryClient.setQueryData<NotificationsFeed>(NOTIFICATIONS_KEY, transform(prev))
    try {
      await call()
    } catch {
      queryClient.setQueryData<NotificationsFeed>(NOTIFICATIONS_KEY, prev)
      toast('Could not update your notifications', 'error')
    } finally {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
    }
  }

  const value = useMemo<NotificationsContextValue>(
    () => ({
      notifications: feed.items,
      unread: feed.unread,
      markRead: (id) =>
        void optimistic(
          (f) => ({
            items: f.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
            // Only decrement when the target was actually unread, so repeated taps can't
            // drive the badge below the server's real count.
            unread: f.items.some((n) => n.id === id && !n.read) ? Math.max(0, f.unread - 1) : f.unread,
          }),
          () => apiMarkOneRead(id),
        ),
      markAllRead: () =>
        void optimistic(
          (f) => ({ items: f.items.map((n) => ({ ...n, read: true })), unread: 0 }),
          () => apiMarkAllRead(),
        ),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [feed],
  )

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useNotifications(): NotificationsContextValue {
  const ctx = useContext(NotificationsContext)
  if (!ctx) throw new Error('useNotifications must be used within a <NotificationsProvider>')
  return ctx
}
