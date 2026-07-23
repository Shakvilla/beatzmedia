import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { supportTicketsQuery, apiReplyToTicket, apiAssignTicket, apiResolveTicket } from './admin-support'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)
const TICKET = { id: 't1', subject: 'S', requester: 'R', channel: 'email', priority: 'high',
  status: 'open', age: '2026-07-22T10:00:00Z', messages: [] }

beforeEach(() => apiFetch.mockReset())

describe('supportTicketsQuery', () => {
  it('GETs /admin/support/tickets and maps', async () => {
    apiFetch.mockResolvedValue([TICKET])
    const res = await supportTicketsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets')
    expect(res[0].id).toBe('t1')
    expect(res[0].priority).toBe('high')
  })
})

describe('mutations', () => {
  it('reply POSTs text', async () => {
    apiFetch.mockResolvedValue({})
    await apiReplyToTicket('t1', 'hello')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/reply', { method: 'POST', body: { text: 'hello' } })
  })
  it('assign POSTs assigneeId', async () => {
    apiFetch.mockResolvedValue({})
    await apiAssignTicket('t1', 'acct-9')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/assign', { method: 'POST', body: { assigneeId: 'acct-9' } })
  })
  it('resolve POSTs', async () => {
    apiFetch.mockResolvedValue({})
    await apiResolveTicket('t1')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/resolve', { method: 'POST' })
  })
})
