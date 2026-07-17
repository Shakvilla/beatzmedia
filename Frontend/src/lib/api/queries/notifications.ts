import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toAppNotification, type AppNotificationWire } from '../mappers'

/**
 * `GET /v1/me/notifications` — the caller's notification feed. The response is the
 * paginated envelope plus an `unread` count the bell badge renders, so this query
 * returns both rather than unwrapping to a bare list.
 */
interface NotificationsWire {
  items: AppNotificationWire[]
  page: number
  size: number
  total: number
  unread: number
}

export function notificationsQuery() {
  return queryOptions({
    queryKey: ['notifications'],
    queryFn: async () => {
      const wire = await apiFetch<NotificationsWire>('/me/notifications')
      return { items: wire.items.map(toAppNotification), unread: wire.unread }
    },
  })
}

/** `POST /v1/me/notifications/read` — mark every notification read. */
export async function apiMarkAllRead(): Promise<void> {
  await apiFetch('/me/notifications/read', { method: 'POST' })
}

/** `POST /v1/me/notifications/{id}/read` — mark one notification read. */
export async function apiMarkOneRead(id: string): Promise<void> {
  await apiFetch(`/me/notifications/${id}/read`, { method: 'POST' })
}
