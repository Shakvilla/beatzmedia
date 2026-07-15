import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useSuspenseQuery, CancelledError } from '@tanstack/react-query'
import { useState } from 'react'
import { Heart, Check, Play, Plus, ListMusic } from 'lucide-react'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { usePlayer } from '../features/player/player-context'
import { useCollection } from '../features/collection/collection-context'
import { CreatePlaylistModal } from '../features/collection/components/create-playlist-modal'
import { resolveQuery } from '../lib/api/queries/catalog'
import { collectionQuery } from '../lib/api/queries/collection'
import { formatDuration } from '../lib/format'
import { cn } from '../utils/cn'

export const Route = createFileRoute('/library')({
  loader: async ({ context: { queryClient } }) => {
    // Best-effort prefetch so the first render is populated. A concurrent collection
    // mutation can cancel these in-flight; that's benign — the component's useSuspenseQuery
    // fetches fresh — so swallow CancelledError and let any real error surface.
    try {
      const c = await queryClient.ensureQueryData(collectionQuery())
      await queryClient.ensureQueryData(
        resolveQuery({
          trackIds: c.likedTracks,
          artistIds: c.followedArtists,
          albumIds: c.savedAlbums,
          playlistIds: c.followedPlaylists,
        }),
      )
    } catch (e) {
      if (!(e instanceof CancelledError)) throw e
    }
  },
  component: LibraryComponent,
})

const TABS = ['All', 'Playlists', 'Albums', 'Artists', 'Liked'] as const
type Tab = (typeof TABS)[number]

