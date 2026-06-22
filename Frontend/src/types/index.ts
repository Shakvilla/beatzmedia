/**
 * BeatzClik shared domain types.
 *
 * This is the single source of truth for the shapes the UI renders. Once the
 * frontend is complete it becomes the contract the Quarkus backend implements,
 * so prefer structured values (numbers, ids, enums) over pre-formatted strings.
 * Use the helpers in `src/lib/format.ts` to turn these into display strings.
 */

export type ID = string

/** Music genres surfaced across BeatzClik (Ghana / Africa focused). */
export type Genre =
  | 'Afrobeats'
  | 'Hiplife'
  | 'Highlife'
  | 'Amapiano'
  | 'Drill'
  | 'Gospel'
  | 'R&B'
  | 'Reggae'
  | 'Jazz'

/**
 * Commercial state of a track for the current user.
 * - `owned`    — user has purchased it (or it came with an album they own)
 * - `free`     — free to play, no purchase required
 * - `for-sale` — must be purchased; see `price`
 */
export type OwnershipStatus = 'owned' | 'free' | 'for-sale'

/** Money is always Ghana Cedis (GHS) for now. `amount` is in cedis. */
export interface Money {
  amount: number
  currency: 'GHS'
}

export interface Artist {
  id: ID
  name: string
  /** Square avatar / profile image. */
  image: string
  /** Wide banner used on the artist page hero. */
  coverImage?: string
  verified?: boolean
  /** Raw counts — format with formatCount(). */
  monthlyListeners?: number
  followers?: number
  bio?: string
  /** e.g. "Konongo, Ghana" */
  location?: string
  genres?: Genre[]
}

export interface Album {
  id: ID
  title: string
  artistId: ID
  /** Denormalized for convenience in lists; canonical source is the Artist. */
  artistName: string
  year: number
  coverImage: string
  genres?: Genre[]
  /** Ordered track ids belonging to this album. */
  trackIds: ID[]
}

export interface Track {
  id: ID
  title: string
  artistId: ID
  artistName: string
  albumId?: ID
  albumTitle?: string
  /** Duration in whole seconds. Format with formatDuration(). */
  duration: number
  /** Square cover art. */
  image: string
  ownership: OwnershipStatus
  /** Present when ownership is 'for-sale'. */
  price?: Money
  /** Lifetime play count. Format with formatCount(). */
  plays?: number
  /** Audio source. Optional today (simulated playback); required once real audio lands. */
  audioUrl?: string
  /** Production / songwriting credits for the track detail page. */
  credits?: TrackCredit[]
  /** e.g. "Lossless • 24-bit/192kHz" */
  quality?: string
  year?: number
}

export interface TrackCredit {
  role: string
  names: string[]
}

export interface Playlist {
  id: ID
  title: string
  description?: string
  /** Display name of the creator. */
  creator: string
  /** Optional creator avatar. */
  creatorAvatar?: string
  image: string
  isPublic: boolean
  followers?: number
  /** Ordered track ids in the playlist. */
  trackIds: ID[]
}

/** A playlist the user created themselves (client-side, in the collection store). */
export interface UserPlaylist {
  id: ID
  title: string
  description?: string
  trackIds: ID[]
  /** ISO created date. */
  createdAt: string
}

/** A browseable category tile on the Search screen. */
export interface BrowseCategory {
  id: ID
  title: string
  /** Tailwind background class used for the tile, e.g. "bg-red-500". */
  colorClass: string
}

/** A concert / event shown on an artist page. */
export interface Show {
  /** ISO date string. */
  date: string
  city: string
  venue: string
}

export interface User {
  id: ID
  name: string
  email: string
  avatar?: string
  /** Total cedis spent across owned tracks/albums. */
  totalSpent?: number
}

// ---------------------------------------------------------------------------
// Music Store (marketplace)
// ---------------------------------------------------------------------------

/** What kind of product a store item is. */
export type StoreItemType = 'TRACK' | 'ALBUM' | 'BEAT_LICENSE' | 'MERCH' | 'EXCLUSIVE'

/** Beat / stem licensing tiers, cheapest to most permissive. */
export type LicenseTier = 'LEASE' | 'PREMIUM' | 'EXCLUSIVE'

