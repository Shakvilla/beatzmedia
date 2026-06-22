import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowUp, Heart } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import {
  getAudience, formatCompact, AUDIENCE_RANGES,
  type AudienceRange, type CountryStat, type AgeBucket, type Superfan,
} from '../lib/studio-analytics'

export const Route = createFileRoute('/studio/audience')({
  component: AudienceComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function formatSession(sec: number): string {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}m ${s}s`
}

function AudienceComponent() {
  const { toast } = useToast()
  const [range, setRange] = useState<AudienceRange>('28d')
  const data = useMemo(() => getAudience(range), [range])

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Audience</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">
            {formatCompact(data.monthlyListeners)} monthly listeners · {formatCompact(data.followers)} followers
          </span>
        </div>
        <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
          {AUDIENCE_RANGES.map((r) => (
            <button
              key={r.key}
              onClick={() => setRange(r.key)}
              className={cn('h-8 px-3.5 rounded-full text-sm font-bold transition-colors',
                range === r.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}
            >
              {r.key}
            </button>
          ))}
        </div>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Kpi label="Monthly listeners" value={formatCompact(data.monthlyListeners)} delta={data.listenersDelta} />
        <Kpi label="Followers" value={formatCompact(data.followers)} sub={`+ ${data.followersGained.toLocaleString()} ${data.followersPeriod}`} />
        <Kpi label="Superfans" value={data.superfans.toLocaleString()} sub="bought 3+ tracks" accent />
        <Kpi label="Avg session" value={formatSession(data.avgSessionSec)} delta={data.avgSessionDelta} />
      </div>

      {/* Cities + gender/age */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top cities</h2>
          <CityBars cities={data.cities} />
        </section>

        <section className={cn(CARD, 'flex flex-col gap-7')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Listener gender · age</h2>
          <div className="flex flex-col gap-3">
            <span className={LABEL}>By gender</span>
            <GenderBar gender={data.gender} />
          </div>
          <div className="flex flex-col gap-3">
            <span className={LABEL}>By age</span>
            <AgeBars ages={data.ages} />
          </div>
        </section>
      </div>

      {/* Superfans */}
      <section className={cn(CARD, 'flex flex-col gap-6')}>
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top superfans</h2>
          <button
            onClick={() => toast('Thank-you sent to your top superfans 💚', 'success')}
            className="h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-xs font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors"
          >
            <Heart size={14} /> Send thank-you
          </button>
        </div>
        <div className="grid grid-cols-3 md:grid-cols-6 gap-4">
          {data.superfansList.map((f) => <SuperfanCard key={f.handle} fan={f} />)}
        </div>
      </section>
    </div>
  )
}

function Kpi({ label, value, delta, sub, accent }: { label: string; value: string; delta?: number; sub?: string; accent?: boolean }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-2')}>
      <span className={LABEL}>{label}</span>
      <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      {delta != null ? (
        <span className="flex items-center gap-1 text-xs font-bold text-beatz-green"><ArrowUp size={13} /> {delta}%</span>
      ) : (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
    </div>
  )
}

function CityBars({ cities }: { cities: CountryStat[] }) {
  const max = Math.max(...cities.map((c) => c.value))
  return (
    <div className="flex flex-col gap-3.5">
      {cities.map((c, i) => (
        <div key={c.name} className="flex items-center gap-3 group">
          <span className="w-28 text-sm text-beatz-dark-bg dark:text-white truncate shrink-0">{c.name}</span>
          <div className="flex-1 h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full transition-all group-hover:opacity-80', i === 0 ? 'bg-beatz-green' : 'bg-gray-400 dark:bg-white/30')} style={{ width: `${(c.value / max) * 100}%` }} />
          </div>
          <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(c.value)}</span>
        </div>
      ))}
    </div>
  )
}

function GenderBar({ gender }: { gender: { male: number; female: number; other: number } }) {
  const segs = [
    { key: 'male', label: '♂', pct: gender.male, cls: 'bg-beatz-green' },
    { key: 'female', label: '♀', pct: gender.female, cls: 'bg-gray-700 dark:bg-white/40' },
    { key: 'other', label: '·', pct: gender.other, cls: 'bg-gray-400 dark:bg-white/20' },
  ]
  return (
    <div className="flex flex-col gap-3">
      <div className="flex w-full h-4 rounded-full overflow-hidden gap-0.5">
        {segs.map((s) => <div key={s.key} className={s.cls} style={{ width: `${s.pct}%` }} />)}
      </div>
      <div className="flex items-center justify-between text-sm">
        {segs.map((s) => (
          <span key={s.key} className="flex items-center gap-1.5">
            <span className={cn('w-2.5 h-2.5 rounded-full', s.cls)} />
            <span className="text-gray-600 dark:text-gray-300">{s.label} {s.pct}%</span>
          </span>
        ))}
      </div>
    </div>
  )
}

function AgeBars({ ages }: { ages: AgeBucket[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(...ages.map((a) => a.value))
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-3 h-32">
        {ages.map((a, i) => (
          <div key={a.label} className="flex-1 h-full flex items-end relative" onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
            {hover === i && <span className="absolute -top-1 left-1/2 -translate-x-1/2 text-[11px] font-bold text-beatz-dark-bg dark:text-white">{a.value}%</span>}
            <div className={cn('w-full rounded-t-md transition-colors', hover === i ? 'bg-beatz-green' : 'bg-beatz-green/80')} style={{ height: `${(a.value / max) * 100}%` }} />
          </div>
        ))}
      </div>
      <div className="flex gap-3">
        {ages.map((a) => <span key={a.label} className="flex-1 text-center text-[11px] font-mono text-gray-400 dark:text-gray-500">{a.label}</span>)}
      </div>
    </div>
  )
}

function SuperfanCard({ fan }: { fan: Superfan }) {
  return (
    <div className="flex flex-col items-center text-center gap-2">
      <div className="w-14 h-14 rounded-full border border-gray-300 dark:border-white/20 bg-beatz-light-surface-2 dark:bg-white/5 flex items-center justify-center text-base font-bold text-gray-600 dark:text-gray-200">
        {fan.initial}
      </div>
      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate max-w-full">{fan.handle}</span>
      <span className="text-[10px] font-mono uppercase tracking-wide text-gray-400 dark:text-gray-500">
        {fan.tracks} tracks · ₵{fan.tipped}
      </span>
    </div>
  )
}
