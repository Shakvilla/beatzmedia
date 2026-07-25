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
  Podcast,
  PodcastEpisode,
  PodcastCategory,
  AppNotification,
  NotificationType,
} from '../../types'
import type {
  Analytics,
  Audience,
  MetricSeries,
  CountryStat,
  TopTrackStat,
  AgeBucket,
  SourceStat,
  Superfan,
} from '../studio-analytics'
import type { StudioProfile, StudioSettings, StudioRelease, StudioPodcastShow, StudioEpisode, EpisodeStatus } from '../studio-data'
import type { UploadedTrack } from '../../features/studio/release-draft-context'
import type { Payouts, PayoutMethod, PayoutTxn, PayoutType, PayoutStatus, MethodKind } from '../studio-payouts'
import type { AdminUserRow, UserRole, UserStatus, UserActionLog, CatalogItem, CatalogStatus, CatalogType, ModerationItem, ModReason, ModSeverity, ModStatus, Finance, PendingPayout, ProviderMix, Dispute, LedgerTxn, LedgerType, TimelineEntry } from '../admin-data'
import { relativeTimeAgo, monthYear, formatDuration, relativeTime, toCedis, monthDay } from '../format'

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

/** Mirrors `PodcastView` served by `GET /v1/podcasts` and `GET /v1/podcasts/:id`. */
export interface PodcastWire {
  id: string
  title: string
  publisher: string
  image: string
  category: PodcastCategory
  description: string | null
  episodeCount: number | null
  popularity: number | null
  seasonPassPrice: Money | null
  supportsTips: boolean | null
}

export function toPodcast(wire: PodcastWire): Podcast {
  return {
    id: wire.id,
    title: wire.title,
    publisher: wire.publisher,
    image: wire.image,
    category: wire.category,
    description: wire.description ?? undefined,
    episodeCount: wire.episodeCount ?? undefined,
    popularity: wire.popularity ?? undefined,
    seasonPassPrice: wire.seasonPassPrice ?? undefined,
    supportsTips: wire.supportsTips ?? undefined,
  }
}

/** Mirrors `PodcastEpisodeView` served by `GET /v1/podcasts/:id/episodes`. */
export interface PodcastEpisodeWire {
  id: string
  podcastId: string
  title: string
  showTitle: string
  image: string
  duration: number
  publishedAt: string
  description: string | null
  episodeNumber: number | null
  isPremium: boolean | null
  price: Money | null
  isOwned: boolean | null
  isEarlyAccess: boolean | null
  publicAt: string | null
}

export function toPodcastEpisode(wire: PodcastEpisodeWire): PodcastEpisode {
  return {
    id: wire.id,
    podcastId: wire.podcastId,
    title: wire.title,
    showTitle: wire.showTitle,
    image: wire.image,
    duration: wire.duration,
    publishedAt: wire.publishedAt,
    description: wire.description ?? undefined,
    episodeNumber: wire.episodeNumber ?? undefined,
    isPremium: wire.isPremium ?? undefined,
    price: wire.price ?? undefined,
    isOwned: wire.isOwned ?? undefined,
    isEarlyAccess: wire.isEarlyAccess ?? undefined,
    publicAt: wire.publicAt ?? undefined,
  }
}

// ---------------------------------------------------------------------------
// Studio analytics + audience
// ---------------------------------------------------------------------------

interface MetricSeriesWire { total: number; delta: number; current: number[]; previous: number[] }

/** Mirrors `AnalyticsView` served by `GET /v1/studio/analytics?range=`. */
export interface AnalyticsWire {
  rangeLabel: string
  axisLabel: string
  labels: string[]
  metrics: {
    streams: MetricSeriesWire
    sales: MetricSeriesWire
    followers: MetricSeriesWire
    tips: MetricSeriesWire
  }
  fans: number
  countries: { name: string; value: number }[]
  topTracks: { title: string; streams: number; revenue: number }[]
  ages: { label: string; value: number }[]
  revenue: { sales: number; streaming: number; tips: number }
  engagement: { completion: number; save: number; skip: number }
  sources: { name: string; pct: number }[]
}

