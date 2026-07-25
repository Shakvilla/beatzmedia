import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowUp, ArrowDown, ChevronRight } from 'lucide-react'
import { cn } from '../utils/cn'
import { ADMIN_RANGES, type AdminRange, type AttentionItem, type RevenueArtist, type PayMethod } from '../lib/admin-data'
import { overviewQuery } from '../lib/api/queries/admin-overview'
import { AdminLoadError } from '../components/admin/load-error'
import { formatCompact } from '../lib/studio-analytics'

export const Route = createFileRoute('/admin/')({
  component: AdminOverview,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedisK = (n: number) => `₵${n >= 1000 ? `${Math.round(n / 1000)}k` : n}`

function AdminOverview() {
  const [range, setRange] = useState<AdminRange>('7d')
  const { data, isLoading, isError, refetch } = useQuery(overviewQuery(range))
  const k = data?.kpis ?? {
    activeUsers: 0, streams: 0, gmv: 0, newArtists: 0, deltas: { users: 0, streams: 0, gmv: 0 },
  }

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Platform overview</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">Real-time · {data?.rangeLabel ?? ''}</span>
        </div>
        <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
          {ADMIN_RANGES.map((r) => (
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

      {isError ? (
        <AdminLoadError label="Couldn't load the overview." onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* KPIs */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <Kpi label="Active users" value={k.activeUsers.toLocaleString()} delta={k.deltas.users} />
            <Kpi label="Streams (24h)" value={formatCompact(k.streams)} delta={k.deltas.streams} />
            <Kpi label="GMV" value={cedisK(k.gmv)} delta={k.deltas.gmv} accent />
            <Kpi label="New artists" value={k.newArtists.toLocaleString()} sub="this week" />
          </div>

          {/* GMV chart + needs attention */}
          <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-6 items-start">
            <section className={cn(CARD, 'flex flex-col gap-5 min-w-0')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">GMV by day (₵)</h2>
              <GmvBars bars={data?.gmvByDay ?? []} />
            </section>

            <section className={cn(CARD, 'flex flex-col gap-2')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white mb-2">Needs attention</h2>
              {(data?.needsAttention ?? []).length === 0 ? (
                <p className="py-6 text-sm text-gray-400 dark:text-gray-500">Nothing needs attention.</p>
              ) : (
                (data?.needsAttention ?? []).map((a) => <AttentionRow key={a.id} item={a} />)
              )}
            </section>
          </div>

          {/* Top artists + payment methods */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <section className={cn(CARD, 'flex flex-col gap-4')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top artists by revenue</h2>
              <div className="flex flex-col">
                {(data?.topArtists ?? []).length === 0 ? (
                  <p className="py-6 text-sm text-gray-400 dark:text-gray-500">No artist revenue yet.</p>
                ) : (
                  (data?.topArtists ?? []).map((a, i) => <ArtistRow key={a.name} rank={i + 1} artist={a} />)
                )}
              </div>
            </section>

            <section className={cn(CARD, 'flex flex-col gap-4')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Payment methods (today)</h2>
              <PaymentBars methods={data?.paymentMethods ?? []} />
            </section>
          </div>
        </>
      )}
    </div>
  )
}

function Kpi({ label, value, delta, sub, accent }: { label: string; value: string; delta?: number; sub?: string; accent?: boolean }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-2')}>
      <span className={LABEL}>{label}</span>
      <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      {delta != null ? (
        <span className={cn('flex items-center gap-1 text-xs font-bold', delta < 0 ? 'text-beatz-red' : 'text-beatz-green')}>
          {delta < 0 ? <ArrowDown size={12} /> : <ArrowUp size={12} />} {delta}%
        </span>
      ) : (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
    </div>
  )
}

function GmvBars({ bars }: { bars: number[] }) {
  const [hover, setHover] = useState<number | null>(null)
  // Values are raw cedis; scale against the window max so heights stay comparable.
  // Guard the empty/all-zero case — Math.max(...[]) is -Infinity and would yield NaN heights.
  const max = bars.length > 0 ? Math.max(...bars) : 0
  if (bars.length === 0) {
    return <div className="h-56 flex items-center justify-center text-sm text-gray-400 dark:text-gray-500">No sales in this range.</div>
  }
  return (
    <div className="flex items-end gap-1.5 h-56">
      {bars.map((b, i) => (
        <div key={i} className="flex-1 h-full flex items-end relative" onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
          {hover === i && (
            <span className="absolute -top-1 left-1/2 -translate-x-1/2 text-[10px] font-bold text-beatz-dark-bg dark:text-white whitespace-nowrap">
              {cedisK(Math.round(b))}
            </span>
          )}
          <div className={cn('w-full rounded-t-md transition-colors', hover === i ? 'bg-beatz-green' : 'bg-beatz-green/75')} style={{ height: `${max > 0 ? (b / max) * 100 : 0}%` }} />
        </div>
      ))}
    </div>
  )
}

function AttentionRow({ item }: { item: AttentionItem }) {
  const navigate = useNavigate()
  return (
    <button
      onClick={() => navigate({ to: item.to })}
      className="flex items-center gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 text-left group"
    >
      <span className="w-1.5 h-1.5 rounded-full bg-beatz-red shrink-0" />
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{item.label}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400">{item.sub}</span>
      </div>
      <ChevronRight size={16} className="text-gray-400 group-hover:text-beatz-green transition-colors shrink-0" />
    </button>
  )
}

function ArtistRow({ rank, artist }: { rank: number; artist: RevenueArtist }) {
  return (
    <div className="flex items-center gap-4 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-4 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{rank}</span>
      <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{artist.name}</span>
      <span className="text-sm font-mono font-bold text-beatz-green shrink-0">₵{Math.round(artist.revenue / 1000)}K</span>
    </div>
  )
}

function PaymentBars({ methods }: { methods: PayMethod[] }) {
  if (methods.length === 0) {
    return <p className="py-6 text-sm text-gray-400 dark:text-gray-500">No payment-method data yet.</p>
  }
  const max = Math.max(...methods.map((m) => m.value))
  return (
    <div className="flex flex-col gap-3.5">
      {methods.map((m, i) => (
        <div key={m.name} className="flex items-center gap-3">
          <span className="w-28 text-sm text-beatz-dark-bg dark:text-white truncate shrink-0">{m.name}</span>
          <div className="flex-1 h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full', i === 0 ? 'bg-beatz-green' : 'bg-gray-400 dark:bg-white/30')} style={{ width: `${(m.value / max) * 100}%` }} />
          </div>
          <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">₵{Math.round(m.value / 1000)}K</span>
        </div>
      ))}
    </div>
  )
}
