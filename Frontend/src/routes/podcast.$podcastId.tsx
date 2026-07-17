import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Play, Pause, Plus, Check, Heart, ShoppingCart, Crown } from 'lucide-react'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useCollection } from '../features/collection/collection-context'
import { useToast } from '../components/ui/toast-provider'
import { EpisodeRow } from '../features/podcasts/components/episode-row'
import { episodeAccessible } from '../features/podcasts/episode-access'
import { SupportModal } from '../features/podcasts/components/support-modal'
import { podcastQuery, podcastEpisodesQuery } from '../lib/api/queries/podcasts'
import { formatPrice } from '../lib/format'
import { cn } from '../utils/cn'
import type { PodcastEpisode, Track } from '../types'

export const Route = createFileRoute('/podcast/$podcastId')({
  loader: async ({ context: { queryClient }, params: { podcastId } }) => {
    await queryClient.ensureQueryData(podcastQuery(podcastId))
    await queryClient.ensureQueryData(podcastEpisodesQuery(podcastId))
  },
  component: PodcastShowPage,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Show not found</h1>
      <Link to="/podcasts" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">
        Back to podcasts
      </Link>
    </div>
  ),
})

const byNewest = (a: PodcastEpisode, b: PodcastEpisode) => b.publishedAt.localeCompare(a.publishedAt)

/** Adapt an episode into a Track so it can flow through the music player. */
function episodeToTrack(ep: PodcastEpisode): Track {
  return {
    id: ep.id,
    title: ep.title,
    artistId: ep.podcastId,
    artistName: ep.showTitle,
    duration: ep.duration,
    image: ep.image,
    ownership: 'free',
  }
}

