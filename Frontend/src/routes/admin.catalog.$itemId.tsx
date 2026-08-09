import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Disc3, Check, Flag, ShieldX, RotateCcw, Clock, Play } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { type CatalogStatus } from '../lib/admin-data'
import { catalogItemQuery, apiApproveCatalog, apiFlagCatalog, apiTakedownCatalog, apiReinstateCatalog } from '../lib/api/queries/admin-catalog'
import { TakedownModal } from '../components/admin/takedown-modal'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'

export const Route = createFileRoute('/admin/catalog/$itemId')({
  component: AdminCatalogDetail,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function coverGradient(t: string): string {
  let h = 0
  for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 50% 44%), hsl(${(h + 48) % 360} 55% 32%))`
}

function AdminCatalogDetail() {
  const { itemId } = Route.useParams()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, error, refetch } = useQuery(catalogItemQuery(itemId))
  const [takedownOpen, setTakedownOpen] = useState(false)

  if (isError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
          <p className="text-sm text-gray-500 dark:text-gray-300">Release not found.</p>
          <Link to="/admin/catalog" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to catalog</Link>
        </div>
      )
    }
    return (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this release." onRetry={() => refetch()} />
      </div>
    )
  }

  const item = data
  if (!item) {
    return (
      <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
    )
  }

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['admin', 'catalog'] })
  }
  const invalidateModeration = () => queryClient.invalidateQueries({ queryKey: ['admin', 'moderation'] })
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string) => {
    try { await fn(); await invalidate(); toast(okMsg, 'success') }
    catch { toast(errMsg, 'error') }
  }
  const approve = () => runAction(() => apiApproveCatalog(item.id), 'Approved & published', 'Could not approve release')
  const flag = () => runAction(async () => { await apiFlagCatalog(item.id); await invalidateModeration() }, 'Flagged for review', 'Could not flag release')
  const takedown = (reason: string) => runAction(() => apiTakedownCatalog(item.id, reason), `Taken down · ${reason}`, 'Could not take down release')
  const reinstate = () => runAction(() => apiReinstateCatalog(item.id), 'Reinstated — live again', 'Could not reinstate release')

  const reviewable = item.status === 'pending' || item.status === 'flagged'

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-4">
        <Link to="/admin/catalog" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
          <ArrowLeft size={14} /> Catalog
        </Link>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4">
            <div className="w-20 h-20 rounded-xl shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(item.title) }}><Disc3 size={30} className="text-white/70" /></div>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-3xl font-bold tracking-tight text-beatz-dark-bg dark:text-white">{item.title}</h1>
                <StatusPill status={item.status} />
              </div>
              <span className="text-sm text-gray-500 dark:text-gray-300">{item.artist} · {item.type} · {item.tracks.length} track{item.tracks.length === 1 ? '' : 's'}{item.note ? ` · ${item.note}` : ''}</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {reviewable && <button onClick={approve} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform"><span className="flex items-center gap-2"><Check size={15} /> Approve</span></button>}
            {item.status !== 'flagged' && item.status !== 'takedown' && <button onClick={flag} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><Flag size={15} /> Flag</button>}
            {/*
              Reinstate replaces Take down once a release is down. The endpoint existed, guarded
              and tested, with nothing calling it — so a takedown could not be undone from the
              console. It re-fires ReleaseWentLive, so the search index and album projection are
              rebuilt; a direct database edit would leave both stale.
            */}
            {item.status === 'takedown'
              ? <button onClick={reinstate} className="h-10 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors"><RotateCcw size={15} /> Reinstate</button>
              : <button onClick={() => setTakedownOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><ShieldX size={15} /> Take down</button>}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-6 items-start">
        {/* Tracklist */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Tracklist</h2>
          <div className="flex flex-col">
            {item.tracks.map((t) => (
              <div key={t.position} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 group">
                <span className="w-5 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{t.position}</span>
                {/*
                  Moderator preview needs the media pipeline (a stream URL per track) which this
                  view does not have; it only ever toasted. Left visible but inert rather than
                  claiming playback that never starts — a moderator deciding a takedown should not
                  believe they have heard the track.
                */}
                <button disabled title="Track preview isn't available here yet." className="w-7 h-7 rounded-full bg-gray-100 dark:bg-white/10 text-gray-400 dark:text-gray-500 flex items-center justify-center shrink-0 opacity-0 group-hover:opacity-100 transition-opacity cursor-not-allowed"><Play size={12} fill="currentColor" /></button>
                <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.title}</span>
                <span className="text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0">{t.isrc ?? '—'}</span>
                <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{t.duration}</span>
              </div>
            ))}
          </div>
        </section>

        {/* Metadata + splits + history */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Metadata</h2>
            <Meta label="UPC" value={item.upc ?? '—'} />
            <Meta label="Primary genre" value="Hiplife / Drill" />
            <Meta label="Label" value={item.artist === 'Various' ? 'Beatzclik Compilations' : 'Independent'} />
            <Meta label="Tracks" value={`${item.tracks.length}`} last />
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Rights & splits</h2>
            {item.splits.map((s) => (
              <div key={`${s.name}-${s.role}-${s.pct}`} className="flex items-center gap-3 py-1.5">
                <div className="flex flex-col flex-1 min-w-0">
                  <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{s.name}</span>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{s.role}</span>
                </div>
                <span className="text-sm font-mono font-bold text-beatz-green">{s.pct}%</span>
              </div>
            ))}
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Action history</h2>
            {item.log.map((l) => (
              <div key={l.id} className="flex items-center gap-3 py-2 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                <Clock size={13} className="text-gray-400 shrink-0" />
                <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{l.action}</span>
                <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{l.time}</span>
              </div>
            ))}
          </section>
        </div>
      </div>

      <TakedownModal isOpen={takedownOpen} title={item.title} onClose={() => setTakedownOpen(false)}
        onConfirm={(reason) => { setTakedownOpen(false); takedown(reason) }} />
    </div>
  )
}

function Meta({ label, value, last }: { label: string; value: string; last?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between py-1.5', !last && 'border-b border-dashed border-gray-200 dark:border-white/5')}>
      <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
      <span className="text-sm font-mono font-bold text-beatz-dark-bg dark:text-white">{value}</span>
    </div>
  )
}

function StatusPill({ status }: { status: CatalogStatus }) {
  const cls = status === 'flagged' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : status === 'published' ? 'bg-beatz-green/15 text-beatz-green' : status === 'takedown' ? 'bg-beatz-red/15 text-beatz-red' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