/** A single selectable licensing tier on a beat product. */
export interface LicenseOption {
  tier: LicenseTier
  label: string
  price: Money
  /** Bullet list of what the buyer gets. */
  features: string[]
  /** Short terms summary, e.g. "MP3 + WAV • up to 10,000 streams". */
  terms?: string
}

/** A configurable attribute on a merch product (size, colour, …). */
export interface MerchVariant {
  /** Attribute name shown as a label, e.g. "Size". */
  label: string
  /** Selectable values, e.g. ["S", "M", "L", "XL"]. */
  options: string[]
}

/** A purchasable product in the Music Store. */
export interface StoreItem {
  id: ID
  type: StoreItemType
  title: string
  artistName: string
  artistId?: ID
  image: string
  /** Base / "from" price. */
  price: Money
  genre?: Genre
  /** Overlay badges, e.g. "HI-FI LOSSLESS", "STEMS INCLUDED", "LIMITED". */
  badges?: string[]
  description?: string
  /** Ranking weight for the "popular" sort. */
  popularity?: number
  /** ISO date used for the "newest" sort. */
  createdAt?: string
  // --- type-specific ---
  /** BEAT_LICENSE: selectable tiers. */
  licenseOptions?: LicenseOption[]
  /** MERCH: configurable variants. */
  variants?: MerchVariant[]
  /** TRACK / ALBUM (Hi-Fi): audio quality string. */
  quality?: string
  /** EXCLUSIVE: ISO drop date. */
  dropsAt?: string
  /** EXCLUSIVE / MERCH: remaining stock for scarcity UI. */
  stockRemaining?: number
}

/** Sort options for store catalog views. */
export type StoreSort = 'popular' | 'newest' | 'price-asc' | 'price-desc'

// ---------------------------------------------------------------------------
// Podcasts
// ---------------------------------------------------------------------------

export type PodcastCategory =
  | 'News & Politics'
  | 'Comedy'
  | 'Business'
  | 'Sports'
  | 'Culture'
  | 'Tech'
  | 'Health'
  | 'Storytelling'

export interface Podcast {
  id: ID
  title: string
  publisher: string
  image: string
  category: PodcastCategory
  description?: string
  episodeCount?: number
  /** Ranking weight for "top shows". */
  popularity?: number
  /** One-off price to own every premium episode of the show. */
  seasonPassPrice?: Money
  /** Whether listeners can tip the show. */
  supportsTips?: boolean
}

export interface PodcastEpisode {
  id: ID
  podcastId: ID
  title: string
  /** Denormalized show title for episode rows. */
  showTitle: string
  image: string
  /** Length in seconds. */
  duration: number
  /** ISO publish date. */
  publishedAt: string
  description?: string
  episodeNumber?: number
  /** Premium episodes must be purchased (buy-to-own) before playing. */
  isPremium?: boolean
  /** Price to buy a premium / early-access episode. */
  price?: Money
  /** Whether the current user already owns this premium episode. */
  isOwned?: boolean
  /** Early-access: locked until `publicAt`, but buyable now to hear it early. */
  isEarlyAccess?: boolean
  /** ISO date an early-access episode becomes free to everyone. */
  publicAt?: string
}

// ---------------------------------------------------------------------------
// Events & ticketing
// ---------------------------------------------------------------------------

export type EventStatus = 'on-sale' | 'selling-fast' | 'sold-out'

export type EventCategory = 'Concert' | 'Festival' | 'Club Night' | 'Listening Party' | 'Tour'

export interface TicketTier {
  name: string
  price: Money
  /** What the tier includes. */
  perks?: string[]
  soldOut?: boolean
}

export interface Event {
  id: ID
  title: string
  /** Headliner, denormalized for cards. */
  artistName: string
  artistId?: ID
  /** Supporting acts. */
  lineup?: string[]
  image: string
  /** ISO datetime of the event. */
  date: string
  /** Display door time, e.g. "7:00 PM". */
  doorsTime?: string
  venue: string
  city: string
  region?: string
  status: EventStatus
  category: EventCategory
  description?: string
  ticketTiers: TicketTier[]
  /** Ranking weight. */
  popularity?: number
  ageRestriction?: string
}

/** Item added to the cart from the store. */
export interface CartStoreItem {
  id: ID
  title: string
  artist: string
  price: Money
  image: string
  type: StoreItemType
  metadata?: {
    licenseTier?: LicenseTier
    merchVariants?: Record<string, string>
  }
}
