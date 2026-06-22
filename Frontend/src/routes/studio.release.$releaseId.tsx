import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowLeft, Disc3, ExternalLink, Trash2, Eye, EyeOff } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { useStudio } from '../features/studio/studio-context'
import { PRICE_OPTIONS, releaseTypeLabel, studioArtist, type ReleaseStatus, type StudioRelease } from '../lib/studio-data'

export const Route = createFileRoute('/studio/release/$releaseId')({
  component: ReleaseManage,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT =
  'w-full h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'

const STATUS_META: Record<ReleaseStatus, { label: string; cls: string }> = {
  live: { label: 'Live', cls: 'bg-beatz-green/15 text-beatz-green' },
  scheduled: { label: 'Scheduled', cls: 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' },
  in_review: { label: 'In review', cls: 'bg-beatz-blue/15 text-beatz-blue' },
  draft: { label: 'Draft', cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' },
}

function coverGradient(title: string): string {
  let h = 0
  for (let i = 0; i < title.length; i++) h = (h * 31 + title.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 55% 42%), hsl(${(h + 48) % 360} 60% 30%))`
}

function ReleaseManage() {
  const { releaseId } = Route.useParams()
  const navigate = useNavigate()
  const { toast } = useToast()
  const { releases, updateRelease, removeRelease } = useStudio()
  const release = useMemo(() => releases.find((r) => r.id === releaseId), [releases, releaseId])

  const [draft, setDraft] = useState<StudioRelease | null>(release ?? null)

  if (!release || !draft) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">This release no longer exists.</p>
        <Link to="/studio/releases" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to releases</Link>
      </div>
    )
  }

  const dirty = JSON.stringify(draft) !== JSON.stringify(release)
  const live = release.status === 'live'
  const status = STATUS_META[release.status]
  const set = <K extends keyof StudioRelease>(k: K, v: StudioRelease[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d))

  const save = () => { updateRelease(release.id, draft); toast('Release updated', 'success') }
  const setStatus = (s: ReleaseStatus, msg: string) => { updateRelease(release.id, { status: s }); setDraft((d) => (d ? { ...d, status: s } : d)); toast(msg, 'success') }
  const del = () => { removeRelease(release.id); toast('Release deleted', 'success'); navigate({ to: '/studio/releases' }) }

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-2">
          <Link to="/studio/releases" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
            <ArrowLeft size={14} /> Releases
          </Link>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-display text-beatz-dark-bg dark:text-white">{release.title}</h1>
            <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', status.cls)}>{status.label}</span>
          </div>
          <span className="text-sm text-gray-500 dark:text-gray-300">{releaseTypeLabel(release.type)} · {release.trackCount} track{release.trackCount === 1 ? '' : 's'} · {release.date}</span>
        </div>
        <div className="flex items-center gap-3">
          {live && (
            <button onClick={() => navigate({ to: '/artist/$artistId', params: { artistId: studioArtist.id } })} className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white font-bold text-sm flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
              <ExternalLink size={16} /> View on BeatzClik
            </button>
          )}
          <button onClick={del} className="h-11 px-4 rounded-full text-beatz-red font-bold text-sm flex items-center gap-2 hover:bg-beatz-red/10 transition-colors">
            <Trash2 size={16} /> Delete
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[300px_1fr] gap-8 items-start">
        {/* Cover + stats */}
        <div className="flex flex-col gap-4">
          <div className="w-full aspect-square rounded-2xl overflow-hidden flex items-center justify-center" style={{ backgroundImage: coverGradient(release.title) }}>
            <Disc3 size={48} className="text-white/70" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className={cn(CARD, '!p-4 flex flex-col gap-1')}>
              <span className={LABEL}>Streams</span>
              <span className="text-xl font-bold text-beatz-dark-bg dark:text-white">{live ? release.streams.toLocaleString() : '—'}</span>
            </div>
            <div className={cn(CARD, '!p-4 flex flex-col gap-1')}>
              <span className={LABEL}>Revenue</span>
              <span className="text-xl font-bold text-beatz-green">{live ? `₵${release.revenue.toLocaleString()}` : '—'}</span>
            </div>
          </div>
        </div>

        {/* Editable details */}
        <section className={cn(CARD, 'flex flex-col gap-6')}>
          <div className="flex items-center justify-between gap-4">
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Details</h2>
            <button onClick={save} disabled={!dirty} className="h-9 px-5 rounded-full bg-beatz-green text-black font-bold text-sm hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100">
              Save changes
            </button>
          </div>

          <div className="flex flex-col gap-2.5">
            <label className={LABEL}>Title</label>
            <input className={INPUT} value={draft.title} onChange={(e) => set('title', e.target.value)} />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div className="flex flex-col gap-2.5">
              <label className={LABEL}>Price</label>
              <select className={cn(INPUT, 'appearance-none cursor-pointer')} value={draft.price} onChange={(e) => set('price', Number(e.target.value))}>
                {PRICE_OPTIONS.map((p) => <option key={p} value={p}>{p === 0 ? 'Free' : `₵${p.toFixed(2)}`}</option>)}
              </select>
            </div>
            <div className="flex flex-col gap-2.5">
              <label className={LABEL}>Release date</label>
              <input className={INPUT} value={draft.date} onChange={(e) => set('date', e.target.value)} />
            </div>
          </div>

          <div className="flex flex-col gap-3 pt-2 border-t border-gray-200 dark:border-white/10">
            <span className={LABEL}>Availability</span>
            <div className="flex items-center gap-3 flex-wrap">
              {live ? (
                <button onClick={() => setStatus('draft', `“${release.title}” unpublished`)} className="h-10 px-4 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
                  <EyeOff size={15} /> Unpublish
                </button>
              ) : (
                <button onClick={() => setStatus('live', `“${release.title}” is now live`)} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform">
                  <Eye size={15} /> Publish now
                </button>
              )}
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {live ? 'Live for fans on BeatzClik.' : release.status === 'in_review' ? 'Currently in review by Beatzclik.' : 'Not visible to fans yet.'}
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
