import { createFileRoute, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Play, Pause, Share2, Heart, Plus, ListPlus, Info, Users, Check, ShoppingCart, Mic2, Send, MoreHorizontal, Clock, CalendarDays } from 'lucide-react'
import { cn } from '../utils/cn'
import { LyricsView } from '../components/music/lyrics-view'
import { AddToPlaylistModal } from '../features/collection/components/add-to-playlist-modal'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useCollection } from '../features/collection/collection-context'
import { useToast } from '../components/ui/toast-provider'
import { trackQuery, artistQuery, lyricsQuery, artistTracksQuery } from '../lib/api/queries/catalog'
import { formatCount, formatDuration, formatPrice } from '../lib/format'

export const Route = createFileRoute('/track/$trackId')({
  loader: async ({ context: { queryClient }, params: { trackId } }) => {
    const track = await queryClient.ensureQueryData(trackQuery(trackId))
    await Promise.all([
      queryClient.ensureQueryData(artistQuery(track.artistId)),
      queryClient.ensureQueryData(lyricsQuery(trackId)),
      queryClient.ensureQueryData(artistTracksQuery(track.artistId)),
    ])
  },
  component: TrackPageComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Track not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
    </div>
  ),
})

// Deterministic, organic waveform amplitudes (0–100%), mirrored around a centre line.
const BAR_COUNT = 150
const hash = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453
  return x - Math.floor(x)
}
const WAVE = Array.from({ length: BAR_COUNT }, (_, i) => {
  const t = i / BAR_COUNT
  const attack = Math.min(1, t / 0.03)
  const release = 1 - Math.max(0, (t - 0.96) / 0.04)
  const body = 0.7 + 0.3 * Math.sin(t * Math.PI * 4)
  const amp = (0.18 + 0.82 * hash(i)) * attack * release * body
  return Math.max(8, Math.round(amp * 100))
})

