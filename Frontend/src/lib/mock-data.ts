/**
 * Centralized mock data for BeatzClik.
 *
 * Every screen should read from here instead of hardcoding its own arrays.
 * The shapes match `src/types`, so this doubles as the reference dataset the
 * backend will eventually serve. Replace the exported helpers with real
 * data-fetching hooks (TanStack Query) when the API exists — call sites won't
 * need to change much.
 */

import type {
  Album,
  Artist,
  BrowseCategory,
  Genre,
  Playlist,
  Show,
  Track,
  User,
} from '../types'

// Reusable Unsplash art (kept as constants so ids map to stable images).
const IMG = {
  bsherif: 'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
  burna: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
  asake: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
  king: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
  lasmid: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
  camidoh: 'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
  rema: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
  villain: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
  generic: 'https://images.unsplash.com/photo-1516280440502-86ec1ed6f0c4?q=80&w=600&auto=format&fit=crop',
  hero: 'https://images.unsplash.com/photo-1493225457284-06f22b161460?q=80&w=2000&auto=format&fit=crop',
}

const GHS = (amount: number) => ({ amount, currency: 'GHS' as const })

// ---------------------------------------------------------------------------
// Artists
// ---------------------------------------------------------------------------

export const artists: Artist[] = [
  {
    id: 'black-sherif',
    name: 'Black Sherif',
    image: IMG.bsherif,
    coverImage: IMG.hero,
    verified: true,
    monthlyListeners: 2_400_000,
    followers: 2_400_000,
    bio: 'Mohammed Ismail Sherif, known as Black Sherif, is a Ghanaian rapper from Konongo whose drill-meets-highlife storytelling has carried Ghanaian music to a global audience.',
    location: 'Konongo, Ghana',
    genres: ['Drill', 'Hiplife'],
  },
  {
    id: 'burna-boy',
    name: 'Burna Boy',
    image: IMG.burna,
    verified: true,
    monthlyListeners: 18_200_000,
    followers: 12_400_000,
    location: 'Port Harcourt, Nigeria',
    genres: ['Afrobeats'],
  },
  {
    id: 'asake',
    name: 'Asake',
    image: IMG.asake,
    verified: true,
    monthlyListeners: 9_100_000,
    followers: 6_300_000,
    location: 'Lagos, Nigeria',
    genres: ['Afrobeats', 'Amapiano'],
  },
  {
    id: 'king-promise',
    name: 'King Promise',
    image: IMG.king,
    verified: true,
    monthlyListeners: 3_200_000,
    followers: 2_100_000,
    location: 'Accra, Ghana',
    genres: ['Afrobeats', 'Highlife'],
  },
  {
    id: 'lasmid',
    name: 'Lasmid',
    image: IMG.lasmid,
    verified: true,
    monthlyListeners: 1_400_000,
    followers: 900_000,
    location: 'Accra, Ghana',
    genres: ['Hiplife', 'Afrobeats'],
  },
  {
    id: 'camidoh',
    name: 'Camidoh',
    image: IMG.camidoh,
    verified: true,
    monthlyListeners: 2_100_000,
    followers: 1_300_000,
    location: 'Accra, Ghana',
    genres: ['Afrobeats', 'R&B'],
  },
  {
    id: 'rema',
    name: 'Rema',
    image: IMG.rema,
    verified: true,
    monthlyListeners: 24_000_000,
    followers: 15_000_000,
    location: 'Benin City, Nigeria',
    genres: ['Afrobeats'],
  },
]

// ---------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------

