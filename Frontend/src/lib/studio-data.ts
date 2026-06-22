/**
 * Studio (creator-side) mock data.
 *
 * The fan app reads from `mock-data.ts`; the Artist Studio reads from here.
 * Shapes are intentionally close to what the backend will serve for the
 * creator dashboard. Swap the exports for TanStack Query hooks once the API
 * exists — call sites won't change.
 */

import type { Genre } from '../types'

/** The signed-in creator (the artist whose studio we're viewing). */
export interface StudioArtist {
  id: string
  name: string
  /** Two-letter monogram used in the sidebar avatar. */
  initials: string
  avatar?: string
  verified: boolean
}

export const studioArtist: StudioArtist = {
  id: 'black-sherif',
  name: 'Black Sherif',
  initials: 'BS',
  verified: true,
}

export type ReleaseType = 'single' | 'ep' | 'album' | 'mixtape'

export const releaseTypes: { value: ReleaseType; label: string; hint?: string }[] = [
  { value: 'single', label: 'Single' },
  { value: 'ep', label: 'EP', hint: '3–6 tracks' },
  { value: 'album', label: 'Album', hint: '7+' },
  { value: 'mixtape', label: 'Mixtape' },
]

/** Genres a creator can tag a release with (reuses the fan-side taxonomy). */
export const studioGenres: Genre[] = [
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

/** The four steps of the new-release wizard, in order. */
export const RELEASE_WIZARD_STEPS = [
  { slug: 'details', label: 'Details', title: 'Release details' },
  { slug: 'tracks', label: 'Tracks', title: 'Upload tracks' },
  { slug: 'splits', label: 'Splits', title: 'Royalty splits' },
  { slug: 'review', label: 'Review', title: 'Review & publish' },
] as const

/** Per-track price options a creator can pick (in cedis; 0 = free). */
export const PRICE_OPTIONS = [2, 2.5, 3, 0] as const

/** Creator's share of each sale. Fans pay the price; the rest is platform fee. */
export const CREATOR_REVENUE_SHARE = 0.7

/** Auto bundle discount applied when fans buy a whole multi-track release. */
export const BUNDLE_DISCOUNT = 0.24

/** Whether a release type allows more than one track. */
export const isMultiTrack = (t: ReleaseType): boolean => t !== 'single'

export const releaseTypeLabel = (t: ReleaseType): string =>
  releaseTypes.find((r) => r.value === t)?.label ?? t

export type ReleaseStepSlug = (typeof RELEASE_WIZARD_STEPS)[number]['slug']

/* -------------------------------- Releases ------------------------------- */

export type ReleaseStatus = 'live' | 'scheduled' | 'in_review' | 'draft'

export interface StudioRelease {
  id: string
  title: string
  type: ReleaseType
  status: ReleaseStatus
  /** Release date (or scheduled date); '—' for drafts. */
  date: string
  trackCount: number
  streams: number
  revenue: number
  price: number
}

export function getReleases(): StudioRelease[] {
  return [
    { id: 'r1', title: 'Iron Boy', type: 'album', status: 'in_review', date: 'Jul 14, 2026', trackCount: 14, streams: 0, revenue: 0, price: 2.5 },
    { id: 'r5', title: '45', type: 'single', status: 'scheduled', date: 'Jun 28, 2026', trackCount: 1, streams: 0, revenue: 0, price: 2.5 },
    { id: 'r2', title: 'The Villain I Never Was', type: 'album', status: 'live', date: 'Oct 5, 2024', trackCount: 14, streams: 2_400_000, revenue: 48_900, price: 18.99 },
    { id: 'r4', title: 'Soja', type: 'single', status: 'live', date: 'Aug 2, 2024', trackCount: 1, streams: 98_000, revenue: 3_890, price: 2.5 },
    { id: 'r3', title: 'Kwaku the Traveller', type: 'single', status: 'live', date: 'Apr 8, 2024', trackCount: 1, streams: 142_000, revenue: 6_420, price: 2.5 },
    { id: 'r6', title: 'Untitled session', type: 'single', status: 'draft', date: '—', trackCount: 2, streams: 0, revenue: 0, price: 2.5 },
  ]
}

/* -------------------------------- Podcasts ------------------------------- */

export type EpisodeStatus = 'published' | 'scheduled' | 'draft'

export const STUDIO_PODCAST_CATEGORIES = [
  'News & Politics', 'Comedy', 'Business', 'Sports', 'Culture', 'Tech', 'Health', 'Storytelling',
] as const

export interface StudioPodcastShow {
  id: string
  title: string
  category: string
}

export interface StudioEpisode {
  id: string
  showId: string
  showTitle: string
  title: string
  /** Length in seconds. */
  duration: number
  status: EpisodeStatus
  premium: boolean
  /** Price in cedis when premium; 0 = free. */
  price: number
  publishedAt: string
  plays: number
}

export function getStudioShows(): StudioPodcastShow[] {
  return [{ id: 'sh-konongo', title: 'Konongo Diaries', category: 'Storytelling' }]
}

export function getStudioEpisodes(): StudioEpisode[] {
  return [
    { id: 'ep1', showId: 'sh-konongo', showTitle: 'Konongo Diaries', title: 'Ep 12 · The Come Up', duration: 2940, status: 'published', premium: false, price: 0, publishedAt: 'May 02', plays: 18_400 },
    { id: 'ep2', showId: 'sh-konongo', showTitle: 'Konongo Diaries', title: 'Ep 11 · Studio Nights', duration: 3360, status: 'published', premium: true, price: 5, publishedAt: 'Apr 25', plays: 9_200 },
    { id: 'ep3', showId: 'sh-konongo', showTitle: 'Konongo Diaries', title: 'Ep 13 · Bonus (early access)', duration: 1980, status: 'scheduled', premium: true, price: 5, publishedAt: 'Jun 28', plays: 0 },
  ]
}

/* -------------------------------- Profile -------------------------------- */

export interface StudioShow {
  id: string
  venue: string
  date: string
  city: string
}

export interface PressAsset { id: string; name: string; url: string }

export interface StudioProfile {
  displayName: string
  username: string
  hometown: string
  genres: string[]
  bio: string
  avatar: string | null
  banner: string | null
  links: { instagram: string; twitter: string; youtube: string; website: string }
  shows: StudioShow[]
  /** Track id pinned to the top of the public profile. */
  featuredTrackId: string | null
  bookingEmail: string
  pressAssets: PressAsset[]
}

/* ------------------------------- Settings -------------------------------- */

export interface TeamMember {
  id: string
  name: string
  email: string
  role: 'Owner' | 'Manager' | 'Label' | 'Invited'
}

export interface SessionInfo {
  id: string
  device: string
  location: string
  lastActive: string
  current: boolean
}

export interface ConnectedApp {
  id: string
  name: string
  description: string
  connected: boolean
}

export interface StudioSettings {
  email: string
  phone: string
  country: string
  language: string
  timezone: string
  twoFactor: boolean
  sessions: SessionInfo[]
  connectedApps: ConnectedApp[]
  verification: { artist: boolean; identity: boolean; payout: boolean; rights: boolean }
  billing: { plan: string; price: number; renews: string }
  notifications: {
    sales: boolean
    tips: boolean
    followers: boolean
    payouts: boolean
    weeklySummary: boolean
    comments: boolean
    marketing: boolean
  }
  defaults: {
    trackPrice: number
    releaseVisibility: 'public' | 'scheduled'
    autoExplicit: boolean
    allowOffers: boolean
  }
  payouts: {
    autoWithdraw: boolean
    autoWithdrawThreshold: number
    taxId: string
  }
  privacy: {
    discoverable: boolean
    showRealName: boolean
    acceptBookings: boolean
    allowDms: boolean
  }
  team: TeamMember[]
}

export const STUDIO_LANGUAGES = ['English', 'Twi', 'Ga', 'Ewe', 'Hausa', 'French'] as const

export function getStudioSettings(): StudioSettings {
  return {
    email: 'hello@onepaygh.com',
    phone: '0244 ··· 9210',
    country: 'Ghana',
    language: 'English',
    timezone: 'GMT (Accra)',
    twoFactor: true,
    sessions: [
      { id: 'd1', device: 'MacBook Pro · Chrome', location: 'Accra, GH', lastActive: 'Active now', current: true },
      { id: 'd2', device: 'iPhone 15 · BeatzClik app', location: 'Accra, GH', lastActive: '2 days ago', current: false },
      { id: 'd3', device: 'Windows · Edge', location: 'Kumasi, GH', lastActive: '1 week ago', current: false },
    ],
    connectedApps: [
      { id: 'a1', name: 'Instagram', description: 'Auto-share new releases to your story', connected: true },
      { id: 'a2', name: 'Audiomack', description: 'Cross-post your catalog', connected: true },
      { id: 'a3', name: 'GHAMRO', description: 'Collect performance royalties in Ghana', connected: false },
      { id: 'a4', name: 'YouTube Content ID', description: 'Protect your audio across YouTube', connected: false },
    ],
    verification: { artist: true, identity: true, payout: true, rights: false },
    billing: { plan: 'Studio Pro', price: 40, renews: 'Jul 1, 2026' },
    notifications: { sales: true, tips: true, followers: true, payouts: true, weeklySummary: true, comments: false, marketing: false },
    defaults: { trackPrice: 2.5, releaseVisibility: 'scheduled', autoExplicit: true, allowOffers: false },
    payouts: { autoWithdraw: false, autoWithdrawThreshold: 5000, taxId: '' },
    privacy: { discoverable: true, showRealName: false, acceptBookings: true, allowDms: true },
    team: [
      { id: 'u1', name: 'Black Sherif', email: 'hello@onepaygh.com', role: 'Owner' },
      { id: 'u2', name: 'Konongo Zongo Records', email: 'splits@empire.com', role: 'Label' },
    ],
  }
}

export function getStudioProfile(): StudioProfile {
  return {
    displayName: 'Black Sherif',
    username: '@blacko',
    hometown: 'Konongo, Ghana',
    genres: ['Drill', 'Hiplife', 'Afrobeats'],
    bio: 'Mohammed Ismail Sherif, known as Black Sherif, is a Ghanaian rapper from Konongo. Best known for "Kwaku the Traveller" and "Soja".',
    avatar: null,
    banner: null,
    links: {
      instagram: '@blacksherif',
      twitter: '@blacko_sherif',
      youtube: 'BlackSherifVEVO',
      website: 'blacksherif.com',
    },
    shows: [
      { id: 's1', venue: 'Independence Square', date: 'May 22', city: 'Accra' },
      { id: 's2', venue: 'Baba Yara Stadium', date: 'Jun 14', city: 'Kumasi' },
    ],
    featuredTrackId: null,
    bookingEmail: 'bookings@blacksherif.com',
    pressAssets: [],
  }
}
