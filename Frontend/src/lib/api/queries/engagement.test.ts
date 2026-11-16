import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../client'
import {
  apiClearListeningHistory,
  apiPutListeningCheckpoint,
  apiStartListeningSession,
  apiUpdateListeningSettings,
  continueListeningQuery,
  listeningSettingsQuery,
  recentListeningQuery,
} from './engagement'

vi.mock('../client')
const ctx = {} as never

beforeEach(() => vi.resetAllMocks())

describe('engagement API', () => {
  it('opens a session and writes an idempotent sequence checkpoint', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined)
    await apiStartListeningSession()
    await apiPutListeningCheckpoint('session/a', 7, {
      contentKind: 'podcast-episode',
      contentId: 'episode-1',
      eventType: 'progress',
      positionSeconds: 42,
      durationSeconds: 300,
      listenedDeltaSeconds: 15,
    })

    expect(apiFetch).toHaveBeenNthCalledWith(1, '/me/listening-sessions', { method: 'POST' })
    expect(apiFetch).toHaveBeenNthCalledWith(
      2,
      '/me/listening-sessions/session%2Fa/checkpoints/7',
      {
        method: 'PUT',
        body: expect.objectContaining({ contentId: 'episode-1', positionSeconds: 42 }),
      },
    )
  })

  it('uses account-scoped query keys and honours authentication gating', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ items: [], nextCursor: null })
    const recent = recentListeningQuery(true, 8)
    const continuing = continueListeningQuery(false, 5)
    const settings = listeningSettingsQuery(true)

    expect(recent.queryKey).toEqual(['me', 'listening', 'recent', 8])
    expect(continuing.enabled).toBe(false)
    expect(settings.queryKey).toEqual(['me', 'listening', 'settings'])
    await recent.queryFn!(ctx)
    expect(apiFetch).toHaveBeenCalledWith('/me/listening/recent?limit=8')
  })

  it('updates preference and clears history through explicit endpoints', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined)
    await apiUpdateListeningSettings(false)
    await apiClearListeningHistory()

    expect(apiFetch).toHaveBeenNthCalledWith(1, '/me/listening/settings', {
      method: 'PUT',
      body: { historyEnabled: false },
    })
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/me/listening/history', { method: 'DELETE' })
  })
})
