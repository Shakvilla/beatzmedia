import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowLeft, Search, Download } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getLedger, type LedgerTxn, type LedgerType } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/finance/ledger')({
  component: AdminLedger,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'
const signed = (n: number) => `${n < 0 ? '−' : ''}₵${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const TYPES: (LedgerType | 'all')[] = ['all', 'Sale', 'Royalty', 'Tip', 'Payout', 'Refund', 'Fee']

function AdminLedger() {
  const { toast } = useToast()
  const all = useMemo(() => getLedger(), [])
  const [type, setType] = useState<LedgerType | 'all'>('all')
  const [query, setQuery] = useState('')

  const q = query.trim().toLowerCase()
  const rows = all.filter((t) => (type === 'all' || t.type === type) && (!q || `${t.party} ${t.ref}`.toLowerCase().includes(q)))
  const net = rows.reduce((s, t) => s + t.amount, 0)
  const paged = usePaged(rows)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4">
        <Link to="/admin/finance" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
          <ArrowLeft size={14} /> Finance
        </Link>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex flex-col gap-1">
            <h1 className="text-display text-beatz-dark-bg dark:text-white">Ledger</h1>
            <span className="text-sm text-gray-500 dark:text-gray-300">Net in view · <span className={cn('font-bold font-mono', net < 0 ? 'text-beatz-red' : 'text-beatz-green')}>{signed(net)}</span></span>
          </div>
          <button onClick={() => toast('Exporting ledger as CSV', 'success')} className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            <Download size={16} /> Export
          </button>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          {TYPES.map((t) => (
            <button key={t} onClick={() => setType(t)}
              className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors',
                type === t ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
              {t === 'all' ? 'All' : t}
            </button>
          ))}
        </div>
        <div className="relative w-56">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search party or ref"
            className="w-full h-10 pl-10 pr-3 rounded-full bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60" />
        </div>
      </div>

      <section className={cn(CARD, 'p-2 sm:p-4')}>
        <div className="overflow-x-auto">
          <div className="min-w-[640px]">
            <div className="flex items-center gap-4 px-3 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="w-16 shrink-0">Date</span>
              <span className="w-24 shrink-0">Type</span>
              <span className="flex-1">Party</span>
              <span className="w-32 shrink-0">Ref</span>
              <span className="w-28 text-right shrink-0">Amount</span>
            </div>
            {rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching entries.</div>
            ) : paged.pageItems.map((t) => <LedgerRow key={t.id} txn={t} />)}
          </div>
        </div>
        <Pagination paged={paged} />
      </section>
    </div>
  )
}

function LedgerRow({ txn: t }: { txn: LedgerTxn }) {
  const out = t.amount < 0
  return (
    <div className="flex items-center gap-4 px-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-16 shrink-0 text-sm font-mono text-gray-500 dark:text-gray-300">{t.date}</span>
      <span className="w-24 shrink-0"><span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">{t.type}</span></span>
      <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.party}</span>
      <span className="w-32 shrink-0 text-xs font-mono text-gray-400 dark:text-gray-500">{t.ref}</span>
      <span className={cn('w-28 text-right shrink-0 text-sm font-mono font-bold', out ? 'text-gray-500 dark:text-gray-300' : 'text-beatz-green')}>{signed(t.amount)}</span>
    </div>
  )
}
