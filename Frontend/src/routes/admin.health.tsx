import { createFileRoute } from '@tanstack/react-router'
import { useMemo } from 'react'
import { cn } from '../utils/cn'
import { getHealth, type HealthMetric, type Incident } from '../lib/admin-data'

export const Route = createFileRoute('/admin/health')({
  component: AdminHealth,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function AdminHealth() {
  const data = useMemo(() => getHealth(), [])
  const normal = data.status === 'normal'

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">System health</h1>
        <span className={cn('inline-flex items-center gap-2 h-9 px-4 rounded-full text-sm font-bold',
          normal ? 'bg-beatz-green/15 text-beatz-green' : 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]')}>
          <span className={cn('w-2 h-2 rounded-full', normal ? 'bg-beatz-green' : 'bg-[#f6c644]')} />
          {normal ? 'All systems normal' : 'Degraded'}
        </span>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {data.metrics.map((m) => <Metric key={m.label} metric={m} />)}
      </div>

      {/* Chart + incidents */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-6 items-start">
        <section className={cn(CARD, 'flex flex-col gap-5 min-w-0')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Concurrent listeners (24h)</h2>
          <ListenersChart series={data.listeners} />
          <div className="flex items-center justify-between text-[11px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">
            <span>00:00</span><span>12:00</span><span>Now</span>
          </div>
        </section>

        <section className={cn(CARD, 'flex flex-col gap-2')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white mb-2">Recent incidents</h2>
          {data.incidents.map((i) => <IncidentRow key={i.id} incident={i} />)}
        </section>
      </div>
    </div>
  )
}

function Metric({ metric: m }: { metric: HealthMetric }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-2')}>
      <span className={LABEL}>{m.label}</span>
      <span className="text-2xl lg:text-3xl font-bold tracking-tight text-beatz-green">{m.value}</span>
      <span className="text-xs text-gray-400 dark:text-gray-500">{m.sub}</span>
    </div>
  )
}

function ListenersChart({ series }: { series: number[] }) {
  const w = 600, h = 220, padTop = 12, padBot = 12
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
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="w-full h-56 overflow-visible">
      <defs>
        <linearGradient id="healthFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#1ED760" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#1ED760" stopOpacity="0" />
        </linearGradient>
      </defs>
      <line x1="0" y1={h - 1} x2={w} y2={h - 1} stroke="currentColor" strokeDasharray="3 6" className="text-gray-300 dark:text-white/15" vectorEffect="non-scaling-stroke" />
      <path d={area} fill="url(#healthFill)" />
      <path d={line} fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" className="text-beatz-green" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function IncidentRow({ incident: i }: { incident: Incident }) {
  return (
    <div className="flex items-center gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-1.5 h-1.5 rounded-full bg-gray-400 dark:bg-white/30 shrink-0" />
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{i.title}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">{i.date}</span>
      </div>
      <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold shrink-0',
        i.status === 'resolved' ? 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' : 'bg-beatz-red/15 text-beatz-red')}>
        {i.status}
      </span>
    </div>
  )
}
