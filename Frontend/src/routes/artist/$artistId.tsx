import { createFileRoute, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Play, Pause, Check, Share2, MoreHorizontal, BadgeCheck, ShoppingCart, MapPin } from 'lucide-react'
import { usePlayer } from '../../features/player/player-context'
import { useCart } from '../../features/cart/cart-context'
import { useCollection } from '../../features/collection/collection-context'
import { useToast } from '../../components/ui/toast-provider'
import { SupportModal } from '../../features/podcasts/components/support-modal'
import { EventListRow } from '../../features/events/components/event-list-row'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../../components/ui/card'
import { artistQuery, artistTracksQuery, artistAlbumsQuery } from '../../lib/api/queries/catalog'
import { eventsListQuery } from '../../lib/api/queries/events'
import { formatCount, formatDuration, formatPrice } from '../../lib/format'
import { cn } from '../../utils/cn'
import type { Track } from '../../types'

export const Route = createFileRoute('/artist/$artistId')({
  loader: async ({ context: { queryClient }, params: { artistId } }) => {
    await Promise.all([
      queryClient.ensureQueryData(artistQuery(artistId)),
      queryClient.ensureQueryData(artistTracksQuery(artistId)),
      queryClient.ensureQueryData(artistAlbumsQuery(artistId)),
      queryClient.ensureQueryData(eventsListQuery()),
    ])
  },
  component: ArtistComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Artist not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
    </div>
  ),
})

function ArtistComponent() {
  const { artistId } = Route.useParams()
  return <Artist artistId={artistId} />
}

