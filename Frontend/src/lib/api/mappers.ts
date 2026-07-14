import type { Artist, Album, Track, TrackCredit, BrowseCategory, Genre, OwnershipStatus, Money } from '../../types'

export interface ArtistWire {
  id: string
  name: string
  image: string
  coverImage: string | null
  verified: boolean | null
  monthlyListeners: number | null
  followers: number | null
  bio: string | null
  location: string | null
  genres: string[] | null
}

export function toArtist(wire: ArtistWire): Artist {
  return {
    id: wire.id,
    name: wire.name,
    image: wire.image,
    coverImage: wire.coverImage ?? undefined,
    verified: wire.verified ?? undefined,
    monthlyListeners: wire.monthlyListeners ?? undefined,
    followers: wire.followers ?? undefined,
    bio: wire.bio ?? undefined,
    location: wire.location ?? undefined,
    genres: (wire.genres ?? undefined) as Genre[] | undefined,
  }
}

export interface TrackCreditWire {
  role: string
  names: string[]
}

export interface TrackWire {
  id: string
  title: string
  artistId: string
  artistName: string
  albumId: string | null
  albumTitle: string | null
  duration: number
  image: string
  ownership: string
  price: Money | null
  plays: number | null
  audioUrl: string | null
  credits: TrackCreditWire[] | null
  quality: string | null
  year: number | null
}

export function toTrack(wire: TrackWire): Track {
  return {
    id: wire.id,
    title: wire.title,
    artistId: wire.artistId,
    artistName: wire.artistName,
    albumId: wire.albumId ?? undefined,
    albumTitle: wire.albumTitle ?? undefined,
    duration: wire.duration,
    image: wire.image,
    ownership: wire.ownership as OwnershipStatus,
    price: wire.price ?? undefined,
    plays: wire.plays ?? undefined,
    audioUrl: wire.audioUrl ?? undefined,
    credits: (wire.credits as TrackCredit[] | null) ?? undefined,
    quality: wire.quality ?? undefined,
    year: wire.year ?? undefined,
  }
}

export interface AlbumWire {
  id: string
  title: string
  artistId: string
  artistName: string
  year: number
  coverImage: string
  genres: string[] | null
  trackIds: string[]
  /** Populated only when the album was fetched with ?tracks=true. */
  tracks: TrackWire[] | null
}

export function toAlbum(wire: AlbumWire): Album {
  return {
    id: wire.id,
    title: wire.title,
    artistId: wire.artistId,
    artistName: wire.artistName,
    year: wire.year,
    coverImage: wire.coverImage,
    genres: (wire.genres ?? undefined) as Genre[] | undefined,
    trackIds: wire.trackIds,
  }
}

export function toAlbumTracks(wire: AlbumWire): Track[] {
  return (wire.tracks ?? []).map(toTrack)
}

export interface BrowseCategoryWire {
  id: string
  title: string
  colorClass: string
}

export function toBrowseCategory(wire: BrowseCategoryWire): BrowseCategory {
  return { id: wire.id, title: wire.title, colorClass: wire.colorClass }
}

export interface LyricLineWire {
  time: number
  text: string
}

export interface LyricsWire {
  lines: LyricLineWire[]
}

export function toLyricLines(wire: LyricsWire): LyricLineWire[] {
  return wire.lines
}
