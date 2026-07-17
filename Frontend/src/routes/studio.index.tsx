import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import {
  Plus, ArrowUp, ArrowRight, ArrowUpRight, Wallet, Headphones, Users,
  Tag, Heart, Music2, CalendarDays, BadgeCheck, Disc3, type LucideIcon,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { getAnalytics, getAudience, formatCompact } from '../lib/studio-analytics'
import { getPayouts, type PayoutTxn } from '../lib/studio-payouts'
import { studioArtist } from '../lib/studio-data'
import { useStudio } from '../features/studio/studio-context'
import { studioProfileQuery, studioSettingsQuery } from '../lib/api/queries/studio'

export const Route = createFileRoute('/studio/')({
  loader: ({ context: { queryClient } }) => Promise.all([
    queryClient.ensureQueryData(studioProfileQuery()),
    queryClient.ensureQueryData(studioSettingsQuery()),
  ]),
  component: OverviewComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis0 = (n: number) => `₵${n.toLocaleString('en-US')}`
const cedis2 = (n: number) => `₵${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

function OverviewComponent() {
  const navigate = useNavigate()
  const analytics = getAnalytics('28d')
  const audience = getAudience('28d')
  const payoutStats = getPayouts()
  const { balance, transactions, releases } = useStudio()
  const { data: profile } = useSuspenseQuery(studioProfileQuery())
  const { data: settings } = useSuspenseQuery(studioSettingsQuery())
  const firstName = (profile.displayName.trim() || studioArtist.name).split(' ')[0]

  const inReview = releases.find((r) => r.status === 'in_review')
  const drafts = releases.filter((r) => r.status === 'draft')
  const todos = [
    releases.length === 0 && { label: 'Drop your first release', desc: 'Upload a single, EP or album', to: '/studio/release/new/details' as const },
    inReview && { label: `“${inReview.title}” is in review`, desc: 'We’ll email you when it’s approved', to: '/studio/releases' as const },
    drafts.length > 0 && { label: `Finish ${drafts.length} draft${drafts.length > 1 ? 's' : ''}`, desc: 'Pick up where you left off', to: '/studio/releases' as const },
    !settings.verification.rights && { label: 'Verify rights & ownership', desc: 'Required before payouts clear', to: '/studio/settings' as const },
    !profile.featuredTrackId && { label: 'Pin a track to your profile', desc: 'Show fans your best work first', to: '/studio/profile' as const },
    { label: 'Schedule your next release', desc: 'Keep the momentum going', to: '/studio/release/new/details' as const },
  ].filter(Boolean).slice(0, 4) as { label: string; desc: string; to: string }[]

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">{greeting()}, {firstName}</h1>
          <span className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-300">
            Here's how {profile.displayName.trim() || studioArtist.name} is doing this month
            {studioArtist.verified && <span className="flex items-center gap-1 text-beatz-green font-bold"><BadgeCheck size={14} /> Verified</span>}
          </span>
        </div>
        <Link to="/studio/release/new/details" className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
          <Plus size={18} /> New release
        </Link>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat label="This month" value={cedis0(payoutStats.thisMonth)} delta={payoutStats.thisMonthDelta} onClick={() => navigate({ to: '/studio/payouts' })} accent />
        <Stat label="Streams" value={formatCompact(analytics.metrics.streams.total)} delta={analytics.metrics.streams.delta} icon={Headphones} onClick={() => navigate({ to: '/studio/analytics' })} />
        <Stat label="Monthly listeners" value={formatCompact(audience.monthlyListeners)} delta={audience.listenersDelta} icon={Users} onClick={() => navigate({ to: '/studio/audience' })} />
        <Stat label="Available balance" value={cedis2(balance)} icon={Wallet} sub="Withdraw anytime" onClick={() => navigate({ to: '/studio/payouts' })} />
      </div>

      {/* Main grid */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-6 items-start">
        {/* Left */}
        <div className="flex flex-col gap-6 min-w-0">
          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <div className="flex items-center justify-between">
              <div className="flex items-baseline gap-3">
                <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Streams</h2>
                <span className="flex items-center gap-1 text-xs font-bold text-beatz-green"><ArrowUp size={12} /> {analytics.metrics.streams.delta}%</span>
              </div>
              <Link to="/studio/analytics" className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green flex items-center gap-1 transition-colors">
                Analytics <ArrowRight size={13} />
              </Link>
            </div>
            <Sparkline series={analytics.metrics.streams.current} />
            <div className="flex items-center justify-between text-[11px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">
              <span>{analytics.labels[0]}</span>
              <span>{analytics.labels[analytics.labels.length - 1]}</span>
            </div>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Recent activity</h2>
              <Link to="/studio/payouts" className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green flex items-center gap-1 transition-colors">All <ArrowRight size={13} /></Link>
            </div>
            <div className="flex flex-col">
              {transactions.slice(0, 5).map((t) => <ActivityRow key={t.id} txn={t} />)}
            </div>
          </section>
        </div>

        {/* Right */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Needs attention</h2>
            <div className="flex flex-col gap-2">
              {todos.map((t) => (
                <Link key={t.label} to={t.to} className="flex items-center gap-3 p-3 rounded-xl hover:bg-gray-50 dark:hover:bg-white/5 transition-colors group">
                  <span className="w-2 h-2 rounded-full bg-[#f6c644] shrink-0" />
                  <div className="flex flex-col flex-1 min-w-0">
                    <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{t.label}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">{t.desc}</span>
                  </div>
                  <ArrowUpRight size={16} className="text-gray-400 group-hover:text-beatz-green transition-colors shrink-0" />
                </Link>
              ))}
            </div>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top tracks</h2>
              <Link to="/studio/analytics" className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green flex items-center gap-1 transition-colors">More <ArrowRight size={13} /></Link>
            </div>
            <div className="flex flex-col">
              {analytics.topTracks.map((t, i) => (
                <div key={t.title} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                  <span className="w-4 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{i + 1}</span>
                  <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.title}</span>
                  <span className="text-xs font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(t.streams)}</span>
                  <span className="w-16 text-right text-sm font-mono font-bold text-beatz-green shrink-0">{cedis0(t.revenue)}</span>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>

      {/* Bottom: audience + shows */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Audience</h2>
            <Link to="/studio/audience" className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green flex items-center gap-1 transition-colors">Details <ArrowRight size={13} /></Link>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <MiniStat label="Followers" value={formatCompact(audience.followers)} />
            <MiniStat label="Superfans" value={audience.superfans.toLocaleString()} accent />
            <MiniStat label="Top city" value={audience.cities[0].name.split(' ·')[0]} />
          </div>
          <div className="flex flex-col gap-2.5">
            {audience.cities.slice(0, 3).map((c, i) => {
              const max = audience.cities[0].value
              return (
                <div key={c.name} className="flex items-center gap-3">
                  <span className="w-24 text-sm text-beatz-dark-bg dark:text-white truncate shrink-0">{c.name}</span>
                  <div className="flex-1 h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
                    <div className={cn('h-full rounded-full', i === 0 ? 'bg-beatz-green' : 'bg-gray-400 dark:bg-white/30')} style={{ width: `${(c.value / max) * 100}%` }} />
                  </div>
                  <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(c.value)}</span>
                </div>
              )
            })}
          </div>
        </section>

        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Upcoming shows</h2>
            <Link to="/studio/profile" className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green flex items-center gap-1 transition-colors">Manage <ArrowRight size={13} /></Link>
          </div>
          {profile.shows.length === 0 ? (
            <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">No shows scheduled.</div>
          ) : (
            <div className="flex flex-col">
              {profile.shows.map((sh) => (
                <div key={sh.id} className="flex items-center gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                  <span className="w-10 h-10 rounded-lg bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><CalendarDays size={17} /></span>
                  <div className="flex flex-col flex-1 min-w-0">
                    <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{sh.venue}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">{[sh.date, sh.city].filter(Boolean).join(' · ')}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function Stat({ label, value, delta, sub, icon: Icon, accent, onClick }: {
  label: string; value: string; delta?: number; sub?: string; icon?: LucideIcon; accent?: boolean; onClick: () => void
}) {
  return (
    <button onClick={onClick} className={cn(CARD, 'text-left transition-all hover:shadow-md hover:-translate-y-0.5 group')}>
      <div className="flex flex-col gap-2">
        <span className="flex items-center justify-between">
          <span className={LABEL}>{label}</span>
          {Icon && <Icon size={15} className="text-gray-400 dark:text-gray-500 group-hover:text-beatz-green transition-colors" />}
        </span>
        <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
        {delta != null ? (
          <span className="flex items-center gap-1 text-xs font-bold text-beatz-green"><ArrowUp size={12} /> {delta}%</span>
        ) : (
          <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
        )}
      </div>
    </button>
  )
}

function MiniStat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className={cn('text-xl font-bold tracking-tight truncate', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400 dark:text-gray-500">{label}</span>
    </div>
  )
}

function Sparkline({ series }: { series: number[] }) {
  const w = 600, h = 120, padTop = 10, padBot = 10
  const max = Math.max(...series), min = Math.min(...series)
  const span = max - min || 1
  const pts = series.map((v, i) => {
    const x = (i / (series.length - 1)) * w
    const y = padTop + (1 - (v - min) / span) * (h - padTop - padBot)
    return [x, y] as const
  })
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  const area = `${line} L ${w} ${h} L 0 ${h} Z`
  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="w-full h-32 overflow-visible">
      <defs>
        <linearGradient id="ovFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#1ED760" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#1ED760" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#ovFill)" />
      <path d={line} fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" className="text-beatz-green" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

const ACTIVITY_ICON: Record<PayoutTxn['type'], LucideIcon> = {
  Sale: Tag,
  Tip: Heart,
  Royalty: Music2,
  'Cash-out': Wallet,
}

function ActivityRow({ txn }: { txn: PayoutTxn }) {
  const Icon = ACTIVITY_ICON[txn.type] ?? Disc3
  const negative = txn.net < 0
  return (
    <div className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Icon size={15} /></span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{txn.source}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400">{txn.date} · {txn.type}</span>
      </div>
      <span className={cn('text-sm font-mono font-bold shrink-0', negative ? 'text-gray-500 dark:text-gray-300' : 'text-beatz-green')}>
        {negative ? `−${cedis2(txn.net)}` : `+${cedis2(txn.net)}`}
      </span>
    </div>
  )
}
