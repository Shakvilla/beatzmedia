import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { notificationsQuery, apiMarkAllRead, apiMarkOneRead } from './notifications'

vi.mock('../client')

const wireItem = {
  id: 'n1',
  type: 'sale',
  title: 'New sale',
  body: '“Soja” sold to @ama_b · +₵2.50',
  time: '12m ago',
  read: false,
  to: '/studio/payouts',
}

beforeEach(() => vi.resetAllMocks())

describe('notificationsQuery', () => {
  it('fetches the feed and returns mapped items plus the unread count', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({
      items: [wireItem, { ...wireItem, id: 'n2', read: true, to: null }],
      page: 1,
      size: 20,
      total: 2,
      unread: 1,
    })

    const res = await notificationsQuery().queryFn!({} as never)

    expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications')
    expect(res.unread).toBe(1)
    expect(res.items).toHaveLength(2)
    expect(res.items[0]).toEqual({
      id: 'n1',
      type: 'sale',
      title: 'New sale',
      body: '“Soja” sold to @ama_b · +₵2.50',
      time: '12m ago',
      read: false,
      to: '/studio/payouts',
    })
    // a null `to` on the wire becomes undefined on the domain type
    expect(res.items[1].to).toBeUndefined()
    expect(res.items[1].read).toBe(true)
  })
})

describe('mark-read mutations', () => {
  it('marks all notifications read', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(undefined)
    await apiMarkAllRead()
    expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications/read', { method: 'POST' })
  })

  it('marks a single notification read by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(undefined)
    await apiMarkOneRead('n1')
    expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications/n1/read', { method: 'POST' })
  })
})
