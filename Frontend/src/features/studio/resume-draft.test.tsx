/**
 * Resuming a saved draft.
 *
 * "Continue editing" used to route every release — drafts included — to the manage/publish page,
 * and the wizard always started from an empty draft. So an artist who had saved a half-finished
 * release (metadata entered, no audio yet) was shown a publish screen for work they had not
 * finished. These tests pin the mapping that makes resuming actually restore the draft.
 */

import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ReactNode } from 'react'
import { apiFetch } from '../../lib/api/client'
import { ReleaseDraftProvider, useReleaseDraft } from './release-draft-context'

vi.mock('../../lib/api/client', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api/client')>('../../lib/api/client')
  return { ...actual, apiFetch: vi.fn() }
})

const wrapper = ({ children }: { children: ReactNode }) => (
  <ReleaseDraftProvider>{children}</ReleaseDraftProvider>
)

const detail = {
  id: 'rel-1',
  title: 'Golden Hour',
  type: 'album',
  status: 'draft',
  date: '2026-08-01',
  trackCount: 2,
  streams: 0,
  revenue: { amount: 0, currency: 'GHS' },
  price: { amount: 12.5, currency: 'GHS' },
  genre: 'Highlife',
  description: 'Recorded in Accra',
  visibility: 'scheduled',
  scheduledAt: '2026-09-15T00:00:00Z',
  tracks: [
    // Deliberately out of order — the wizard must show the artist's ordering, not the API's.
    {
      trackId: 't2', title: 'Second', duration: 200, status: 'uploading', position: 1,
      price: { amount: 5, currency: 'GHS' }, splits: [],
    },
    {
      trackId: 't1', title: 'First', duration: 180, status: 'ready', position: 0,
      price: { amount: 7.5, currency: 'GHS' },
      splits: [{ id: 's1', name: 'Bob', email: 'bob@x.com', role: 'Producer', percent: 30, confirmation: 'pending' }],
    },
  ],
}

beforeEach(() => vi.mocked(apiFetch).mockReset())

describe('hydrateFromServer', () => {
  it('restores the metadata the artist already entered', async () => {
    vi.mocked(apiFetch).mockResolvedValue(detail as never)
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.hydrateFromServer('rel-1') })

    await waitFor(() => expect(result.current.draft.releaseId).toBe('rel-1'))
    expect(result.current.draft.title).toBe('Golden Hour')
    expect(result.current.draft.releaseType).toBe('album')
    expect(result.current.draft.genre).toBe('Highlife')
    expect(result.current.draft.description).toBe('Recorded in Accra')
    expect(result.current.draft.visibility).toBe('scheduled')
    // The date input is yyyy-mm-dd, not an ISO instant — passing the raw value renders blank.
    expect(result.current.draft.releaseDate).toBe('2026-09-15')
  })

  it('restores tracks in the artist’s order, not the order the API returned them', async () => {
    vi.mocked(apiFetch).mockResolvedValue(detail as never)
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.hydrateFromServer('rel-1') })

    await waitFor(() => expect(result.current.draft.tracks).toHaveLength(2))
    expect(result.current.draft.tracks.map((t) => t.id)).toEqual(['t1', 't2'])
    expect(result.current.draft.tracks[0].price).toBe(7.5)
  })

  it('never marks a resumed track as failed', async () => {
    vi.mocked(apiFetch).mockResolvedValue(detail as never)
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.hydrateFromServer('rel-1') })

    // A track that exists server-side uploaded successfully; anything not yet READY is still
    // processing. Mapping it to 'error' would resurrect the bogus "upload failed" badge on a
    // perfectly good draft the artist is coming back to.
    await waitFor(() => expect(result.current.draft.tracks).toHaveLength(2))
    expect(result.current.draft.tracks.map((t) => t.status)).toEqual(['ready', 'uploading'])
  })

  it('restores collaborator splits so the artist does not re-enter them', async () => {
    vi.mocked(apiFetch).mockResolvedValue(detail as never)
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.hydrateFromServer('rel-1') })

    await waitFor(() => expect(result.current.draft.splits.t1).toBeDefined())
    expect(result.current.draft.splits.t1[0]).toMatchObject({
      name: 'Bob', percent: 30, confirmation: 'pending',
    })
  })
})
