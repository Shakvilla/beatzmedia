import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowLeft, Clock, RotateCcw, ShieldX, ArrowUpCircle, AlertTriangle } from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import { getFinance, getDisputeTimeline, type Dispute } from '../lib/admin-data'

export const Route = createFileRoute('/admin/finance/dispute/$disputeId')({
  component: DisputeDetail,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis = (n: number) => `₵${n.toLocaleString('en-US', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })}`

interface Log { id: string; text: string; time: string }

function DisputeDetail() {
  const { disputeId } = Route.useParams()
  const { toast } = useToast()
  const found = useMemo(() => getFinance().disputes.find((d) => d.id === disputeId), [disputeId])
  const baseTimeline = useMemo(() => getDisputeTimeline(), [])

  const [status, setStatus] = useState<'open' | 'resolved'>('open')
  const [refundOpen, setRefundOpen] = useState(false)
  const [log, setLog] = useState<Log[]>([])

  if (!found) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Dispute not found.</p>
        <Link to="/admin/finance" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to finance</Link>
      </div>
    )
  }
  const d: Dispute = found
  const resolve = (text: string) => { setStatus('resolved'); setLog((l) => [{ id: `l-${Date.now()}`, text, time: 'just now' }, ...l]); toast(text, 'success') }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-4">
        <Link to="/admin/finance" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
          <ArrowLeft size={14} /> Finance
        </Link>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-3xl font-bold tracking-tight text-beatz-dark-bg dark:text-white">{d.kind}</h1>
              <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', status === 'resolved' ? 'bg-beatz-green/15 text-beatz-green' : 'bg-beatz-red/15 text-beatz-red')}>{status}</span>
            </div>
            <span className="text-sm text-gray-500 dark:text-gray-300">{d.subject} · {d.detail}{d.opened ? ` · opened ${d.opened}` : ''}</span>
          </div>
          {status === 'open' && (
            <div className="flex items-center gap-2">
              <button onClick={() => setRefundOpen(true)} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform"><RotateCcw size={15} /> Refund</button>
              <button onClick={() => resolve('Dispute rejected · evidence sufficient')} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><ShieldX size={15} /> Reject</button>
              <button onClick={() => toast('Escalated to senior finance', 'info')} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><ArrowUpCircle size={15} /> Escalate</button>
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_1fr] gap-6 items-start">
        {/* Summary */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Summary</h2>
          <Meta label="Type" value={d.kind} />
          <Meta label="Subject" value={d.subject} />
          {d.amount != null && <Meta label="Amount in dispute" value={cedis(d.amount)} />}
          {d.opened && <Meta label="Opened" value={d.opened} />}
          <Meta label="Reason" value={d.detail} last />
          <div className="flex items-start gap-2 p-3 rounded-xl bg-[#f6c644]/10 text-[#b8881f] dark:text-[#f6c644] text-xs">
            <AlertTriangle size={14} className="mt-0.5 shrink-0" /> Funds are held until this dispute is resolved.
          </div>
        </section>

        {/* Timeline */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Timeline</h2>
          <div className="flex flex-col">
            {[...log, ...baseTimeline].map((t) => (
              <div key={t.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                <Clock size={13} className="text-gray-400 shrink-0" />
                <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{t.text}</span>
                <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{t.time}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <RefundModal isOpen={refundOpen} amount={d.amount ?? 0} onClose={() => setRefundOpen(false)}
        onConfirm={() => { setRefundOpen(false); resolve(`Refunded ${cedis(d.amount ?? 0)} · dispute closed`) }} />
    </div>
  )
}

function Meta({ label, value, last }: { label: string; value: string; last?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between gap-4 py-1.5', !last && 'border-b border-dashed border-gray-200 dark:border-white/5')}>
      <span className="text-sm text-gray-500 dark:text-gray-400 shrink-0">{label}</span>
      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white text-right truncate">{value}</span>
    </div>
  )
}

function RefundModal({ isOpen, amount, onClose, onConfirm }: { isOpen: boolean; amount: number; onClose: () => void; onConfirm: () => void }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Issue refund">
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">Refund <span className="font-bold text-white">{cedis(amount)}</span> to the fan and close this dispute. This is logged and cannot be undone.</p>
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={onConfirm} className="flex-1 h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform">Confirm refund</button>
        </div>
      </div>
    </Modal>
  )
}