const toMetricSeries = (w: MetricSeriesWire): MetricSeries => ({
  total: w.total,
  delta: w.delta,
  current: w.current,
  previous: w.previous,
})

export function toAnalytics(wire: AnalyticsWire): Analytics {
  return {
    rangeLabel: wire.rangeLabel,
    axisLabel: wire.axisLabel,
    labels: wire.labels,
    metrics: {
      streams: toMetricSeries(wire.metrics.streams),
      sales: toMetricSeries(wire.metrics.sales),
      followers: toMetricSeries(wire.metrics.followers),
      tips: toMetricSeries(wire.metrics.tips),
    },
    fans: wire.fans,
    countries: wire.countries.map((c): CountryStat => ({ name: c.name, value: c.value })),
    topTracks: wire.topTracks.map(
      (t): TopTrackStat => ({ title: t.title, streams: t.streams, revenue: t.revenue }),
    ),
    ages: wire.ages.map((a): AgeBucket => ({ label: a.label, value: a.value })),
    revenue: { sales: wire.revenue.sales, streaming: wire.revenue.streaming, tips: wire.revenue.tips },
    engagement: {
      completion: wire.engagement.completion,
      save: wire.engagement.save,
      skip: wire.engagement.skip,
    },
    sources: wire.sources.map((s): SourceStat => ({ name: s.name, pct: s.pct })),
  }
}

/** Mirrors `AudienceView` served by `GET /v1/studio/audience?range=`. */
export interface AudienceWire {
  rangeLabel: string
  monthlyListeners: number
  listenersDelta: number
  followers: number
  followersGained: number
  followersPeriod: string
  superfans: number
  avgSessionSec: number
  avgSessionDelta: number
  cities: { name: string; value: number }[]
  gender: { male: number; female: number; other: number }
  ages: { label: string; value: number }[]
  superfansList: { handle: string; initial: string; tracks: number; tipped: number }[]
}

export function toAudience(wire: AudienceWire): Audience {
  return {
    rangeLabel: wire.rangeLabel,
    monthlyListeners: wire.monthlyListeners,
    listenersDelta: wire.listenersDelta,
    followers: wire.followers,
    followersGained: wire.followersGained,
    followersPeriod: wire.followersPeriod,
    superfans: wire.superfans,
    avgSessionSec: wire.avgSessionSec,
    avgSessionDelta: wire.avgSessionDelta,
    cities: wire.cities.map((c): CountryStat => ({ name: c.name, value: c.value })),
    gender: { male: wire.gender.male, female: wire.gender.female, other: wire.gender.other },
    ages: wire.ages.map((a): AgeBucket => ({ label: a.label, value: a.value })),
    superfansList: wire.superfansList.map(
      (s): Superfan => ({ handle: s.handle, initial: s.initial, tracks: s.tracks, tipped: s.tipped }),
    ),
  }
}

// ── Studio profile ────────────────────────────────────────────────
// StudioProfileView serves null for any unset optional text field (and can
// omit arrays) on a fresh profile — the read mapper coerces every field to
// the non-null StudioProfile shape the UI expects. The save body is the
// whole (writable) shape with blob: object URLs stripped — those are
// local-only and meaningless server-side (no media-upload endpoint yet).
export interface StudioProfileWire {
  displayName: string | null
  username: string | null
  hometown: string | null
  genres: string[] | null
  bio: string | null
  avatar: string | null
  banner: string | null
  links: { instagram: string; twitter: string; youtube: string; website: string } | null
  shows: StudioProfile['shows'] | null
  featuredTrackId: string | null
  bookingEmail: string | null
  pressAssets: StudioProfile['pressAssets'] | null
}

export function toStudioProfile(w: StudioProfileWire): StudioProfile {
  return {
    displayName: w.displayName ?? '',
    username: w.username ?? '',
    hometown: w.hometown ?? '',
    genres: w.genres ?? [],
    bio: w.bio ?? '',
    avatar: w.avatar,
    banner: w.banner,
    links: {
      instagram: w.links?.instagram ?? '',
      twitter: w.links?.twitter ?? '',
      youtube: w.links?.youtube ?? '',
      website: w.links?.website ?? '',
    },
    shows: w.shows ?? [],
    featuredTrackId: w.featuredTrackId,
    bookingEmail: w.bookingEmail ?? '',
    pressAssets: w.pressAssets ?? [],
  }
}

