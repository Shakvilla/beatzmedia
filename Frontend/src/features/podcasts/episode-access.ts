import type { PodcastEpisode } from '../../types'

/** Whether the current user can play an episode (free, owned, or now-public). */
export function episodeAccessible(ep: PodcastEpisode): boolean {
  if (ep.isOwned) return true
  if (ep.isEarlyAccess) return ep.publicAt ? new Date(ep.publicAt) <= new Date() : false
  return !ep.isPremium
}
