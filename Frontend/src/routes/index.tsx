import { createFileRoute, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Heart, Play } from 'lucide-react'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle, QuickPickCard } from '../components/ui/card'
import { MediaRail } from '../features/discover/components/media-rail'
import { ArtistCircle } from '../features/discover/components/artist-circle'
import { FeaturedCarousel } from '../features/discover/components/featured-carousel'
import { usePlayer } from '../features/player/player-context'
import { homeQuery, browseCategoriesQuery } from '../lib/api/queries/catalog'
import { formatCount } from '../lib/format'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/')({
  loader: async ({ context: { queryClient } }) => {
    await Promise.all([
      queryClient.ensureQueryData(homeQuery()),
      queryClient.ensureQueryData(browseCategoriesQuery()),
    ])
  },
  component: HomeComponent,
})

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'

function HomeComponent() {
  const { playQueue } = usePlayer()
  const { account } = useAuth()
  const firstName = (account?.name ?? 'there').split(' ')[0]
  const { data: home } = useSuspenseQuery(homeQuery())
  const { data: browseCategories } = useSuspenseQuery(browseCategoriesQuery())
  const trending = home.trending
  const top10 = home.top10
  const featuredAlbums = home.featuredAlbums
  const newReleases = home.rails.newReleases
  const popularArtists = home.rails.popularArtists
  const curatedPlaylists = home.rails.curatedPlaylists
  const quickPickPlaylists = curatedPlaylists.slice(0, 3)

  return (
    <div className="flex flex-col gap-14">
      {/* Greeting + quick picks */}
      <section className="flex flex-col gap-6">
        <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">
          {greeting()}, {firstName}
        </h1>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <QuickPickCard
            title="Liked Songs"
            to="/library"
            icon={
              <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-purple-400 to-purple-600">
                <Heart className="text-white" fill="white" size={32} />
              </div>
            }
          />
          {quickPickPlaylists.length > 0 &&
            quickPickPlaylists.map((playlist) => (
              <QuickPickCard
                key={playlist.id}
                title={playlist.title}
                to={`/playlist/${playlist.id}`}
                icon={<img src={playlist.image} alt={playlist.title} className="w-full h-full object-cover" />}
              />
            ))}
        </div>
      </section>

      {/* Featured albums carousel (premium placement) */}
      <FeaturedCarousel albums={featuredAlbums} />

      {/* Made for you */}
      {curatedPlaylists.length > 0 && (
        <MediaRail
          title="Made for you"
          subtitle="Mixes and playlists picked for your taste"
          action={<span className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">Updated daily</span>}
        >
          {curatedPlaylists.map((playlist) => (
            <div key={playlist.id} className={RAIL_ITEM}>
              <Card to={`/playlist/${playlist.id}`}>
                <CardImage src={playlist.image} alt={playlist.title} />
                <CardContent className="mt-2">
                  <CardTitle>{playlist.title}</CardTitle>
                  <CardSubtitle>{playlist.trackIds.length} songs • by {playlist.creator}</CardSubtitle>
                </CardContent>
              </Card>
            </div>
          ))}
        </MediaRail>
      )}

      {/* Trending */}
      <MediaRail title="Trending in Ghana 🇬🇭" subtitle="What the country has on repeat right now">
        {trending.map((track, index) => (
          <div key={track.id} className={RAIL_ITEM}>
            <Card to={`/track/${track.id}`}>
              <CardImage src={track.image} alt={track.title} onPlay={() => playQueue(trending, index)} />
              <CardContent className="mt-2">
                <CardTitle>{track.title}</CardTitle>
                <CardSubtitle>{track.artistName}</CardSubtitle>
              </CardContent>
            </Card>
          </div>
        ))}
      </MediaRail>

      {/* New releases */}
      {newReleases.length > 0 && (
        <MediaRail title="New releases" subtitle="Fresh albums and EPs">
          {newReleases.map((album) => (
            <div key={album.id} className={RAIL_ITEM}>
              <Card to={`/album/${album.id}`}>
                <CardImage src={album.coverImage} alt={album.title} />
                <CardContent className="mt-2">
                  <CardTitle>{album.title}</CardTitle>
                  <CardSubtitle>{album.artistName} • {album.year}</CardSubtitle>
                </CardContent>
              </Card>
            </div>
          ))}
        </MediaRail>
      )}

      {/* Popular artists */}
      {popularArtists.length > 0 && (
        <MediaRail title="Popular artists" subtitle="The voices defining the sound of Ghana & Africa">
          {popularArtists.map((artist) => (
            <div key={artist.id} className={RAIL_ITEM}>
              <ArtistCircle artist={artist} />
            </div>
          ))}
        </MediaRail>
      )}

      {/* Charts */}
      <section className="flex flex-col gap-5">
        <div className="flex items-end justify-between gap-4 border-b border-gray-200 dark:border-white/5 pb-2">
          <div className="flex flex-col gap-1">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">Ghana Top 10</h2>
            <p className="text-sm text-gray-500 dark:text-gray-300">The most-streamed tracks this week</p>
          </div>
          <span className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">Chart</span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-x-8 gap-y-1">
          {top10.map((track, index) => (
            <div
              key={track.id}
              onClick={() => playQueue(top10, index)}
              className="flex items-center gap-4 p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group text-left cursor-pointer"
            >
              <span className="w-7 text-center font-mono text-lg font-bold text-gray-400 group-hover:text-beatz-green transition-colors">
                {index + 1}
              </span>
              <div className="relative w-12 h-12 rounded-md overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
                <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
                <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <Play size={16} className="text-white" fill="currentColor" />
                </div>
              </div>
              <div className="flex flex-col flex-1 min-w-0">
                <Link
                  to="/track/$trackId"
                  params={{ trackId: track.id }}
                  onClick={(e) => e.stopPropagation()}
                  className="font-bold truncate text-beatz-dark-bg dark:text-white hover:underline w-fit max-w-full"
                >
                  {track.title}
                </Link>
                <span className="text-sm text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
              </div>
              <span className="text-xs font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCount(track.plays)}</span>
            </div>
          ))}
        </div>
      </section>

      {/* Browse by mood & genre */}
      <section className="flex flex-col gap-5">
        <div className="flex flex-col gap-1 border-b border-gray-200 dark:border-white/5 pb-2">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">Browse by mood & genre</h2>
          <p className="text-sm text-gray-500 dark:text-gray-300">Dive into a sound</p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {browseCategories.map((category) => (
            <Link
              key={category.id}
              to="/search"
              search={{ q: category.title }}
              className={`relative overflow-hidden rounded-xl p-4 aspect-[2/1] flex items-start ${category.colorClass} shadow-md hover:scale-[1.02] transition-transform duration-300 group`}
            >
              <h3 className="text-white font-bold text-lg lg:text-xl relative z-10">{category.title}</h3>
              <div className="absolute -right-3 -bottom-3 w-16 h-16 bg-black/20 rounded-lg rotate-[25deg] shadow-lg group-hover:scale-110 transition-transform duration-300" />
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}