export type SaveStudioProfileBody = StudioProfile
const dropBlob = (u: string | null): string | null => (u && u.startsWith('blob:') ? null : u)
export function toSaveProfileBody(p: StudioProfile): SaveStudioProfileBody {
  return {
    ...p,
    avatar: dropBlob(p.avatar),
    banner: dropBlob(p.banner),
    pressAssets: p.pressAssets.filter((a) => !a.url.startsWith('blob:')),
  }
}

// ── Studio settings ───────────────────────────────────────────────
// GET returns the full shape (Category A + B). PUT accepts ONLY the
// Category-A writable subset; Category B (email/phone/2FA/sessions/apps/
// verification/billing) has no persistence endpoint yet, so the save body
// narrows to what the backend will actually store.
export type StudioSettingsWire = StudioSettings
export function toStudioSettings(w: StudioSettingsWire): StudioSettings {
  return w
}

export interface SaveStudioSettingsBody {
  notifications: StudioSettings['notifications']
  defaults: StudioSettings['defaults']
  payouts: StudioSettings['payouts']
  privacy: StudioSettings['privacy']
  team: StudioSettings['team']
}
export function toSaveSettingsBody(s: StudioSettings): SaveStudioSettingsBody {
  return {
    notifications: s.notifications,
    defaults: s.defaults,
    payouts: s.payouts,
    privacy: s.privacy,
    team: s.team,
  }
}

// ── Studio releases ───────────────────────────────────────────────
// StudioReleaseView matches StudioRelease except revenue/price are
// MoneyView { amount(cedis), currency }; the frontend type wants plain
// cedis numbers (same precedent as settings' trackPrice), so unwrap .amount.
export interface StudioReleaseWire {
  id: string
  title: string
  type: StudioRelease['type']
  status: StudioRelease['status']
  date: string
  trackCount: number
  streams: number
  revenue: { amount: number; currency: string }
  price: { amount: number; currency: string }
}
export function toStudioRelease(w: StudioReleaseWire): StudioRelease {
  return {
    id: w.id,
    title: w.title,
    type: w.type,
    status: w.status,
    date: w.date,
    trackCount: w.trackCount,
    streams: w.streams,
    revenue: w.revenue?.amount ?? 0,
    price: w.price?.amount ?? 0,
  }
}

// ── Studio podcasts ───────────────────────────────────────────────
export interface StudioPodcastShowWire { id: string; title: string; category: string }
export function toStudioShow(w: StudioPodcastShowWire): StudioPodcastShow {
  return { id: w.id, title: w.title, category: w.category }
}

export interface EpisodeWire {
  id: string; showId: string; showTitle: string; title: string; duration: number
  status: string; premium: boolean; price: number | string; publishedAt: string; plays: number
}
export function toStudioEpisode(w: EpisodeWire): StudioEpisode {
  return {
    id: w.id, showId: w.showId, showTitle: w.showTitle, title: w.title, duration: w.duration,
    status: w.status as EpisodeStatus, premium: w.premium, price: Number(w.price),
    publishedAt: w.publishedAt, plays: w.plays,
  }
}

// ── Studio release wizard: upload-attach track ────────────────────
// UploadedTrackView from POST /studio/releases/:id/tracks. price is a
// MoneyView; the wizard's UploadedTrack wants plain cedis. status is
// narrowed to the wizard's union.
export interface UploadedTrackWire {
  id: string
  title: string
  duration: number
  status: string
  progress: number
  src: string | null
  price: { amount: number; currency: string }
  explicit: boolean
  position: number
}

