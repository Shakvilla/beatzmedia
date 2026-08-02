/**
 * Provider-level tests. The reducer tests next door cover the state machine's contract; these
 * cover the glue around it — the query, the effects and the derived context value — which is
 * where every shipped bug in this player has actually lived. A pure reducer test structurally
 * cannot catch "the effect never re-runs, so the contract is never exercised".
 */

import { describe, it, expect, vi, beforeAll } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../../lib/api/client'
import { PlayerProvider, usePlayer } from './player-context'
import type { Track } from '../../types'

vi.mock('../../lib/api/client', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api/client')>('../../lib/api/client')
  return { ...actual, apiFetch: vi.fn() }
})

const track = (id: string): Track =>
  ({ id, title: id, artistId: 'a', artistName: 'A', duration: 204, image: '', ownership: 'for-sale' }) as Track

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <PlayerProvider>{children}</PlayerProvider>
      </QueryClientProvider>
    )
  }
}

// jsdom implements none of the media transport; the provider only ever calls these three.
beforeAll(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
})

/**
 * Install a stream response and count the calls locally.
 *
 * The counter is deliberately not `apiFetch.mock.calls` — clearing a module mock between tests
 * detaches vitest's tracking of already-settled rejected results, which then resurface as
 * unhandled errors against whichever test happens to be running. Each test owns its own counter
 * instead, so nothing has to be cleared.
 */
function stubStream(respond: () => Promise<unknown>) {
  const calls = { count: 0 }
  vi.mocked(apiFetch).mockImplementation((() => {
    calls.count++
    return respond()
  }) as typeof apiFetch)
  return calls
}

const unavailable = () => Promise.reject(new Error('MEDIA_UNAVAILABLE'))
const signed = (previewSeconds: number | null) => () =>
  Promise.resolve({
    audioUrl: `https://cdn.test/delivery/t1/${previewSeconds == null ? 'full' : 'preview'}.m4a?sig=1`,
    previewSeconds,
    expiresAt: '2026-08-01T12:05:00Z',
  })

describe('boot', () => {
  it('signs no stream until the fan asks for playback (I7)', async () => {
    const calls = stubStream(unavailable)
    const { result } = renderHook(() => usePlayer(), { wrapper: makeWrapper() })

    // The app boots with a queue nobody chose. Fetching for it would paint "not available"
    // before the first interaction.
    expect(calls.count).toBe(0)
    expect(result.current.unavailable).toBe(false)
    expect(result.current.isPlaying).toBe(false)

    act(() => result.current.playQueue([track('t1')], 0))
    await waitFor(() => expect(calls.count).toBe(1))
  })
})

describe('a track whose stream is unavailable', () => {
  it('never claims to be playing, however many times play is pressed (C1)', async () => {
    stubStream(unavailable)
    const { result } = renderHook(() => usePlayer(), { wrapper: makeWrapper() })

    act(() => result.current.playQueue([track('t1')], 0))
    await waitFor(() => expect(result.current.unavailable).toBe(true))
    expect(result.current.isPlaying).toBe(false)

    // Nothing audible happened, so the fan clicks the same row again. The query key, `isError`
    // and `errorUpdatedAt` are all unchanged, so the STREAM_ERROR effect cannot re-fire — the
    // reducer's isPlaying:true must not reach the UI, or every equalizer animation in the app
    // starts asserting playback over silence.
    act(() => result.current.playQueue([track('t1')], 0))
    expect(result.current.isPlaying).toBe(false)
    expect(result.current.unavailable).toBe(true)

    act(() => result.current.togglePlay())
    expect(result.current.isPlaying).toBe(false)
    expect(result.current.unavailable).toBe(true)
  })

  it('recovers through retry() when the stream comes back (I1)', async () => {
    stubStream(unavailable)
    const { result } = renderHook(() => usePlayer(), { wrapper: makeWrapper() })

    act(() => result.current.playQueue([track('t1')], 0))
    await waitFor(() => expect(result.current.unavailable).toBe(true))

    // The retry path exists because every play control is disabled in this state. If it were not
    // reachable and effective, `unavailable` would be a one-way door.
    stubStream(signed(null))
    act(() => result.current.retry())

    await waitFor(() => expect(result.current.unavailable).toBe(false))
    expect(result.current.isPlaying).toBe(true)
  })

  it('stays honest when the retry fails again (I1 + C1)', async () => {
    const calls = stubStream(unavailable)
    const { result } = renderHook(() => usePlayer(), { wrapper: makeWrapper() })

    act(() => result.current.playQueue([track('t1')], 0))
    await waitFor(() => expect(result.current.unavailable).toBe(true))

    act(() => result.current.retry())
    await waitFor(() => expect(calls.count).toBe(2))

    // A repeat failure is an isError true→true transition; only `errorUpdatedAt` moves. If the
    // effect misses it, the reducer's optimistic isPlaying:true from PLAY survives forever.
    await waitFor(() => expect(result.current.isPlaying).toBe(false))
    expect(result.current.unavailable).toBe(true)
  })
})

describe('a playable track', () => {
  it('reports the server-signed preview length and plays', async () => {
    stubStream(signed(30))
    const { result } = renderHook(() => usePlayer(), { wrapper: makeWrapper() })

    act(() => result.current.playQueue([track('t1')], 0))

    await waitFor(() => expect(result.current.isPreview).toBe(true))
    expect(result.current.previewSeconds).toBe(30)
    expect(result.current.unavailable).toBe(false)
    expect(result.current.isPlaying).toBe(true)
  })
})
