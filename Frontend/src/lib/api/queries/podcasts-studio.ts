import { queryOptions } from '@tanstack/react-query'
import type { StudioEpisode } from '../../studio-data'
import { apiFetch } from '../client'
import type { StudioPodcastShow } from '../../studio-data'
import {
  toStudioShow, type StudioPodcastShowWire,
  toStudioEpisode, type EpisodeWire,
} from '../mappers'

/** `GET /v1/studio/podcasts/shows` — the signed-in creator's shows. */
export function studioShowsQuery() {
  return queryOptions({
    queryKey: ['studio', 'podcast-shows'],
    queryFn: async () =>
      (await apiFetch<StudioPodcastShowWire[]>('/studio/podcasts/shows')).map(toStudioShow),
  })
}

/** `GET /v1/studio/podcasts/episodes` — the creator's episodes (with plays + status). */
export function studioEpisodesQuery() {
  return queryOptions({
    queryKey: ['studio', 'podcast-episodes'],
    queryFn: async () =>
      (await apiFetch<EpisodeWire[]>('/studio/podcasts/episodes')).map(toStudioEpisode),
  })
}

export interface NewEpisodeInput {
  audio: File
  showId: string | null
  newShow: { title: string; category: string } | null
  title: string
  description: string
  cover: string | null
  visibility: 'public' | 'scheduled'
  date: string | null
  premium: boolean
  price: number | null
  earlyAccess: boolean
}

/**
 * `POST /v1/studio/podcasts/episodes` — multipart create. The `audio` file part carries the upload;
 * the `data` part is the JSON body. `duration`/`status` are server-derived and deliberately omitted.
 * A fresh `Idempotency-Key` guards against double-submit.
 */
export function apiCreateEpisode(input: NewEpisodeInput): Promise<StudioEpisode> {
  const data = {
    showId: input.showId,
    newShow: input.newShow,
    title: input.title,
    description: input.description,
    cover: input.cover,
    visibility: input.visibility,
    date: input.visibility === 'scheduled' && input.date ? new Date(input.date).toISOString() : null,
    premium: input.premium,
    price: input.premium ? input.price : null,
    earlyAccess: input.earlyAccess,
  }
  const form = new FormData()
  form.append('audio', input.audio)
  form.append('data', JSON.stringify(data))
  return apiFetch<EpisodeWire>('/studio/podcasts/episodes', {
    method: 'POST', body: form, idempotencyKey: crypto.randomUUID(),
  }).then(toStudioEpisode)
}

/** `DELETE /v1/studio/podcasts/episodes/:id`. */
export function apiDeleteEpisode(id: string): Promise<void> {
  return apiFetch<void>(`/studio/podcasts/episodes/${id}`, { method: 'DELETE' })
}

/** `POST /v1/studio/podcasts/shows` — creates a show up front, so a cover can be attached to it. */
export function apiCreateShow(input: {
  title: string
  category: string
  description?: string | null
}): Promise<StudioPodcastShow> {
  return apiFetch<StudioPodcastShowWire>('/studio/podcasts/shows', {
    method: 'POST',
    body: { title: input.title, category: input.category, description: input.description ?? null },
  }).then(toStudioShow)
}

/**
 * `POST /v1/studio/podcasts/shows/:id/cover` — uploads the show's cover art.
 *
 * Returns the stable URL the backend stored. This is the first real image upload in the app:
 * everything before it was a seeded URL or a `blob:` object URL that never left the browser, which
 * is why a cover could look set in the UI and be absent server-side. A show cannot publish an
 * episode to fans without one — `podcast.image` is NOT NULL.
 */
export function apiUploadShowCover(showId: string, image: File): Promise<string> {
  const form = new FormData()
  form.append('image', image)
  return apiFetch<{ image: string }>(`/studio/podcasts/shows/${showId}/cover`, {
    method: 'POST',
    body: form,
  }).then((r) => r.image)
}
