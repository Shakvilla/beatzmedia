import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { MoreHorizontal, Check, ShieldX, ArrowUpCircle, X } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getModerationQueue, MOD_TYPES, MOD_SLA_HOURS, MOD_ESCALATED, type ModerationItem, type ModReason, type ModSeverity, type ModStatus } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/moderation')({
  component: AdminModeration,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

const STATUS_TABS: { key: ModStatus | 'all'; label: string }[] = [
  { key: 'open', label: 'Open' },
  { key: 'in_review', label: 'In review' },
  { key: 'resolved', label: 'Resolved' },
  { key: 'all', label: 'All' },
]

function AdminModeration() {
  const { toast } = useToast()
  const [items, setItems] = useState<ModerationItem[]>(() => getModerationQueue())
  const [status, setStatus] = useState<ModStatus | 'all'>('open')
  const [type, setType] = useState<ModReason | 'all'>('all')

  const openCount = items.filter((i) => i.status === 'open').length
  const rows = useMemo(
    () => items.filter((i) => (status === 'all' || i.status === status) && (type === 'all' || i.reason === type)),
    [items, status, type],
  )
  const paged = usePaged(rows)

  const setItemStatus = (id: string, s: ModStatus) => setItems((list) => list.map((i) => (i.id === id ? { ...i, status: s } : i)))

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Moderation queue</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">{openCount} open · {MOD_SLA_HOURS}h SLA · {MOD_ESCALATED} escalated</span>
        </div>
        <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
          {STATUS_TABS.map((t) => (
            <button key={t.key} onClick={() => setStatus(t.key)}
              className={cn('h-8 px-3.5 rounded-full text-sm font-bold transition-colors',
                status === t.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* Type chips */}
      <div className="flex items-center gap-2 flex-wrap">
        <Chip active={type === 'all'} onClick={() => setType('all')}>All types</Chip>
        {MOD_TYPES.map((t) => <Chip key={t} active={type === t} onClick={() => setType(t)}>{t}</Chip>)}
      </div>

      {/* Table */}
      <section className={cn(CARD, 'p-2 sm:p-4')}>
        <div className="overflow-x-auto">
          <div className="min-w-[820px]">
            <div className="flex items-center gap-4 px-3 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="flex-1">Item</span>
              <span className="w-28 shrink-0">Reporter</span>
              <span className="w-32 shrink-0">Reason</span>
              <span className="w-14 shrink-0">Age</span>
              <span className="w-20 shrink-0">Severity</span>
              <span className="w-28 text-right shrink-0">Action</span>
            </div>

            {rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Nothing in this queue.</div>
            ) : (
              paged.pageItems.map((it) => (
                <ModRow key={it.id} item={it}
                  onReview={() => { setItemStatus(it.id, 'in_review'); toast(`Reviewing “${it.item}”`, 'info') }}
                  onApprove={() => { setItemStatus(it.id, 'resolved'); toast('Content approved & kept', 'success') }}
                  onRemove={() => { setItemStatus(it.id, 'resolved'); toast('Content removed', 'success') }}
                  onEscalate={() => toast('Escalated to senior review', 'info')}
                  onDismiss={() => { setItemStatus(it.id, 'resolved'); toast('Report dismissed', 'success') }}
                />
              ))
            )}
          </div>
        </div>
        <Pagination paged={paged} />
      </section>
    </div>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick}
      className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors',
        active ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
      {children}
    </button>
  )
}

function SeverityPill({ severity }: { severity: ModSeverity }) {
  const cls = severity === 'high'
    ? 'bg-beatz-red/15 text-beatz-red'
    : severity === 'med'
      ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]'
      : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{severity}</span>
}

function StatusTag({ status }: { status: ModStatus }) {
  if (status === 'open') return null
  const cls = status === 'in_review' ? 'bg-beatz-blue/15 text-beatz-blue' : 'bg-beatz-green/15 text-beatz-green'
  return <span className={cn('px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider', cls)}>{status === 'in_review' ? 'In review' : 'Resolved'}</span>
}

function ModRow({ item: it, onReview, onApprove, onRemove, onEscalate, onDismiss }: {
  item: ModerationItem
  onReview: () => void; onApprove: () => void; onRemove: () => void; onEscalate: () => void; onDismiss: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const resolved = it.status === 'resolved'
  return (
    <div className={cn('flex items-center gap-4 px-3 py-3.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-colors hover:bg-gray-50 dark:hover:bg-white/5', resolved && 'opacity-55')}>
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{it.item}</span>
        <StatusTag status={it.status} />
      </div>
      <span className="w-28 shrink-0 text-sm text-gray-500 dark:text-gray-300 truncate">{it.reporter}</span>
      <span className="w-32 shrink-0">
        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">{it.reason}</span>
      </span>
      <span className="w-14 shrink-0 text-sm font-mono text-gray-500 dark:text-gray-300 uppercase">{it.age}</span>
      <span className="w-20 shrink-0"><SeverityPill severity={it.severity} /></span>
      <div className="w-28 shrink-0 flex items-center justify-end gap-1">
        {!resolved && (
          <button onClick={onReview} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Review</button>
        )}
        <div className="relative">
          <button onClick={() => setMenuOpen((o) => !o)} aria-label="Actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <MoreHorizontal size={18} />
          </button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-9 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                <MenuItem icon={Check} label="Approve & keep" onClick={() => { onApprove(); setMenuOpen(false) }} />
                <MenuItem icon={ShieldX} label="Remove content" danger onClick={() => { onRemove(); setMenuOpen(false) }} />
                <MenuItem icon={ArrowUpCircle} label="Escalate" onClick={() => { onEscalate(); setMenuOpen(false) }} />
                <MenuItem icon={X} label="Dismiss report" onClick={() => { onDismiss(); setMenuOpen(false) }} />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Check; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
