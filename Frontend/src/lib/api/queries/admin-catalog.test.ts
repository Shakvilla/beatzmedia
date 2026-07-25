import { afterEach, describe, expect, it, vi } from 'vitest'
import { catalogQuery, catalogItemQuery, apiApproveCatalog, apiFlagCatalog, apiTakedownCatalog } from './admin-catalog'

const pagedWire = {
  items: [{ id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending' }],
  page: 1, size: 100, total: 1, counts: { pending: 1, published: 0, takedown: 0 },
}
const detailWire = {
  id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'Album', status: 'pending', upc: 'BZ1',
  tracklist: [], splits: [], actionLog: [],
}
function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-catalog queries', () => {
  it('catalogQuery hits /v1/admin/catalog and maps items + counts', async () => {
    const f = mockFetch(200, pagedWire); vi.stubGlobal('fetch', f)
    const result = await catalogQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog', expect.objectContaining({ method: 'GET' }))
    expect(result.items[0].title).toBe('Iron Boy')
    expect(result.counts.pending).toBe(1)
    expect(catalogQuery().queryKey).toEqual(['admin', 'catalog', 'list'])
  })

  it('catalogItemQuery hits /v1/admin/catalog/:id and keys by id', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    const result = await catalogItemQuery('c1').queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog/c1', expect.objectContaining({ method: 'GET' }))
    expect(result.id).toBe('c1')
    expect(catalogItemQuery('c1').queryKey).toEqual(['admin', 'catalog', 'detail', 'c1'])
  })

  it('apiApproveCatalog POSTs to /approve', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiApproveCatalog('c1')
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog/c1/approve', expect.objectContaining({ method: 'POST' }))
  })

  it('apiFlagCatalog POSTs optional note to /flag', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiFlagCatalog('c1', 'dupe')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/catalog/c1/flag')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ note: 'dupe' })
  })

  it('apiTakedownCatalog POSTs reason to /takedown', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiTakedownCatalog('c1', 'Copyright claim')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/catalog/c1/takedown')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Copyright claim' })
  })
})
