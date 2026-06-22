import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Search, MoreHorizontal, Check, Eye, Flag, ShieldX, Disc3 } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getCatalog, CATALOG_SUMMARY, CATALOG_COUNTS, type CatalogItem, type CatalogStatus } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/catalog')({
  component: AdminCatalog,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

type FilterKey = 'pending' | 'published' | 'takedown' | 'all'
const FILTERS: { key: FilterKey; label: string; count?: number }[] = [
  { key: 'pending', label: 'Pending review', count: CATALOG_COUNTS.pending },
  { key: 'published', label: 'Published', count: CATALOG_COUNTS.published },
  { key: 'takedown', label: 'Takedown', count: CATALOG_COUNTS.takedown },
  { key: 'all', label: 'All' },
]

const inFilter = (c: CatalogItem, f: FilterKey) =>
  f === 'all' ? true : f === 'pending' ? (c.status === 'pending' || c.status === 'flagged') : f === 'published' ? c.status === 'published' : c.status === 'takedown'

function coverGradient(title: string): string {
  let h = 0
  for (let i = 0; i < title.length; i++) h = (h * 31 + title.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 50% 44%), hsl(${(h + 48) % 360} 55% 32%))`
}

function AdminCatalog() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const [items, setItems] = useState<CatalogItem[]>(() => getCatalog())
  const [filter, setFilter] = useState<FilterKey>('pending')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const q = query.trim().toLowerCase()
  const rows = useMemo(
    () => items.filter((c) => inFilter(c, filter) && (!q || `${c.title} ${c.artist}`.toLowerCase().includes(q))),
    [items, filter, q],
  )
  const paged = usePaged(rows)

  const setStatus = (id: string, status: CatalogStatus) => setItems((list) => list.map((c) => (c.id === id ? { ...c, status } : c)))
  const allShownSelected = rows.length > 0 && rows.every((c) => selected.has(c.id))
  const toggleAll = () => setSelected(allShownSelected ? new Set() : new Set(rows.map((c) => c.id)))
  const toggleOne = (id: string) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })

  const bulkApprove = () => {
    setItems((list) => list.map((c) => (selected.has(c.id) ? { ...c, status: 'published' } : c)))
    toast(`${selected.size} release${selected.size > 1 ? 's' : ''} approved`, 'success')
    setSelected(new Set())
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Catalog</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">
            {CATALOG_SUMMARY.artists.toLocaleString()} artists · {CATALOG_SUMMARY.albums.toLocaleString()} albums · {CATALOG_SUMMARY.tracks.toLocaleString()} tracks
          </span>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative w-56 sm:w-72">
            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="ISRC, title, artist…"
              className="w-full h-11 pl-10 pr-3 rounded-full bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60" />
          </div>
          <button onClick={toggleAll} className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            Bulk edit
          </button>
        </div>
      </div>

      {/* Filter chips */}
      <div className="flex items-center gap-2 flex-wrap">
        {FILTERS.map((f) => (
          <button key={f.key} onClick={() => setFilter(f.key)}
            className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors',
              filter === f.key ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
            {f.label}{f.count != null ? ` · ${f.count.toLocaleString()}` : ''}
          </button>
        ))}
      </div>

      {/* Bulk bar */}
      {selected.size > 0 && (
        <div className="flex items-center justify-between gap-4 px-4 py-3 rounded-xl bg-beatz-green/10 border border-beatz-green/20">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{selected.size} selected</span>
          <div className="flex items-center gap-2">
            <button onClick={bulkApprove} className="h-9 px-4 rounded-full bg-beatz-green text-black text-xs font-bold hover:scale-105 transition-transform">Approve</button>
            <button onClick={() => setSelected(new Set())} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">Clear</button>
          </div>
        </div>
      )}

      {/* Table */}
      <section className={cn(CARD, 'p-2 sm:p-4')}>
        <div className="overflow-x-auto">
          <div className="min-w-[860px]">
            <div className="flex items-center gap-4 px-3 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="w-5 shrink-0"><Checkbox checked={allShownSelected} onChange={toggleAll} /></span>
              <span className="w-11 shrink-0" />
              <span className="flex-1">Title</span>
              <span className="w-32 shrink-0">Artist</span>
              <span className="w-28 shrink-0">Type</span>
              <span className="w-16 shrink-0">Tracks</span>
              <span className="w-24 shrink-0">Status</span>
              <span className="w-28 text-right shrink-0">Action</span>
            </div>

            {rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Nothing here.</div>
            ) : (
              paged.pageItems.map((c) => (
                <CatalogRow key={c.id} item={c} selected={selected.has(c.id)} onSelect={() => toggleOne(c.id)}
                  onApprove={() => { setStatus(c.id, 'published'); toast(`“${c.title}” approved`, 'success') }}
                  onView={() => navigate({ to: '/admin/catalog/$itemId', params: { itemId: c.id } })}
                  onFlag={() => { setStatus(c.id, 'flagged'); toast(`“${c.title}” flagged`, 'info') }}
                  onTakedown={() => { setStatus(c.id, 'takedown'); toast(`“${c.title}” taken down`, 'success') }}
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

function Checkbox({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button onClick={onChange} role="checkbox" aria-checked={checked}
      className={cn('w-5 h-5 rounded-md border flex items-center justify-center transition-colors shrink-0',
        checked ? 'bg-beatz-green border-beatz-green' : 'border-gray-300 dark:border-white/25 hover:border-beatz-green')}>
      {checked && <Check size={13} strokeWidth={3} className="text-black" />}
    </button>
  )
}

function StatusPill({ status }: { status: CatalogStatus }) {
  const cls = status === 'flagged'
    ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]'
    : status === 'published'
      ? 'bg-beatz-green/15 text-beatz-green'
      : status === 'takedown'
        ? 'bg-beatz-red/15 text-beatz-red'
        : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function CatalogRow({ item: c, selected, onSelect, onApprove, onView, onFlag, onTakedown }: {
  item: CatalogItem; selected: boolean; onSelect: () => void
  onApprove: () => void; onView: () => void; onFlag: () => void; onTakedown: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const reviewable = c.status === 'pending' || c.status === 'flagged'
  return (
    <div className={cn('flex items-center gap-4 px-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-colors', selected ? 'bg-beatz-green/[0.05]' : 'hover:bg-gray-50 dark:hover:bg-white/5')}>
      <span className="w-5 shrink-0"><Checkbox checked={selected} onChange={onSelect} /></span>
      <div className="w-11 h-11 rounded-md shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(c.title) }}>
        <Disc3 size={16} className="text-white/70" />
      </div>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{c.title}</span>
        {c.note && <span className={cn('text-xs truncate', c.status === 'flagged' || c.status === 'takedown' ? 'text-beatz-red' : 'text-gray-500 dark:text-gray-400')}>{c.note}</span>}
      </div>
      <span className="w-32 shrink-0 text-sm text-gray-500 dark:text-gray-300 truncate">{c.artist}</span>
      <span className="w-28 shrink-0"><span className="px-2.5 py-1 rounded-full text-xs font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">{c.type}</span></span>
      <span className="w-16 shrink-0 text-sm font-mono text-gray-500 dark:text-gray-300">{c.tracks}</span>
      <span className="w-24 shrink-0"><StatusPill status={c.status} /></span>
      <div className="w-28 shrink-0 flex items-center justify-end gap-1">
        {reviewable && <button onClick={onApprove} className="h-8 px-3 rounded-full text-beatz-green text-xs font-bold hover:bg-beatz-green/10 transition-colors">Approve</button>}
        <div className="relative">
          <button onClick={() => setMenuOpen((o) => !o)} aria-label="Actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <MoreHorizontal size={18} />
          </button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-9 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                {reviewable && <MenuItem icon={Check} label="Approve" onClick={() => { onApprove(); setMenuOpen(false) }} />}
                <MenuItem icon={Eye} label="View details" onClick={() => { onView(); setMenuOpen(false) }} />
                {c.status !== 'flagged' && <MenuItem icon={Flag} label="Flag" onClick={() => { onFlag(); setMenuOpen(false) }} />}
                <MenuItem icon={ShieldX} label="Take down" danger onClick={() => { onTakedown(); setMenuOpen(false) }} />
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
