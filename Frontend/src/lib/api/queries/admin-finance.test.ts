import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  financeOverviewQuery, ledgerQuery, disputeQuery, pendingPayoutsQuery,
  apiRefundDispute, apiRejectDispute, apiEscalateDispute, apiRunWeeklyPayouts, apiSendPayout,
} from './admin-finance'

const overviewWire = {
  kpis: { gmvMtd: 1, gmvDelta: 2, platformFee: 3, feeTakePct: 4, payoutsDue: 5, payoutsArtists: 6, momoFloat: 7 },
  pendingPayouts: [], providerMix: [], disputes: [],
}
const ledgerWire = { items: [], page: 1, size: 8, total: 0 }
const disputeWire = {
  id: 'd1', kind: 'Refund request', subject: '@a', detail: 'x',
  amount: { amount: 1, currency: 'GHS' }, status: 'open', opened: null, timeline: [],
}

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-finance reads', () => {
  it('financeOverviewQuery hits /v1/admin/finance with no range param', async () => {
    const f = mockFetch(200, overviewWire); vi.stubGlobal('fetch', f)
    const r = await financeOverviewQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance')
    expect(r.kpis.gmvMtd).toBe(1)
    expect(financeOverviewQuery().queryKey).toEqual(['admin', 'finance', 'overview'])
  })

  it('ledgerQuery sends page + size and OMITS type/q when unset', async () => {
    const f = mockFetch(200, ledgerWire); vi.stubGlobal('fetch', f)
    await ledgerQuery('all', '', 3).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('page=3')
    expect(url).toContain('size=8')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('q=')
    expect(ledgerQuery('all', '', 3).queryKey).toEqual(['admin', 'finance', 'ledger', 'all', '', 3])
  })

  it('ledgerQuery sends type and q when set', async () => {
    const f = mockFetch(200, ledgerWire); vi.stubGlobal('fetch', f)
    await ledgerQuery('Payout', 'kojo', 1).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('type=Payout')
    expect(url).toContain('q=kojo')
  })

  it('disputeQuery hits /disputes/:id and keys by id', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    const r = await disputeQuery('d1').queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance/disputes/d1')
    expect(r.status).toBe('open')
    expect(disputeQuery('d1').queryKey).toEqual(['admin', 'finance', 'dispute', 'd1'])
  })

  it('pendingPayoutsQuery hits /payouts and maps the MoneyView envelope', async () => {
    const f = mockFetch(200, [{ id: 'p1', artist: 'A', amount: { amount: 12.5, currency: 'GHS' }, method: 'MoMo', status: 'ready' }])
    vi.stubGlobal('fetch', f)
    const r = await pendingPayoutsQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance/payouts')
    expect(r[0].amount).toBe(12.5)
    expect(pendingPayoutsQuery().queryKey).toEqual(['admin', 'finance', 'payouts'])
  })
})

describe('admin-finance mutations', () => {
  it('apiRefundDispute POSTs a reason, no amount (full refund), WITH an Idempotency-Key', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiRefundDispute('d1', 'Refunded · dispute closed')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/disputes/d1/refund')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Refunded · dispute closed' })
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
  })

  it('apiRejectDispute POSTs a reason and needs no Idempotency-Key', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiRejectDispute('d1', 'evidence sufficient')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/disputes/d1/reject')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'evidence sufficient' })
    expect(opts.headers['Idempotency-Key']).toBeUndefined()
  })

  it('apiEscalateDispute POSTs to /escalate', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiEscalateDispute('d1')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/disputes/d1/escalate')
    expect(opts.method).toBe('POST')
  })

  it('apiRunWeeklyPayouts POSTs WITH an Idempotency-Key and surfaces the server\'s authoritative paid count', async () => {
    const f = mockFetch(200, { id: 'b1', kind: 'weekly', count: 7, total: { amount: 1, currency: 'GHS' }, runAt: null })
    vi.stubGlobal('fetch', f)
    const result = await apiRunWeeklyPayouts()
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/payouts/run-weekly')
    expect(opts.method).toBe('POST')
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
    expect(result).toEqual({ count: 7 })
  })

  it('apiSendPayout POSTs WITH an Idempotency-Key', async () => {
    const f = mockFetch(200, {}); vi.stubGlobal('fetch', f)
    await apiSendPayout('w1')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/payouts/w1/send')
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
  })

  it.each([
    ['apiSendPayout', () => apiSendPayout('w1')],
    ['apiRunWeeklyPayouts', () => apiRunWeeklyPayouts()],
    ['apiRefundDispute', () => apiRefundDispute('d1', 'reason')],
  ] as const)('%s generates a DIFFERENT Idempotency-Key per call', async (_name, call) => {
    const f = mockFetch(200, {}); vi.stubGlobal('fetch', f)
    await call()
    await call()
    expect(f.mock.calls[0][1].headers['Idempotency-Key']).not.toBe(f.mock.calls[1][1].headers['Idempotency-Key'])
  })
})
