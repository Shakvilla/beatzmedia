import { afterEach, describe, expect, it, vi } from 'vitest'
import { moderationQuery, apiReviewCase, apiApproveCase, apiRemoveCase, apiEscalateCase, apiDismissCase } from './admin-moderation'

const queueWire = {
  items: [{ id: 'm1', item: 'X', reporter: '@a', reason: 'Spam', time: '2026-07-24T06:00:00Z', severity: 'low', status: 'open', escalated: false }],
  page: 1, size: 100, total: 1, summary: { openCount: 1, slaHours: 6, escalatedCount: 0 },
}
function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-moderation queries', () => {
  it('moderationQuery with no filters hits /v1/admin/moderation with only size=100 and maps items + summary', async () => {
    const f = mockFetch(200, queueWire); vi.stubGlobal('fetch', f)
    const result = await moderationQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation?size=100', expect.objectContaining({ method: 'GET' }))
    expect(result.items[0].item).toBe('X')
    expect(result.summary).toEqual({ open: 1, sla: 6, escalated: 0 })
    expect(moderationQuery().queryKey).toEqual(['admin', 'moderation', 'queue', 'all', 'all'])
  })

  it('moderationQuery(status) sends status server-side', async () => {
    const f = mockFetch(200, queueWire); vi.stubGlobal('fetch', f)
    await moderationQuery('open').queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation?size=100&status=open', expect.objectContaining({ method: 'GET' }))
    expect(moderationQuery('open').queryKey).toEqual(['admin', 'moderation', 'queue', 'open', 'all'])
  })

  it('moderationQuery(status, type) sends both status and type server-side', async () => {
    const f = mockFetch(200, queueWire); vi.stubGlobal('fetch', f)
    await moderationQuery('in_review', 'Copyright').queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation?size=100&status=in_review&type=Copyright', expect.objectContaining({ method: 'GET' }))
    expect(moderationQuery('in_review', 'Copyright').queryKey).toEqual(['admin', 'moderation', 'queue', 'in_review', 'Copyright'])
  })

  it('apiReviewCase POSTs to /review', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiReviewCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/review', expect.objectContaining({ method: 'POST' }))
  })

  it('apiApproveCase POSTs to /approve', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiApproveCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/approve', expect.objectContaining({ method: 'POST' }))
  })

  it('apiRemoveCase POSTs to /remove (no body when no reason)', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiRemoveCase('m1')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/moderation/m1/remove')
    expect(opts.method).toBe('POST')
    expect(opts.body).toBeUndefined()
  })

  it('apiEscalateCase POSTs to /escalate', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiEscalateCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/escalate', expect.objectContaining({ method: 'POST' }))
  })

  it('apiDismissCase POSTs to /dismiss', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiDismissCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/dismiss', expect.objectContaining({ method: 'POST' }))
  })
})
