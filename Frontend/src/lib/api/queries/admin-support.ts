import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toSupportTicket, type SupportTicketWire } from '../mappers'

/** `GET /v1/admin/support/tickets` — the full inbox with threads inline (admin roles only). */
export function supportTicketsQuery() {
  return queryOptions({
    queryKey: ['admin', 'support', 'tickets'],
    queryFn: async () =>
      (await apiFetch<SupportTicketWire[]>('/admin/support/tickets')).map((t) => toSupportTicket(t)),
  })
}

/** `POST /v1/admin/support/tickets/:id/reply` — append an agent reply. */
export function apiReplyToTicket(id: string, text: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/reply`, { method: 'POST', body: { text } })
}

/** `POST /v1/admin/support/tickets/:id/assign` — assign the ticket (assign-to-self: pass own account id). */
export function apiAssignTicket(id: string, assigneeId: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/assign`, { method: 'POST', body: { assigneeId } })
}

/** `POST /v1/admin/support/tickets/:id/resolve` — mark resolved. */
export function apiResolveTicket(id: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/resolve`, { method: 'POST' })
}
