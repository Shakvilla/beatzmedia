import { afterEach, describe, expect, it, vi } from 'vitest'
import { complianceQuery, apiStartRequest, apiCompleteRequest, apiExportRequest, apiNoticeRequest } from './admin-compliance'

const complianceListWire = [
  { id: 'c1', type: 'DSAR-export', subject: 'user-1', detail: 'Export request', due: '2025-02-01', status: 'new' },
  { id: 'c2', type: 'DSAR-delete', subject: 'user-2', detail: 'Delete request', due: '2025-02-05', status: 'in_progress' },
]

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

describe('admin-compliance queries', () => {
  it('complianceQuery hits /v1/admin/compliance with NO type param (the DSAR chip spans two wire types)', async () => {
    const f = mockFetch(200, complianceListWire)
    vi.stubGlobal('fetch', f)
    const result = await complianceQuery().queryFn!({} as never)
    // Assert the URL is EXACTLY '/v1/admin/compliance' -- no query string of any kind.
    expect(f.mock.calls[0][0]).toBe('/v1/admin/compliance')
    expect(f).toHaveBeenCalledWith('/v1/admin/compliance', expect.objectContaining({ method: 'GET' }))
    expect(result).toHaveLength(2)
    expect(result.map((r) => r.type)).toEqual(['DSAR-export', 'DSAR-delete'])
    expect(complianceQuery().queryKey).toEqual(['admin', 'compliance', 'list'])
  })

  it('start / complete / export / notice POST to their own paths', async () => {
    const f = mockFetch(200, {})
    vi.stubGlobal('fetch', f)

    await apiStartRequest('c1')
    expect(f.mock.calls[0][0]).toBe('/v1/admin/compliance/c1/start')
    expect(f.mock.calls[0][1].method).toBe('POST')

    await apiCompleteRequest('c1')
    expect(f.mock.calls[1][0]).toBe('/v1/admin/compliance/c1/complete')
    expect(f.mock.calls[1][1].method).toBe('POST')

    await apiExportRequest('c1')
    expect(f.mock.calls[2][0]).toBe('/v1/admin/compliance/c1/export')
    expect(f.mock.calls[2][1].method).toBe('POST')

    await apiNoticeRequest('c1')
    expect(f.mock.calls[3][0]).toBe('/v1/admin/compliance/c1/notice')
    expect(f.mock.calls[3][1].method).toBe('POST')
  })
})
