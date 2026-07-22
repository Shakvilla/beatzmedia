import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { payoutsQuery, apiRequestWithdrawal, apiAddPayoutMethod, apiSetDefaultPayoutMethod, apiRemovePayoutMethod } from './payouts'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)
const METHOD = { id: 'm1', label: 'MTN MoMo', detail: '0244', kind: 'momo', isDefault: true }

beforeEach(() => apiFetch.mockReset())

describe('payoutsQuery', () => {
  it('GETs /studio/payouts and maps', async () => {
    apiFetch.mockResolvedValue({ available: 10, pending: 0, thisMonth: 0, thisMonthDelta: 0, lifetime: 0,
      since: 'x', earnings: [], bySource: { sales: 0, royalties: 0, tips: 0 }, methods: [METHOD], transactions: [] })
    const res = await payoutsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/payouts')
    expect(res.available).toBe(10)
    expect(res.methods[0].id).toBe('m1')
  })
})

describe('apiRequestWithdrawal', () => {
  it('POSTs the money shape with an Idempotency-Key', async () => {
    apiFetch.mockResolvedValue({})
    await apiRequestWithdrawal(250, 'm1')
    const [path, opts] = apiFetch.mock.calls[0]! as [string, any]
    expect(path).toBe('/studio/payouts/withdraw')
    expect(opts.method).toBe('POST')
    expect(opts.body).toEqual({ amount: { amount: 250, currency: 'GHS' }, methodId: 'm1' })
    expect(typeof opts.idempotencyKey).toBe('string')
    expect((opts.idempotencyKey as string).length).toBeGreaterThan(0)
  })
})

describe('apiAddPayoutMethod', () => {
  it('POSTs a momo method with network + walletNumber', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiAddPayoutMethod({ kind: 'momo', label: 'MTN MoMo', detail: '0244000000',
      network: 'mtn', walletNumber: '0244000000', bankName: null, bankCode: null, accountName: null, accountNumber: null })
    const [path, opts] = apiFetch.mock.calls[0]! as [string, any]
    expect(path).toBe('/studio/payout-methods')
    expect(opts.method).toBe('POST')
    expect(opts.body).toMatchObject({ kind: 'momo', network: 'mtn', walletNumber: '0244000000', bankCode: null })
  })
  it('POSTs a bank method with the four bank fields', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiAddPayoutMethod({ kind: 'bank', label: 'GCB Bank', detail: '1234567890',
      network: null, walletNumber: null, bankName: 'GCB Bank', bankCode: 'GCB', accountName: 'Kojo', accountNumber: '1234567890' })
    const opts = apiFetch.mock.calls[0]![1] as any
    expect(opts.body).toMatchObject({ kind: 'bank', bankCode: 'GCB', accountName: 'Kojo', network: null })
  })
})

describe('apiSetDefaultPayoutMethod / apiRemovePayoutMethod', () => {
  it('PATCHes default', async () => {
    apiFetch.mockResolvedValue(METHOD)
    await apiSetDefaultPayoutMethod('m2')
    expect(apiFetch).toHaveBeenCalledWith('/studio/payout-methods/m2/default', { method: 'PATCH' })
  })
  it('DELETEs by id', async () => {
    apiFetch.mockResolvedValue(undefined)
    await apiRemovePayoutMethod('m2')
    expect(apiFetch).toHaveBeenCalledWith('/studio/payout-methods/m2', { method: 'DELETE' })
  })
})