function LibraryComponent() {
  const [tab, setTab] = useState<Tab>('All')
  const [createOpen, setCreateOpen] = useState(false)
  const navigate = useNavigate()
  const { playQueue } = usePlayer()
  const { likedTracks, followedPlaylists, followedArtists, savedAlbums, userPlaylists, ownedTracks } = useCollection()

  const { data: resolved } = useSuspenseQuery(
    resolveQuery({ trackIds: likedTracks, artistIds: followedArtists, albumIds: savedAlbums, playlistIds: followedPlaylists }),
  )
  const likedList = resolved.tracks
  const playlists = resolved.playlists
  const artistsList = resolved.artists
  const albumsList = resolved.albums
  const ownedCount = ownedTracks.length
  const playlistCoverById = new Map(resolved.tracks.map((t) => [t.id, t.image]))

  const showSection = (s: Tab) => tab === 'All' || tab === s

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Your Library</h1>

      {/* Tabs */}
      <div className="flex items-center gap-2 flex-wrap">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn(
              'px-4 py-2 rounded-full text-sm font-bold transition-colors',
              tab === t
                ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
            )}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Liked tab → playable list */}
      {tab === 'Liked' ? (
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-5 p-6 rounded-2xl bg-gradient-to-br from-purple-500 to-indigo-700">
            <div className="w-20 h-20 rounded-lg bg-white/10 flex items-center justify-center">
              <Heart size={40} className="text-white" fill="white" />
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-[10px] font-bold uppercase tracking-widest text-white/70">Playlist</span>
              <h2 className="text-3xl font-bold text-white">Liked Songs</h2>
              <span className="text-sm text-white/80">{likedList.length} songs</span>
            </div>
            <button
              onClick={() => likedList.length && playQueue(likedList, 0)}
              className="ml-auto w-14 h-14 rounded-full bg-beatz-green flex items-center justify-center shadow-lg hover:scale-105 transition-transform"
              aria-label="Play liked songs"
            >
              <Play size={28} className="text-black ml-1" fill="currentColor" />
            </button>
          </div>

          <div className="flex flex-col">
            {likedList.map((track, index) => (
              <div key={track.id} onClick={() => playQueue(likedList, index)} className="flex items-center gap-4 px-4 py-2.5 rounded-lg hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group cursor-pointer">
                <span className="w-6 text-center text-sm font-mono text-gray-500 dark:text-gray-300">{index + 1}</span>
                <div className="w-11 h-11 rounded overflow-hidden shrink-0"><img src={track.image} alt={track.title} className="w-full h-full object-cover" /></div>
                <div className="flex flex-col flex-1 min-w-0">
                  <Link
                    to="/track/$trackId"
                    params={{ trackId: track.id }}
                    onClick={(e) => e.stopPropagation()}
                    className="font-bold text-beatz-dark-bg dark:text-white truncate hover:underline w-fit max-w-full"
                  >
                    {track.title}
                  </Link>
                  <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
                </div>
                <span className="text-sm font-mono text-gray-500 dark:text-gray-300 tabular-nums">{formatDuration(track.duration)}</span>
              </div>
            ))}
            {likedList.length === 0 && <p className="text-gray-500 dark:text-gray-300 py-8 text-center">No liked songs yet — tap the heart on any track.</p>}
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6 mt-2">
          {/* Create playlist tile */}
          {showSection('Playlists') && (
            <button onClick={() => setCreateOpen(true)} className="group flex flex-col gap-3 text-left">
              <div className="relative aspect-square rounded-md border-2 border-dashed border-gray-300 dark:border-white/15 flex items-center justify-center group-hover:border-beatz-green transition-colors">
                <Plus size={40} className="text-gray-400 group-hover:text-beatz-green transition-colors" />
              </div>
              <div className="flex flex-col">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">Create playlist</span>
                <span className="text-sm text-gray-500 dark:text-gray-300">New playlist</span>
              </div>
            </button>
          )}

          {/* User-created playlists */}
          {showSection('Playlists') && userPlaylists.map((p) => {
            const cover = p.trackIds.map((id) => playlistCoverById.get(id)).find(Boolean)
            return (
              <Card key={p.id} to={`/playlist/${p.id}`}>
                {cover ? (
                  <CardImage src={cover} alt={p.title} />
                ) : (
                  <div className="relative aspect-square rounded-md overflow-hidden shadow-md bg-gradient-to-br from-beatz-green/40 to-beatz-blue/40 flex items-center justify-center">
                    <ListMusic size={40} className="text-white/80" />
                  </div>
                )}
                <CardContent className="mt-2"><CardTitle>{p.title}</CardTitle><CardSubtitle>Playlist • {p.trackIds.length} songs</CardSubtitle></CardContent>
              </Card>
            )
          })}

          {/* Liked Songs + Owned cards (in All / Playlists) */}
          {showSection('Playlists') && (
            <button onClick={() => setTab('Liked')} className="group flex flex-col gap-3 text-left">
              <div className="relative aspect-square rounded-md overflow-hidden shadow-md bg-gradient-to-br from-purple-500 to-indigo-700 flex items-center justify-center group-hover:-translate-y-1 transition-transform">
                <Heart size={48} className="text-white" fill="white" />
              </div>
              <div className="flex flex-col">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">Liked Songs</span>
                <span className="text-sm text-gray-500 dark:text-gray-300">{likedList.length} songs</span>
              </div>
            </button>
          )}

          {showSection('Playlists') && playlists.map((playlist) => (
            <Card key={playlist.id} to={`/playlist/${playlist.id}`}>
              <CardImage src={playlist.image} alt={playlist.title} />
              <CardContent className="mt-2"><CardTitle>{playlist.title}</CardTitle><CardSubtitle>Playlist • {playlist.creator}</CardSubtitle></CardContent>
            </Card>
          ))}

          {showSection('Albums') && (
            <div className="group flex flex-col gap-3">
              <div className="relative aspect-square rounded-md overflow-hidden shadow-md bg-gradient-to-br from-[#f6c644] to-orange-600 flex items-center justify-center">
                <Check size={48} className="text-black" strokeWidth={3} />
              </div>
              <div className="flex flex-col">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">Owned Tracks</span>
                <span className="text-sm text-gray-500 dark:text-gray-300">{ownedCount} tracks</span>
              </div>
            </div>
          )}

          {showSection('Albums') && albumsList.map((album) => (
            <Card key={album.id} to={`/album/${album.id}`}>
              <CardImage src={album.coverImage} alt={album.title} />
              <CardContent className="mt-2"><CardTitle>{album.title}</CardTitle><CardSubtitle>Album • {album.artistName}</CardSubtitle></CardContent>
            </Card>
          ))}

          {showSection('Artists') && artistsList.map((artist) => (
            <Card key={artist.id} to={`/artist/${artist.id}`}>
              <CardImage src={artist.image} alt={artist.name} className="rounded-full" />
              <CardContent className="mt-2"><CardTitle>{artist.name}</CardTitle><CardSubtitle>Artist</CardSubtitle></CardContent>
            </Card>
          ))}
        </div>
      )}

      <CreatePlaylistModal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(id) => navigate({ to: '/playlist/$playlistId', params: { playlistId: id } })}
      />
    </div>
  )
}
