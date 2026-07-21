import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Mic, MoreHorizontal, Pencil, Trash2, ExternalLink } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { studioShowsQuery, studioEpisodesQuery, apiDeleteEpisode } from '../lib/api/queries/podcasts-studio'
import type { StudioEpisode, EpisodeStatus } from '../lib/studio-data'
import { formatDuration } from '../lib/format'
import { formatCompact } from '../lib/studio-analytics'

export const Route = createFileRoute('/studio/podcasts/')({
  component: StudioPodcasts,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function coverGradient(t: string): string {
  let h = 0
  for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 52% 42%), hsl(${(h + 40) % 360} 56% 30%))`
}

function StudioPodcasts() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: shows = [] } = useQuery(studioShowsQuery())
  const { data: episodes = [] } = useQuery(studioEpisodesQuery())

  const onDelete = async (id: string) => {
    try {
      await apiDeleteEpisode(id)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: studioEpisodesQuery().queryKey }),
        queryClient.invalidateQueries({ queryKey: studioShowsQuery().queryKey }),
      ])
      toast('Episode deleted', 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not delete the episode', 'error')
    }
  }

  if (episodes.length === 0 && shows.length === 0) {
    return (
      <div className="flex flex-col gap-10">
        <Header onNew={() => navigate({ to: '/studio/podcasts/new' })} />
        <div className="flex flex-col items-center justify-center text-center gap-5 py-24 rounded-2xl border border-dashed border-gray-300 dark:border-white/10">
          <div className="w-16 h-16 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 dark:text-gray-500"><Mic size={30} /></div>
          <p className="text-sm text-gray-500 dark:text-gray-300 max-w-sm">Start a show and publish episodes — free, premium, or early-access for your fans.</p>
          <Link to="/studio/podcasts/new" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform"><Plus size={16} /> New episode</Link>
        </div>
      </div>
    )
  }

  const totalPlays = episodes.reduce((s, e) => s + e.plays, 0)

  return (
    <div className="flex flex-col gap-8">
      <Header onNew={() => navigate({ to: '/studio/podcasts/new' })} />

      {/* Shows */}
      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Your shows</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {shows.map((sh) => (
            <div key={sh.id} className={cn(CARD, 'flex items-center gap-4 !p-4')}>
              <div className="w-14 h-14 rounded-xl shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(sh.title) }}><Mic size={22} className="text-white/80" /></div>
              <div className="flex flex-col min-w-0">
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{sh.title}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400">{sh.category} · {episodes.filter((e) => e.showId === sh.id).length} episodes</span>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Episodes */}
      <section className={cn(CARD, 'flex flex-col gap-4')}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Episodes</h2>
          <span className="text-xs text-gray-500 dark:text-gray-400">{formatCompact(totalPlays)} total plays</span>
        </div>
        <div className="flex flex-col">
          {episodes.map((e) => (
            <EpisodeRow key={e.id} ep={e}
              onPlay={() => toast(`Previewing “${e.title}”`, 'info')}
              onEdit={() => toast(`Edit “${e.title}”`, 'info')}
              onView={() => navigate({ to: '/podcasts' })}
              onDelete={() => onDelete(e.id)}
            />
          ))}
        </div>
      </section>
    </div>
  )
}

function Header({ onNew }: { onNew: () => void }) {
  return (
    <div className="flex items-center justify-between gap-4 flex-wrap">
      <h1 className="text-display text-beatz-dark-bg dark:text-white">Podcasts</h1>
      <button onClick={onNew} className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
        <Plus size={18} /> New episode
      </button>
    </div>
  )
}

function StatusPill({ status }: { status: EpisodeStatus }) {
  const cls = status === 'published' ? 'bg-beatz-green/15 text-beatz-green' : status === 'scheduled' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function EpisodeRow({ ep, onPlay, onEdit, onView, onDelete }: {
  ep: StudioEpisode; onPlay: () => void; onEdit: () => void; onView: () => void; onDelete: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  return (
    <div className="flex items-center gap-4 px-2 py-3 rounded-lg border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors group">
      <button onClick={onPlay} className="w-9 h-9 rounded-full shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(ep.title) }}><Mic size={15} className="text-white/80" /></button>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="flex items-center gap-2 min-w-0">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{ep.title}</span>
          {ep.premium && <span className="shrink-0 text-[9px] font-bold uppercase tracking-wider text-[#b8881f] dark:text-[#f6c644] bg-[#f6c644]/20 px-1.5 py-0.5 rounded">Premium · ₵{ep.price}</span>}
        </span>
        <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{ep.showTitle} · {ep.publishedAt}</span>
      </div>
      <span className="hidden sm:block w-20 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatCompact(ep.plays)} plays</span>
      <span className="w-16 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{formatDuration(ep.duration)}</span>
      <span className="w-24 flex justify-center shrink-0"><StatusPill status={ep.status} /></span>
      <div className="w-8 shrink-0 relative flex justify-end">
        <button onClick={() => setMenuOpen((o) => !o)} aria-label="Episode options" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"><MoreHorizontal size={18} /></button>
        {menuOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 top-9 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
              <MenuItem icon={Pencil} label="Edit" onClick={() => { onEdit(); setMenuOpen(false) }} />
              {ep.status === 'published' && <MenuItem icon={ExternalLink} label="View on BeatzClik" onClick={() => { onView(); setMenuOpen(false) }} />}
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
    <button onClick={onClick} className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors', danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
