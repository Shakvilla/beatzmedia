import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { studioShowsQuery, studioEpisodesQuery, apiCreateEpisode, apiDeleteEpisode } from './podcasts-studio'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)

const EP = { id: 'ep1', showId: 'sh1', showTitle: 'S', title: 'T', duration: 1, status: 'published',
  premium: false, price: 0, publishedAt: 'x', plays: 0 }

beforeEach(() => apiFetch.mockReset())

describe('studioShowsQuery', () => {
  it('GETs /studio/podcasts/shows and maps', async () => {
    apiFetch.mockResolvedValue([{ id: 'sh1', title: 'S', category: 'C' }])
    const res = await studioShowsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/shows')
    expect(res).toEqual([{ id: 'sh1', title: 'S', category: 'C' }])
  })
})

describe('studioEpisodesQuery', () => {
  it('GETs /studio/podcasts/episodes and maps', async () => {
    apiFetch.mockResolvedValue([EP])
    const res = await studioEpisodesQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/episodes')
    expect(res[0].id).toBe('ep1')
  })
})

describe('apiCreateEpisode', () => {
  it('POSTs multipart with audio + data parts and an Idempotency-Key', async () => {
    apiFetch.mockResolvedValue(EP)
    const audio = new File(['x'], 'ep.mp3', { type: 'audio/mpeg' })
    await apiCreateEpisode({ audio, showId: 'sh1', newShow: null, title: 'T', description: 'D',
      cover: null, visibility: 'public', date: null, premium: false, price: null, earlyAccess: false })
    const [path, opts] = apiFetch.mock.calls[0]! as [string, any]
    expect(path).toBe('/studio/podcasts/episodes')
    expect(opts.method).toBe('POST')
    expect(opts.body).toBeInstanceOf(FormData)
    const fd = opts.body as FormData
    expect(fd.get('audio')).toBeInstanceOf(File)
    const data = JSON.parse(fd.get('data') as string)
    expect(data).toMatchObject({ showId: 'sh1', newShow: null, title: 'T', visibility: 'public' })
    expect(data.duration).toBeUndefined()
    expect(data.status).toBeUndefined()
    expect(typeof opts.idempotencyKey).toBe('string')
    expect((opts.idempotencyKey as string).length).toBeGreaterThan(0)
  })

  it('sends newShow + null showId + ISO date when scheduling a new show', async () => {
    apiFetch.mockResolvedValue(EP)
    const audio = new File(['x'], 'ep.mp3', { type: 'audio/mpeg' })
    await apiCreateEpisode({ audio, showId: null, newShow: { title: 'New', category: 'Storytelling' },
      title: 'T', description: '', cover: null, visibility: 'scheduled', date: '2030-01-02',
      premium: true, price: 5, earlyAccess: true })
    const data = JSON.parse(((apiFetch.mock.calls[0]![1] as any).body as FormData).get('data') as string)
    expect(data.showId).toBeNull()
    expect(data.newShow).toEqual({ title: 'New', category: 'Storytelling' })
    expect(data.date).toBe('2030-01-02T00:00:00.000Z')
    expect(data.premium).toBe(true)
    expect(data.price).toBe(5)
  })
})

describe('apiDeleteEpisode', () => {
  it('DELETEs by id', async () => {
    apiFetch.mockResolvedValue(undefined)
    await apiDeleteEpisode('ep1')
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/episodes/ep1', { method: 'DELETE' })
  })
})