export function toWizardTrack(w: UploadedTrackWire): UploadedTrack {
  const status = w.status === 'ready' || w.status === 'error' ? w.status : 'uploading'
  return {
    id: w.id,
    title: w.title,
    duration: w.duration ?? 0,
    status,
    progress: w.progress ?? 100,
    src: w.src ?? '',
    price: w.price?.amount ?? 0,
    explicit: w.explicit ?? false,
  }
}

/** Wire shape of `AppNotificationDto` served by `GET /v1/me/notifications`. */
export interface AppNotificationWire {
  id: string
  type: string
  title: string
  body: string
  time: string
  read: boolean
  to: string | null
}

export function toAppNotification(wire: AppNotificationWire): AppNotification {
  return {
    id: wire.id,
    type: wire.type as NotificationType,
    title: wire.title,
    body: wire.body,
    time: wire.time,
    read: wire.read,
    to: wire.to ?? undefined,
  }
}

// ── Studio payouts ────────────────────────────────────────────────
export interface PayoutMethodWire { id: string; label: string; detail: string; kind: string; isDefault: boolean }
export function toPayoutMethod(w: PayoutMethodWire): PayoutMethod {
  return { id: w.id, label: w.label, detail: w.detail, kind: w.kind as MethodKind, isDefault: w.isDefault }
}

export interface PayoutTxnWire {
  id: string; date: string; source: string; type: string
  gross: number | string | null; net: number | string; status: string
}
export function toPayoutTxn(w: PayoutTxnWire): PayoutTxn {
  return {
    id: w.id, date: w.date, source: w.source, type: w.type as PayoutType,
    gross: w.gross === null ? null : Number(w.gross), net: Number(w.net), status: w.status as PayoutStatus,
  }
}

export interface PayoutsWire {
  available: number | string; pending: number | string; thisMonth: number | string
  thisMonthDelta: number; lifetime: number | string; since: string
  earnings: { label: string; value: number | string }[]
  bySource: { sales: number | string; royalties: number | string; tips: number | string }
  methods: PayoutMethodWire[]; transactions: PayoutTxnWire[]
}
export function toPayouts(w: PayoutsWire): Payouts {
  return {
    available: Number(w.available), pending: Number(w.pending), thisMonth: Number(w.thisMonth),
    thisMonthDelta: w.thisMonthDelta, lifetime: Number(w.lifetime), since: w.since,
    earnings: w.earnings.map((e) => ({ label: e.label, value: Number(e.value) })),
    bySource: { sales: Number(w.bySource.sales), royalties: Number(w.bySource.royalties), tips: Number(w.bySource.tips) },
    methods: w.methods.map(toPayoutMethod),
    transactions: w.transactions.map(toPayoutTxn),
  }
}

// ── Admin users (AdminUsersResource) ──────────────────────────────────────────
export interface AdminUserRowWire {
  id: string
  name: string
  initial: string
  email: string
  role: string
  verified: boolean
  joined: string
  lastActive: string
  status: string
}
export interface UserCountsWire { all: number; fans: number; artists: number; verified: number; suspended: number }
export interface PagedUsersWire {
  items: AdminUserRowWire[]
  page: number
  size: number
  total: number
  counts: UserCountsWire
}
export interface UserActionLogWire { id: string; action: string; by: string; time: string }
export interface UserDetailWire {
  summary: AdminUserRowWire
  activity: unknown[]
  orders: unknown[]
  devices: unknown[]
  actionLog: UserActionLogWire[]
}

export interface UserCounts { all: number; fans: number; artists: number; verified: number; suspended: number }
export interface AdminUsersList { users: AdminUserRow[]; counts: UserCounts }
export interface AdminUserDetailData { summary: AdminUserRow; actionLog: UserActionLog[] }

export function toAdminUserRow(w: AdminUserRowWire, now?: number): AdminUserRow {
  return {
    id: w.id,
    name: w.name,
    initial: w.initial,
    email: w.email,
    role: w.role as UserRole,
    verified: w.verified,
    joined: monthYear(w.joined),
    lastActive: relativeTimeAgo(w.lastActive, now),
    status: w.status as UserStatus,
  }
}

