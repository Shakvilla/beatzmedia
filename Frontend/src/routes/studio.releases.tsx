import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Disc3, Search, MoreHorizontal, Pencil, ExternalLink, Copy, Trash2, EyeOff } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { releaseTypeLabel, studioArtist, type StudioRelease, type ReleaseStatus } from '../lib/studio-data'
import { formatCompact } from '../lib/studio-analytics'
import { studioReleasesQuery, apiDeleteRelease } from '../lib/api/queries/studio'

export const Route = createFileRoute('/studio/releases')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioReleasesQuery()),
  component: ReleasesComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis0 = (n: number) => `₵${n.toLocaleString('en-US')}`

const STATUS_META: Record<ReleaseStatus, { label: string; cls: string }> = {
  live: { label: 'Live', cls: 'bg-beatz-green/15 text-beatz-green' },
  scheduled: { label: 'Scheduled', cls: 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' },
  in_review: { label: 'In review', cls: 'bg-beatz-blue/15 text-beatz-blue' },
  draft: { label: 'Draft', cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' },
}

const FILTERS: { key: 'all' | ReleaseStatus; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'live', label: 'Live' },
  { key: 'scheduled', label: 'Scheduled' },
  { key: 'in_review', label: 'In review' },
  { key: 'draft', label: 'Drafts' },
]

/** Deterministic two-stop gradient cover from the title. */
function coverGradient(title: string): string {
  let h = 0
  for (let i = 0; i < title.length; i++) h = (h * 31 + title.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 55% 42%), hsl(${(h + 48) % 360} 60% 30%))`
}

function ReleasesComponent() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: releases } = useSuspenseQuery(studioReleasesQuery())
  const [filter, setFilter] = useState<'all' | ReleaseStatus>('all')
  const [query, setQuery] = useState('')

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: releases.length }
    for (const r of releases) c[r.status] = (c[r.status] ?? 0) + 1
    return c
  }, [releases])

  const live = releases.filter((r) => r.status === 'live')
  const totalStreams = live.reduce((s, r) => s + r.streams, 0)
  const totalRevenue = live.reduce((s, r) => s + r.revenue, 0)

  const q = query.trim().toLowerCase()
  const filtered = releases.filter((r) => (filter === 'all' || r.status === filter) && (!q || r.title.toLowerCase().includes(q)))

  const remove = async (id: string) => {
    const key = studioReleasesQuery().queryKey
    const previous = queryClient.getQueryData(key)
    queryClient.setQueryData(key, (rows: typeof releases | undefined) => rows?.filter((r) => r.id !== id))
    try {
      await apiDeleteRelease(id)
      toast('Release deleted', 'success')
    } catch {
      queryClient.setQueryData(key, previous)
      toast('Only drafts and in-review releases can be deleted.', 'error')
    }
  }

  if (releases.length === 0) return <EmptyState />

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Releases</h1>
        <Link to="/studio/release/new/details" className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
          <Plus size={18} /> New release
        </Link>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4">
        <Summary label="Releases" value={releases.length.toString()} />
        <Summary label="Total streams" value={formatCompact(totalStreams)} />
        <Summary label="Total revenue" value={cedis0(totalRevenue)} accent />
      </div>

      {/* Filters + search */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10">
          {FILTERS.map((f) => (
            <button key={f.key} onClick={() => setFilter(f.key)}
              className={cn('h-8 px-3.5 rounded-full text-sm font-bold transition-colors flex items-center gap-1.5',
                filter === f.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}>
              {f.label}
              {counts[f.key] != null && <span className={cn('text-[10px]', filter === f.key ? 'text-beatz-green' : 'text-gray-400')}>{counts[f.key] ?? 0}</span>}
            </button>
          ))}
        </div>
        <div className="relative w-48">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search releases"
            className="w-full h-9 pl-9 pr-3 rounded-full bg-gray-100 dark:bg-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none" />
        </div>
      </div>

      {/* List */}
      <section className={cn(CARD, 'flex flex-col gap-1 !py-4')}>
        <div className="hidden md:flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
          <span className="w-12 shrink-0" />
          <span className="flex-1">Release</span>
          <span className="w-24 text-right shrink-0">Streams</span>
          <span className="w-24 text-right shrink-0">Revenue</span>
          <span className="w-24 text-center shrink-0">Status</span>
          <span className="w-8 shrink-0" />
        </div>

        {filtered.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No {filter !== 'all' ? FILTERS.find((f) => f.key === filter)?.label.toLowerCase() : ''} releases{q && ` matching “${query}”`}.</div>
        ) : (
          filtered.map((r) => (
            <ReleaseRow key={r.id} release={r}
              onOpen={() => navigate({ to: '/studio/release/$releaseId', params: { releaseId: r.id } })}
              onView={() => navigate({ to: '/artist/$artistId', params: { artistId: studioArtist.id } })}
              onDuplicate={() => toast(`Duplicated “${r.title}” as a draft`, 'success')}
              onUnpublish={() => toast(`“${r.title}” unpublished`, 'info')}
              onDelete={() => remove(r.id)}
            />
          ))
        )}
      </section>
    </div>
  )
}

function Summary({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className={cn(CARD, 'flex flex-col gap-1.5 !p-5')}>
      <span className={LABEL}>{label}</span>
      <span className={cn('text-2xl font-bold tracking-tight', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
    </div>
  )
}

function ReleaseRow({ release: r, onOpen, onView, onDuplicate, onUnpublish, onDelete }: {
  release: StudioRelease
  onOpen: () => void; onView: () => void; onDuplicate: () => void; onUnpublish: () => void; onDelete: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const status = STATUS_META[r.status]
  const live = r.status === 'live'
  const stop = (e: React.MouseEvent) => e.stopPropagation()

  return (
    <div onClick={onOpen} className="flex items-center gap-4 px-2 py-3 rounded-lg hover:bg-gray-50 dark:hover:bg-white/5 transition-colors group cursor-pointer">
      {/* Cover */}
      <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(r.title) }}>
        <Disc3 size={18} className="text-white/70" />
      </div>

      {/* Title + meta */}
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{r.title}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400 truncate">
          {releaseTypeLabel(r.type)} · {r.trackCount} track{r.trackCount === 1 ? '' : 's'} · {r.date}
        </span>
      </div>

      {/* Stats */}
      <span className="hidden md:block w-24 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{live ? formatCompact(r.streams) : '—'}</span>
      <span className="hidden md:block w-24 text-right text-sm font-mono font-bold shrink-0">{live ? <span className="text-beatz-green">₵{r.revenue.toLocaleString('en-US')}</span> : <span className="text-gray-400">—</span>}</span>

      {/* Status */}
      <div className="w-24 flex md:justify-center shrink-0">
        <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', status.cls)}>{status.label}</span>
      </div>

      {/* Menu */}
      <div className="w-8 shrink-0 relative flex justify-end" onClick={stop}>
        <button onClick={() => setMenuOpen((o) => !o)} aria-label="Release options" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
          <MoreHorizontal size={18} />
        </button>
        {menuOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 top-9 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
              <MenuItem icon={Pencil} label={r.status === 'draft' || r.status === 'in_review' ? 'Continue editing' : 'Manage'} onClick={() => { onOpen(); setMenuOpen(false) }} />
              {live && <MenuItem icon={ExternalLink} label="View on BeatzClik" onClick={() => { onView(); setMenuOpen(false) }} />}
              <MenuItem icon={Copy} label="Duplicate" onClick={() => { onDuplicate(); setMenuOpen(false) }} />
              {live && <MenuItem icon={EyeOff} label="Unpublish" onClick={() => { onUnpublish(); setMenuOpen(false) }} />}
              <MenuItem icon={Trash2} label="Delete" danger onClick={() => { onDelete(); setMenuOpen(false) }} />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Pencil; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col gap-10">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Releases</h1>
        <Link to="/studio/release/new/details" className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
          <Plus size={18} /> New release
        </Link>
      </div>
      <div className="flex flex-col items-center justify-center text-center gap-5 py-24 rounded-2xl border border-dashed border-gray-300 dark:border-white/10">
        <div className="w-16 h-16 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 dark:text-gray-500"><Disc3 size={30} /></div>
        <div className="flex flex-col gap-1">
          <span className="text-lg font-bold text-beatz-dark-bg dark:text-white">No releases yet</span>
          <p className="text-sm text-gray-500 dark:text-gray-300 max-w-sm">Drop your first single, EP or album and start selling to fans across Ghana.</p>
        </div>
        <Link to="/studio/release/new/details" className="h-11 px-6 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold text-sm flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
          <Plus size={16} /> Create a release
        </Link>
      </div>
    </div>
  )
}
