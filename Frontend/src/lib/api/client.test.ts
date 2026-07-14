import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiFetch, setUnauthorizedHandler } from './client'
import { getToken, setToken } from './token'

function mockFetchOnce(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue({
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('apiFetch', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => {})
  })

  it('attaches the Bearer token when one is stored', async () => {
    setToken('tok-123')
    const fetchMock = mockFetchOnce(200, { ok: true })

    await apiFetch('/me')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBe('Bearer tok-123')
  })

  it('sends no Authorization header when signed out', async () => {
    const fetchMock = mockFetchOnce(200, { ok: true })

    await apiFetch('/home')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBeUndefined()
  })

  it('returns the parsed JSON body on success', async () => {
    mockFetchOnce(200, { id: 'a1', name: 'Black Sherif' })

    const result = await apiFetch<{ id: string; name: string }>('/artists/a1')

    expect(result).toEqual({ id: 'a1', name: 'Black Sherif' })
  })

  it('returns undefined for a 204 response', async () => {
    mockFetchOnce(204, null)

    const result = await apiFetch('/auth/logout', { method: 'POST' })

    expect(result).toBeUndefined()
  })

  it('parses the backend error envelope into an ApiError', async () => {
    mockFetchOnce(404, { error: { code: 'ARTIST_NOT_FOUND', message: 'No such artist' } })

    await expect(apiFetch('/artists/missing')).rejects.toMatchObject({
      status: 404,
      code: 'ARTIST_NOT_FOUND',
      message: 'No such artist',
    })
  })

  it('clears the token and calls the unauthorized handler on 401', async () => {
    setToken('tok-123')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockFetchOnce(401, { error: { code: 'UNAUTHENTICATED', message: 'Token expired' } })

    await expect(apiFetch('/me')).rejects.toMatchObject({ status: 401 })
    expect(getToken()).toBeNull()
    expect(handler).toHaveBeenCalledOnce()
  })

  it('sends a JSON body and Idempotency-Key when provided', async () => {
    const fetchMock = mockFetchOnce(200, { token: 't', account: {} })

    await apiFetch('/auth/login', {
      method: 'POST',
      body: { email: 'a@b.com', password: 'pw' },
      idempotencyKey: 'key-1',
    })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.com', password: 'pw' })
    expect(init.headers['Idempotency-Key']).toBe('key-1')
  })
})
