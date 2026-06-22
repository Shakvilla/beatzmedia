import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { Play, Pause, Share2, Clock, ShoppingCart, Globe, Lock, Check, Trash2, ListMusic } from 'lucide-react'
import { cn } from '../utils/cn'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useCollection } from '../features/collection/collection-context'
import { useToast } from '../components/ui/toast-provider'
import { getPlaylist, getPlaylistTracks, getTracksByIds } from '../lib/mock-data'
import { formatDuration, formatPrice, formatCount } from '../lib/format'
import { useAuth } from '../features/auth/auth-context'
import type { Track } from '../types'

export const Route = createFileRoute('/playlist/$playlistId')({
  component: PlaylistComponent,
})

function PlaylistComponent() {
  const { playlistId } = Route.useParams()
  const navigate = useNavigate()
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const {
    isPlaylistFollowed,
    toggleFollowedPlaylist,
    getUserPlaylist,
    removeTrackFromPlaylist,
    deletePlaylist,
  } = useCollection()
  const { toast } = useToast()
  const { account } = useAuth()

  const mock = getPlaylist(playlistId)
  const userPl = getUserPlaylist(playlistId)

  if (!mock && !userPl) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Playlist not found</h1>
        <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
      </div>
    )
  }

  const isOwn = Boolean(userPl)
  const tracks = userPl ? getTracksByIds(userPl.trackIds) : getPlaylistTracks(mock!.id)
  const view = userPl
    ? {
        title: userPl.title,
        description: undefined as string | undefined,
        isPublic: false,
        creator: account?.name ?? 'You',
        creatorAvatar: account?.avatar ?? undefined,
        followers: undefined as number | undefined,
        image: tracks[0]?.image,
      }
    : {
        title: mock!.title,
        description: mock!.description,
        isPublic: mock!.isPublic,
        creator: mock!.creator,
        creatorAvatar: mock!.creatorAvatar,
        followers: mock!.followers,
        image: mock!.image,
      }

  const isFollowing = isPlaylistFollowed(playlistId)
  const isPlaylistPlaying = isPlaying && tracks.some((t) => t.id === currentTrack?.id)

  const handlePlay = () => {
    if (isPlaylistPlaying) togglePlay()
    else if (tracks.length) playQueue(tracks, 0)
  }
  const buyTrack = (track: Track) => {
    addItem({ id: `track:${track.id}`, kind: 'track', title: track.title, subtitle: track.artistName, image: track.image, price: track.price ?? { amount: 0, currency: 'GHS' } })
    toast(`“${track.title}” added to cart`, 'success')
  }
  const handleDelete = () => {
    deletePlaylist(playlistId)
    toast('Playlist deleted', 'success')
    navigate({ to: '/library' })
  }

  return (
    <div className="flex flex-col gap-8 pb-12 animate-in fade-in duration-500">
      {/* Header */}
      <section className="flex flex-col md:flex-row gap-8 items-center md:items-end p-2">
        <div className="w-64 h-64 lg:w-72 lg:h-72 rounded-2xl overflow-hidden shadow-2xl shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-2 flex items-center justify-center">
          {view.image ? (
            <img src={view.image} alt={view.title} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-beatz-green/40 to-beatz-blue/40 flex items-center justify-center">
              <ListMusic size={64} className="text-white/80" />
            </div>
          )}
        </div>

        <div className="flex flex-col items-center md:items-start gap-4 flex-1 min-w-0">
          <div className="flex items-center gap-2 text-xs font-bold text-beatz-green uppercase tracking-[0.2em]">
            {view.isPublic ? <Globe size={14} /> : <Lock size={14} />}
            {isOwn ? 'Your Playlist' : view.isPublic ? 'Public Playlist' : 'Private Playlist'}
          </div>
          <h1 className="text-4xl sm:text-5xl lg:text-7xl font-bold text-beatz-dark-bg dark:text-white tracking-tighter leading-tight text-center md:text-left break-words">{view.title}</h1>
          {view.description && <p className="text-gray-500 dark:text-gray-300 font-medium max-w-2xl text-center md:text-left">{view.description}</p>}
          <div className="flex items-center flex-wrap gap-2 mt-1 text-sm">
            {view.creatorAvatar && (
              <span className="w-6 h-6 rounded-full overflow-hidden border border-gray-200 dark:border-white/20">
                <img src={view.creatorAvatar} alt={view.creator} className="w-full h-full object-cover" />
              </span>
            )}
            <span className="font-bold text-beatz-dark-bg dark:text-white">{view.creator}</span>
            {view.followers != null && <><span className="text-gray-400">•</span><span className="text-gray-500 dark:text-gray-300">{formatCount(view.followers)} followers</span></>}
            <span className="text-gray-400">•</span>
            <span className="text-gray-500 dark:text-gray-300">{tracks.length} songs</span>
          </div>
        </div>
      </section>

      {/* Actions */}
      <div className="flex items-center gap-4 py-2">
        <button
          onClick={handlePlay}
          disabled={tracks.length === 0}
          aria-label={isPlaylistPlaying ? 'Pause' : 'Play'}
          className="w-14 h-14 bg-beatz-green rounded-full flex items-center justify-center shadow-lg shadow-beatz-green/20 hover:scale-105 active:scale-95 transition-all disabled:opacity-50 disabled:hover:scale-100"
        >
          {isPlaylistPlaying ? <Pause fill="black" size={28} className="text-black" /> : <Play fill="black" size={28} className="text-black ml-1" />}
        </button>

        {isOwn ? (
          <button
            onClick={handleDelete}
            className="h-10 px-6 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold text-sm flex items-center gap-2 hover:border-beatz-red hover:text-beatz-red transition-colors"
          >
            <Trash2 size={16} /> Delete
          </button>
        ) : (
          <button
            onClick={() => { toggleFollowedPlaylist(playlistId); toast(isFollowing ? 'Unfollowed playlist' : 'Following playlist', 'success') }}
            className={cn(
              'h-10 px-6 rounded-full border font-bold text-sm transition-all',
              isFollowing ? 'bg-beatz-green border-beatz-green text-black' : 'border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5',
            )}
          >
            {isFollowing ? 'Following' : 'Follow'}
          </button>
        )}
        <button onClick={() => toast('Link copied to clipboard', 'success')} className="w-10 h-10 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5 transition-colors" aria-label="Share">
          <Share2 size={18} />
        </button>
      </div>

      {/* Tracklist */}
      {tracks.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-20 text-center rounded-2xl border border-dashed border-gray-200 dark:border-white/10">
          <span className="font-bold text-beatz-dark-bg dark:text-white">No songs yet</span>
          <span className="text-sm text-gray-500 dark:text-gray-300">Open any track and use “Add to playlist” to fill it up.</span>
        </div>
      ) : (
        <div className="flex flex-col mt-2">
          <div className="grid grid-cols-[16px_4fr_minmax(120px,auto)] md:grid-cols-[16px_4fr_3fr_minmax(140px,1fr)] gap-4 px-4 py-2 border-b border-gray-200 dark:border-white/5 text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">
            <span>#</span>
            <span>Title</span>
            <span className="hidden md:block">Album</span>
            <span className="flex justify-end pr-1"><Clock size={14} /></span>
          </div>

          <div className="flex flex-col mt-2">
            {tracks.map((track, index) => {
              const isCurrent = currentTrack?.id === track.id
              return (
                <div
                  key={track.id}
                  onClick={() => playQueue(tracks, index)}
                  className="grid grid-cols-[16px_4fr_minmax(120px,auto)] md:grid-cols-[16px_4fr_3fr_minmax(140px,1fr)] gap-4 px-4 py-3 rounded-xl hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group items-center cursor-pointer"
                >
                  <span className="text-sm font-mono text-gray-500 dark:text-gray-300 group-hover:text-beatz-green">{index + 1}</span>

                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
                      <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
                    </div>
                    <div className="flex flex-col min-w-0">
                      <Link to="/track/$trackId" params={{ trackId: track.id }} onClick={(e) => e.stopPropagation()} className={cn('font-bold truncate hover:underline', isCurrent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>
                        {track.title}
                      </Link>
                      <Link to="/artist/$artistId" params={{ artistId: track.artistId }} onClick={(e) => e.stopPropagation()} className="text-xs text-gray-500 dark:text-gray-300 truncate hover:underline">{track.artistName}</Link>
                    </div>
                  </div>

                  <div className="hidden md:flex items-center min-w-0">
                    {track.albumId ? (
                      <Link to="/album/$albumId" params={{ albumId: track.albumId }} onClick={(e) => e.stopPropagation()} className="text-sm text-gray-500 dark:text-gray-300 truncate hover:text-beatz-dark-bg dark:hover:text-white transition-colors">{track.albumTitle}</Link>
                    ) : (
                      <span className="text-sm text-gray-500 dark:text-gray-300 truncate">Single</span>
                    )}
                  </div>

                  <div className="flex items-center justify-end gap-3">
                    {track.ownership === 'owned' ? (
                      <span className="text-[10px] font-bold text-[#f6c644] bg-[#f6c644]/10 px-2 py-0.5 rounded flex items-center gap-1"><Check size={10} strokeWidth={3} /> Owned</span>
                    ) : track.ownership === 'free' ? (
                      <span className="text-[10px] font-bold text-beatz-green bg-beatz-green/10 px-2 py-0.5 rounded uppercase">Free</span>
                    ) : (
                      <button onClick={(e) => { e.stopPropagation(); buyTrack(track) }} className="text-[10px] font-bold text-[#f6c644] border border-[#f6c644]/30 px-3 py-1 rounded hover:bg-[#f6c644]/10 flex items-center gap-1 transition-colors">
                        <ShoppingCart size={12} /> {formatPrice(track.price)}
                      </button>
                    )}
                    <span className="text-xs font-mono text-gray-500 dark:text-gray-300 w-10 text-right tabular-nums">{formatDuration(track.duration)}</span>
                    {isOwn && (
                      <button
                        onClick={(e) => { e.stopPropagation(); removeTrackFromPlaylist(playlistId, track.id); toast('Removed from playlist', 'success') }}
                        aria-label="Remove from playlist"
                        className="text-gray-400 hover:text-beatz-red transition-colors opacity-0 group-hover:opacity-100"
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
