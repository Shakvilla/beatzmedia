import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { MoreHorizontal, Play, Check, Download, FileText, ShieldX, FileSpreadsheet, Trash2, type LucideIcon } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getCompliance, type ComplianceRequest, type ComplianceType, type ComplianceStatus } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/compliance')({
  component: AdminCompliance,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

const TYPE_META: Record<ComplianceType, { label: string; icon: LucideIcon }> = {
  'DSAR-export': { label: 'Data export', icon: FileSpreadsheet },
  'DSAR-delete': { label: 'Data deletion', icon: Trash2 },
  Takedown: { label: 'Takedown', icon: ShieldX },
  Tax: { label: 'Tax', icon: FileText },
}
type FilterKey = 'all' | 'DSAR' | 'Takedown' | 'Tax'
const FILTERS: FilterKey[] = ['all', 'DSAR', 'Takedown', 'Tax']
const inFilter = (c: ComplianceRequest, f: FilterKey) => f === 'all' ? true : f === 'DSAR' ? c.type.startsWith('DSAR') : c.type === f

function AdminCompliance() {
  const { toast } = useToast()
  const [items, setItems] = useState<ComplianceRequest[]>(() => getCompliance())
  const [filter, setFilter] = useState<FilterKey>('all')

  const rows = useMemo(() => items.filter((c) => inFilter(c, filter)), [items, filter])
  const paged = usePaged(rows)
  const open = items.filter((c) => c.status !== 'completed').length
  const overdue = items.filter((c) => c.status === 'overdue').length

  const setStatus = (id: string, status: ComplianceStatus, msg: string) => { setItems((l) => l.map((c) => (c.id === id ? { ...c, status } : c))); toast(msg, 'success') }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Compliance</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">{open} open · <span className={overdue > 0 ? 'text-beatz-red font-bold' : ''}>{overdue} overdue</span> · DSAR, takedowns &amp; tax</span>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        {FILTERS.map((f) => (
          <button key={f} onClick={() => setFilter(f)}
            className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors',
              filter === f ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
            {f === 'all' ? 'All' : f}
          </button>
        ))}
      </div>

      <section className={cn(CARD, 'p-2 sm:p-4')}>
        <div className="overflow-x-auto">
          <div className="min-w-[760px]">
            <div className="flex items-center gap-4 px-3 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="w-32 shrink-0">Type</span>
              <span className="flex-1">Subject</span>
              <span className="w-32 shrink-0">Due</span>
              <span className="w-28 shrink-0">Status</span>
              <span className="w-32 text-right shrink-0">Action</span>
            </div>
            {paged.pageItems.map((c) => (
              <ComplianceRow key={c.id} req={c}
                onStart={() => setStatus(c.id, 'in_progress', 'Request started')}
                onComplete={() => setStatus(c.id, 'completed', 'Request completed')}
                onDownload={() => toast('Preparing data export', 'success')}
                onNotice={() => toast('Generating takedown notice', 'success')}
              />
            ))}
          </div>
        </div>
        <Pagination paged={paged} />
      </section>
    </div>
  )
}

function StatusPill({ status }: { status: ComplianceStatus }) {
  const map = {
    new: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300',
    in_progress: 'bg-beatz-blue/15 text-beatz-blue',
    completed: 'bg-beatz-green/15 text-beatz-green',
    overdue: 'bg-beatz-red/15 text-beatz-red',
  }[status]
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', map)}>{status === 'in_progress' ? 'in progress' : status}</span>
}

function ComplianceRow({ req: c, onStart, onComplete, onDownload, onNotice }: {
  req: ComplianceRequest; onStart: () => void; onComplete: () => void; onDownload: () => void; onNotice: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const meta = TYPE_META[c.type]
  const Icon = meta.icon
  const done = c.status === 'completed'
  return (
    <div className={cn('flex items-center gap-4 px-3 py-3.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-opacity', done && 'opacity-55')}>
      <span className="w-32 shrink-0 flex items-center gap-2">
        <span className="w-7 h-7 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Icon size={14} /></span>
        <span className="text-xs font-bold text-beatz-dark-bg dark:text-white truncate">{meta.label}</span>
      </span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{c.subject}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{c.detail}</span>
      </div>
      <span className={cn('w-32 shrink-0 text-sm', c.status === 'overdue' ? 'text-beatz-red font-bold' : 'text-gray-500 dark:text-gray-300')}>{c.due}</span>
      <span className="w-28 shrink-0"><StatusPill status={c.status} /></span>
      <div className="w-32 shrink-0 flex items-center justify-end gap-1">
        {!done && (c.status === 'new'
          ? <button onClick={onStart} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Start</button>
          : <button onClick={onComplete} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Complete</button>)}
        <div className="relative">
          <button onClick={() => setMenuOpen((o) => !o)} aria-label="Actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"><MoreHorizontal size={18} /></button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-9 z-50 w-48 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                {c.status === 'new' && <MenuItem icon={Play} label="Start processing" onClick={() => { onStart(); setMenuOpen(false) }} />}
                {c.type === 'DSAR-export' && <MenuItem icon={Download} label="Download data" onClick={() => { onDownload(); setMenuOpen(false) }} />}
                {c.type === 'Takedown' && <MenuItem icon={FileText} label="Generate notice" onClick={() => { onNotice(); setMenuOpen(false) }} />}
                {!done && <MenuItem icon={Check} label="Mark completed" onClick={() => { onComplete(); setMenuOpen(false) }} />}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick }: { icon: LucideIcon; label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
      <Icon size={15} /> {label}
    </button>
  )
}
