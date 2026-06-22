/**
 * Studio analytics mock data.
 *
 * Returns a self-consistent dataset for a chosen time range so the Analytics
 * page can render without a backend. Numbers for the 28-day range match the
 * reference design; other ranges scale from that base. Swap `getAnalytics`
 * for a TanStack Query hook once the API exists.
 */

export type AnalyticsRange = '7d' | '28d' | '90d' | '12m' | 'all'
export type MetricKey = 'streams' | 'sales' | 'followers' | 'tips'

interface RangeConfig {
  key: AnalyticsRange
  label: string
  axis: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  points: number
  spanDays: number
  /** Scale relative to the 28-day base. */
  factor: number
  /** % change vs previous period per metric. */
  deltas: Record<MetricKey, number>
}

const RANGES: RangeConfig[] = [
  { key: '7d', label: 'Last 7 days', axis: 'DAILY', points: 7, spanDays: 7, factor: 0.25, deltas: { streams: 9, sales: 12, followers: 3, tips: 7 } },
  { key: '28d', label: 'Last 28 days', axis: 'DAILY', points: 28, spanDays: 28, factor: 1, deltas: { streams: 18, sales: 24, followers: 6, tips: 15 } },
  { key: '90d', label: 'Last 90 days', axis: 'WEEKLY', points: 13, spanDays: 90, factor: 3.1, deltas: { streams: 31, sales: 28, followers: 14, tips: 22 } },
  { key: '12m', label: 'Last 12 months', axis: 'MONTHLY', points: 12, spanDays: 365, factor: 11, deltas: { streams: 120, sales: 96, followers: 70, tips: 88 } },
  { key: 'all', label: 'All time', axis: 'MONTHLY', points: 24, spanDays: 730, factor: 23, deltas: { streams: 0, sales: 0, followers: 0, tips: 0 } },
]

export const ANALYTICS_RANGES = RANGES.map(({ key, label }) => ({ key, label }))

export interface CountryStat { name: string; value: number }
export interface TopTrackStat { title: string; streams: number; revenue: number }
export interface AgeBucket { label: string; value: number }
export interface SourceStat { name: string; pct: number }

export interface MetricSeries {
  total: number
  delta: number
  /** Absolute per-point values for the current period. */
  current: number[]
  /** Per-point values for the previous period (comparison line). */
  previous: number[]
}

export interface Analytics {
  rangeLabel: string
  axisLabel: string
  /** Per-point date labels aligned with each series index. */
  labels: string[]
  metrics: Record<MetricKey, MetricSeries>
  fans: number
  countries: CountryStat[]
  topTracks: TopTrackStat[]
  ages: AgeBucket[]
  revenue: { sales: number; streaming: number; tips: number }
  engagement: { completion: number; save: number; skip: number }
  sources: SourceStat[]
}

const round = (n: number) => Math.round(n)
const sum = (a: number[]) => a.reduce((s, v) => s + v, 0)

// Deterministic PRNG so the chart shape is stable across renders.
function rng(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function buildNormalized(points: number, seed: number): number[] {
  const r = rng(seed)
  const out: number[] = []
  let v = 0.32
  for (let i = 0; i < points; i++) {
    v += 0.62 / points + (r() - 0.45) * 0.06
    out.push(Math.max(0.05, v))
  }
  return out
}

function metricSeries(points: number, total: number, delta: number, seed: number): MetricSeries {
  const norm = buildNormalized(points, seed)
  const s = sum(norm)
  const current = norm.map((v) => round((v / s) * total))
  const prevFactor = delta === 0 ? 1 : 1 / (1 + delta / 100)
  const previous = current.map((v) => round(v * prevFactor))
  return { total: round(total), delta, current, previous }
}

function fmt(date: Date, monthly: boolean): string {
  return monthly
    ? date.toLocaleDateString('en-US', { month: 'short', year: '2-digit' }).toUpperCase()
    : date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }).toUpperCase()
}

function pointLabels(points: number, spanDays: number, monthly: boolean): string[] {
  const now = Date.now()
  const day = 86400000
  const out: string[] = []
  for (let i = 0; i < points; i++) {
    const t = now - spanDays * (1 - i / (points - 1)) * day
    out.push(fmt(new Date(t), monthly))
  }
  return out
}

