import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowUp, ArrowDown, Download } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import {
  getAnalytics, formatCompact, ANALYTICS_RANGES,
  type AnalyticsRange, type MetricKey, type CountryStat, type TopTrackStat, type AgeBucket, type SourceStat,
} from '../lib/studio-analytics'

export const Route = createFileRoute('/studio/analytics')({
  component: AnalyticsComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis = (n: number) => `₵${n.toLocaleString('en-US')}`

const METRICS: { key: MetricKey; label: string; fmt: (n: number) => string }[] = [
  { key: 'streams', label: 'Streams', fmt: formatCompact },
  { key: 'sales', label: 'Track sales', fmt: cedis },
  { key: 'followers', label: 'New followers', fmt: (n) => n.toLocaleString('en-US') },
  { key: 'tips', label: 'Tips received', fmt: cedis },
]

function AnalyticsComponent() {
  const { toast } = useToast()
  const [range, setRange] = useState<AnalyticsRange>('28d')
  const [metric, setMetric] = useState<MetricKey>('streams')
  const [showPrev, setShowPrev] = useState(true)
  const [exportOpen, setExportOpen] = useState(false)
  const data = useMemo(() => getAnalytics(range), [range])

  const active = data.metrics[metric]
  const metricMeta = METRICS.find((m) => m.key === metric)!

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Analytics</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">{data.rangeLabel} · updated just now</span>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
            {ANALYTICS_RANGES.map((r) => (
              <button
                key={r.key}
                onClick={() => setRange(r.key)}
                className={cn('h-8 px-3.5 rounded-full text-sm font-bold transition-colors',
                  range === r.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}
              >
                {r.key === '12m' ? '12m' : r.key === 'all' ? 'All' : r.key}
              </button>
            ))}
          </div>
          <div className="relative">
            <button
              onClick={() => setExportOpen((o) => !o)}
              className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
            >
              <Download size={16} /> Export
            </button>
            {exportOpen && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setExportOpen(false)} />
                <div className="absolute right-0 top-12 z-50 w-40 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                  {['CSV', 'PDF report'].map((f) => (
                    <button key={f} onClick={() => { setExportOpen(false); toast(`Exporting ${data.rangeLabel.toLowerCase()} as ${f}`, 'success') }}
                      className="w-full text-left px-3 py-2 text-sm font-medium text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
                      {f}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* KPI cards — also the chart's metric selector */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {METRICS.map((m) => {
          const series = data.metrics[m.key]
          return (
            <button
              key={m.key}
              onClick={() => setMetric(m.key)}
              className={cn(CARD, 'text-left transition-all hover:shadow-md', metric === m.key && 'ring-2 ring-beatz-green border-transparent')}
            >
              <div className="flex flex-col gap-2">
                <span className={LABEL}>{m.label}</span>
                <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', metric === m.key ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>
                  {m.fmt(series.total)}
                </span>
                <DeltaBadge delta={series.delta} sub={m.key === 'tips' ? `from ${data.fans.toLocaleString()} fans` : 'vs prev'} />
              </div>
            </button>
          )
        })}
      </div>

      {/* Main chart + top countries */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
        <section className={cn(CARD, 'flex flex-col gap-5 min-w-0')}>
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">{metricMeta.label} over time</h2>
              <span className={LABEL}>{data.axisLabel}</span>
            </div>
            <button
              onClick={() => setShowPrev((v) => !v)}
              className={cn('flex items-center gap-2 text-xs font-bold transition-colors',
                showPrev ? 'text-beatz-dark-bg dark:text-white' : 'text-gray-400')}
            >
              <span className={cn('w-6 h-3.5 rounded-full transition-colors relative', showPrev ? 'bg-beatz-green' : 'bg-gray-300 dark:bg-white/20')}>
                <span className={cn('absolute top-0.5 w-2.5 h-2.5 rounded-full bg-white transition-all', showPrev ? 'left-3' : 'left-0.5')} />
              </span>
              Compare vs previous
            </button>
          </div>
          <MetricChart labels={data.labels} current={active.current} previous={active.previous} showPrev={showPrev} format={metricMeta.fmt} />
        </section>

        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top countries</h2>
          <CountryBars countries={data.countries} />
        </section>
      </div>

      {/* Revenue / sources / engagement */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Revenue breakdown</h2>
          <RevenueBreakdown revenue={data.revenue} />
        </section>

        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Traffic sources</h2>
          <SourceBars sources={data.sources} />
        </section>

        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Engagement</h2>
          <Engagement engagement={data.engagement} />
        </section>
      </div>

      {/* Top tracks + ages */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top tracks</h2>
          <div className="flex flex-col">
            {data.topTracks.map((t, i) => <TopTrackRow key={t.title} rank={i + 1} track={t} />)}
          </div>
        </section>

        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Listeners by age</h2>
          <AgeBars ages={data.ages} />
        </section>
      </div>
    </div>
  )
}

function DeltaBadge({ delta, sub }: { delta: number; sub: string }) {
  if (delta === 0) return <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
  const up = delta > 0
  return (
    <span className={cn('flex items-center gap-1 text-xs font-bold', up ? 'text-beatz-green' : 'text-beatz-red')}>
      {up ? <ArrowUp size={13} /> : <ArrowDown size={13} />}{Math.abs(delta)}%
      <span className="font-medium text-gray-400 dark:text-gray-500">{sub}</span>
    </span>
  )
}

function MetricChart({ labels, current, previous, showPrev, format }: {
  labels: string[]; current: number[]; previous: number[]; showPrev: boolean; format: (n: number) => string
}) {
  const [hover, setHover] = useState<number | null>(null)
  const n = current.length
  const w = 600, h = 240, padTop = 18, padBot = 18
  const pool = showPrev ? [...current, ...previous] : current
  const max = Math.max(...pool), min = Math.min(...pool)
  const span = max - min || 1
  const toXY = (arr: number[]) => arr.map((v, i) => {
    const x = (i / (n - 1)) * w
    const y = padTop + (1 - (v - min) / span) * (h - padTop - padBot)
    return [x, y] as const
  })
  const cur = toXY(current)
  const prev = toXY(previous)
  const path = (pts: readonly (readonly [number, number])[]) => pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  const area = `${path(cur)} L ${w} ${h} L 0 ${h} Z`
  const hx = hover != null ? (hover / (n - 1)) * w : 0
  const leftPct = hover != null ? (hover / (n - 1)) * 100 : 0

  return (
    <div className="relative">
      <svg
        viewBox={`0 0 ${w} ${h}`}
        preserveAspectRatio="none"
        className="w-full h-60 overflow-visible cursor-crosshair"
        onMouseLeave={() => setHover(null)}
        onMouseMove={(e) => {
          const r = e.currentTarget.getBoundingClientRect()
          const idx = Math.round(((e.clientX - r.left) / r.width) * (n - 1))
          setHover(Math.max(0, Math.min(n - 1, idx)))
        }}
      >
        <defs>
          <linearGradient id="streamFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#1ED760" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#1ED760" stopOpacity="0" />
          </linearGradient>
        </defs>

        <line x1="0" y1={h - 1} x2={w} y2={h - 1} stroke="currentColor" strokeDasharray="3 6" className="text-gray-300 dark:text-white/15" vectorEffect="non-scaling-stroke" />
        <path d={area} fill="url(#streamFill)" />
        {showPrev && (
          <polyline points={prev.map((p) => p.join(',')).join(' ')} fill="none" stroke="currentColor" strokeWidth={1.5} strokeDasharray="4 5" className="text-gray-400 dark:text-white/30" vectorEffect="non-scaling-stroke" />
        )}
        <polyline points={cur.map((p) => p.join(',')).join(' ')} fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" className="text-beatz-green" vectorEffect="non-scaling-stroke" />

        {hover != null && (
          <>
            <line x1={hx} y1={padTop} x2={hx} y2={h} stroke="currentColor" strokeWidth={1} className="text-gray-300 dark:text-white/20" vectorEffect="non-scaling-stroke" />
            <circle cx={cur[hover][0]} cy={cur[hover][1]} r={4} className="fill-beatz-green" vectorEffect="non-scaling-stroke" />
          </>
        )}
      </svg>

      {hover != null && (
        <div
          className="pointer-events-none absolute -top-1 z-10 -translate-x-1/2 whitespace-nowrap rounded-lg bg-beatz-dark-bg dark:bg-white px-2.5 py-1.5 shadow-lg"
          style={{ left: `${leftPct}%` }}
        >
          <div className="text-[10px] font-mono uppercase tracking-wider text-white/60 dark:text-black/50">{labels[hover]}</div>
          <div className="text-xs font-bold text-white dark:text-black">{format(current[hover])}</div>
          {showPrev && <div className="text-[10px] text-white/50 dark:text-black/40">prev {format(previous[hover])}</div>}
        </div>
      )}

      <div className="flex items-center justify-between text-[11px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 mt-2">
        <span>{labels[0]}</span>
        <span>{labels[Math.floor((n - 1) / 2)]}</span>
        <span>{labels[n - 1]}</span>
      </div>
    </div>
  )
}

function CountryBars({ countries }: { countries: CountryStat[] }) {
  const max = Math.max(...countries.map((c) => c.value))
  return (
    <div className="flex flex-col gap-3.5">
      {countries.map((c, i) => (
        <div key={c.name} className="flex items-center gap-3 group">
          <span className="w-24 text-sm text-beatz-dark-bg dark:text-white truncate shrink-0">{c.name}</span>
          <div className="flex-1 h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full transition-all group-hover:opacity-80', i === 0 ? 'bg-beatz-green' : 'bg-gray-400 dark:bg-white/30')} style={{ width: `${(c.value / max) * 100}%` }} />
          </div>
          <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(c.value)}</span>
        </div>
      ))}
    </div>
  )
}

function RevenueBreakdown({ revenue }: { revenue: { sales: number; streaming: number; tips: number } }) {
  const total = revenue.sales + revenue.streaming + revenue.tips
  const segs = [
    { label: 'Track sales', value: revenue.sales, cls: 'bg-beatz-green' },
    { label: 'Streaming', value: revenue.streaming, cls: 'bg-beatz-green/50' },
    { label: 'Tips', value: revenue.tips, cls: 'bg-[#f6c644]' },
  ]
  return (
    <div className="flex flex-col gap-5">
      <div>
        <span className="text-3xl font-bold text-beatz-dark-bg dark:text-white">{cedis(total)}</span>
        <span className="text-xs text-gray-400 dark:text-gray-500 ml-2">total earned</span>
      </div>
      <div className="flex w-full h-3 rounded-full overflow-hidden">
        {segs.map((s) => <div key={s.label} className={s.cls} style={{ width: `${(s.value / total) * 100}%` }} />)}
      </div>
      <div className="flex flex-col gap-2.5">
        {segs.map((s) => (
          <div key={s.label} className="flex items-center gap-2 text-sm">
            <span className={cn('w-2.5 h-2.5 rounded-full', s.cls)} />
            <span className="flex-1 text-gray-600 dark:text-gray-300">{s.label}</span>
            <span className="font-mono font-bold text-beatz-dark-bg dark:text-white">{cedis(s.value)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function SourceBars({ sources }: { sources: SourceStat[] }) {
  const max = Math.max(...sources.map((s) => s.pct))
  return (
    <div className="flex flex-col gap-3">
      {sources.map((s) => (
        <div key={s.name} className="flex items-center gap-3">
          <span className="w-24 text-sm text-beatz-dark-bg dark:text-white truncate shrink-0">{s.name}</span>
          <div className="flex-1 h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
            <div className="h-full rounded-full bg-beatz-green" style={{ width: `${(s.pct / max) * 100}%` }} />
          </div>
          <span className="w-10 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{s.pct}%</span>
        </div>
      ))}
    </div>
  )
}

function Engagement({ engagement }: { engagement: { completion: number; save: number; skip: number } }) {
  const rows = [
    { label: 'Completion rate', value: engagement.completion, cls: 'bg-beatz-green' },
    { label: 'Save rate', value: engagement.save, cls: 'bg-beatz-green/60' },
    { label: 'Skip rate', value: engagement.skip, cls: 'bg-gray-400 dark:bg-white/30' },
  ]
  return (
    <div className="flex flex-col gap-4">
      {rows.map((r) => (
        <div key={r.label} className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-600 dark:text-gray-300">{r.label}</span>
            <span className="font-bold text-beatz-dark-bg dark:text-white">{r.value}%</span>
          </div>
          <div className="h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full', r.cls)} style={{ width: `${r.value}%` }} />
          </div>
        </div>
      ))}
    </div>
  )
}

function TopTrackRow({ rank, track }: { rank: number; track: TopTrackStat }) {
  return (
    <div className="flex items-center gap-4 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-4 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{rank}</span>
      <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{track.title}</span>
      <span className="w-20 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(track.streams)}</span>
      <span className="w-20 text-right text-sm font-mono font-bold text-beatz-green shrink-0">{cedis(track.revenue)}</span>
    </div>
  )
}

function AgeBars({ ages }: { ages: AgeBucket[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(...ages.map((a) => a.value))
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-3 h-40">
        {ages.map((a, i) => (
          <div
            key={a.label}
            className="flex-1 h-full flex items-end relative"
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover(null)}
          >
            {hover === i && (
              <span className="absolute -top-1 left-1/2 -translate-x-1/2 text-[11px] font-bold text-beatz-dark-bg dark:text-white">{a.value}%</span>
            )}
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