export const albums: Album[] = [
  {
    id: 'iron-boy',
    title: 'Iron Boy',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    year: 2024,
    coverImage: IMG.villain,
    genres: ['Hiplife', 'Drill'],
    trackIds: ['iron-boy-intro', 'konongo-zongo-ii', 'hold-on', 'mountains', 'akwasidae', 'jah-jah'],
  },
  {
    id: 'love-damini',
    title: 'Love, Damini',
    artistId: 'burna-boy',
    artistName: 'Burna Boy',
    year: 2022,
    coverImage: IMG.burna,
    genres: ['Afrobeats'],
    trackIds: ['last-last', 'its-plenty', 'for-my-hand'],
  },
  {
    id: 'the-villain-i-never-was',
    title: 'The Villain I Never Was',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    year: 2022,
    coverImage: IMG.bsherif,
    genres: ['Drill', 'Hiplife'],
    trackIds: ['soja', '45', 'kwaku-the-traveller'],
  },
]

// ---------------------------------------------------------------------------
// Tracks
// ---------------------------------------------------------------------------

export const tracks: Track[] = [
  {
    id: 'last-last',
    title: 'Last Last',
    artistId: 'burna-boy',
    artistName: 'Burna Boy',
    albumId: 'love-damini',
    albumTitle: 'Love, Damini',
    duration: 172,
    image: IMG.burna,
    ownership: 'owned',
    plays: 1_200_000_000,
    year: 2022,
    quality: 'Lossless • 24-bit/192kHz',
    credits: [
      { role: 'Producer', names: ['Chopstix'] },
      { role: 'Songwriter', names: ['Damini Ebunoluwa Ogulu', 'Mikael Haataja', 'Samuel Haataja'] },
      { role: 'Mixing Engineer', names: ['Jesse Ray Ernster'] },
      { role: 'Mastering Engineer', names: ['Colin Leonard'] },
    ],
  },
  {
    id: 'its-plenty',
    title: "It's Plenty",
    artistId: 'burna-boy',
    artistName: 'Burna Boy',
    albumId: 'love-damini',
    albumTitle: 'Love, Damini',
    duration: 194,
    image: IMG.king,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 84_000_000,
    year: 2022,
  },
  {
    id: 'for-my-hand',
    title: 'For My Hand',
    artistId: 'burna-boy',
    artistName: 'Burna Boy ft. Ed Sheeran',
    albumId: 'love-damini',
    albumTitle: 'Love, Damini',
    duration: 195,
    image: IMG.rema,
    ownership: 'for-sale',
    price: GHS(3.0),
    plays: 210_000_000,
    year: 2022,
  },
  {
    id: 'kwaku-the-traveller',
    title: 'Kwaku the Traveller',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'the-villain-i-never-was',
    albumTitle: 'The Villain I Never Was',
    duration: 195,
    image: IMG.bsherif,
    ownership: 'for-sale',
    price: GHS(3.0),
    plays: 124_000_000,
    year: 2022,
  },
  {
    id: 'soja',
    title: 'Soja',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'the-villain-i-never-was',
    albumTitle: 'The Villain I Never Was',
    duration: 218,
    image: IMG.lasmid,
    ownership: 'owned',
    plays: 8_900_000,
    year: 2022,
  },
  {
    id: '45',
    title: '45',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'the-villain-i-never-was',
    albumTitle: 'The Villain I Never Was',
    duration: 241,
    image: IMG.lasmid,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 6_200_000,
    year: 2022,
  },
  {
    id: 'iron-boy-intro',
    title: 'Iron Boy (intro)',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 72,
    image: IMG.villain,
    ownership: 'free',
    year: 2024,
  },
  {
    id: 'konongo-zongo-ii',
    title: 'Konongo Zongo II',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 222,
    image: IMG.asake,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 8_400_000,
    year: 2024,
  },
  {
    id: 'hold-on',
    title: 'Hold On',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 218,
    image: IMG.king,
    ownership: 'owned',
    plays: 24_100_000,
    year: 2024,
  },
  {
    id: 'mountains',
    title: 'Mountains',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 241,
    image: IMG.burna,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 12_800_000,
    year: 2024,
  },
  {
    id: 'akwasidae',
    title: 'Akwasidae',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 198,
    image: IMG.bsherif,
    ownership: 'owned',
    plays: 18_200_000,
    year: 2024,
  },
  {
    id: 'jah-jah',
    title: 'Jah Jah',
    artistId: 'black-sherif',
    artistName: 'Black Sherif',
    albumId: 'iron-boy',
    albumTitle: 'Iron Boy',
    duration: 242,
    image: IMG.rema,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 6_400_000,
    year: 2024,
  },
  {
    id: 'sungba',
    title: 'Sungba',
    artistId: 'asake',
    artistName: 'Asake',
    duration: 165,
    image: IMG.asake,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 95_000_000,
    year: 2022,
  },
  {
    id: 'terminator',
    title: 'Terminator',
    artistId: 'king-promise',
    artistName: 'King Promise',
    duration: 210,
    image: IMG.king,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 32_000_000,
    year: 2023,
  },
  {
    id: 'friday-night',
    title: 'Friday Night',
    artistId: 'lasmid',
    artistName: 'Lasmid',
    duration: 185,
    image: IMG.lasmid,
    ownership: 'owned',
    plays: 14_000_000,
    year: 2023,
  },
  {
    id: 'sugarcane',
    title: 'Sugarcane',
    artistId: 'camidoh',
    artistName: 'Camidoh',
    duration: 188,
    image: IMG.camidoh,
    ownership: 'for-sale',
    price: GHS(2.5),
    plays: 40_000_000,
    year: 2022,
  },
  {
    id: 'calm-down',
    title: 'Calm Down',
    artistId: 'rema',
    artistName: 'Rema, Selena Gomez',
    duration: 219,
    image: IMG.rema,
    ownership: 'for-sale',
    price: GHS(3.0),
    plays: 900_000_000,
    year: 2022,
  },
]

