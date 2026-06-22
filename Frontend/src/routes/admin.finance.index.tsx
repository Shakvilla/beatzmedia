import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowUp, Download, Check } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getFinance, type PendingPayout, type ProviderMix, type Dispute } from '../lib/admin-data'

export const Route = createFileRoute('/admin/finance/')({
  component: AdminFinance,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

const compactCedis = (n: number) => (n >= 1_000_000 ? `₵${(n / 1_000_000).toFixed(1)}M` : n >= 1000 ? `₵${Math.round(n / 1000)}k` : `₵${n}`)
const full = (n: number) => `₵${n.toLocaleString('en-US')}`

function AdminFinance() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const base = useMemo(() => getFinance(), [])
  const k = base.kpis
  const [payouts, setPayouts] = useState<(PendingPayout & { sent?: boolean })[]>(() => base.pendingPayouts)

  const send = (id: string) => {
    const p = payouts.find((x) => x.id === id)
    if (!p) return
    if (p.status === 'kyc_pending') { toast(`Resolve KYC for ${p.artist} before paying`, 'error'); return }
    setPayouts((list) => list.map((x) => (x.id === id ? { ...x, sent: true } : x)))
    toast(`Sent ${full(p.amount)} to ${p.artist}`, 'success')
  }
  const runWeekly = () => {
    const ready = payouts.filter((p) => p.status === 'ready' && !p.sent)
    if (ready.length === 0) { toast('No ready payouts to run', 'info'); return }
    setPayouts((list) => list.map((p) => (p.status === 'ready' ? { ...p, sent: true } : p)))
    toast(`Weekly payout run · ${ready.length} artists paid`, 'success')
  }

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Finance &amp; payouts</h1>
        <div className="flex items-center gap-3">
          <button onClick={() => navigate({ to: '/admin/finance/ledger' })} className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            <Download size={16} /> View ledger
          </button>
          <button onClick={runWeekly} className="h-11 px-5 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
            Run weekly payout
          </button>
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Kpi label="GMV (MTD)" value={compactCedis(k.gmvMtd)} delta={k.gmvDelta} accent />
        <Kpi label="Platform fee" value={compactCedis(k.platformFee)} sub={`${k.feeTakePct}% take`} />
        <Kpi label="Artist payouts due" value={compactCedis(k.payoutsDue)} sub={`${k.payoutsArtists.toLocaleString()} artists`} />
        <Kpi label="MoMo float" value={compactCedis(k.momoFloat)} sub="settled daily" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-6 items-start">
        {/* Pending payouts */}
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Payouts pending · this Friday</h2>
          <div className="overflow-x-auto">
            <div className="min-w-[520px]">
              <div className="flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
                <span className="flex-1">Artist</span>
                <span className="w-24 text-right shrink-0">Amount</span>
                <span className="w-32 shrink-0">Method</span>
                <span className="w-24 text-center shrink-0">Status</span>
                <span className="w-16 text-right shrink-0">Action</span>
              </div>
              {payouts.map((p) => <PayoutRow key={p.id} payout={p} onSend={() => send(p.id)} />)}
            </div>
          </div>
        </section>

        {/* Provider mix + disputes */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">MoMo provider mix (24h)</h2>
            <ProviderBars mix={base.providerMix} />
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Disputes · {base.disputes.length} open</h2>
            {base.disputes.map((d) => <DisputeRow key={d.id} dispute={d} onOpen={() => navigate({ to: '/admin/finance/dispute/$disputeId', params: { disputeId: d.id } })} />)}
          </section>
        </div>
      </div>
    </div>
  )
}

function Kpi({ label, value, delta, sub, accent }: { label: string; value: string; delta?: number; sub?: string; accent?: boolean }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-2')}>
      <span className={LABEL}>{label}</span>
      <span className={cn('text-2xl lg:text-3xl font-bold tracking-tight', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      {delta != null ? (
        <span className="flex items-center gap-1 text-xs font-bold text-beatz-green"><ArrowUp size={12} /> {delta}%</span>
      ) : (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
    </div>
  )
}

function PayoutRow({ payout: p, onSend }: { payout: PendingPayout & { sent?: boolean }; onSend: () => void }) {
  const kyc = p.status === 'kyc_pending'
  return (
    <div className={cn('flex items-center gap-4 px-2 py-3.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-opacity', p.sent && 'opacity-50')}>
      <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{p.artist}</span>
      <span className="w-24 text-right shrink-0 text-sm font-mono font-bold text-beatz-green">{full(p.amount)}</span>
      <span className="w-32 shrink-0 text-sm text-gray-500 dark:text-gray-300">{p.method}</span>
      <span className="w-24 flex justify-center shrink-0">
        {p.sent
          ? <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-beatz-green/15 text-beatz-green flex items-center gap-1"><Check size={10} strokeWidth={3} /> sent</span>
          : kyc
            ? <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]">KYC pending</span>
            : <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300">ready</span>}
      </span>
      <span className="w-16 flex justify-end shrink-0">
        {!p.sent && (
          <button onClick={onSend} className={cn('h-8 px-3 rounded-full text-xs font-bold transition-colors', kyc ? 'text-gray-400 hover:bg-gray-100 dark:hover:bg-white/5' : 'text-beatz-green hover:bg-beatz-green/10')}>
            Send
          </button>
        )}
      </span>
    </div>
  )
}

function ProviderBars({ mix }: { mix: ProviderMix[] }) {
  const max = Math.max(...mix.map((m) => m.value))
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-3 h-40">
        {mix.map((m) => (
          <div key={m.name} className="flex-1 h-full flex items-end">
            <div className="w-full rounded-t-md bg-beatz-green/80" style={{ height: `${(m.value / max) * 100}%` }} />
          </div>
        ))}
      </div>
      <div className="flex gap-3">
        {mix.map((m) => <span key={m.name} className="flex-1 text-center text-[11px] font-mono uppercase text-gray-400 dark:text-gray-500">{m.name}</span>)}
      </div>
    </div>
  )
}

function DisputeRow({ dispute: d, onOpen }: { dispute: Dispute; onOpen: () => void }) {
  return (
    <button onClick={onOpen} className="flex items-start gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 text-left w-full group">
      <span className="w-1.5 h-1.5 rounded-full bg-beatz-red shrink-0 mt-1.5" />
      <div className="flex flex-col min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white group-hover:text-beatz-green transition-colors truncate">{d.kind} · {d.subject}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">{d.detail}</span>
      </div>
    </button>
  )
}
