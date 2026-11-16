import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Mic, Search } from 'lucide-react'
import { cn } from '../utils/cn'
import { formatDuration } from '../lib/format'
import { podcastsListQuery, podcastEpisodesQuery } from '../lib/api/queries/podcasts'
import { adminPodcastEpisodesQuery } from '../lib/api/queries/admin-podcasts'
import { AdminLoadError } from '../components/admin/load-error'

export const Route = createFileRoute('/admin/podcasts')({
  component: AdminPodcasts,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

function AdminPodcasts() {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const uploadsQuery = useQuery(adminPodcastEpisodesQuery(page))
  const showsQuery = useQuery(podcastsListQuery())
  const shows = showsQuery.data ?? []
  const activeId = selectedId && shows.some((show) => show.id === selectedId)
    ? selectedId
    : shows[0]?.id ?? null
  const episodesQuery = useQuery({ ...podcastEpisodesQuery(activeId ?? ''), enabled: Boolean(activeId) })
  const search = query.trim().toLowerCase()
  const visibleShows = shows.filter((show) =>
    `${show.title} ${show.publisher} ${show.category}`.toLowerCase().includes(search))
  const activeShow = shows.find((show) => show.id === activeId)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <span className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.2em] text-beatz-green"><Mic size={15} /> Podcast catalog</span>
          <h1 className="mt-2 text-display text-beatz-dark-bg dark:text-white">Podcasts</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-300">Published shows and episodes visible to fans after audio processing.</p>
        </div>
        <span className="rounded-full bg-gray-100 dark:bg-white/10 px-3 py-1.5 text-xs font-bold text-gray-500 dark:text-gray-300">
          {shows.length} show{shows.length === 1 ? '' : 's'}
        </span>
      </div>

      <section className={cn(CARD, 'p-5')} aria-label="Podcast uploads">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Episode uploads</h2>
            <p className="text-xs text-gray-500 dark:text-gray-300">All creators · processing, scheduled and published</p>
          </div>
          <span className="text-xs text-gray-500 dark:text-gray-300">{uploadsQuery.data?.total ?? 0} total</span>
        </div>
        {uploadsQuery.isError ? (
          <AdminLoadError label="Couldn't load episode uploads." onRetry={() => uploadsQuery.refetch()} />
        ) : uploadsQuery.isPending ? (
          <p className="py-6 text-sm text-gray-500">Loading uploads…</p>
        ) : uploadsQuery.data.items.length === 0 ? (
          <p className="py-6 text-sm text-gray-500">No episodes uploaded yet.</p>
        ) : (
          <div className="divide-y divide-gray-200 dark:divide-white/10">
            {uploadsQuery.data.items.map((episode) => (
              <div key={episode.id} className="flex flex-wrap items-center gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-beatz-dark-bg dark:text-white">{episode.title}</p>
                  <p className="truncate text-xs text-gray-500 dark:text-gray-300">{episode.showTitle} · {new Date(episode.createdAt).toLocaleDateString()}</p>
                </div>
                <span className={cn('rounded-full px-2.5 py-1 text-xs font-bold',
                  episode.status === 'published' ? 'bg-beatz-green/15 text-beatz-green' :
                  episode.status === 'processing' ? 'bg-amber-500/15 text-amber-600 dark:text-amber-300' :
                  'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-gray-300')}>
                  {episode.status === 'processing' ? 'Processing audio' : episode.status}
                </span>
                {episode.status === 'published' && episode.durationSec > 0 && (
                  <Link to="/podcast/$podcastId" params={{ podcastId: episode.showId }}
                    className="text-xs font-bold text-beatz-green hover:underline">View fan page</Link>
                )}
              </div>
            ))}
          </div>
        )}
        {(uploadsQuery.data?.total ?? 0) > 50 && (
          <div className="mt-4 flex items-center justify-end gap-3 border-t border-gray-200 pt-4 text-xs dark:border-white/10">
            <button type="button" disabled={page <= 1} onClick={() => setPage((value) => value - 1)} className="disabled:opacity-40">Previous</button>
            <span>Page {page}</span>
            <button type="button" disabled={page * 50 >= (uploadsQuery.data?.total ?? 0)} onClick={() => setPage((value) => value + 1)} className="disabled:opacity-40">Next</button>
          </div>
        )}
      </section>

      <label className="relative block max-w-md">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
        <span className="sr-only">Search published podcast shows</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search published shows"
          className="h-11 w-full rounded-xl border border-gray-200 bg-white pl-10 pr-3 text-sm text-beatz-dark-bg outline-none focus:border-beatz-green dark:border-white/10 dark:bg-white/5 dark:text-white" />
      </label>

      {showsQuery.isError ? (
        <AdminLoadError label="Couldn't load podcasts." onRetry={() => showsQuery.refetch()} />
      ) : showsQuery.isPending ? (
        <p className="py-12 text-center text-sm text-gray-500">Loading podcasts…</p>
      ) : shows.length === 0 ? (
        <p className={cn(CARD, 'p-8 text-sm text-gray-500 dark:text-gray-300')}>No published podcasts yet. Studio episodes appear here after their audio finishes processing.</p>
      ) : (
        <div className="grid gap-5 lg:grid-cols-[minmax(250px,0.85fr)_minmax(0,1.5fr)]">
          <section className={cn(CARD, 'p-3')} aria-label="Podcast shows">
            {visibleShows.length === 0 ? (
              <p className="p-5 text-sm text-gray-500">No shows match your search.</p>
            ) : visibleShows.map((show) => (
              <button key={show.id} type="button" onClick={() => setSelectedId(show.id)}
                className={cn('flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors',
                  activeId === show.id ? 'bg-beatz-green/10' : 'hover:bg-gray-100 dark:hover:bg-white/5')}
                aria-current={activeId === show.id ? 'true' : undefined}>
                <img src={show.image} alt="" className="h-12 w-12 shrink-0 rounded-lg object-cover" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-bold text-beatz-dark-bg dark:text-white">{show.title}</span>
                  <span className="block truncate text-xs text-gray-500 dark:text-gray-300">{show.publisher} · {show.category}</span>
                  <span className="block text-xs text-gray-400">{show.episodeCount ?? 0} episodes</span>
                </span>
              </button>
            ))}
          </section>

          <section className={cn(CARD, 'min-w-0 p-5')} aria-label="Podcast episodes">
            {activeShow && <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">{activeShow.title}</h2>
                <p className="text-xs text-gray-500 dark:text-gray-300">{activeShow.episodeCount ?? 0} published episodes</p>
              </div>
              <Link to="/podcast/$podcastId" params={{ podcastId: activeShow.id }}
                className="text-xs font-bold text-beatz-green hover:underline">View fan page</Link>
            </div>}
            {episodesQuery.isError ? (
              <AdminLoadError label="Couldn't load episodes." onRetry={() => episodesQuery.refetch()} />
            ) : episodesQuery.isPending ? (
              <p className="py-8 text-sm text-gray-500">Loading episodes…</p>
            ) : episodesQuery.data?.length === 0 ? (
              <p className="py-8 text-sm text-gray-500">No published episodes in this show.</p>
            ) : (
              <ul className="divide-y divide-gray-200 dark:divide-white/10">
                {episodesQuery.data?.map((episode) => (
                  <li key={episode.id} className="flex items-center gap-3 py-3">
                    <img src={episode.image} alt="" className="h-12 w-12 shrink-0 rounded-lg object-cover" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-beatz-dark-bg dark:text-white">{episode.title}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-300">
                        {formatDuration(episode.duration)} · {episode.isPremium ? 'Premium' : 'Free'} · {new Date(episode.publishedAt).toLocaleDateString()}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
