import { afterEach, describe, expect, it, vi } from 'vitest'
import { overviewQuery, healthQuery, auditQuery } from './admin-overview'

const overviewWire = {
  rangeLabel: 'last 7 days',
  kpis: { activeUsers: 1, streams: 2, gmv: 3, newArtists: 4, deltas: { users: 0, streams: 1, gmv: -2 } },
  gmvByDay: [1], needsAttention: [], topArtists: [], paymentMethods: [],
}
const healthWire = { status: 'normal', metrics: [], listeners: [], incidents: [] }
const auditWire = { items: [], page: 1, size: 8, total: 0 }

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin overview queries', () => {
  it('overviewQuery sends the range and keys by it', async () => {
    const f = mockFetch(200, overviewWire); vi.stubGlobal('fetch', f)
    const r = await overviewQuery('30d').queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/overview?range=30d')
    expect(r.rangeLabel).toBe('last 7 days')
    expect(overviewQuery('30d').queryKey).toEqual(['admin', 'overview', '30d'])
  })

  it('healthQuery hits /v1/admin/health', async () => {
    const f = mockFetch(200, healthWire); vi.stubGlobal('fetch', f)
    const r = await healthQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/health')
    expect(r.status).toBe('normal')
    expect(healthQuery().queryKey).toEqual(['admin', 'health'])
  })

  it('auditQuery sends page + size and OMITS type/q when unset', async () => {
    const f = mockFetch(200, auditWire); vi.stubGlobal('fetch', f)
    await auditQuery('all', '', 2).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('page=2')
    expect(url).toContain('size=8')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('q=')
    expect(auditQuery('all', '', 2).queryKey).toEqual(['admin', 'audit', 'all', '', 2])
  })

  it('auditQuery sends type and q when set', async () => {
    const f = mockFetch(200, auditWire); vi.stubGlobal('fetch', f)
    await auditQuery('finance', 'refund', 1).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('type=finance')
    expect(url).toContain('q=refund')
  })
})
