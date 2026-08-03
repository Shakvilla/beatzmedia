import { describe, it, expect, vi, beforeEach } from 'vitest'
import { trackStreamQuery } from './playback'
import { apiFetch } from '../client'

vi.mock('../client')

const ctx = {} as never

beforeEach(() => vi.resetAllMocks())

describe('trackStreamQuery', () => {
  it('fetches /tracks/:id/stream and maps the wire shape', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      audioUrl: 'https://minio.local/full.m4a?sig=abc',
      previewSeconds: 30,
      expiresAt: '2026-08-01T12:00:00Z',
    })

    const result = await trackStreamQuery('last-last').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/last-last/stream')
    expect(result).toEqual({
      audioUrl: 'https://minio.local/full.m4a?sig=abc',
      previewSeconds: 30,
      expiresAt: '2026-08-01T12:00:00Z',
    })
  })

  it('encodes the track id into the path', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ audioUrl: 'u', previewSeconds: null, expiresAt: null })

    await trackStreamQuery('a/b').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/a%2Fb/stream')
  })

  it('keys per track so switching tracks does not reuse a cached URL', () => {
    expect(trackStreamQuery('t1').queryKey).toEqual(['track', 't1', 'stream'])
    expect(trackStreamQuery('t2').queryKey).not.toEqual(trackStreamQuery('t1').queryKey)
  })

  it('does not retry — a 503 MEDIA_UNAVAILABLE must not retry-storm', () => {
    expect(trackStreamQuery('t1').retry).toBe(false)
  })
})
