import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export interface TrackStreamWire {
  audioUrl: string
  previewSeconds: number | null
  expiresAt: string | null
}

export interface TrackStream {
  audioUrl: string
  /** Length of the preview the server signed, when this is a preview stream. */
  previewSeconds: number | null
  /** ISO instant the signed URL stops working. */
  expiresAt: string | null
}

/**
 * `GET /v1/tracks/:id/stream` — a signed, time-boxed delivery URL for one track.
 *
 * The server decides FULL vs PREVIEW from ownership (INV-3); the client never asks for a
 * variant and cannot widen one. `retry: false` because the failure mode here is
 * `503 MEDIA_UNAVAILABLE` for a track with no READY asset — retrying cannot help and would
 * hammer the API for every track in a queue.
 */
export function trackStreamQuery(trackId: string) {
  return queryOptions({
    queryKey: ['track', trackId, 'stream'],
    queryFn: async (): Promise<TrackStream> => {
      const w = await apiFetch<TrackStreamWire>(`/tracks/${encodeURIComponent(trackId)}/stream`)
      return { audioUrl: w.audioUrl, previewSeconds: w.previewSeconds, expiresAt: w.expiresAt }
    },
    retry: false,
    // Signed URLs are short-lived; never serve a cached one that may already be dead.
    staleTime: 0,
    gcTime: 60_000,
    // A focus refetch would mint a fresh signature for the same track and force the player to
    // reload the element — audibly restarting playback. Mid-track URL refresh is handled
    // explicitly by the player (expiry recovery), not by an implicit background refetch.
    refetchOnWindowFocus: false,
  })
}