export function toUserCounts(w: UserCountsWire): UserCounts {
  return { all: w.all, fans: w.fans, artists: w.artists, verified: w.verified, suspended: w.suspended }
}

export function toUsersList(w: PagedUsersWire, now?: number): AdminUsersList {
  return { users: w.items.map((r) => toAdminUserRow(r, now)), counts: toUserCounts(w.counts) }
}

export function toUserActionLog(w: UserActionLogWire, now?: number): UserActionLog {
  return { id: w.id, action: w.action, by: w.by, time: relativeTimeAgo(w.time, now) }
}

export function toUserDetail(w: UserDetailWire, now?: number): AdminUserDetailData {
  return { summary: toAdminUserRow(w.summary, now), actionLog: w.actionLog.map((l) => toUserActionLog(l, now)) }
}

// ── Admin catalog (AdminCatalogResource) ──────────────────────────────────────
export interface CatalogItemWire {
  id: string
  title: string
  note: string | null
  artist: string
  type: string
  tracks: number
  status: string
}
export interface CatalogCountsWire { pending: number; published: number; takedown: number }
export interface PagedCatalogWire {
  items: CatalogItemWire[]
  page: number
  size: number
  total: number
  counts: CatalogCountsWire
}
export interface CatalogTrackWire {
  position: number
  trackId: string
  title: string
  isrc: string | null
  durationSec: number
  priceMinor: number
}
export interface CatalogSplitWire { trackId: string; name: string; role: string; percent: number; confirmation: string }
export interface CatalogActionLogWire { id: string; action: string; by: string; time: string }
export interface CatalogDetailWire {
  id: string
  title: string
  note: string | null
  artist: string
  type: string
  status: string
  upc: string | null
  tracklist: CatalogTrackWire[]
  splits: CatalogSplitWire[]
  actionLog: CatalogActionLogWire[]
}

export interface CatalogCounts { pending: number; published: number; takedown: number }
export interface CatalogList { items: CatalogItem[]; counts: CatalogCounts }
export interface CatalogDetailTrack { position: number; title: string; isrc: string | null; duration: string }
export interface CatalogSplit { name: string; role: string; pct: number }
export interface CatalogLogEntry { id: string; action: string; time: string }
export interface CatalogDetail {
  id: string
  title: string
  note?: string
  artist: string
  type: CatalogType
  status: CatalogStatus
  upc: string | null
  tracks: CatalogDetailTrack[]
  splits: CatalogSplit[]
  log: CatalogLogEntry[]
}

/**
 * Backend serves the raw release status (`draft|in_review|scheduled|live|takedown`); the admin UI
 * speaks `pending|published|takedown`. Bucketing mirrors CatalogAdminReaderAdapter's own counts
 * query and filter switch. Note `flagged` is never produced: flagging opens a moderation case
 * rather than transitioning the release, so no release ever carries a `flagged` status.
 */
export function toCatalogStatus(wire: string): CatalogStatus {
  switch (wire) {
    case 'draft':
    case 'in_review':
      return 'pending'
    case 'scheduled':
    case 'live':
      return 'published'
    case 'takedown':
      return 'takedown'
    default:
      return 'pending'
  }
}

/**
 * Backend serves the lowercase `ReleaseType` enum name (`single|ep|album|mixtape`); the admin UI
 * renders title-case. The `'Compilation'` fallback is a deliberate diagnostic sentinel: the backend
 * has no `compilation` release type, so if this ever renders in the UI it means an unrecognised
 * `ReleaseType` value reached the mapper (as opposed to silently mislabelling it, e.g. as a Single).
 */
export function toCatalogType(wire: string): CatalogType {
  switch (wire) {
    case 'single': return 'Single'
    case 'ep': return 'EP'
    case 'album': return 'Album'
    case 'mixtape': return 'Mixtape'
    default: return 'Compilation'
  }
}

export function toCatalogItem(w: CatalogItemWire): CatalogItem {
  return {
    id: w.id,
    title: w.title,
    note: w.note ?? undefined,
    artist: w.artist,
    type: toCatalogType(w.type),
    tracks: w.tracks,
    status: toCatalogStatus(w.status),
  }
}