export function getAnalytics(range: AnalyticsRange): Analytics {
  const cfg = RANGES.find((r) => r.key === range) ?? RANGES[1]
  const f = cfg.factor
  const monthly = cfg.axis === 'MONTHLY'

  return {
    rangeLabel: cfg.label,
    axisLabel: cfg.axis,
    labels: pointLabels(cfg.points, cfg.spanDays, monthly),
    metrics: {
      streams: metricSeries(cfg.points, 412_000 * f, cfg.deltas.streams, cfg.points * 97 + 1),
      sales: metricSeries(cfg.points, 18_420 * f, cfg.deltas.sales, cfg.points * 53 + 2),
      followers: metricSeries(cfg.points, 2_140 * f, cfg.deltas.followers, cfg.points * 31 + 3),
      tips: metricSeries(cfg.points, 3_260 * f, cfg.deltas.tips, cfg.points * 17 + 4),
    },
    fans: round(412 * f),
    countries: [
      { name: 'Ghana', value: round(256_000 * f) },
      { name: 'Nigeria', value: round(74_000 * f) },
      { name: 'UK', value: round(33_000 * f) },
      { name: 'USA', value: round(24_000 * f) },
      { name: 'Côte d’Ivoire', value: round(16_000 * f) },
    ],
    topTracks: [
      { title: 'Kwaku the Traveller', streams: round(142_000 * f), revenue: round(6_420 * f) },
      { title: 'Soja', streams: round(98_000 * f), revenue: round(3_890 * f) },
      { title: '45', streams: round(64_000 * f), revenue: round(2_140 * f) },
    ],
    ages: [
      { label: '<18', value: 18 },
      { label: '18–24', value: 62 },
      { label: '25–34', value: 90 },
      { label: '35–44', value: 55 },
      { label: '45–54', value: 32 },
      { label: '55+', value: 20 },
    ],
    revenue: { sales: round(18_420 * f), streaming: round(4_944 * f), tips: round(3_260 * f) },
    engagement: { completion: 78, save: 21, skip: 9 },
    sources: [
      { name: 'Search', pct: 34 },
      { name: 'Your profile', pct: 26 },
      { name: 'Playlists', pct: 22 },
      { name: 'Shared links', pct: 11 },
      { name: 'Radio', pct: 7 },
    ],
  }
}

/* ------------------------------- Audience -------------------------------- */

export type AudienceRange = '7d' | '28d' | '90d' | '12m'

export const AUDIENCE_RANGES: { key: AudienceRange; label: string }[] = [
  { key: '7d', label: 'Last 7 days' },
  { key: '28d', label: 'Last 28 days' },
  { key: '90d', label: 'Last 90 days' },
  { key: '12m', label: 'Last 12 months' },
]

export interface Superfan { handle: string; initial: string; tracks: number; tipped: number }

export interface Audience {
  rangeLabel: string
  monthlyListeners: number
  listenersDelta: number
  followers: number
  followersGained: number
  followersPeriod: string
  superfans: number
  avgSessionSec: number
  avgSessionDelta: number
  cities: CountryStat[]
  gender: { male: number; female: number; other: number }
  ages: AgeBucket[]
  superfansList: Superfan[]
}

const AUDIENCE_META: Record<AudienceRange, { label: string; listenersDelta: number; sessionDelta: number; gained: number; period: string }> = {
  '7d': { label: 'Last 7 days', listenersDelta: 5, sessionDelta: 3, gained: 540, period: 'this week' },
  '28d': { label: 'Last 28 days', listenersDelta: 18, sessionDelta: 8, gained: 2_140, period: 'this month' },
  '90d': { label: 'Last 90 days', listenersDelta: 34, sessionDelta: 12, gained: 6_800, period: 'this quarter' },
  '12m': { label: 'Last 12 months', listenersDelta: 140, sessionDelta: 20, gained: 24_000, period: 'this year' },
}

export function getAudience(range: AudienceRange): Audience {
  const m = AUDIENCE_META[range]
  return {
    rangeLabel: m.label,
    monthlyListeners: 2_400_000,
    listenersDelta: m.listenersDelta,
    followers: 412_000,
    followersGained: m.gained,
    followersPeriod: m.period,
    superfans: 8_420,
    avgSessionSec: 760,
    avgSessionDelta: m.sessionDelta,
    cities: [
      { name: 'Accra · GH', value: 182_000 },
      { name: 'Kumasi · GH', value: 88_000 },
      { name: 'Lagos · NG', value: 64_000 },
      { name: 'Tema · GH', value: 42_000 },
      { name: 'London · UK', value: 24_000 },
      { name: 'New York · US', value: 18_000 },
    ],
    gender: { male: 58, female: 40, other: 2 },
    ages: [
      { label: '<18', value: 18 },
      { label: '18–24', value: 62 },
      { label: '25–34', value: 90 },
      { label: '35–44', value: 55 },
      { label: '45–54', value: 32 },
      { label: '55+', value: 20 },
    ],
    superfansList: [
      { handle: '@ama_b', initial: 'A', tracks: 42, tipped: 86 },
      { handle: '@kwesi_', initial: 'K', tracks: 28, tipped: 54 },
      { handle: '@yaw_g', initial: 'Y', tracks: 31, tipped: 40 },
      { handle: '@esi_o', initial: 'E', tracks: 24, tipped: 32 },
      { handle: '@nana_a', initial: 'N', tracks: 22, tipped: 28 },
      { handle: '@osei_', initial: 'O', tracks: 18, tipped: 24 },
    ],
  }
}

/** Compact number formatting: 412000 → "412K", 1_240_000 → "1.2M". */
export function formatCompact(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`
  return `${n}`
}
