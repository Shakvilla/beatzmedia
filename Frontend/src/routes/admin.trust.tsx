import { createFileRoute } from '@tanstack/react-router'
import { useState, useRef } from 'react'
import { MoreHorizontal, Ban, Check, Eye } from 'lucide-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import type { RiskSignal, RiskLevel } from '../lib/admin-data'
import { riskBoardQuery, apiReviewSignal, apiClearSignal, apiBanSignal } from '../lib/api/queries/admin-trust'
import { AdminLoadError } from '../components/admin/load-error'
import { Modal } from '../components/ui/modal'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/trust')({
  component: AdminTrust,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function AdminTrust() {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, isPending, refetch } = useQuery(riskBoardQuery())
  const signals = data?.signals ?? []
  const paged = usePaged(signals)

  const [banTarget, setBanTarget] = useState<RiskSignal | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string, tone: 'success' | 'info' = 'success') => {
    if (inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    let ok = false
    try { await fn(); ok = true } catch { toast(errMsg, 'error') }
    finally {
      inFlight.current = false
      setSubmitting(false)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'risk'] })
      if (ok) toast(okMsg, tone)
    }
  }

  const review = (s: RiskSignal) => runAction(() => apiReviewSignal(s.id), `Reviewing ${s.subject}`, 'Could not record the review', 'info')
  const clear = (s: RiskSignal) => runAction(() => apiClearSignal(s.id), `${s.subject} cleared`, 'Could not clear the signal')
  const ban = (s: RiskSignal, reason: string) => runAction(() => apiBanSignal(s.id, reason), `${s.subject} banned`, 'Could not ban the subject')

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Trust &amp; safety</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">Fraud, abuse and risk signals across the platform</span>
      </div>

      {isError ? (
        <AdminLoadError label="Couldn't load risk signals." onRetry={() => refetch()} />
      ) : isPending || !data ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* KPIs */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <Kpi label="Chargeback rate" value="—" sub="not measured yet" />
            <Kpi label="Suspicious signups" value="—" sub="not measured yet" warn />
            <Kpi label="Open fraud flags" value={data.kpis.fraudFlags.toLocaleString()} sub="need review" warn />
            <Kpi label="Bot streams" value="—" sub="not measured yet" />
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
                {signals.length === 0 ? (
                  <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No risk signals.</div>
                ) : (
                  paged.pageItems.map((s) => (
                    <SignalRow key={s.id} signal={s} disabled={submitting}
                      onReview={() => review(s)}
                      onBan={() => setBanTarget(s)}
                      onClear={() => clear(s)}
                    />
                  ))
                )}
              </div>
            </div>
            <Pagination paged={paged} />
          </section>
        </>
      )}

      {banTarget && (
        <BanModal signal={banTarget} submitting={submitting} onClose={() => setBanTarget(null)}
          onConfirm={(reason) => { const s = banTarget; setBanTarget(null); if (s) ban(s, reason) }} />
      )}
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

function SignalRow({ signal: s, disabled, onReview, onBan, onClear }: { signal: RiskSignal; disabled?: boolean; onReview: () => void; onBan: () => void; onClear: () => void }) {
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
            <button onClick={onReview} disabled={disabled} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Review</button>
            <div className="relative">
              <button onClick={() => setMenuOpen((o) => !o)} aria-label="Actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"><MoreHorizontal size={18} /></button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 top-9 z-50 w-40 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                    <MenuItem icon={Eye} label="Investigate" disabled={disabled} onClick={() => { onReview(); setMenuOpen(false) }} />
                    <MenuItem icon={Check} label="Clear" disabled={disabled} onClick={() => { onClear(); setMenuOpen(false) }} />
                    <MenuItem icon={Ban} label="Ban subject" danger disabled={disabled} onClick={() => { onBan(); setMenuOpen(false) }} />
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

function MenuItem({ icon: Icon, label, onClick, disabled, danger }: { icon: typeof Eye; label: string; onClick: () => void; disabled?: boolean; danger?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        disabled ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed' : danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}

function BanModal({ signal, submitting, onClose, onConfirm }: { signal: RiskSignal | null; submitting?: boolean; onClose: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState('')
  const REASONS = ['Payment fraud', 'Chargeback abuse', 'Bot activity', 'Impersonation', 'Other']
  return (
    <Modal isOpen={signal !== null} onClose={onClose} title={`Ban ${signal?.subject ?? ''}`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The subject loses access immediately and the account is banned. A reason is required and is recorded in the audit trail.</p>
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button key={r} onClick={() => setReason(r)} className={cn('h-9 px-3.5 rounded-full text-xs font-bold border transition-colors', reason === r ? 'border-beatz-red bg-beatz-red/10 text-beatz-red' : 'border-white/10 text-white/70 hover:border-white/20')}>{r}</button>
          ))}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Add a note…" className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim() || submitting} className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">Ban</button>
        </div>
      </div>
    </Modal>
  )
}