function TrackPageComponent() {
  const { trackId } = Route.useParams()
  // All hooks must run unconditionally and in a stable order every render — so every
  // useSuspenseQuery goes ABOVE the `isLyricsMode` early return below. (The old mock code
  // called plain functions like getArtist/getLyrics after the return, which was fine; hooks
  // are not.) The loader has already cached all of these, so the reads resolve immediately.
  const { data: track } = useSuspenseQuery(trackQuery(trackId))
  const { data: artist } = useSuspenseQuery(artistQuery(track.artistId))
  const { data: lyricsLines } = useSuspenseQuery(lyricsQuery(trackId))
  const { data: artistTracks } = useSuspenseQuery(artistTracksQuery(track.artistId))
  const [isLyricsMode, setIsLyricsMode] = useState(false)
  const [reaction, setReaction] = useState('')
  const [addOpen, setAddOpen] = useState(false)

  const { currentTrack, isPlaying, progress, playQueue, togglePlay, seek } = usePlayer()
  const { addItem } = useCart()
  const { isTrackLiked, toggleLikedTrack } = useCollection()
  const { toast } = useToast()

  if (isLyricsMode) return <LyricsView onClose={() => setIsLyricsMode(false)} />

  const liked = isTrackLiked(track.id)
  const isThis = currentTrack?.id === track.id
  const isThisPlaying = isPlaying && isThis
  const ratio = isThis && track.duration ? Math.min(1, progress / track.duration) : 0
  const lyricLines = lyricsLines.filter((l) => l.text !== '♪').slice(0, 5)
  const moreTracks = artistTracks.filter((t) => t.id !== track.id).slice(0, 5)

  const handlePlay = () => {
    if (isThisPlaying) togglePlay()
    else playQueue([track], 0)
  }
  const openLyrics = () => {
    if (!isThis) playQueue([track], 0)
    setIsLyricsMode(true)
  }
  const handleWaveClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isThis) {
      playQueue([track], 0)
      return
    }
    const rect = e.currentTarget.getBoundingClientRect()
    seek(((e.clientX - rect.left) / rect.width) * track.duration)
  }
  const buyTrack = () => {
    addItem({ id: `track:${track.id}`, kind: 'track', title: track.title, subtitle: track.artistName, image: track.image, price: track.price ?? { amount: 0, currency: 'GHS' } })
    toast(`“${track.title}” added to cart`, 'success')
  }
  const share = () => toast('Link copied to clipboard', 'success')
  const postReaction = () => {
    if (!reaction.trim()) return
    toast('Reaction posted', 'success')
    setReaction('')
  }

  return (
    <div className="flex flex-col -mt-20 -mx-4 md:-mx-8">
      {/* Hero with blurred-art backdrop */}
      <div className="relative px-4 md:px-8 pt-24 md:pt-28 pb-8 overflow-hidden">
        <div className="absolute inset-0 overflow-hidden">
          <img src={track.image} alt="" className="w-full h-full object-cover blur-3xl scale-125 opacity-50 dark:opacity-35" />
          <div className="absolute inset-0 bg-gradient-to-b from-white/30 dark:from-black/40 via-beatz-light-bg/85 dark:via-beatz-dark-bg/85 to-beatz-light-bg dark:to-beatz-dark-bg" />
        </div>

        <div className="relative z-10 flex flex-col md:flex-row items-center md:items-end gap-8">
          <div className="w-48 h-48 lg:w-60 lg:h-60 shrink-0 rounded-xl overflow-hidden shadow-2xl ring-1 ring-black/10 dark:ring-white/10">
            <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
          </div>

          <div className="flex flex-col items-center md:items-start gap-3 min-w-0">
            <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">Song</span>
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-beatz-dark-bg dark:text-white tracking-tight leading-[1.05] text-center md:text-left break-words">{track.title}</h1>
            <div className="flex items-center flex-wrap justify-center md:justify-start gap-2 text-sm font-medium text-beatz-dark-bg dark:text-white">
              {artist && (
                <Link to="/artist/$artistId" params={{ artistId: artist.id }} className="flex items-center gap-2 hover:underline">
                  <span className="w-6 h-6 rounded-full overflow-hidden"><img src={artist.image} alt={artist.name} className="w-full h-full object-cover" /></span>
                  <span className="font-bold">{artist.name}</span>
                </Link>
              )}
              {track.albumId && (
                <><span className="text-gray-400">•</span>
                <Link to="/album/$albumId" params={{ albumId: track.albumId }} className="text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">{track.albumTitle}</Link></>
              )}
              {track.year && <><span className="text-gray-400">•</span><span className="text-gray-500 dark:text-gray-300">{track.year}</span></>}
              <span className="text-gray-400">•</span><span className="text-gray-500 dark:text-gray-300">{formatDuration(track.duration)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Action bar + content */}
      <div className="px-4 md:px-8 pt-6 pb-16 flex flex-col gap-10">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-4 md:gap-5">
            <button
              onClick={handlePlay}
              aria-label={isThisPlaying ? 'Pause' : 'Play'}
              className="w-14 h-14 rounded-full bg-beatz-green flex items-center justify-center hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20"
            >
              {isThisPlaying ? <Pause fill="black" size={26} className="text-black" /> : <Play fill="black" size={28} className="text-black ml-1" />}
            </button>
            <button
              onClick={() => { toggleLikedTrack(track.id); toast(liked ? 'Removed from Liked Songs' : 'Added to Liked Songs', 'success') }}
              aria-label="Like"
              className={cn('w-11 h-11 rounded-full flex items-center justify-center transition-colors', liked ? 'text-beatz-green' : 'text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white')}
            >
              <Heart size={24} fill={liked ? 'currentColor' : 'none'} />
            </button>
            <button
              onClick={() => (track.ownership === 'for-sale' ? buyTrack() : setAddOpen(true))}
              aria-label="Add to playlist"
              className="w-11 h-11 rounded-full flex items-center justify-center text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors"
            >
              <Plus size={24} />
            </button>
            <button onClick={openLyrics} aria-label="Lyrics" className="w-11 h-11 rounded-full flex items-center justify-center text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
              <Mic2 size={22} />
            </button>
            <button onClick={share} aria-label="Share" className="w-11 h-11 rounded-full flex items-center justify-center text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
              <Share2 size={22} />
            </button>
            <button aria-label="More" className="w-11 h-11 rounded-full flex items-center justify-center text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
              <MoreHorizontal size={24} />
            </button>
          </div>

          {/* Ownership / buy */}
          {track.ownership === 'owned' ? (
            <span className="flex items-center gap-2 text-sm font-bold text-[#f6c644]">
              <Check size={16} strokeWidth={3} /> In your collection
            </span>
          ) : track.ownership === 'free' ? (
            <span className="text-sm font-bold text-beatz-green uppercase tracking-widest">Free to stream</span>
          ) : (
            <button onClick={buyTrack} className="h-11 px-6 rounded-full bg-[#f6c644] text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform">
              <ShoppingCart size={16} /> Buy • {formatPrice(track.price)}
            </button>
          )}
        </div>

        {/* Waveform */}
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-2 text-[10px] font-mono text-gray-400 uppercase tracking-widest px-1">
            <span className="shrink-0">{formatDuration(isThis ? progress : 0)}</span>
            {track.quality && <span className="hidden sm:block truncate">{track.quality}</span>}
            <span className="shrink-0">{formatDuration(track.duration)}</span>
          </div>
          <div className="relative h-20 flex items-center gap-[1.5px] cursor-pointer" onClick={handleWaveClick}>
            {WAVE.map((h, i) => (
              <div key={i} style={{ height: `${h}%` }} className={cn('flex-1 rounded-full transition-colors', i / WAVE.length <= ratio ? 'bg-beatz-green' : 'bg-gray-300 dark:bg-white/20')} />
            ))}
          </div>
        </section>

        {/* Lyrics + credits / info */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-10">
          <div className="lg:col-span-2 flex flex-col gap-10">
            {/* Lyrics preview */}
            <section className="flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-gray-200 dark:border-white/5 pb-3">
                <h2 className="text-title text-beatz-dark-bg dark:text-white">Lyrics</h2>
                <button onClick={openLyrics} className="text-xs font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors">Lyrics mode</button>
              </div>
              <button onClick={openLyrics} className="flex flex-col items-start gap-2 text-left relative">
                {lyricLines.map((line, i) => (
                  <span key={i} className={cn('text-xl font-bold', i >= 3 ? 'text-gray-400 dark:text-gray-500' : 'text-beatz-dark-bg dark:text-white')}>{line.text}</span>
                ))}
                <span className="mt-2 text-sm font-bold text-beatz-green">Show full lyrics →</span>
              </button>
            </section>

            {/* Credits */}
            {track.credits && track.credits.length > 0 && (
              <section className="flex flex-col gap-5">
                <div className="flex items-center gap-3 border-b border-gray-200 dark:border-white/5 pb-3">
                  <Users className="text-beatz-green" size={22} />
                  <h2 className="text-title text-beatz-dark-bg dark:text-white">Credits</h2>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-8 gap-x-12">
                  {track.credits.map((item, idx) => (
                    <div key={idx} className="flex flex-col gap-1.5">
                      <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">{item.role}</span>
                      <div className="flex flex-wrap gap-x-2 gap-y-1">
                        {item.names.map((name, nIdx) => (
                          <span key={nIdx} className="text-base font-bold text-beatz-dark-bg dark:text-white">{name}{nIdx < item.names.length - 1 && ','}</span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Fan reactions */}
            <section className="flex flex-col gap-4">
              <h2 className="text-title text-beatz-dark-bg dark:text-white">Fan reactions</h2>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full overflow-hidden shrink-0"><img src="https://i.pravatar.cc/100?img=11" alt="You" className="w-full h-full object-cover" /></div>
                <div className="flex-1 flex items-center gap-2 bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 rounded-full h-12 pl-5 pr-2">
                  <input
                    value={reaction}
                    onChange={(e) => setReaction(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && postReaction()}
                    placeholder="Write a reaction…"
                    className="flex-1 bg-transparent text-sm text-beatz-dark-bg dark:text-white placeholder-gray-400 focus:outline-none"
                  />
                  <button onClick={postReaction} disabled={!reaction.trim()} className="w-9 h-9 rounded-full bg-beatz-green text-black flex items-center justify-center disabled:opacity-40 transition-opacity"><Send size={16} /></button>
                </div>
              </div>
            </section>
          </div>

          {/* About this track */}
          <aside className="flex flex-col gap-4">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">About</h2>
            <div className="bg-white dark:bg-beatz-dark-surface-2 rounded-2xl p-6 flex flex-col gap-5 shadow-sm">
              <InfoRow icon={<CalendarDays size={18} />} label="Released" value={track.year ? String(track.year) : '—'} />
              <InfoRow icon={<Clock size={18} />} label="Duration" value={formatDuration(track.duration)} />
              {track.plays != null && <InfoRow icon={<Info size={18} />} label="Plays" value={`${formatCount(track.plays)}`} />}
              {track.quality && <InfoRow icon={<Info size={18} />} label="Audio quality" value={track.quality} />}
              <button
                onClick={() => (track.ownership === 'for-sale' ? buyTrack() : setAddOpen(true))}
                className="flex items-center gap-3 text-left group pt-1"
              >
                <div className="w-10 h-10 rounded-xl bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 group-hover:text-beatz-green transition-colors"><ListPlus size={18} /></div>
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">
                  {track.ownership === 'for-sale' ? 'Buy to add to playlist' : 'Add to playlist'}
                </span>
              </button>
            </div>
          </aside>
        </div>

        {/* More from artist */}
        {moreTracks.length > 0 && (
          <section className="flex flex-col gap-4">
            <div className="flex items-end justify-between border-b border-gray-200 dark:border-white/5 pb-2">
              <h2 className="text-title text-beatz-dark-bg dark:text-white">More from {track.artistName}</h2>
              {artist && <Link to="/artist/$artistId" params={{ artistId: artist.id }} className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors">See artist</Link>}
            </div>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
              {moreTracks.map((t, i) => (
                <Card key={t.id} to={`/track/${t.id}`}>
                  <CardImage src={t.image} alt={t.title} onPlay={() => playQueue(moreTracks, i)} />
                  <CardContent className="mt-2"><CardTitle>{t.title}</CardTitle><CardSubtitle>{formatCount(t.plays)} plays</CardSubtitle></CardContent>
                </Card>
              ))}
            </div>
          </section>
        )}
      </div>

      <AddToPlaylistModal trackId={track.id} isOpen={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  )
}

function InfoRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-xl bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 shrink-0">{icon}</div>
      <div className="flex flex-col min-w-0">
        <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">{label}</span>
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{value}</span>
      </div>
    </div>
  )
}
