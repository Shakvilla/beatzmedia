import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Plus, MoreHorizontal, ArrowUp, ArrowDown, Trash2, Bell, Disc3, Music2 } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getEditorial, type FeaturedSlot, type PushItem, type CuratedPlaylist } from '../lib/admin-data'

export const Route = createFileRoute('/admin/editorial')({
  component: AdminEditorial,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function coverGradient(seed: string): string {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 52% 44%), hsl(${(h + 50) % 360} 56% 32%))`
}

function AdminEditorial() {
  const { toast } = useToast()
  const base = useMemo(() => getEditorial(), [])
  const [featured, setFeatured] = useState<FeaturedSlot[]>(base.featured)

  const move = (id: string, dir: -1 | 1) => setFeatured((list) => {
    const i = list.findIndex((s) => s.id === id)
    const j = i + dir
    if (i === -1 || j < 0 || j >= list.length) return list
    const next = [...list]
    ;[next[i], next[j]] = [next[j], next[i]]
    return next
  })
  const remove = (id: string) => { setFeatured((list) => list.filter((s) => s.id !== id)); toast('Removed from featured', 'success') }

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Editorial</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">Featured slots · curated playlists · push notifications</span>
        </div>
        <button onClick={() => toast('New playlist — pick tracks to curate', 'info')} className="h-11 px-5 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
          <Plus size={18} /> New playlist
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* Home featured */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <div className="flex flex-col gap-0.5">
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Home featured · Ghana</h2>
            <span className="text-xs text-gray-400 dark:text-gray-500">Drag to reorder · live in 2h</span>
          </div>
          <div className="flex flex-col">
            {featured.map((s, i) => (
              <FeaturedRow key={s.id} slot={s} index={i} isFirst={i === 0} isLast={i === featured.length - 1}
                onMove={(d) => move(s.id, d)} onRemove={() => remove(s.id)} onReplace={() => toast(`Replace “${s.title}”`, 'info')} />
            ))}
            {featured.length === 0 && <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">No featured slots.</div>}
          </div>
        </section>

        {/* Push schedule */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Push schedule · this week</h2>
          <div className="flex flex-col">
            {base.pushSchedule.map((p) => <PushRow key={p.id} push={p} />)}
          </div>
          <button onClick={() => toast('Schedule a new push notification', 'info')} className="self-start h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors">
            <Plus size={15} /> Schedule push
          </button>
        </section>
      </div>

      {/* Curated playlists */}
      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Curated playlists</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          {base.playlists.map((p) => <PlaylistCard key={p.id} playlist={p} onOpen={() => toast(`Open “${p.name}”`, 'info')} />)}
        </div>
      </section>
    </div>
  )
}

function FeaturedRow({ slot: s, index, isFirst, isLast, onMove, onRemove, onReplace }: {
  slot: FeaturedSlot; index: number; isFirst: boolean; isLast: boolean
  onMove: (d: -1 | 1) => void; onRemove: () => void; onReplace: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  return (
    <div className="flex items-center gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 group">
      <span className="w-4 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{index + 1}</span>
      <div className="w-11 h-11 rounded-md shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(s.title) }}>
        <Disc3 size={16} className="text-white/70" />
      </div>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{s.title}</span>
        <span className={cn('text-xs truncate', s.sponsored ? 'text-[#b8881f] dark:text-[#f6c644] font-bold' : 'text-gray-500 dark:text-gray-400')}>{s.note}</span>
      </div>
      <div className="relative shrink-0">
        <button onClick={() => setMenuOpen((o) => !o)} aria-label="Slot options" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
          <MoreHorizontal size={18} />
        </button>
        {menuOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 top-9 z-50 w-40 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
              <MenuItem icon={ArrowUp} label="Move up" disabled={isFirst} onClick={() => { onMove(-1); setMenuOpen(false) }} />
              <MenuItem icon={ArrowDown} label="Move down" disabled={isLast} onClick={() => { onMove(1); setMenuOpen(false) }} />
              <MenuItem icon={Music2} label="Replace" onClick={() => { onReplace(); setMenuOpen(false) }} />
              <MenuItem icon={Trash2} label="Remove" danger onClick={() => { onRemove(); setMenuOpen(false) }} />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function PushRow({ push: p }: { push: PushItem }) {
  return (
    <div className="flex items-center gap-4 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <div className="w-20 shrink-0 flex flex-col">
        <span className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-300">{p.day}</span>
        <span className="text-xs font-mono text-gray-400 dark:text-gray-500">{p.time}</span>
      </div>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{p.title}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 flex items-center gap-1"><Bell size={10} /> Audience · {p.audience}</span>
      </div>
      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300 shrink-0">scheduled</span>
    </div>
  )
}

function PlaylistCard({ playlist: p, onOpen }: { playlist: CuratedPlaylist; onOpen: () => void }) {
  return (
    <button onClick={onOpen} className="flex flex-col gap-2 group text-left">
      <div className="w-full aspect-square rounded-xl flex items-center justify-center transition-transform group-hover:-translate-y-0.5" style={{ backgroundImage: coverGradient(p.name) }}>
        <span className="text-[10px] font-bold uppercase tracking-[0.15em] text-white/80 text-center px-2">{p.name}</span>
      </div>
      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{p.name}</span>
    </button>
  )
}

function MenuItem({ icon: Icon, label, onClick, disabled, danger }: { icon: typeof ArrowUp; label: string; onClick: () => void; disabled?: boolean; danger?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        disabled ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed' : danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
