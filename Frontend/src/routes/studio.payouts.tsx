import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowUp, Download, Plus, Search, Check, Trash2, Clock, Smartphone, Landmark } from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import {
  withdrawalFee, arrivalTime, daysUntilFriday, MIN_PAYOUT,
  type Payouts, type PayoutTxn, type PayoutType, type PayoutStatus, type PayoutMethod,
} from '../lib/studio-payouts'
import {
  payoutsQuery, apiRequestWithdrawal, apiAddPayoutMethod, apiSetDefaultPayoutMethod, apiRemovePayoutMethod,
  MOMO_NETWORKS, GHANA_BANK_CODES, type NewPayoutMethodInput,
} from '../lib/api/queries/payouts'

export const Route = createFileRoute('/studio/payouts')({
  component: PayoutsComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis2 = (n: number) => `₵${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const cedis0 = (n: number) => `₵${n.toLocaleString('en-US')}`

const TYPE_FILTERS: ('All' | PayoutType)[] = ['All', 'Sale', 'Royalty', 'Tip', 'Cash-out']

const EMPTY: Payouts = {
  available: 0, pending: 0, thisMonth: 0, thisMonthDelta: 0, lifetime: 0, since: '',
  earnings: [], bySource: { sales: 0, royalties: 0, tips: 0 }, methods: [], transactions: [],
}

function PayoutsComponent() {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data: base = EMPTY } = useQuery(payoutsQuery())
  const { available, transactions: txns, methods } = base
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [addOpen, setAddOpen] = useState(false)

  const [typeFilter, setTypeFilter] = useState<'All' | PayoutType>('All')
  const [statusFilter, setStatusFilter] = useState<'all' | PayoutStatus>('all')
  const [query, setQuery] = useState('')

  const daysToFri = daysUntilFriday()
  const nextPayout = daysToFri === 0 ? 'Next payout today · Friday' : `Next payout in ${daysToFri} day${daysToFri > 1 ? 's' : ''} · Friday`

  const invalidate = () => queryClient.invalidateQueries({ queryKey: payoutsQuery().queryKey })

  const withdraw = async (amount: number, method: PayoutMethod) => {
    try {
      await apiRequestWithdrawal(amount, method.id)
      await invalidate()
      setWithdrawOpen(false)
      toast(`Withdrawal of ${cedis2(amount)} to ${method.label} requested`, 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Withdrawal failed', 'error')
    }
  }

  const setDefaultMethod = async (id: string) => {
    try { await apiSetDefaultPayoutMethod(id); await invalidate() }
    catch (e) { toast(e instanceof Error ? e.message : 'Could not set default', 'error') }
  }
  const removeMethod = async (id: string) => {
    try { await apiRemovePayoutMethod(id); await invalidate() }
    catch (e) { toast(e instanceof Error ? e.message : 'Could not remove method', 'error') }
  }
  const addMethod = async (input: NewPayoutMethodInput) => {
    try {
      await apiAddPayoutMethod(input)
      await invalidate()
      setAddOpen(false)
      toast(`${input.label} added`, 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not add method', 'error')
    }
  }

  const filtered = txns.filter((t) =>
    (typeFilter === 'All' || t.type === typeFilter) &&
    (statusFilter === 'all' || t.status === statusFilter) &&
    (!query.trim() || t.source.toLowerCase().includes(query.toLowerCase())),
  )
  const groups = useMemo(() => {
    const map = new Map<string, PayoutTxn[]>()
    for (const t of filtered) {
      if (!map.has(t.date)) map.set(t.date, [])
      map.get(t.date)!.push(t)
    }
    return [...map.entries()]
  }, [filtered])

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Payouts</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">Withdraw to MoMo or bank · paid every Friday</span>
      </div>

      {/* Balance hero + stats */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-4">
        <div className={cn(CARD, 'flex flex-col gap-4')}>
          <div className="flex flex-col gap-1">
            <span className={LABEL}>Available balance</span>
            <span className="text-4xl lg:text-5xl font-bold tracking-tight text-beatz-green">{cedis2(available)}</span>
            <span className="text-sm text-gray-500 dark:text-gray-400">+ {cedis2(base.pending)} pending clearance</span>
          </div>
          <div className="flex items-center gap-4 flex-wrap">
            <button onClick={() => setWithdrawOpen(true)} disabled={available < MIN_PAYOUT}
              className="h-11 px-7 rounded-full bg-beatz-green text-black font-bold text-sm hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100">
              Withdraw
            </button>
            <span className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400"><Clock size={15} /> {nextPayout}</span>
          </div>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-1 gap-4">
          <div className={cn(CARD, 'flex flex-col gap-1.5 !p-5')}>
            <span className={LABEL}>This month</span>
            <span className="text-2xl font-bold text-beatz-dark-bg dark:text-white">{cedis0(base.thisMonth)}</span>
            <span className="flex items-center gap-1 text-xs font-bold text-beatz-green"><ArrowUp size={12} /> {base.thisMonthDelta}%</span>
          </div>
          <div className={cn(CARD, 'flex flex-col gap-1.5 !p-5')}>
            <span className={LABEL}>Lifetime</span>
            <span className="text-2xl font-bold text-beatz-dark-bg dark:text-white">{cedis0(base.lifetime)}</span>
            <span className="text-xs text-gray-400 dark:text-gray-500">since {base.since}</span>
          </div>
        </div>
      </div>

      {/* Earnings chart + source split */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-4">
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Earnings over time</h2>
          <EarningsChart earnings={base.earnings} />
        </section>
        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">This month by source</h2>
          <SourceSplit bySource={base.bySource} />
        </section>
      </div>

      {/* Payout methods */}
      <section className={cn(CARD, 'flex flex-col gap-5')}>
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Payout methods</h2>
          <button onClick={() => setAddOpen(true)} className="h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-xs font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors">
            <Plus size={14} /> Add method
          </button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {methods.map((m) => (
            <MethodCard key={m.id} method={m} onDefault={() => setDefaultMethod(m.id)} onRemove={() => removeMethod(m.id)} />
          ))}
        </div>
      </section>

      {/* Transactions */}
      <section className={cn(CARD, 'flex flex-col gap-5')}>
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Transactions</h2>
          <button onClick={() => toast('Exporting transactions as CSV', 'success')} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            <Download size={14} /> Export CSV
          </button>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
            {TYPE_FILTERS.map((t) => (
              <button key={t} onClick={() => setTypeFilter(t)} className={cn('h-7 px-3 rounded-full text-xs font-bold transition-colors',
                typeFilter === t ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}>
                {t}
              </button>
            ))}
          </div>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'all' | PayoutStatus)}
            className="h-9 px-3 rounded-full bg-gray-100 dark:bg-white/10 text-sm font-bold text-beatz-dark-bg dark:text-white focus:outline-none cursor-pointer appearance-none">
            <option value="all">All status</option>
            <option value="cleared">Cleared</option>
            <option value="pending">Pending</option>
            <option value="paid">Paid</option>
          </select>
          <div className="relative flex-1 min-w-[160px]">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search transactions"
              className="w-full h-9 pl-9 pr-3 rounded-full bg-gray-100 dark:bg-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none" />
          </div>
        </div>

        <div className="overflow-x-auto">
          <div className="min-w-[680px]">
            <div className="flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="flex-1">Source</span>
              <span className="w-20 shrink-0">Type</span>
              <span className="w-24 text-right shrink-0">Gross</span>
              <span className="w-28 text-right shrink-0">Net</span>
              <span className="w-20 text-center shrink-0">Status</span>
            </div>

            {groups.length === 0 && <div className="py-10 text-center text-sm text-gray-400 dark:text-gray-500">No matching transactions.</div>}

            {groups.map(([date, rows]) => (
              <div key={date}>
                <div className="px-2 pt-4 pb-1 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">{date}</div>
                {rows.map((t) => (
                  <div key={t.id} className="flex items-center gap-4 px-2 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                    <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.source}</span>
                    <span className="w-20 shrink-0 text-sm text-gray-500 dark:text-gray-300">{t.type}</span>
                    <span className="w-24 text-right shrink-0 text-sm font-mono text-gray-500 dark:text-gray-300">{t.gross == null ? '—' : cedis2(t.gross)}</span>
                    <span className={cn('w-28 text-right shrink-0 text-sm font-mono font-bold', t.net < 0 ? 'text-gray-500 dark:text-gray-300' : 'text-beatz-green')}>
                      {t.net < 0 ? `−${cedis2(t.net)}` : cedis2(t.net)}
                    </span>
                    <span className="w-20 flex justify-center shrink-0"><StatusPill status={t.status} /></span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      </section>

      <WithdrawModal isOpen={withdrawOpen} onClose={() => setWithdrawOpen(false)} max={available} methods={methods} onConfirm={withdraw} />
      <AddMethodModal isOpen={addOpen} onClose={() => setAddOpen(false)} onAdd={addMethod} />
    </div>
  )
}

function StatusPill({ status }: { status: PayoutStatus }) {
  const cls = status === 'pending' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function EarningsChart({ earnings }: { earnings: { label: string; value: number }[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(...earnings.map((e) => e.value))
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-2 h-44">
        {earnings.map((e, i) => (
          <div key={e.label} className="flex-1 h-full flex items-end relative" onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
            {hover === i && <span className="absolute -top-1 left-1/2 -translate-x-1/2 text-[10px] font-bold text-beatz-dark-bg dark:text-white whitespace-nowrap">₵{(e.value / 1000).toFixed(1)}k</span>}
            <div className={cn('w-full rounded-t-md transition-colors', hover === i ? 'bg-beatz-green' : 'bg-beatz-green/75')} style={{ height: `${(e.value / max) * 100}%` }} />
          </div>
        ))}
      </div>
      <div className="flex gap-2">
        {earnings.map((e) => <span key={e.label} className="flex-1 text-center text-[11px] font-mono text-gray-400 dark:text-gray-500">{e.label}</span>)}
      </div>
    </div>
  )
}

function SourceSplit({ bySource }: { bySource: { sales: number; royalties: number; tips: number } }) {
  const total = bySource.sales + bySource.royalties + bySource.tips
  const segs = [
    { label: 'Track sales', value: bySource.sales, cls: 'bg-beatz-green' },
    { label: 'Streaming royalties', value: bySource.royalties, cls: 'bg-beatz-green/50' },
    { label: 'Tips', value: bySource.tips, cls: 'bg-[#f6c644]' },
  ]
  return (
    <div className="flex flex-col gap-5">
      <div><span className="text-3xl font-bold text-beatz-dark-bg dark:text-white">{cedis0(total)}</span></div>
      <div className="flex w-full h-3 rounded-full overflow-hidden">
        {segs.map((s) => <div key={s.label} className={s.cls} style={{ width: `${(s.value / total) * 100}%` }} />)}
      </div>
      <div className="flex flex-col gap-2.5">
        {segs.map((s) => (
          <div key={s.label} className="flex items-center gap-2 text-sm">
            <span className={cn('w-2.5 h-2.5 rounded-full', s.cls)} />
            <span className="flex-1 text-gray-600 dark:text-gray-300">{s.label}</span>
            <span className="font-mono font-bold text-beatz-dark-bg dark:text-white">{cedis0(s.value)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function MethodCard({ method, onDefault, onRemove }: { method: PayoutMethod; onDefault: () => void; onRemove: () => void }) {
  const Icon = method.kind === 'bank' ? Landmark : Smartphone
  return (
    <div className={cn('flex items-center gap-3 p-4 rounded-xl border transition-colors', method.isDefault ? 'border-beatz-green bg-beatz-green/5' : 'border-gray-200 dark:border-white/10')}>
      <span className="w-10 h-10 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Icon size={18} /></span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{method.label}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{method.detail}</span>
      </div>
      {method.isDefault ? (
        <span className="text-[10px] font-bold uppercase tracking-wider text-beatz-green flex items-center gap-1"><Check size={12} /> Default</span>
      ) : (
        <button onClick={onDefault} className="text-xs font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors">Set default</button>
      )}
      <button onClick={onRemove} aria-label="Remove method" className="w-7 h-7 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors shrink-0"><Trash2 size={14} /></button>
    </div>
  )
}

function WithdrawModal({ isOpen, onClose, max, methods, onConfirm }: {
  isOpen: boolean; onClose: () => void; max: number; methods: PayoutMethod[]; onConfirm: (amount: number, method: PayoutMethod) => void
}) {
  const [amount, setAmount] = useState('')
  const [methodId, setMethodId] = useState(methods.find((m) => m.isDefault)?.id ?? methods[0]?.id ?? '')
  const method = methods.find((m) => m.id === methodId) ?? methods[0]
  const value = Number(amount)
  const fee = method ? withdrawalFee(method.kind, value || 0) : 0
  const net = Math.max(0, (value || 0) - fee)
  const valid = value >= MIN_PAYOUT && value <= max && !!method

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Withdraw funds">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-bold uppercase tracking-[0.15em] text-white/50">Amount</label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-white/40 font-bold">₵</span>
            <input type="number" autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00"
              className="w-full h-12 rounded-xl bg-white/5 border border-white/10 pl-9 pr-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/60 transition-colors" />
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-white/40">Available: {cedis2(max)} · min {cedis2(MIN_PAYOUT)}</span>
            <button onClick={() => setAmount(String(max))} className="text-beatz-green font-bold hover:underline">Withdraw all</button>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-bold uppercase tracking-[0.15em] text-white/50">To</label>
          <div className="flex flex-col gap-2">
            {methods.map((m) => (
              <button key={m.id} onClick={() => setMethodId(m.id)}
                className={cn('flex items-center gap-3 p-3 rounded-xl border text-left transition-colors', methodId === m.id ? 'border-beatz-green bg-beatz-green/10' : 'border-white/10 hover:border-white/20')}>
                <span className="w-8 h-8 rounded-full bg-white/10 flex items-center justify-center text-white/70 shrink-0">{m.kind === 'bank' ? <Landmark size={15} /> : <Smartphone size={15} />}</span>
                <div className="flex flex-col flex-1 min-w-0"><span className="text-sm font-bold text-white truncate">{m.label}</span><span className="text-xs text-white/40 truncate">{m.detail}</span></div>
                {methodId === m.id && <Check size={16} className="text-beatz-green shrink-0" />}
              </button>
            ))}
          </div>
        </div>

        {value > 0 && method && (
          <div className="flex flex-col gap-1.5 text-sm p-3 rounded-xl bg-white/5">
            <div className="flex justify-between text-white/60"><span>Fee</span><span className="font-mono">{cedis2(fee)}</span></div>
            <div className="flex justify-between text-white font-bold"><span>You receive</span><span className="font-mono text-beatz-green">{cedis2(net)}</span></div>
            <div className="text-xs text-white/40">Arrives {arrivalTime(method.kind)}</div>
          </div>
        )}

        <button onClick={() => valid && method && onConfirm(Math.round(value * 100) / 100, method)} disabled={!valid}
          className="h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform disabled:opacity-40 disabled:hover:scale-100">
          {value > max ? 'Amount exceeds balance' : value > 0 && value < MIN_PAYOUT ? `Minimum ${cedis2(MIN_PAYOUT)}` : `Withdraw ${value > 0 ? cedis2(value) : ''}`.trim()}
        </button>
        <p className="text-center text-xs text-white/40">Payouts are processed every Friday.</p>
      </div>
    </Modal>
  )
}

const ADD_METHOD_INPUT = 'w-full h-12 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/60'

function AddMethodModal({ isOpen, onClose, onAdd }: {
  isOpen: boolean; onClose: () => void; onAdd: (input: NewPayoutMethodInput) => void
}) {
  const [kind, setKind] = useState<'momo' | 'bank'>('momo')
  const [network, setNetwork] = useState(MOMO_NETWORKS[0].value)
  const [wallet, setWallet] = useState('')
  const [bankCode, setBankCode] = useState(GHANA_BANK_CODES[0].code)
  const [accountName, setAccountName] = useState('')
  const [accountNumber, setAccountNumber] = useState('')

  const momoValid = wallet.trim() !== ''
  const bankValid = accountName.trim() !== '' && accountNumber.trim() !== ''
  const valid = kind === 'momo' ? momoValid : bankValid

  const reset = () => {
    setKind('momo'); setNetwork(MOMO_NETWORKS[0].value); setWallet('')
    setBankCode(GHANA_BANK_CODES[0].code); setAccountName(''); setAccountNumber('')
  }

  const submit = () => {
    if (!valid) return
    if (kind === 'momo') {
      const label = `${MOMO_NETWORKS.find((n) => n.value === network)!.label} MoMo`
      onAdd({
        kind, label, detail: wallet.trim(), network, walletNumber: wallet.trim(),
        bankName: null, bankCode: null, accountName: null, accountNumber: null,
      })
    } else {
      const bank = GHANA_BANK_CODES.find((b) => b.code === bankCode)!
      onAdd({
        kind, label: bank.name, detail: accountNumber.trim(), network: null, walletNumber: null,
        bankName: bank.name, bankCode, accountName: accountName.trim(), accountNumber: accountNumber.trim(),
      })
    }
    reset()
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Add payout method">
      <div className="flex flex-col gap-5">
        <div className="grid grid-cols-2 gap-2">
          {(['momo', 'bank'] as const).map((k) => (
            <button key={k} onClick={() => setKind(k)}
              className={cn('h-11 rounded-xl border text-sm font-bold flex items-center justify-center gap-2 transition-colors', kind === k ? 'border-beatz-green bg-beatz-green/10 text-beatz-green' : 'border-white/10 text-white/70 hover:border-white/20')}>
              {k === 'momo' ? <Smartphone size={16} /> : <Landmark size={16} />} {k === 'momo' ? 'Mobile money' : 'Bank'}
            </button>
          ))}
        </div>

        {kind === 'momo' ? (
          <>
            <select value={network} onChange={(e) => setNetwork(e.target.value)}
              className={cn(ADD_METHOD_INPUT, 'appearance-none cursor-pointer')}>
              {MOMO_NETWORKS.map((n) => <option key={n.value} value={n.value}>{n.label}</option>)}
            </select>
            <input value={wallet} onChange={(e) => setWallet(e.target.value)} placeholder="Wallet number"
              className={ADD_METHOD_INPUT} />
          </>
        ) : (
          <>
            <select value={bankCode} onChange={(e) => setBankCode(e.target.value)}
              className={cn(ADD_METHOD_INPUT, 'appearance-none cursor-pointer')}>
              {GHANA_BANK_CODES.map((b) => <option key={b.code} value={b.code}>{b.name}</option>)}
            </select>
            <input value={accountName} onChange={(e) => setAccountName(e.target.value)} placeholder="Account name"
              className={ADD_METHOD_INPUT} />
            <input value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} placeholder="Account number"
              className={ADD_METHOD_INPUT} />
          </>
        )}

        <button onClick={submit} disabled={!valid}
          className="h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform disabled:opacity-40 disabled:hover:scale-100">
          Add method
        </button>
      </div>
    </Modal>
  )
}