// ---------------------------------------------------------------------------
// Playlists
// ---------------------------------------------------------------------------

export const playlists: Playlist[] = [
  {
    id: 'vibes-from-the-233',
    title: 'Vibes from the 233',
    description:
      'The best of Ghanaian drill, highlife, and afrobeats. Hand-picked for your weekend vibes.',
    creator: 'Ama Serwaa',
    creatorAvatar: 'https://i.pravatar.cc/100?img=11',
    image: IMG.burna,
    isPublic: true,
    followers: 1_240,
    trackIds: ['last-last', 'kwaku-the-traveller', 'sungba', 'terminator', 'friday-night'],
  },
  {
    id: 'made-in-ghana',
    title: 'Made in Ghana 🇬🇭',
    description: 'Eighty of the finest records to ever come out of Ghana.',
    creator: 'BeatzClik',
    image: IMG.generic,
    isPublic: true,
    followers: 80_000,
    trackIds: ['kwaku-the-traveller', 'soja', 'friday-night', 'sugarcane', 'terminator'],
  },
  {
    id: 'hiplife-throwback',
    title: 'Hiplife Throwback',
    creator: 'BeatzClik',
    image: IMG.king,
    isPublic: true,
    followers: 22_000,
    trackIds: ['soja', '45', 'akwasidae'],
  },
]

// ---------------------------------------------------------------------------
// Browse categories (Search screen)
// ---------------------------------------------------------------------------

