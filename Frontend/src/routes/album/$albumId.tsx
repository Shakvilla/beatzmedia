import { createFileRoute, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Play, Pause, Plus, Download, Share2, MoreHorizontal, Clock, ShoppingCart, Check } from 'lucide-react'
import { cn } from '../../utils/cn'
import { usePlayer } from '../../features/player/player-context'
import { useCart } from '../../features/cart/cart-context'
import { useToast } from '../../components/ui/toast-provider'
import { albumQuery, artistQuery } from '../../lib/api/queries/catalog'
import { formatCount, formatDuration, formatPrice, formatTotalDuration } from '../../lib/format'
import type { Track } from '../../types'

export const Route = createFileRoute('/album/$albumId')({
  loader: async ({ context: { queryClient }, params: { albumId } }) => {
    const { album } = await queryClient.ensureQueryData(albumQuery(albumId))
    await queryClient.ensureQueryData(artistQuery(album.artistId))
  },
  component: AlbumComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Album not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">
        Back to home
      </Link>
    </div>
  ),
})

function AlbumComponent() {
  const { albumId } = Route.useParams()
  return <Album albumId={albumId} />
}

function Album({ albumId }: { albumId: string }) {
  const { data: albumData } = useSuspenseQuery(albumQuery(albumId))
  const { album, tracks } = albumData
  const { data: artist } = useSuspenseQuery(artistQuery(album.artistId))
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { toast } = useToast()

  const totalSeconds = tracks.reduce((sum, t) => sum + t.duration, 0)
  const ownedCount = tracks.filter((t) => t.ownership === 'owned').length
  const freeCount = tracks.filter((t) => t.ownership === 'free').length
  const forSale = tracks.filter((t) => t.ownership === 'for-sale')
  const buyRest = forSale.reduce((sum, t) => sum + (t.price?.amount ?? 0), 0)
  const albumPrice = Math.round(buyRest * 0.8 * 100) / 100
  const allOwned = forSale.length === 0

  const isAlbumPlaying = isPlaying && tracks.some((t) => t.id === currentTrack?.id)
  const handlePlayAlbum = () => {
    if (isAlbumPlaying) togglePlay()
    else playQueue(tracks, 0)
  }

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
  const buyRemaining = () => {
    addItem({
      id: `album-rest:${album.id}`,
      kind: 'album-rest',
      title: `${album.title} — remaining tracks`,
      subtitle: album.artistName,
      image: album.coverImage,
      price: { amount: buyRest, currency: 'GHS' },
    })
    toast('Remaining tracks added to cart', 'success')
  }
  const buyAlbum = () => {
    addItem({
      id: `album:${album.id}`,
      kind: 'album',
      title: album.title,
      subtitle: album.artistName,
      image: album.coverImage,
      price: { amount: albumPrice, currency: 'GHS' },
    })
    toast(`${album.title} added to cart`, 'success')
  }

  return (
    <div className="flex flex-col -mt-20 -mx-4 md:-mx-8">
      {/* Hero with blurred-art backdrop */}
      <div className="relative px-4 md:px-8 pt-24 md:pt-28 pb-8 overflow-hidden">
        <div className="absolute inset-0 overflow-hidden">
          <img src={album.coverImage} alt="" className="w-full h-full object-cover blur-3xl scale-125 opacity-50 dark:opacity-35" />
          <div className="absolute inset-0 bg-gradient-to-b from-white/30 dark:from-black/40 via-beatz-light-bg/85 dark:via-beatz-dark-bg/85 to-beatz-light-bg dark:to-beatz-dark-bg" />
        </div>

        <div className="relative z-10 flex flex-col md:flex-row items-center md:items-end gap-8">
          <div className="w-48 h-48 lg:w-60 lg:h-60 shrink-0 rounded-xl overflow-hidden shadow-2xl ring-1 ring-black/10 dark:ring-white/10">
            <img src={album.coverImage} alt={album.title} className="w-full h-full object-cover" />
          </div>

          <div className="flex flex-col items-center md:items-start gap-4 min-w-0">
            <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">Album</span>
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-beatz-dark-bg dark:text-white tracking-tight leading-[1.05] text-center md:text-left break-words">
              {album.title}
            </h1>
            <div className="flex items-center flex-wrap justify-center md:justify-start gap-2 text-sm font-medium text-beatz-dark-bg dark:text-white">
              {artist && (
                <Link to="/artist/$artistId" params={{ artistId: artist.id }} className="flex items-center gap-2 hover:underline">
                  <span className="w-6 h-6 rounded-full overflow-hidden">
                    <img src={artist.image} alt={artist.name} className="w-full h-full object-cover" />
                  </span>
                  <span className="font-bold">{artist.name}</span>
                </Link>
              )}
              <span className="text-gray-400">•</span>
              <span>{album.year}</span>
              <span className="text-gray-400">•</span>
              <span>{tracks.length} songs</span>
              <span className="text-gray-400">•</span>
              <span className="text-gray-500 dark:text-gray-300">{formatTotalDuration(totalSeconds)}</span>
              {album.genres && album.genres.length > 0 && (
                <span className="flex gap-2 ml-1">
                  {album.genres.map((genre) => (
                    <span key={genre} className="text-[10px] font-mono uppercase tracking-widest border border-gray-300 dark:border-white/20 px-2 py-0.5 rounded text-gray-600 dark:text-white/80">
                      {genre}
                    </span>
                  ))}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Action bar + tracklist */}
      <div className="px-4 md:px-8 pt-6 pb-16 flex flex-col gap-8">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-6">
            <button
              onClick={handlePlayAlbum}
              aria-label={isAlbumPlaying ? 'Pause' : 'Play album'}
              className="w-14 h-14 rounded-full bg-beatz-green flex items-center justify-center hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20"
            >
              {isAlbumPlaying ? (
                <Pause fill="black" className="text-black" size={26} />
              ) : (
                <Play fill="black" className="text-black ml-1" size={28} />
              )}
            </button>
            <div className="flex items-center gap-5 text-gray-400">
              <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors" aria-label="Add to library"><Plus size={24} /></button>
              <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors" aria-label="Download"><Download size={22} /></button>
              <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors" aria-label="Share"><Share2 size={22} /></button>
              <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors" aria-label="More"><MoreHorizontal size={24} /></button>
            </div>
          </div>

          {/* Ownership / buy */}
          {allOwned ? (
            <div className="flex items-center gap-2 text-sm font-bold text-beatz-green">
              <div className="w-8 h-8 rounded-full bg-beatz-green/10 flex items-center justify-center">
                <Check size={16} />
              </div>
              You own this album
            </div>
          ) : (
            <div className="flex items-center gap-4">
              <div className="flex flex-col items-end mr-1">
                <span className="text-xs font-bold text-[#f6c644] uppercase tracking-wider">You own {ownedCount}/{tracks.length}</span>
                {freeCount > 0 && <span className="text-[10px] text-gray-500 dark:text-gray-300">{freeCount} free track{freeCount > 1 ? 's' : ''} included</span>}
              </div>
              <button onClick={buyRemaining} className="h-11 px-5 rounded-full border border-[#f6c644]/40 text-[#f6c644] font-bold text-sm flex items-center gap-2 hover:bg-[#f6c644]/10 transition-colors">
                <ShoppingCart size={16} /> Buy rest • {formatPrice({ amount: buyRest, currency: 'GHS' })}
              </button>
              <button onClick={buyAlbum} className="h-11 px-5 rounded-full bg-[#f6c644] text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform">
                <ShoppingCart size={16} /> Buy album • {formatPrice({ amount: albumPrice, currency: 'GHS' })}
              </button>
            </div>
          )}
        </div>

        {/* Tracklist */}
        <div className="flex flex-col">
          <div className="flex items-center gap-4 px-4 py-2 border-b border-gray-200 dark:border-white/5 text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">
            <span className="w-6 text-center shrink-0">#</span>
            <span className="flex-1">Title</span>
            <span className="hidden md:block w-24 text-right shrink-0">Plays</span>
            <span className="w-32 text-right shrink-0">Status</span>
            <span className="w-14 flex justify-end shrink-0"><Clock size={14} /></span>
          </div>

          <div className="flex flex-col mt-2">
            {tracks.map((track, index) => (
              <TrackRow
                key={track.id}
                track={track}
                index={index}
                isCurrent={currentTrack?.id === track.id}
                isPlaying={isPlaying && currentTrack?.id === track.id}
                onPlay={() => playQueue(tracks, index)}
                onBuy={() => buyTrack(track)}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function TrackRow({
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
      className="flex items-center gap-4 px-4 py-2.5 rounded-lg hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group cursor-pointer"
    >
      {/* Index / play / playing */}
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

      {/* Art + title */}
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
        <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
      </div>

      {/* Plays */}
      <span className="hidden md:block w-24 text-right font-mono text-sm text-gray-500 dark:text-gray-300 shrink-0 tabular-nums">
        {track.plays != null ? formatCount(track.plays) : '—'}
      </span>

      {/* Status */}
      <div className="w-32 flex justify-end items-center shrink-0">
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

      {/* Duration */}
      <span className="w-14 text-right font-mono text-sm text-gray-500 dark:text-gray-300 shrink-0 tabular-nums">{formatDuration(track.duration)}</span>
    </div>
  )
}
