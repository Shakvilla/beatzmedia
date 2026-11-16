import { useQuery } from '@tanstack/react-query'
import { Play } from 'lucide-react'
import { useAuth } from '../auth/auth-context'
import { usePlayer } from '../player/player-context'
import { MediaRail } from '../discover/components/media-rail'
import {
  continueListeningQuery,
  recentListeningQuery,
  type ListeningHistoryItem,
} from '../../lib/api/queries/engagement'
import type { Track } from '../../types'

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'

interface ListeningHistoryRailProps {
  mode: 'recent' | 'continue'
  contentKind?: ListeningHistoryItem['contentKind']
}

function historyItemToTrack(item: ListeningHistoryItem): Track {
  return {
    id: item.contentId,
    playbackKind: item.contentKind === 'podcast-episode' ? 'podcast_episode' : 'track',
    title: item.title,
    artistId: '',
    artistName: item.subtitle,
    duration: item.durationSeconds,
    image: item.image,
    ownership: 'free',
    downloadable: false,
  }
}

export function ListeningHistoryRail({ mode, contentKind }: ListeningHistoryRailProps) {
  const { isAuthenticated } = useAuth()
  const { playTrack } = usePlayer()
  const query = useQuery(
    mode === 'continue'
      ? continueListeningQuery(isAuthenticated)
      : recentListeningQuery(isAuthenticated),
  )

  if (!isAuthenticated || query.isError) return null
  const items = (query.data?.items ?? []).filter(
    (item) => !contentKind || item.contentKind === contentKind,
  )
  if (items.length === 0) return null

  const title = mode === 'continue' ? 'Continue listening' : 'Recently played'
  const subtitle = mode === 'continue'
    ? 'Pick up exactly where you left off'
    : 'Jump back into your latest listens'

  return (
    <MediaRail title={title} subtitle={subtitle}>
      {items.map((item) => (
        <div key={`${item.contentKind}:${item.contentId}`} className={RAIL_ITEM}>
          <button
            type="button"
            onClick={() => playTrack(historyItemToTrack(item), item.positionSeconds)}
            className="group flex w-full flex-col text-left"
            aria-label={`Play ${item.title}${mode === 'continue' ? ` from ${item.positionSeconds} seconds` : ''}`}
          >
            <span className="relative aspect-square w-full overflow-hidden rounded-xl bg-beatz-light-surface-2 shadow-md dark:bg-beatz-dark-surface-3">
              <img src={item.image} alt="" className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105" />
              <span className="absolute bottom-3 right-3 flex h-10 w-10 items-center justify-center rounded-full bg-beatz-green text-black opacity-0 shadow-lg transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
                <Play size={17} fill="currentColor" />
              </span>
              {mode === 'continue' && item.durationSeconds > 0 && (
                <span className="absolute inset-x-0 bottom-0 h-1 bg-black/35">
                  <span
                    className="block h-full bg-beatz-green"
                    style={{ width: `${Math.min(100, (item.positionSeconds / item.durationSeconds) * 100)}%` }}
                  />
                </span>
              )}
            </span>
            <span className="mt-3 w-full truncate font-bold text-beatz-dark-bg dark:text-white">{item.title}</span>
            <span className="mt-1 w-full truncate text-sm text-gray-500 dark:text-gray-300">{item.subtitle}</span>
          </button>
        </div>
      ))}
    </MediaRail>
  )
}
