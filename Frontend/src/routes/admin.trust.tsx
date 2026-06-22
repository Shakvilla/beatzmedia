import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { MoreHorizontal, Ban, Check, Eye } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getRiskSignals, RISK_KPIS, type RiskSignal, type RiskLevel } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/trust')({
  component: AdminTrust,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function AdminTrust() {
  const { toast } = useToast()
  const [signals, setSignals] = useState<RiskSignal[]>(() => getRiskSignals())
  const paged = usePaged(signals)

  const setStatus = (id: string, status: RiskSignal['status'], msg: string) => {
    setSignals((list) => list.map((s) => (s.id === id ? { ...s, status } : s)))
    toast(msg, 'success')
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Trust &amp; safety</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">Fraud, abuse and risk signals across the platform</span>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Kpi label="Chargeback rate" value={RISK_KPIS.chargebackRate} sub="last 30d" />
        <Kpi label="Suspicious signups" value={RISK_KPIS.suspiciousSignups.toString()} sub="last 24h" warn />
        <Kpi label="Open fraud flags" value={RISK_KPIS.fraudFlags.toString()} sub="need review" warn />
        <Kpi label="Bot streams" value={RISK_KPIS.botStreams} sub="of total plays" />
      </div>

      {/* Signals */}
      <section className={cn(CARD, 'flex flex-col gap-4')}>
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Risk signals</h2>
        <div className="overflow-x-auto">
          <div className="min-w-[760px]">
            <div className="flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="w-40 shrink-0">Subject</span>
              <span className="w-36 shrink-0">Type</span>
              <span className="flex-1">Detail</span>
              <span className="w-16 shrink-0">Risk</span>
              <span className="w-16 shrink-0">Age</span>
              <span className="w-28 text-right shrink-0">Action</span>
            </div>
            {paged.pageItems.map((s) => (
              <SignalRow key={s.id} signal={s}
                onReview={() => toast(`Reviewing ${s.subject}`, 'info')}
                onBan={() => setStatus(s.id, 'banned', `${s.subject} banned`)}
                onClear={() => setStatus(s.id, 'cleared', `${s.subject} cleared`)}
              />
            ))}
          </div>
        </div>
        <Pagination paged={paged} />
      </section>
    </div>
  )
}

function Kpi({ label, value, sub, warn }: { label: string; value: string; sub: string; warn?: boolean }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-2')}>
      <span className={LABEL}>{label}</span>
      <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', warn ? 'text-beatz-red' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
    </div>
  )
}

function LevelPill({ level }: { level: RiskLevel }) {
  const cls = level === 'high' ? 'bg-beatz-red/15 text-beatz-red' : level === 'med' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{level}</span>
}

function SignalRow({ signal: s, onReview, onBan, onClear }: { signal: RiskSignal; onReview: () => void; onBan: () => void; onClear: () => void }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const closed = s.status !== 'open'
  return (
    <div className={cn('flex items-center gap-4 px-2 py-3.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-opacity', closed && 'opacity-50')}>
      <span className="w-40 shrink-0 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{s.subject}</span>
      <span className="w-36 shrink-0"><span className="px-2.5 py-1 rounded-full text-xs font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">{s.type}</span></span>
      <span className="flex-1 text-sm text-gray-500 dark:text-gray-300 truncate">{s.detail}</span>
      <span className="w-16 shrink-0"><LevelPill level={s.level} /></span>
      <span className="w-16 shrink-0 text-xs font-mono text-gray-400 dark:text-gray-500">{s.time}</span>
      <div className="w-28 shrink-0 flex items-center justify-end gap-1">
        {closed ? (
          <span className={cn('text-[10px] font-bold uppercase tracking-wider', s.status === 'banned' ? 'text-beatz-red' : 'text-beatz-green')}>{s.status}</span>
        ) : (
          <>
            <button onClick={onReview} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Review</button>
            <div className="relative">
              <button onClick={() => setMenuOpen((o) => !o)} aria-label="Actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"><MoreHorizontal size={18} /></button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 top-9 z-50 w-40 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                    <MenuItem icon={Eye} label="Investigate" onClick={() => { onReview(); setMenuOpen(false) }} />
                    <MenuItem icon={Check} label="Clear" onClick={() => { onClear(); setMenuOpen(false) }} />
                    <MenuItem icon={Ban} label="Ban subject" danger onClick={() => { onBan(); setMenuOpen(false) }} />
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Eye; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors', danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
