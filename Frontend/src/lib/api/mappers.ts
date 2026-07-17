import type {
  Artist,
  Album,
  Track,
  TrackCredit,
  BrowseCategory,
  Playlist,
  Genre,
  OwnershipStatus,
  Money,
  StoreItem,
  StoreItemType,
  LicenseOption,
  LicenseTier,
  Event,
  TicketTier,
  EventStatus,
  EventCategory,
} from '../../types'

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

export interface PlaylistWire {
  id: string
  title: string
  description: string | null
  creator: string
  creatorAvatar: string | null
  image: string
  isPublic: boolean
  followers: number | null
  trackIds: string[]
  /** Populated by the detail endpoint; unused by list resolution. */
  tracks: TrackWire[] | null
}

export function toPlaylist(wire: PlaylistWire): Playlist {
  return {
    id: wire.id,
    title: wire.title,
    description: wire.description ?? undefined,
    creator: wire.creator,
    creatorAvatar: wire.creatorAvatar ?? undefined,
    image: wire.image,
    isPublic: wire.isPublic,
    followers: wire.followers ?? undefined,
    trackIds: wire.trackIds,
  }
}

export interface LicenseOptionWire {
  tier: LicenseTier
  label: string
  price: Money
  features: string[]
  terms: string | null
}

function toLicenseOption(wire: LicenseOptionWire): LicenseOption {
  return {
    tier: wire.tier,
    label: wire.label,
    price: wire.price,
    features: wire.features,
    terms: wire.terms ?? undefined,
  }
}

export interface MerchVariantWire {
  label: string
  options: string[]
}

/** Mirrors `StoreItemView` served by `GET /v1/store` and `GET /v1/store/:id`. */
export interface StoreItemWire {
  id: string
  type: StoreItemType
  title: string
  artistName: string
  artistId: string | null
  image: string
  price: Money
  genre: Genre | null
  badges: string[] | null
  description: string | null
  popularity: number | null
  createdAt: string | null
  licenseOptions: LicenseOptionWire[] | null
  variants: MerchVariantWire[] | null
  quality: string | null
  dropsAt: string | null
  stockRemaining: number | null
}

export function toStoreItem(wire: StoreItemWire): StoreItem {
  return {
    id: wire.id,
    type: wire.type,
    title: wire.title,
    artistName: wire.artistName,
    artistId: wire.artistId ?? undefined,
    image: wire.image,
    price: wire.price,
    genre: wire.genre ?? undefined,
    badges: wire.badges ?? undefined,
    description: wire.description ?? undefined,
    popularity: wire.popularity ?? undefined,
    createdAt: wire.createdAt ?? undefined,
    licenseOptions: wire.licenseOptions?.map(toLicenseOption) ?? undefined,
    variants: wire.variants ?? undefined,
    quality: wire.quality ?? undefined,
    dropsAt: wire.dropsAt ?? undefined,
    stockRemaining: wire.stockRemaining ?? undefined,
  }
}

/** Mirrors `TicketTierView` nested in `EventView` served by `GET /v1/events`. No `id` on the wire — the tier's internal id is not part of this shape. */
export interface TicketTierWire {
  name: string
  price: Money
  perks: string[] | null
  soldOut: boolean | null
}

export function toTicketTier(wire: TicketTierWire): TicketTier {
  return {
    name: wire.name,
    price: wire.price,
    perks: wire.perks ?? undefined,
    soldOut: wire.soldOut ?? undefined,
  }
}

/** Mirrors `EventView` served by `GET /v1/events` and `GET /v1/events/:id`. */
export interface EventWire {
  id: string
  title: string
  artistName: string
  artistId: string | null
  lineup: string[] | null
  image: string
  date: string
  doorsTime: string | null
  venue: string
  city: string
  region: string | null
  status: EventStatus
  category: EventCategory
  description: string | null
  ticketTiers: TicketTierWire[]
  popularity: number | null
  ageRestriction: string | null
}

export function toEvent(wire: EventWire): Event {
  return {
    id: wire.id,
    title: wire.title,
    artistName: wire.artistName,
    artistId: wire.artistId ?? undefined,
    lineup: wire.lineup ?? undefined,
    image: wire.image,
    date: wire.date,
    doorsTime: wire.doorsTime ?? undefined,
    venue: wire.venue,
    city: wire.city,
    region: wire.region ?? undefined,
    status: wire.status,
    category: wire.category,
    description: wire.description ?? undefined,
    ticketTiers: wire.ticketTiers.map(toTicketTier),
    popularity: wire.popularity ?? undefined,
    ageRestriction: wire.ageRestriction ?? undefined,
  }
}
