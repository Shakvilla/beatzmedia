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
