import { afterEach, describe, expect, it, vi } from 'vitest'
import { featuredQuery, pushScheduleQuery, curatedPlaylistsQuery, apiSaveFeatured } from './admin-editorial'

const featuredWire = [{ id: 'f1', title: 'A', note: 'n1', sponsored: false }]
const pushWire = [{ id: 'p1', day: 'Fri', timeLabel: '6PM', title: 'T', audience: 'All', scheduledAt: null }]
const playlistWire = [{ id: 'pl1', name: 'Hiplife' }]

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-editorial reads', () => {
  it('featuredQuery hits /v1/admin/editorial/featured', async () => {
    const f = mockFetch(200, featuredWire); vi.stubGlobal('fetch', f)
    const r = await featuredQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/featured')
    expect(r).toEqual([{ id: 'f1', title: 'A', note: 'n1', sponsored: false }])
    expect(featuredQuery().queryKey).toEqual(['admin', 'editorial', 'featured'])
  })

  it('pushScheduleQuery hits /push and maps timeLabel to time', async () => {
    const f = mockFetch(200, pushWire); vi.stubGlobal('fetch', f)
    const r = await pushScheduleQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/push')
    expect(r[0].time).toBe('6PM')
    expect(pushScheduleQuery().queryKey).toEqual(['admin', 'editorial', 'push'])
  })

  it('curatedPlaylistsQuery hits /playlists', async () => {
    const f = mockFetch(200, playlistWire); vi.stubGlobal('fetch', f)
    const r = await curatedPlaylistsQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/playlists')
    expect(r).toEqual([{ id: 'pl1', name: 'Hiplife' }])
    expect(curatedPlaylistsQuery().queryKey).toEqual(['admin', 'editorial', 'playlists'])
  })
})

describe('apiSaveFeatured', () => {
  it('PUTs the FULL list in the given order (the endpoint is a whole-list replace)', async () => {
    const f = mockFetch(200, featuredWire); vi.stubGlobal('fetch', f)
    await apiSaveFeatured([
      { id: 'b', title: 'Second', note: 'n2', sponsored: true },
      { id: 'a', title: 'First', note: 'n1' },
    ])
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/editorial/featured')
    expect(opts.method).toBe('PUT')
    expect(JSON.parse(opts.body)).toEqual([
      { id: 'b', title: 'Second', note: 'n2', sponsored: true },
      { id: 'a', title: 'First', note: 'n1', sponsored: false },
    ])
  })
})
