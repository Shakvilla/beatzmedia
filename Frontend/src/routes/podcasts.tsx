import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { Play, Pause, Check, Plus, Mic } from 'lucide-react'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { MediaRail } from '../features/discover/components/media-rail'
import { EpisodeRow } from '../features/podcasts/components/episode-row'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useCollection } from '../features/collection/collection-context'
import { useToast } from '../components/ui/toast-provider'
import {
  podcastCategories,
  topShows,
  trendingEpisodes,
  getPodcast,
  latestPlayable,
  episodeToTrack,
} from '../lib/podcast-data'
import type { PodcastCategory } from '../types'
import { cn } from '../utils/cn'

export const Route = createFileRoute('/podcasts')({
  component: PodcastsComponent,
})

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'
type Filter = 'All' | PodcastCategory

function PodcastsComponent() {
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { isShowFollowed, toggleFollowedShow } = useCollection()
  const { toast } = useToast()
  const [category, setCategory] = useState<Filter>('All')

  const featured = topShows[0]
  const subscribed = isShowFollowed(featured.id)
  const featuredPlayable = latestPlayable(featured.id)
  const isFeaturedPlaying = isPlaying && currentTrack?.id === featuredPlayable?.id

  const shows = category === 'All' ? topShows : topShows.filter((p) => p.category === category)
  const eps = category === 'All' ? trendingEpisodes : trendingEpisodes.filter((e) => getPodcast(e.podcastId)?.category === category)

  const playFeatured = () => {
    if (!featuredPlayable) return
    if (isFeaturedPlaying) togglePlay()
    else playQueue([episodeToTrack(featuredPlayable)], 0)
  }

  return (
    <div className="flex flex-col gap-12">
      <div className="flex flex-col gap-2">
        <span className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">
          <Mic size={14} /> Podcasts
        </span>
        <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Listen & learn</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-2xl">
          Ghana’s best shows on culture, news, business, sport and more — free to stream, with premium drops.
        </p>
      </div>

      {/* Featured show */}
      <div className="relative overflow-hidden rounded-3xl min-h-[300px] flex">
        <div className="absolute inset-0">
          <img src={featured.image} alt="" className="w-full h-full object-cover blur-2xl scale-125 opacity-50 dark:opacity-40" />
          <div className="absolute inset-0 bg-gradient-to-r from-white/40 dark:from-black/70 via-beatz-light-bg/70 dark:via-black/50 to-transparent" />
        </div>

        <div className="relative z-10 flex flex-col sm:flex-row items-center sm:items-end gap-6 p-8 lg:p-10">
          <div className="w-40 h-40 lg:w-48 lg:h-48 rounded-2xl overflow-hidden shadow-2xl ring-1 ring-black/10 dark:ring-white/10 shrink-0">
            <img src={featured.image} alt={featured.title} className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col items-center sm:items-start gap-3 text-center sm:text-left">
            <span className="text-[10px] font-bold tracking-[0.3em] uppercase text-[#f6c644]">Featured Show</span>
            <h2 className="text-3xl lg:text-4xl font-bold text-beatz-dark-bg dark:text-white tracking-tight">{featured.title}</h2>
            <p className="text-sm text-gray-600 dark:text-gray-300 max-w-md">{featured.description}</p>
            <span className="text-xs text-gray-500 dark:text-gray-300">{featured.publisher} • {featured.episodeCount} episodes</span>
            <div className="flex items-center gap-3 mt-2">
              <button
                onClick={playFeatured}
                className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2 hover:scale-105 transition-transform"
              >
                {isFeaturedPlaying ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" />}
                {isFeaturedPlaying ? 'Pause' : 'Play latest'}
              </button>
              <button
                onClick={() => { toggleFollowedShow(featured.id); toast(subscribed ? `Unfollowed ${featured.title}` : `Following ${featured.title}`, 'success') }}
                className={cn(
                  'h-11 px-6 rounded-full border font-bold text-sm flex items-center gap-2 transition-colors',
                  subscribed
                    ? 'bg-beatz-green border-beatz-green text-black'
                    : 'border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10',
                )}
              >
                {subscribed ? <><Check size={16} /> Following</> : <><Plus size={16} /> Follow</>}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Category filter */}
      <div className="flex items-center gap-2 flex-wrap">
        {(['All', ...podcastCategories] as Filter[]).map((cat) => (
          <button
            key={cat}
            onClick={() => setCategory(cat)}
            className={cn(
              'px-4 py-2 rounded-full text-sm font-bold transition-colors',
              category === cat
                ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Top shows */}
      <MediaRail title="Top shows in Ghana" subtitle="The most-followed podcasts right now">
        {shows.map((show) => (
          <div key={show.id} className={RAIL_ITEM}>
            <Card to={`/podcast/${show.id}`}>
              <CardImage src={show.image} alt={show.title} />
              <CardContent className="mt-2">
                <CardTitle>{show.title}</CardTitle>
                <CardSubtitle>{show.publisher}</CardSubtitle>
              </CardContent>
            </Card>
          </div>
        ))}
      </MediaRail>

      {/* Trending episodes */}
      <section className="flex flex-col gap-5">
        <div className="flex flex-col gap-1 border-b border-gray-200 dark:border-white/5 pb-2">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">Trending episodes</h2>
          <p className="text-sm text-gray-500 dark:text-gray-300">Fresh drops worth your commute</p>
        </div>
        <div className="flex flex-col gap-1">
          {eps.map((episode) => (
            <EpisodeRow
              key={episode.id}
              episode={episode}
              isCurrent={currentTrack?.id === episode.id}
              isPlaying={isPlaying && currentTrack?.id === episode.id}
              onPlay={() => {
                if (isPlaying && currentTrack?.id === episode.id) togglePlay()
                else playQueue([episodeToTrack(episode)], 0)
              }}
              onBuy={(ep) => {
                addItem({
                  id: `episode:${ep.id}`,
                  kind: 'episode',
                  title: ep.title,
                  subtitle: ep.showTitle,
                  image: ep.image,
                  price: ep.price ?? { amount: 0, currency: 'GHS' },
                })
                toast(`“${ep.title}” added to cart`, 'success')
              }}
            />
          ))}
          {eps.length === 0 && <p className="text-gray-500 dark:text-gray-300 py-8 text-center">No episodes in this category yet.</p>}
        </div>
      </section>

      {/* New & noteworthy */}
      <MediaRail title="New & noteworthy" subtitle="Shows climbing the charts">
        {[...topShows].reverse().map((show) => (
          <div key={show.id} className={RAIL_ITEM}>
            <Card to={`/podcast/${show.id}`}>
              <CardImage src={show.image} alt={show.title} />
              <CardContent className="mt-2">
                <CardTitle>{show.title}</CardTitle>
                <CardSubtitle>{show.category}</CardSubtitle>
              </CardContent>
            </Card>
          </div>
        ))}
      </MediaRail>
    </div>
  )
}
