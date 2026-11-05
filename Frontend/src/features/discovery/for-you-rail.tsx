import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Album, Disc3, DiscAlbum, Mic2, RefreshCw, Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/auth-context'
import { MediaRail } from '../discover/components/media-rail'
import { recommendationsQuery, type RecommendationItem } from '../../lib/api/queries/recommendations'

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'

function RecommendationLink({ item, children }: { item: RecommendationItem; children: ReactNode }) {
  if (item.kind === 'single') {
    return <Link to="/track/$trackId" params={{ trackId: item.contentId }} className="group block">{children}</Link>
  }
  if (item.kind === 'album' || item.kind === 'ep') {
    return <Link to="/album/$albumId" params={{ albumId: item.contentId }} className="group block">{children}</Link>
  }
  return <Link to="/podcast/$podcastId" params={{ podcastId: item.creatorId }} className="group block">{children}</Link>
}

function kindDetails(kind: RecommendationItem['kind']) {
  if (kind === 'single') return { label: 'Single', icon: <Disc3 size={11} /> }
  if (kind === 'album') return { label: 'Album', icon: <Album size={11} /> }
  if (kind === 'ep') return { label: 'EP', icon: <DiscAlbum size={11} /> }
  if (kind === 'podcast') return { label: 'Podcast', icon: <Mic2 size={11} /> }
  return { label: 'Single', icon: <Disc3 size={11} /> }
}

function RecommendationCard({ item }: { item: RecommendationItem }) {
  const details = kindDetails(item.kind)
  return (
    <div className={RAIL_ITEM}>
      <RecommendationLink item={item}>
        <div className="relative aspect-square overflow-hidden rounded-2xl bg-beatz-light-surface-2 shadow-lg dark:bg-beatz-dark-surface-3">
          {item.image
            ? <img src={item.image} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
            : <div className="flex h-full w-full items-center justify-center text-gray-400">{item.kind === 'podcast' ? <Mic2 size={36} /> : <DiscAlbum size={36} />}</div>}
          <span className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-black/70 px-2.5 py-1 text-[10px] font-bold uppercase tracking-widest text-white backdrop-blur">
            {details.icon}
            {details.label}
          </span>
        </div>
        <h3 className="mt-3 truncate font-bold text-beatz-dark-bg dark:text-white">{item.title}</h3>
        <p className="mt-1 truncate text-sm text-gray-500 dark:text-gray-300">{item.subtitle}</p>
        <p className="mt-2 flex items-start gap-1.5 text-xs font-medium leading-4 text-beatz-green">
          <Sparkles size={12} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>{item.reason}</span>
        </p>
      </RecommendationLink>
    </div>
  )
}

export function ForYouRail() {
  const { isAuthenticated } = useAuth()
  const query = useQuery(recommendationsQuery(isAuthenticated))
  if (!isAuthenticated) return null

  if (query.isPending) {
    return (
      <MediaRail title="For you" subtitle="Shaped by your library and listening">
        {[0, 1, 2, 3].map((item) => (
          <div key={item} className={`${RAIL_ITEM} animate-pulse`} aria-hidden="true">
            <div className="aspect-square rounded-2xl bg-gray-200 dark:bg-white/10" />
            <div className="mt-3 h-4 w-3/4 rounded bg-gray-200 dark:bg-white/10" />
            <div className="mt-2 h-3 w-1/2 rounded bg-gray-200 dark:bg-white/10" />
          </div>
        ))}
      </MediaRail>
    )
  }

  if (query.isError) {
    return (
      <section className="rounded-2xl border border-gray-200 bg-white/50 p-5 dark:border-white/10 dark:bg-white/[0.03]" role="status">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-title text-beatz-dark-bg dark:text-white">For you</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-300">Your picks could not be loaded. The rest of Home is still available.</p>
          </div>
          <button type="button" onClick={() => query.refetch()} className="flex items-center gap-2 rounded-full border border-beatz-green px-4 py-2 text-sm font-bold text-beatz-green transition hover:bg-beatz-green hover:text-black">
            <RefreshCw size={14} /> Try again
          </button>
        </div>
      </section>
    )
  }

  if (!query.data.items.length) {
    return (
      <section className="rounded-2xl border border-dashed border-gray-300 p-6 dark:border-white/15">
        <h2 className="text-title text-beatz-dark-bg dark:text-white">For you</h2>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-300">Follow an artist or show, like a track, or keep listening and your personal picks will appear here.</p>
      </section>
    )
  }

  return (
    <MediaRail title="For you" subtitle="Music and podcasts shaped by your taste" action={<span className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">Personal picks</span>}>
      {query.data.items.map((item) => <RecommendationCard key={`${item.kind}:${item.contentId}`} item={item} />)}
    </MediaRail>
  )
}
