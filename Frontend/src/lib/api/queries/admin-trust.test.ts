import { afterEach, describe, expect, it, vi } from 'vitest'
import { riskBoardQuery, apiReviewSignal, apiClearSignal, apiBanSignal } from './admin-trust'

const riskBoardWire = {
  kpis: { chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 2, botStreams: '0%' },
  signals: [
    { id: 'r1', subject: 'user-1', type: 'chargeback', detail: 'Card dispute', level: 'high', time: '2025-01-01T00:00:00Z', status: 'open' },
  ],
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

describe('admin-trust queries', () => {
  it('riskBoardQuery hits /v1/admin/risk', async () => {
    const f = mockFetch(200, riskBoardWire)
    vi.stubGlobal('fetch', f)
    const result = await riskBoardQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/risk', expect.objectContaining({ method: 'GET' }))
    expect(result.signals[0].id).toBe('r1')
    expect(riskBoardQuery().queryKey).toEqual(['admin', 'risk', 'board'])
  })

  it('apiBanSignal POSTs the reason (the API rejects a blank one)', async () => {
    const f = mockFetch(200, {})
    vi.stubGlobal('fetch', f)
    await apiBanSignal('r1', 'Fraud')
    expect(f.mock.calls[0][0]).toBe('/v1/admin/risk/r1/ban')
    const [, opts] = f.mock.calls[0]
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Fraud' })
  })

  it('apiClearSignal and apiReviewSignal POST with no body', async () => {
    const f = mockFetch(200, {})
    vi.stubGlobal('fetch', f)

    await apiClearSignal('r1')
    expect(f.mock.calls[0][0]).toBe('/v1/admin/risk/r1/clear')
    expect(f.mock.calls[0][1].method).toBe('POST')
    expect(f.mock.calls[0][1].body).toBeUndefined()

    await apiReviewSignal('r1')
    expect(f.mock.calls[1][0]).toBe('/v1/admin/risk/r1/review')
    expect(f.mock.calls[1][1].method).toBe('POST')
    expect(f.mock.calls[1][1].body).toBeUndefined()
  })
})
