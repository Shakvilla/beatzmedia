import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { Search, Download, User, ListMusic, Wallet, Flag, SlidersHorizontal, Radio, Clock, type LucideIcon } from 'lucide-react'
import { cn } from '../utils/cn'
import { useQuery } from '@tanstack/react-query'
import type { AuditEntry, AuditType } from '../lib/admin-data'
import { auditQuery, AUDIT_PAGE_SIZE } from '../lib/api/queries/admin-overview'
import { useServerPaged, Pagination } from '../components/admin/pagination'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'

export const Route = createFileRoute('/admin/audit')({
  component: AdminAudit,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

const TYPE_META: Record<AuditType, { label: string; icon: LucideIcon }> = {
  user: { label: 'Users', icon: User },
  catalog: { label: 'Catalog', icon: ListMusic },
  finance: { label: 'Finance', icon: Wallet },
  moderation: { label: 'Moderation', icon: Flag },
  settings: { label: 'Settings', icon: SlidersHorizontal },
  editorial: { label: 'Editorial', icon: Radio },
}
const TYPES: (AuditType | 'all')[] = ['all', 'user', 'catalog', 'finance', 'moderation', 'editorial', 'settings']

function AdminAudit() {
  const [type, setType] = useState<AuditType | 'all'>('all')
  const [query, setQuery] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(1)

  // Search is a server param now, so debounce it: one request per pause, not per keystroke.
  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(query.trim())
      setPage(1)
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  const { data, isPending, isError, error, refetch } = useQuery(auditQuery(type, debouncedQ, page))
  const rows: AuditEntry[] = data?.items ?? []
  const paged = useServerPaged({ items: rows, total: data?.total ?? 0, page, setPage, size: AUDIT_PAGE_SIZE })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Audit log</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">Every privileged admin action, with actor and time</span>
        </div>
        {/*
          Disabled rather than removed: an auditable export is a real requirement, it just has no
          endpoint. This reported success and produced no file, which is the worst outcome for an
          audit trail — an operator would believe they held a record they never received.
        */}
        <button disabled title="Audit export isn't available yet." className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-gray-400 dark:text-gray-500 text-sm font-bold flex items-center gap-2 cursor-not-allowed">
          <Download size={16} /> Export
        </button>
      </div>

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          {TYPES.map((t) => (
            <button key={t} onClick={() => { setType(t); setPage(1) }}
              className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors capitalize',
                type === t ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
              {t === 'all' ? 'All' : TYPE_META[t].label}
            </button>
          ))}
        </div>
        <div className="relative w-56">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search actions"
            className="w-full h-10 pl-10 pr-3 rounded-full bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60" />
        </div>
      </div>

      <section className={cn(CARD, 'p-2 sm:p-4')}>
        {isError ? (
          error instanceof ApiError && error.status === 403 ? (
            <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">You don't have access to the audit log.</div>
          ) : (
            <AdminLoadError label="Couldn't load the audit log." onRetry={() => refetch()} />
          )
        ) : isPending ? (
          <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
        ) : rows.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching entries.</div>
        ) : (
          paged.pageItems.map((e) => <AuditRow key={e.id} entry={e} />)
        )}
        <div className="px-1"><Pagination paged={paged} /></div>
      </section>
    </div>
  )
}

function AuditRow({ entry: e }: { entry: AuditEntry }) {
  const meta = TYPE_META[e.type]
  const Icon = meta.icon
  return (
    <div className="flex items-center gap-4 px-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Icon size={16} /></span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm text-beatz-dark-bg dark:text-white truncate">
          <span className="font-bold">{e.actor}</span> · {e.action} · <span className="text-gray-500 dark:text-gray-300">{e.target}</span>
        </span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">{meta.label}</span>
      </div>
      <span className="flex items-center gap-1.5 text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0"><Clock size={12} /> {e.time}</span>
    </div>
  )
}