export const browseCategories: BrowseCategory[] = [
  { id: 'afrobeats', title: 'Afrobeats', colorClass: 'bg-red-500' },
  { id: 'hiplife', title: 'Hiplife', colorClass: 'bg-purple-500' },
  { id: 'amapiano', title: 'Amapiano', colorClass: 'bg-green-500' },
  { id: 'highlife', title: 'Highlife', colorClass: 'bg-orange-600' },
  { id: 'gospel', title: 'Gospel', colorClass: 'bg-blue-500' },
  { id: 'drill', title: 'Drill', colorClass: 'bg-neutral-800' },
  { id: 'rnb', title: 'R&B', colorClass: 'bg-pink-600' },
  { id: 'reggae', title: 'Reggae', colorClass: 'bg-teal-500' },
  { id: 'jazz', title: 'Jazz', colorClass: 'bg-yellow-600' },
  { id: 'made-in-ghana', title: 'Made in Ghana', colorClass: 'bg-yellow-500' },
  { id: 'charts', title: 'Charts', colorClass: 'bg-indigo-500' },
  { id: 'podcasts', title: 'Podcasts', colorClass: 'bg-emerald-900' },
  { id: 'mood', title: 'Mood', colorClass: 'bg-red-600' },
  { id: 'workout', title: 'Workout', colorClass: 'bg-green-500' },
  { id: 'focus', title: 'Focus', colorClass: 'bg-blue-600' },
  { id: 'sleep', title: 'Sleep', colorClass: 'bg-indigo-400' },
]

export const genres: Genre[] = [
  'Afrobeats',
  'Hiplife',
  'Highlife',
  'Amapiano',
  'Drill',
  'Gospel',
  'R&B',
  'Reggae',
  'Jazz',
]

// ---------------------------------------------------------------------------
// Current user + their shows
// ---------------------------------------------------------------------------

export const currentUser: User = {
  id: 'kojo',
  name: 'Kojo',
  email: 'kojo@onepaygh.com',
  avatar: 'https://i.pravatar.cc/100?img=11',
  totalSpent: 312,
}

export const upcomingShows: Record<string, Show[]> = {
  'black-sherif': [
    { date: '2026-05-22', city: 'Accra', venue: 'Independence Square' },
    { date: '2026-06-14', city: 'Kumasi', venue: 'Baba Yara Stadium' },
    { date: '2026-07-09', city: 'Tema', venue: 'Beach Festival' },
  ],
}

// ---------------------------------------------------------------------------
// Lookup helpers — swap these for TanStack Query hooks once the API exists.
// ---------------------------------------------------------------------------

export const getArtist = (id: string): Artist | undefined =>
  artists.find((a) => a.id === id)

export const getAlbum = (id: string): Album | undefined =>
  albums.find((a) => a.id === id)

export const getTrack = (id: string): Track | undefined =>
  tracks.find((t) => t.id === id)

/**
 * Whether a track can be played / added to a playlist. Free and owned tracks
 * are accessible; premium (for-sale) tracks must be purchased first.
 */
export const trackAccessible = (track: Track): boolean => track.ownership !== 'for-sale'

export const getPlaylist = (id: string): Playlist | undefined =>
  playlists.find((p) => p.id === id)

/** Resolve an ordered list of track ids to Track objects (skips unknown ids). */
export const getTracksByIds = (ids: string[]): Track[] =>
  ids.map((id) => getTrack(id)).filter((t): t is Track => Boolean(t))

export const getAlbumTracks = (albumId: string): Track[] => {
  const album = getAlbum(albumId)
  return album ? getTracksByIds(album.trackIds) : []
}

export const getPlaylistTracks = (playlistId: string): Track[] => {
  const playlist = getPlaylist(playlistId)
  return playlist ? getTracksByIds(playlist.trackIds) : []
}

/** Top tracks by an artist, most-played first. */
export const getArtistTracks = (artistId: string): Track[] =>
  tracks
    .filter((t) => t.artistId === artistId)
    .sort((a, b) => (b.plays ?? 0) - (a.plays ?? 0))

/**
 * Albums in the premium "Featured" carousel on the home page. This is a paid
 * placement — artists pay to have an album promoted here. Order = display order.
 */
export const featuredAlbums: Album[] = ['iron-boy', 'love-damini', 'the-villain-i-never-was']
  .map((id) => getAlbum(id))
  .filter((a): a is Album => Boolean(a))

/** A small default queue used to seed the player on first load. */
export const defaultQueue: Track[] = getTracksByIds([
  'last-last',
  'kwaku-the-traveller',
  'sungba',
  'terminator',
  'friday-night',
])