function Artist({ artistId }: { artistId: string }) {
  const { data: artist } = useSuspenseQuery(artistQuery(artistId))
  const { data: topTracks } = useSuspenseQuery(artistTracksQuery(artistId))
  const { data: discography } = useSuspenseQuery(artistAlbumsQuery(artistId))
  const { data: allEvents } = useSuspenseQuery(eventsListQuery())
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { isArtistFollowed, toggleFollowedArtist } = useCollection()
  const { toast } = useToast()
  const [tipOpen, setTipOpen] = useState(false)
  const following = isArtistFollowed(artistId)

  const buyTrack = (track: Track) => {
    addItem({
      id: `track:${track.id}`,
      kind: 'track',
      title: track.title,
      subtitle: track.artistName,
      image: track.image,
      price: track.price ?? { amount: 0, currency: 'GHS' },
    })
    toast(`“${track.title}” added to cart`, 'success')
  }

  const shows = allEvents.filter((e) => e.artistId === artistId).sort((a, b) => a.date.localeCompare(b.date))

  const isArtistPlaying = isPlaying && topTracks.some((t) => t.id === currentTrack?.id)
  const handlePlay = () => {
    if (isArtistPlaying) togglePlay()
    else if (topTracks.length) playQueue(topTracks, 0)
  }

  return (
    <div className="flex flex-col -mt-20 -mx-4 md:-mx-8">
      {/* Hero banner */}
      <div className="relative h-[300px] md:h-[380px] flex items-end px-4 md:px-8 pb-8 overflow-hidden">
        <div className="absolute inset-0">
          <img src={artist.coverImage ?? artist.image} alt={artist.name} className="w-full h-full object-cover object-top" />
          <div className="absolute inset-0 bg-gradient-to-t from-beatz-light-bg dark:from-beatz-dark-bg via-black/40 to-black/20" />
        </div>
        <div className="relative z-10 flex flex-col gap-3">
          {artist.verified && (
            <span className="flex items-center gap-2 text-xs font-bold tracking-widest uppercase text-white drop-shadow">
              <BadgeCheck size={18} className="text-beatz-blue fill-white" /> Verified Artist
            </span>
          )}
          <h1 className="text-4xl sm:text-5xl lg:text-7xl font-bold text-white tracking-tighter leading-none drop-shadow-lg break-words">{artist.name}</h1>
          <span className="text-sm font-medium text-white/80">
            {formatCount(artist.monthlyListeners)} monthly listeners
          </span>
        </div>
      </div>

      <div className="px-4 md:px-8 pt-6 pb-16 flex flex-col gap-10">
        {/* Action bar */}
        <div className="flex items-center gap-4 flex-wrap">
          <button
            onClick={handlePlay}
            aria-label={isArtistPlaying ? 'Pause' : 'Play'}
            className="w-14 h-14 rounded-full bg-beatz-green flex items-center justify-center hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20"
          >
            {isArtistPlaying ? <Pause fill="black" className="text-black" size={26} /> : <Play fill="black" className="text-black ml-1" size={28} />}
          </button>
          <button
            onClick={() => { toggleFollowedArtist(artistId); toast(following ? `Unfollowed ${artist.name}` : `Following ${artist.name}`, 'success') }}
            className={cn(
              'h-11 px-6 rounded-full border font-bold text-sm transition-colors',
              following
                ? 'bg-beatz-green border-beatz-green text-black'
                : 'border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10',
            )}
          >
            {following ? 'Following' : 'Follow'}
          </button>
          <button
            onClick={() => setTipOpen(true)}
            className="h-11 px-6 rounded-full bg-[#f6c644] text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform"
          >
            <span className="font-serif italic">₵</span> Tip artist
          </button>
          <button className="h-11 px-5 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold text-sm flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <Share2 size={16} /> Share
          </button>
          <button className="w-11 h-11 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white flex items-center justify-center hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <MoreHorizontal size={20} />
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
          {/* Popular */}
          <div className="lg:col-span-2 flex flex-col gap-4">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">Popular</h2>
            <div className="flex flex-col">
              {topTracks.map((track, index) => (
                <PopularRow
                  key={track.id}
                  track={track}
                  index={index}
                  isCurrent={currentTrack?.id === track.id}
                  isPlaying={isPlaying && currentTrack?.id === track.id}
                  onPlay={() => playQueue(topTracks, index)}
                  onBuy={() => buyTrack(track)}
                />
              ))}
            </div>
          </div>

          {/* About + shows */}
          <div className="flex flex-col gap-8">
            <section className="flex flex-col gap-4">
              <h2 className="text-title text-beatz-dark-bg dark:text-white">About</h2>
              <div className="bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-2xl p-6 flex flex-col gap-4">
                {artist.bio && <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed">{artist.bio}</p>}
                <div className="flex flex-wrap gap-2">
                  {artist.location && (
                    <span className="flex items-center gap-1 text-[10px] font-bold tracking-widest uppercase text-gray-500 dark:text-gray-300 border border-gray-200 dark:border-transparent px-3 py-1 rounded-full">
                      <MapPin size={11} /> {artist.location}
                    </span>
                  )}
                  {artist.genres?.map((genre) => (
                    <span key={genre} className="text-[10px] font-bold tracking-widest uppercase text-gray-500 dark:text-gray-300 border border-gray-200 dark:border-transparent px-3 py-1 rounded-full">
                      {genre}
                    </span>
                  ))}
                </div>
                {artist.followers != null && (
                  <span className="text-xs text-gray-500 dark:text-gray-300">{formatCount(artist.followers)} followers</span>
                )}
              </div>
            </section>

            {shows.length > 0 && (
              <section className="flex flex-col gap-4">
                <div className="flex items-end justify-between">
                  <h2 className="text-title text-beatz-dark-bg dark:text-white">Upcoming shows</h2>
                  <Link to="/events" className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors">All events</Link>
                </div>
                <div className="flex flex-col gap-1">
                  {shows.map((event) => (
                    <EventListRow key={event.id} event={event} />
                  ))}
                </div>
              </section>
            )}
          </div>
        </div>

        {/* Discography */}
        {discography.length > 0 && (
          <section className="flex flex-col gap-4">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">Discography</h2>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
              {discography.map((album) => (
                <Card key={album.id} to={`/album/${album.id}`}>
                  <CardImage src={album.coverImage} alt={album.title} />
                  <CardContent className="mt-2">
                    <CardTitle>{album.title}</CardTitle>
                    <CardSubtitle>{album.year} • Album</CardSubtitle>
                  </CardContent>
                </Card>
              ))}
            </div>
          </section>
        )}
      </div>

      <SupportModal
        showTitle={artist.name}
        title="Tip artist"
        isOpen={tipOpen}
        onClose={() => setTipOpen(false)}
        onSend={(amount) => toast(`Thank you! ₵${amount} tipped to ${artist.name} via MoMo 💚`, 'success')}
      />
    </div>
  )
}

function PopularRow({
  track,
  index,
  isCurrent,
  isPlaying,
  onPlay,
  onBuy,
}: {
  track: Track
  index: number
  isCurrent: boolean
  isPlaying: boolean
  onPlay: () => void
  onBuy: () => void
}) {
  return (
    <div
      onClick={onPlay}
      className="flex items-center gap-4 px-3 py-2.5 rounded-lg hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group cursor-pointer"
    >
      <div className="w-6 flex items-center justify-center shrink-0 text-sm font-mono text-gray-500 dark:text-gray-300">
        {isPlaying ? (
          <div className="flex gap-0.5 items-end h-3.5">
            <span className="w-0.5 bg-beatz-green animate-bounce h-2" />
            <span className="w-0.5 bg-beatz-green animate-bounce h-3.5 [animation-delay:75ms]" />
            <span className="w-0.5 bg-beatz-green animate-bounce h-1.5 [animation-delay:150ms]" />
          </div>
        ) : (
          <>
            <span className="group-hover:hidden">{index + 1}</span>
            <Play size={14} className="hidden group-hover:block text-beatz-dark-bg dark:text-white" fill="currentColor" />
          </>
        )}
      </div>

      <div className="w-11 h-11 rounded overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
        <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
      </div>

      <div className="flex flex-col flex-1 min-w-0">
        <Link
          to="/track/$trackId"
          params={{ trackId: track.id }}
          onClick={(e) => e.stopPropagation()}
          className={cn('font-bold truncate hover:underline w-fit max-w-full', isCurrent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}
        >
          {track.title}
        </Link>
        <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{track.albumTitle ?? 'Single'}</span>
      </div>

      <span className="hidden md:block w-20 text-right font-mono text-sm text-gray-500 dark:text-gray-300 shrink-0 tabular-nums">
        {track.plays != null ? formatCount(track.plays) : '—'}
      </span>

      <div className="w-28 flex justify-end items-center shrink-0">
        {track.ownership === 'free' ? (
          <span className="text-[10px] font-bold text-beatz-green bg-beatz-green/10 px-2 py-0.5 rounded uppercase tracking-wider">Free</span>
        ) : track.ownership === 'owned' ? (
          <span className="flex items-center gap-1 text-[10px] font-bold text-[#f6c644] bg-[#f6c644]/10 px-2 py-0.5 rounded uppercase tracking-wider">
            <Check size={10} strokeWidth={3} /> Owned
          </span>
        ) : (
          <div className="flex items-center gap-2">
            <span className="font-mono font-bold text-beatz-green text-sm whitespace-nowrap">{formatPrice(track.price)}</span>
            <button
              onClick={(e) => {
                e.stopPropagation()
                onBuy()
              }}
              className="text-[10px] font-bold text-beatz-dark-bg dark:text-white border border-gray-300 dark:border-white/20 px-2.5 py-1 rounded hover:bg-gray-100 dark:hover:bg-white/10 flex items-center gap-1 transition-colors"
            >
              <ShoppingCart size={11} /> Buy
            </button>
          </div>
        )}
      </div>

      <span className="w-12 text-right font-mono text-sm text-gray-500 dark:text-gray-300 shrink-0 tabular-nums">{formatDuration(track.duration)}</span>
    </div>
  )
}