export function toCatalogList(w: PagedCatalogWire): CatalogList {
  return {
    items: w.items.map(toCatalogItem),
    counts: { pending: w.counts.pending, published: w.counts.published, takedown: w.counts.takedown },
  }
}

export function toCatalogDetail(w: CatalogDetailWire, now?: number): CatalogDetail {
  const seenSplits = new Set<string>()
  const splits = w.splits
    .filter((s) => {
      const k = `${s.name}|${s.role}|${s.percent}`
      if (seenSplits.has(k)) return false
      seenSplits.add(k)
      return true
    })
    .map((s) => ({ name: s.name, role: s.role, pct: s.percent }))

  return {
    id: w.id,
    title: w.title,
    note: w.note ?? undefined,
    artist: w.artist,
    type: toCatalogType(w.type),
    status: toCatalogStatus(w.status),
    upc: w.upc ?? null,
    tracks: w.tracklist.map((t) => ({ position: t.position, title: t.title, isrc: t.isrc ?? null, duration: formatDuration(t.durationSec) })),
    splits,
    log: w.actionLog.map((l) => ({ id: l.id, action: l.action, time: relativeTimeAgo(l.time, now) })),
  }
}

// ── Admin moderation (AdminModerationResource) ────────────────────────────────
export interface ModerationCaseWire {
  id: string
  item: string
  reporter: string
  reason: string
  time: string
  severity: string
  status: string
  escalated: boolean
}
export interface ModerationSummaryWire { openCount: number; slaHours: number; escalatedCount: number }
export interface ModerationQueueWire {
  items: ModerationCaseWire[]
  page: number
  size: number
  total: number
  summary: ModerationSummaryWire
}

export interface ModerationSummary { open: number; sla: number; escalated: number }
export interface ModerationQueueData { items: ModerationItem[]; summary: ModerationSummary }

export function toModerationCase(w: ModerationCaseWire, now?: number): ModerationItem {
  return {
    id: w.id,
    item: w.item,
    reporter: w.reporter,
    reason: w.reason as ModReason,
    age: relativeTime(w.time, now),
    severity: w.severity as ModSeverity,
    status: w.status as ModStatus,
  }
}

export function toModerationQueue(w: ModerationQueueWire, now?: number): ModerationQueueData {
  return {
    items: w.items.map((c) => toModerationCase(c, now)),
    summary: { open: w.summary.openCount, sla: w.summary.slaHours, escalated: w.summary.escalatedCount },
  }
}

// ── Admin finance overview (AdminFinanceOverviewResource) ─────────────────────
// Money on THIS endpoint is a bare BigDecimal of cedis (a plain JSON number), unlike the
// disputes/payouts endpoints which use the { amount, currency } MoneyView envelope.
export interface FinanceKpisWire {
  gmvMtd: number
  gmvDelta: number
  platformFee: number
  feeTakePct: number
  payoutsDue: number
  payoutsArtists: number
  momoFloat: number
}
export interface PendingPayoutSummaryWire { id: string; artist: string; amount: number; method: string; status: string }
export interface ProviderMixWire { name: string; value: number }
export interface DisputeSummaryWire {
  id: string
  kind: string
  subject: string
  detail: string
  amount: number | null
  opened: string | null
}
export interface FinanceOverviewWire {
  kpis: FinanceKpisWire
  pendingPayouts: PendingPayoutSummaryWire[]
  providerMix: ProviderMixWire[]
  disputes: DisputeSummaryWire[]
}

