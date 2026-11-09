import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getToken } from '../../lib/api/token'
import { apiPutListeningCheckpoint, apiStartListeningSession } from '../../lib/api/queries/engagement'
import type { Track } from '../../types'
import { ListeningEngagementRecorder } from './listening-engagement'

vi.mock('../../lib/api/token')
vi.mock('../../lib/api/queries/engagement')

const track: Track = {
  id: 'episode-1',
  playbackKind: 'podcast_episode',
  title: 'Episode one',
  artistId: 'show-1',
  artistName: 'A show',
  duration: 300,
  image: '',
  ownership: 'free',
  downloadable: false,
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getToken).mockReturnValue('jwt')
  vi.mocked(apiStartListeningSession).mockResolvedValue({
    recording: true,
    sessionId: 'session-1',
    startedAt: '2026-11-02T10:00:00Z',
  })
  vi.mocked(apiPutListeningCheckpoint).mockResolvedValue(undefined)
})

describe('ListeningEngagementRecorder', () => {
  it('does nothing for a signed-out listener', () => {
    vi.mocked(getToken).mockReturnValue(null)
    const recorder = new ListeningEngagementRecorder()
    recorder.start(track, 300)
    recorder.progress(31, 300)
    expect(apiStartListeningSession).not.toHaveBeenCalled()
  })

  it('uses one session and monotonic checkpoint sequences', async () => {
    const recorder = new ListeningEngagementRecorder()
    recorder.start(track, 300)
    for (let second = 1; second <= 31; second += 1) recorder.progress(second, 300)
    recorder.complete(false)

    await vi.waitFor(() => expect(apiPutListeningCheckpoint).toHaveBeenCalledTimes(4))
    expect(apiStartListeningSession).toHaveBeenCalledTimes(1)
    expect(vi.mocked(apiPutListeningCheckpoint).mock.calls.map((call) => call[1])).toEqual([1, 2, 3, 4])
    expect(vi.mocked(apiPutListeningCheckpoint).mock.calls.map((call) => call[2].eventType)).toEqual([
      'start',
      'progress',
      'qualified-play',
      'completion',
    ])
  })

  it('records a podcast resume position without counting a seek as listening', async () => {
    const recorder = new ListeningEngagementRecorder()
    recorder.start(track, 300)
    recorder.progress(120, 300)
    for (let second = 121; second <= 135; second += 1) recorder.progress(second, 300)

    await vi.waitFor(() => expect(apiPutListeningCheckpoint).toHaveBeenCalledTimes(2))
    const progress = vi.mocked(apiPutListeningCheckpoint).mock.calls[1][2]
    expect(progress).toMatchObject({
      contentKind: 'podcast-episode',
      positionSeconds: 135,
      listenedDeltaSeconds: 15,
    })
  })

  it('opens a fresh session when the authenticated account token changes', async () => {
    const recorder = new ListeningEngagementRecorder()
    recorder.start(track, 300)
    await vi.waitFor(() => expect(apiPutListeningCheckpoint).toHaveBeenCalledTimes(1))

    vi.mocked(getToken).mockReturnValue('different-account-jwt')
    vi.mocked(apiStartListeningSession).mockResolvedValue({
      recording: true,
      sessionId: 'session-2',
      startedAt: '2026-11-02T10:05:00Z',
    })
    recorder.start({ ...track, id: 'episode-2' }, 300)

    await vi.waitFor(() => expect(apiPutListeningCheckpoint).toHaveBeenCalledTimes(2))
    expect(apiStartListeningSession).toHaveBeenCalledTimes(2)
    expect(vi.mocked(apiPutListeningCheckpoint).mock.calls[1][1]).toBe(1)
  })
})
