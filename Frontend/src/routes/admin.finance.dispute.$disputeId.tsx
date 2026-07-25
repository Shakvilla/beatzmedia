import { createFileRoute, Link } from '@tanstack/react-router'
import { useRef, useState } from 'react'
import { ArrowLeft, Clock, RotateCcw, ShieldX, ArrowUpCircle, AlertTriangle } from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { disputeQuery, apiRefundDispute, apiRejectDispute, apiEscalateDispute } from '../lib/api/queries/admin-finance'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'

export const Route = createFileRoute('/admin/finance/dispute/$disputeId')({
  component: DisputeDetail,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis = (n: number) => `₵${n.toLocaleString('en-US', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })}`

function DisputeDetail() {
  const { disputeId } = Route.useParams()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error, refetch } = useQuery(disputeQuery(disputeId))
  const [refundOpen, setRefundOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  if (isError) {
    const notFound = error instanceof ApiError && error.status === 404
    return notFound ? (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Dispute not found.</p>
        <Link to="/admin/finance" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to finance</Link>
      </div>
    ) : (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this dispute." onRetry={() => refetch()} />
      </div>
    )
  }

  const d = data
  if (!d) {
    return <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">{isLoading ? 'Loading…' : ''}</div>
  }

  const status = d.status
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string, tone: 'success' | 'info' = 'success') => {
    if (inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    try {
      await fn()
      await queryClient.invalidateQueries({ queryKey: ['admin', 'finance'] })
      toast(okMsg, tone)
    } catch { toast(errMsg, 'error') }
    finally { inFlight.current = false; setSubmitting(false) }
  }
  const reject = () => runAction(() => apiRejectDispute(d.id, 'Dispute rejected · evidence sufficient'), 'Dispute rejected · evidence sufficient', 'Could not reject the dispute')
  const escalate = () => runAction(() => apiEscalateDispute(d.id), 'Escalated to senior finance', 'Could not escalate the dispute', 'info')
  const refund = () => runAction(() => apiRefundDispute(d.id, `Refunded ${cedis(d.amount ?? 0)} · dispute closed`), `Refunded ${cedis(d.amount ?? 0)} · dispute closed`, 'Could not issue the refund')

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
              <button onClick={() => setRefundOpen(true)} disabled={submitting} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100"><RotateCcw size={15} /> Refund</button>
              <button onClick={reject} disabled={submitting} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors disabled:opacity-40"><ShieldX size={15} /> Reject</button>
              <button onClick={escalate} disabled={submitting} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors disabled:opacity-40"><ArrowUpCircle size={15} /> Escalate</button>
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
            {d.timeline.map((t) => (
              <div key={t.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                <Clock size={13} className="text-gray-400 shrink-0" />
                <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{t.text}</span>
                <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{t.time}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <RefundModal isOpen={refundOpen} amount={d.amount ?? 0} submitting={submitting} onClose={() => setRefundOpen(false)}
        onConfirm={() => { setRefundOpen(false); refund() }} />
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

function RefundModal({ isOpen, amount, submitting, onClose, onConfirm }: { isOpen: boolean; amount: number; submitting: boolean; onClose: () => void; onConfirm: () => void }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Issue refund">
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">Refund <span className="font-bold text-white">{cedis(amount)}</span> to the fan and close this dispute. This is logged and cannot be undone.</p>
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={onConfirm} disabled={submitting} className="flex-1 h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform disabled:opacity-40 disabled:hover:scale-100">Confirm refund</button>
        </div>
      </div>
    </Modal>
  )
}
