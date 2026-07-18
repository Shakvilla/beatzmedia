import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { ReleaseDraftProvider, useReleaseDraft } from './release-draft-context'
import * as studio from '../../lib/api/queries/studio'

vi.mock('../../lib/api/queries/studio')

// jsdom doesn't implement URL.createObjectURL (https://github.com/jsdom/jsdom/issues/1721);
// uploadTrack calls it to preview the local file, so stub it for this environment.
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mock-url'
}

const wrapper = ({ children }: { children: ReactNode }) => (
  <ReleaseDraftProvider initial={{ releaseType: 'single', title: 'Soja', price: 2.5 }}>{children}</ReleaseDraftProvider>
)

beforeEach(() => vi.resetAllMocks())

describe('release-draft-context', () => {
  it('createOrUpdateDraft creates once, stores the id, then PATCHes on the next call', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUpdateRelease).mockResolvedValue()
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.createOrUpdateDraft() })
    expect(studio.apiCreateDraft).toHaveBeenCalledOnce()
    expect(result.current.draft.releaseId).toBe('rel-1')

    await act(async () => { await result.current.createOrUpdateDraft() })
    expect(studio.apiCreateDraft).toHaveBeenCalledOnce()          // not called again
    expect(studio.apiUpdateRelease).toHaveBeenCalledOnce()
  })

  it('uploadTrack adds a placeholder then replaces it with the server track', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUploadTrack).mockResolvedValue({
      id: 'trk-9', title: 'Soja', duration: 180, status: 'ready', progress: 100, src: '/a', price: 0, explicit: false,
    })
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.uploadTrack(new File(['x'], 'soja.wav')) })

    await waitFor(() => expect(result.current.draft.tracks).toHaveLength(1))
    expect(result.current.draft.tracks[0].id).toBe('trk-9')
    expect(result.current.draft.tracks[0].status).toBe('ready')
    expect(result.current.draft.tracks[0].price).toBe(2.5)        // wizard's chosen price preserved
  })

  it('submitRelease flushes tracks then submits', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUploadTrack).mockResolvedValue({
      id: 'trk-9', title: 'Soja', duration: 180, status: 'ready', progress: 100, src: '/a', price: 0, explicit: false,
    })
    vi.mocked(studio.apiUpdateRelease).mockResolvedValue()
    vi.mocked(studio.apiSubmitRelease).mockResolvedValue()
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.uploadTrack(new File(['x'], 'soja.wav')) })
    await act(async () => { await result.current.submitRelease() })

    expect(studio.apiUpdateRelease).toHaveBeenCalledWith('rel-1', expect.objectContaining({
      tracks: [{ trackId: 'trk-9', position: 0, priceMinor: 250 }],
    }))
    expect(studio.apiSubmitRelease).toHaveBeenCalledWith('rel-1', expect.any(String))
  })
})