export function toFinanceOverview(w: FinanceOverviewWire): Finance {
  return {
    kpis: {
      gmvMtd: toCedis(w.kpis.gmvMtd),
      gmvDelta: w.kpis.gmvDelta,
      platformFee: toCedis(w.kpis.platformFee),
      feeTakePct: w.kpis.feeTakePct,
      payoutsDue: toCedis(w.kpis.payoutsDue),
      payoutsArtists: w.kpis.payoutsArtists,
      momoFloat: toCedis(w.kpis.momoFloat),
    },
    pendingPayouts: w.pendingPayouts.map(
      (p): PendingPayout => ({
        id: p.id,
        artist: p.artist,
        amount: toCedis(p.amount),
        method: p.method,
        status: p.status as PendingPayout['status'],
      }),
    ),
    providerMix: w.providerMix.map((m): ProviderMix => ({ name: m.name, value: m.value })),
    disputes: w.disputes.map(
      (d): Dispute => ({
        id: d.id,
        kind: d.kind,
        subject: d.subject,
        detail: d.detail,
        amount: d.amount == null ? undefined : toCedis(d.amount),
        opened: d.opened ? monthDay(d.opened) : undefined,
      }),
    ),
  }
}

// ── Admin finance ledger / payouts / disputes ─────────────────────────────────
/** The `MoneyView` envelope used by the disputes + payouts endpoints. */
export interface MoneyWire { amount: number; currency: string }

export interface LedgerEntryWire {
  id: string
  date: string | null
  type: string
  party: string
  ref: string
  amount: number
}
export interface LedgerPageWire { items: LedgerEntryWire[]; page: number; size: number; total: number }
export interface LedgerPage { items: LedgerTxn[]; page: number; size: number; total: number }

export function toLedgerEntry(w: LedgerEntryWire): LedgerTxn {
  return {
    id: w.id,
    // `type` arrives via LedgerType.display(), which returns the UI's exact tokens — a cast is right.
    type: w.type as LedgerType,
    party: w.party,
    ref: w.ref,
    date: w.date ? monthDay(w.date) : '',
    amount: toCedis(w.amount),
  }
}

export function toLedgerPage(w: LedgerPageWire): LedgerPage {
  return { items: w.items.map(toLedgerEntry), page: w.page, size: w.size, total: w.total }
}

export interface PendingPayoutWire {
  id: string
  artist: string
  amount: MoneyWire
  method: string
  status: string
}

export function toPendingPayout(w: PendingPayoutWire): PendingPayout {
  return {
    id: w.id,
    artist: w.artist,
    amount: toCedis(w.amount),
    method: w.method,
    status: w.status as PendingPayout['status'],
  }
}

export interface DisputeTimelineWire { id: string; text: string; time: string | null }
export interface DisputeDetailWire {
  id: string
  kind: string
  subject: string
  detail: string
  amount: MoneyWire | null
  status: string
  opened: string | null
  timeline: DisputeTimelineWire[]
}
export interface DisputeDetail {
  id: string
  kind: string
  subject: string
  detail: string
  amount?: number
  status: 'open' | 'resolved'
  /**
   * The raw, un-folded wire status (`open|refunded|rejected|escalated`). `status` above folds
   * `escalated` into `open` for the two-state pill; callers that need to tell an escalated
   * dispute apart from a plain open one (e.g. to detect a refund/reject no-op, or to hide actions
   * the server will refuse) must check THIS field instead.
   */
  wireStatus: string
  opened?: string
  timeline: TimelineEntry[]
}

/**
 * The wire carries four statuses (`open|refunded|rejected|escalated`); this screen shows two.
 * `escalated` maps to `open` on purpose — an escalated dispute is still open work, and this
 * matches the existing behaviour where Escalate does not resolve the dispute. Unknown values
 * fall back to `open` so a new backend status never reads as resolved.
 */
export function toDisputeStatus(wire: string): 'open' | 'resolved' {
  return wire === 'refunded' || wire === 'rejected' ? 'resolved' : 'open'
}

export function toDisputeDetail(w: DisputeDetailWire, now?: number): DisputeDetail {
  return {
    id: w.id,
    kind: w.kind,
    subject: w.subject,
    detail: w.detail,
    amount: w.amount == null ? undefined : toCedis(w.amount),
    status: toDisputeStatus(w.status),
    wireStatus: w.status,
    opened: w.opened ? monthDay(w.opened) : undefined,
    timeline: w.timeline.map(
      (t): TimelineEntry => ({ id: t.id, text: t.text, time: t.time ? relativeTimeAgo(t.time, now) : '' }),
    ),
  }
}
