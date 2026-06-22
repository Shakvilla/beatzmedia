import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { Search as SearchIcon, X, Check, Play, ShoppingCart } from 'lucide-react'
import { z } from 'zod'
import { cn } from '../utils/cn'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useToast } from '../components/ui/toast-provider'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { ArtistCircle } from '../features/discover/components/artist-circle'
import { tracks, artists, albums, playlists, browseCategories } from '../lib/mock-data'
import { formatDuration, formatPrice } from '../lib/format'
import type { Track } from '../types'

const searchSchema = z.object({ q: z.string().optional().catch('') })

export const Route = createFileRoute('/search')({
  validateSearch: searchSchema,
  component: SearchComponent,
})

const FILTERS = ['All', 'Tracks', 'Artists', 'Albums', 'Playlists'] as const
type Filter = (typeof FILTERS)[number]

function SearchComponent() {
  const { q } = Route.useSearch()
  const navigate = useNavigate({ from: Route.fullPath })
  const [filter, setFilter] = useState<Filter>('All')
  const { currentTrack, playQueue } = usePlayer()
  const { addItem } = useCart()
  const { toast } = useToast()

  const needle = (q ?? '').toLowerCase().trim()
  const matchTracks = tracks.filter((t) => `${t.title} ${t.artistName}`.toLowerCase().includes(needle))
  const matchArtists = artists.filter((a) => a.name.toLowerCase().includes(needle))
  const matchAlbums = albums.filter((a) => `${a.title} ${a.artistName}`.toLowerCase().includes(needle))
  const matchPlaylists = playlists.filter((p) => p.title.toLowerCase().includes(needle))
  const noResults = !matchTracks.length && !matchArtists.length && !matchAlbums.length && !matchPlaylists.length

  const show = (section: Filter) => filter === 'All' || filter === section
  const topResult = matchArtists[0] ?? null

  const buyTrack = (track: Track) => {
    addItem({ id: `track:${track.id}`, kind: 'track', title: track.title, subtitle: track.artistName, image: track.image, price: track.price ?? { amount: 0, currency: 'GHS' } })
    toast(`“${track.title}” added to cart`, 'success')
  }

  return (
    <div className="flex flex-col gap-8 -mt-6">
      {/* Search input */}
      <div className="relative group max-w-4xl">
        <SearchIcon className="absolute left-6 top-1/2 -translate-y-1/2 text-gray-400" size={24} />
        <input
          type="text"
          value={q || ''}
          onChange={(e) => navigate({ search: { q: e.target.value || undefined } })}
          placeholder="What do you want to listen to?"
          className="w-full h-16 pl-16 pr-14 bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white rounded-full font-bold text-lg border-2 border-transparent focus:border-beatz-green outline-none transition-all shadow-xl"
        />
        {q && (
          <button onClick={() => navigate({ search: {} })} className="absolute right-6 top-1/2 -translate-y-1/2 text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
            <X size={24} />
          </button>
        )}
      </div>

      {!needle ? (
        /* Browse */
        <div className="flex flex-col gap-6 animate-in fade-in duration-500">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">Browse all</h2>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {browseCategories.map((category) => (
              <Link
                key={category.id}
                to="/search"
                search={{ q: category.title }}
                className={cn('relative overflow-hidden rounded-xl p-4 aspect-[2/1] flex items-start shadow-md hover:scale-[1.02] transition-transform duration-300 group', category.colorClass)}
              >
                <h3 className="text-white font-bold text-lg md:text-xl z-10 relative">{category.title}</h3>
                <div className="absolute -right-3 -bottom-3 w-16 h-16 bg-black/20 rounded-lg rotate-[25deg] shadow-lg group-hover:scale-110 transition-transform duration-300" />
              </Link>
            ))}
          </div>
        </div>
      ) : noResults ? (
        <div className="flex flex-col items-center gap-2 py-24 text-center">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">No results for “{q}”</h2>
          <p className="text-gray-500 dark:text-gray-300">Try a different spelling or search for something else.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-10 animate-in slide-in-from-bottom-4 duration-500">
          {/* Filters */}
          <div className="flex items-center gap-2 flex-wrap">
            {FILTERS.map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  'px-4 py-2 rounded-full text-sm font-bold transition-colors',
                  filter === f
                    ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                    : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
                )}
              >
                {f}
              </button>
            ))}
          </div>

          {/* Top result + tracks */}
          {show('Tracks') && (matchTracks.length > 0 || topResult) && (
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
              {filter === 'All' && topResult && (
                <div className="lg:col-span-2 flex flex-col gap-4">
                  <h3 className="text-title text-beatz-dark-bg dark:text-white">Top result</h3>
                  <Link to="/artist/$artistId" params={{ artistId: topResult.id }} className="p-8 rounded-2xl bg-white dark:bg-white/5 hover:bg-gray-50 dark:hover:bg-white/10 border border-gray-100 dark:border-transparent transition-colors group cursor-pointer flex flex-col gap-4">
                    <div className="w-28 h-28 rounded-full overflow-hidden shadow-xl">
                      <img src={topResult.image} alt={topResult.name} className="w-full h-full object-cover" />
                    </div>
                    <h4 className="text-3xl font-bold text-beatz-dark-bg dark:text-white">{topResult.name}</h4>
                    <span className="text-xs font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300">Artist</span>
                  </Link>
                </div>
              )}

              {matchTracks.length > 0 && (
                <div className={cn('flex flex-col gap-4', filter === 'All' && topResult ? 'lg:col-span-3' : 'lg:col-span-5')}>
                  <h3 className="text-title text-beatz-dark-bg dark:text-white">Songs</h3>
                  <div className="flex flex-col">
                    {(filter === 'All' ? matchTracks.slice(0, 5) : matchTracks).map((track, index) => (
                      <div key={track.id} onClick={() => playQueue(matchTracks, index)} className="flex items-center gap-4 p-2 rounded-md hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group cursor-pointer">
                        <div className="relative w-12 h-12 rounded overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
                          <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
                          <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"><Play size={16} className="text-white" fill="currentColor" /></div>
                        </div>
                        <div className="flex flex-col flex-1 min-w-0">
                          <Link
                            to="/track/$trackId"
                            params={{ trackId: track.id }}
                            onClick={(e) => e.stopPropagation()}
                            className={cn('font-bold truncate hover:underline w-fit max-w-full', currentTrack?.id === track.id ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}
                          >
                            {track.title}
                          </Link>
                          <span className="text-sm text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
                        </div>
                        <div className="flex items-center gap-4 shrink-0">
                          {track.ownership === 'owned' ? (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-[#f6c644] bg-[#f6c644]/10 px-2 py-0.5 rounded uppercase tracking-wider"><Check size={10} strokeWidth={3} /> Owned</span>
                          ) : track.ownership === 'free' ? (
                            <span className="text-[10px] font-bold text-beatz-green bg-beatz-green/10 px-2 py-0.5 rounded uppercase">Free</span>
                          ) : (
                            <button onClick={(e) => { e.stopPropagation(); buyTrack(track) }} className="text-[10px] font-bold text-beatz-green border border-beatz-green/30 px-2.5 py-1 rounded hover:bg-beatz-green/10 flex items-center gap-1 transition-colors">
                              <ShoppingCart size={11} /> {formatPrice(track.price)}
                            </button>
                          )}
                          <span className="text-sm text-gray-500 dark:text-gray-300 font-mono w-10 text-right tabular-nums">{formatDuration(track.duration)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Artists */}
          {show('Artists') && matchArtists.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Artists</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {matchArtists.map((artist) => <ArtistCircle key={artist.id} artist={artist} />)}
              </div>
            </div>
          )}

          {/* Albums */}
          {show('Albums') && matchAlbums.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Albums</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {matchAlbums.map((album) => (
                  <Card key={album.id} to={`/album/${album.id}`}>
                    <CardImage src={album.coverImage} alt={album.title} />
                    <CardContent className="mt-2"><CardTitle>{album.title}</CardTitle><CardSubtitle>{album.artistName} • {album.year}</CardSubtitle></CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}

          {/* Playlists */}
          {show('Playlists') && matchPlaylists.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Playlists</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {matchPlaylists.map((playlist) => (
                  <Card key={playlist.id} to={`/playlist/${playlist.id}`}>
                    <CardImage src={playlist.image} alt={playlist.title} />
                    <CardContent className="mt-2"><CardTitle>{playlist.title}</CardTitle><CardSubtitle>By {playlist.creator}</CardSubtitle></CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