function PodcastShowPage() {
  const { podcastId } = Route.useParams()
  const { data: show } = useSuspenseQuery(podcastQuery(podcastId))
  const { data: rawEpisodes } = useSuspenseQuery(podcastEpisodesQuery(podcastId))

  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { isShowFollowed, toggleFollowedShow } = useCollection()
  const { toast } = useToast()
  const [supportOpen, setSupportOpen] = useState(false)
  const following = isShowFollowed(show.id)

  const episodes = [...rawEpisodes].sort(byNewest)
  const playable = episodes.find(episodeAccessible)
  const premiumCount = episodes.filter((e) => e.isPremium).length
  const isShowPlaying = isPlaying && episodes.some((e) => e.id === currentTrack?.id)

  const playEpisode = (ep: PodcastEpisode) => {
    if (isPlaying && currentTrack?.id === ep.id) togglePlay()
    else playQueue([episodeToTrack(ep)], 0)
  }

  const playLatest = () => {
    if (!playable) return
    if (isShowPlaying) togglePlay()
    else playEpisode(playable)
  }

  const buyEpisode = (ep: PodcastEpisode) => {
    addItem({
      id: `episode:${ep.id}`,
      kind: 'episode',
      title: ep.title,
      subtitle: ep.showTitle,
      image: ep.image,
      price: ep.price ?? { amount: 0, currency: 'GHS' },
    })
    toast(`“${ep.title}” added to cart`, 'success')
  }
  const buySeasonPass = () => {
    if (!show.seasonPassPrice) return
    addItem({
      id: `season-pass:${show.id}`,
      kind: 'season-pass',
      title: `${show.title} — Season Pass`,
      subtitle: show.publisher,
      image: show.image,
      price: show.seasonPassPrice,
    })
    toast(`${show.title} season pass added to cart`, 'success')
  }

  return (
    <div className="flex flex-col -mt-20 -mx-4 md:-mx-8">
      {/* Hero */}
      <div className="relative px-4 md:px-8 pt-24 md:pt-28 pb-8 overflow-hidden">
        <div className="absolute inset-0">
          <img src={show.image} alt="" className="w-full h-full object-cover blur-3xl scale-125 opacity-50 dark:opacity-35" />
          <div className="absolute inset-0 bg-gradient-to-b from-white/30 dark:from-black/40 via-beatz-light-bg/85 dark:via-beatz-dark-bg/85 to-beatz-light-bg dark:to-beatz-dark-bg" />
        </div>

        <div className="relative z-10 flex flex-col md:flex-row items-center md:items-end gap-8">
          <div className="w-44 h-44 lg:w-56 lg:h-56 shrink-0 rounded-2xl overflow-hidden shadow-2xl ring-1 ring-black/10 dark:ring-white/10">
            <img src={show.image} alt={show.title} className="w-full h-full object-cover" />
          </div>

          <div className="flex flex-col items-center md:items-start gap-3 min-w-0">
            <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">{show.category} • Podcast</span>
            <h1 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-beatz-dark-bg dark:text-white tracking-tight leading-[1.05] text-center md:text-left break-words">
              {show.title}
            </h1>
            <p className="text-gray-600 dark:text-gray-300 max-w-xl text-center md:text-left">{show.description}</p>
            <span className="text-sm text-gray-500 dark:text-gray-300">{show.publisher} • {show.episodeCount} episodes</span>

            <div className="flex items-center flex-wrap justify-center md:justify-start gap-3 mt-3">
              <button
                onClick={playLatest}
                className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2 hover:scale-105 transition-transform"
              >
                {isShowPlaying ? <Pause size={20} fill="currentColor" /> : <Play size={20} fill="currentColor" />}
                {isShowPlaying ? 'Pause' : 'Play'}
              </button>
              <button
                onClick={() => { toggleFollowedShow(show.id); toast(following ? `Unfollowed ${show.title}` : `Following ${show.title}`, 'success') }}
                className={cn(
                  'h-11 px-6 rounded-full border font-bold text-sm flex items-center gap-2 transition-colors',
                  following
                    ? 'bg-beatz-green border-beatz-green text-black'
                    : 'border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10',
                )}
              >
                {following ? <><Check size={16} /> Following</> : <><Plus size={16} /> Follow</>}
              </button>
              {show.supportsTips !== false && (
                <button
                  onClick={() => setSupportOpen(true)}
                  className="h-11 px-6 rounded-full border border-[#f6c644]/50 text-[#f6c644] font-bold text-sm flex items-center gap-2 hover:bg-[#f6c644]/10 transition-colors"
                >
                  <Heart size={16} fill="currentColor" /> Support the show
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="px-4 md:px-8 pt-4 pb-16 flex flex-col gap-8">
        {/* Season pass */}
        {show.seasonPassPrice && premiumCount > 0 && (
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-6 rounded-2xl bg-gradient-to-r from-[#f6c644]/15 to-transparent border border-[#f6c644]/20">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-full bg-[#f6c644]/20 flex items-center justify-center shrink-0">
                <Crown className="text-[#f6c644]" size={24} />
              </div>
              <div className="flex flex-col">
                <span className="font-bold text-beatz-dark-bg dark:text-white">Season Pass</span>
                <span className="text-sm text-gray-500 dark:text-gray-300">Own all {premiumCount} premium episodes — yours forever, cheaper than buying each.</span>
              </div>
            </div>
            <button
              onClick={buySeasonPass}
              className="h-11 px-6 rounded-full bg-[#f6c644] text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform shrink-0"
            >
              <ShoppingCart size={16} /> Buy pass • {formatPrice(show.seasonPassPrice)}
            </button>
          </div>
        )}

        {/* Episodes */}
        <section className="flex flex-col gap-4">
          <div className="flex items-end justify-between gap-4 border-b border-gray-200 dark:border-white/5 pb-2">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">Episodes</h2>
            <span className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">{episodes.length} episodes</span>
          </div>
          <div className="flex flex-col gap-1">
            {episodes.map((episode) => (
              <EpisodeRow
                key={episode.id}
                episode={episode}
                isCurrent={currentTrack?.id === episode.id}
                isPlaying={isPlaying && currentTrack?.id === episode.id}
                onPlay={() => episodeAccessible(episode) && playEpisode(episode)}
                onBuy={buyEpisode}
              />
            ))}
          </div>
        </section>
      </div>

      <SupportModal
        showTitle={show.title}
        isOpen={supportOpen}
        onClose={() => setSupportOpen(false)}
        onSend={(amount) => toast(`Thank you! ₵${amount} sent to ${show.title} via MoMo 💚`, 'success')}
      />
    </div>
  )
}
