import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useRef, useState } from 'react'
import {
  Upload, Play, Pause, GripVertical, MoreHorizontal, X,
  ArrowUp, ArrowDown, Trash2, AlertTriangle, Image as ImageIcon,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { useReleaseDraft, type UploadedTrack } from '../features/studio/release-draft-context'
import { PRICE_OPTIONS, CREATOR_REVENUE_SHARE, BUNDLE_DISCOUNT, isMultiTrack } from '../lib/studio-data'

export const Route = createFileRoute('/studio/release/new/tracks')({
  component: UploadTracksStep,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'

function makeTrack(file: File, price: number): UploadedTrack {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title: file.name.replace(/\.[^.]+$/, ''),
    duration: 0,
    status: 'uploading',
    progress: 0,
    src: URL.createObjectURL(file),
    price,
    explicit: false,
  }
}

function clock(seconds: number): string {
  if (!seconds) return '—'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

const priceLabel = (p: number) => (p === 0 ? 'FREE' : `₵${p.toFixed(2)}`)

function UploadTracksStep() {
  const { draft, addTracks, updateTrack, removeTrack, moveTrack, reorderTracks, setAllPrices, tickUploads, setField } = useReleaseDraft()
  const multi = isMultiTrack(draft.releaseType)

  // Shared single-element audio preview so the artist can confirm uploads.
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [playingId, setPlayingId] = useState<string | null>(null)
  const togglePlay = (track: UploadedTrack) => {
    const el = audioRef.current
    if (!el) return
    if (playingId === track.id) {
      el.pause()
      setPlayingId(null)
      return
    }
    el.src = track.src
    el.play().catch(() => {})
    setPlayingId(track.id)
  }

  const hasUploading = draft.tracks.some((t) => t.status === 'uploading')
  useEffect(() => {
    if (!hasUploading) return
    const id = window.setInterval(tickUploads, 400)
    return () => window.clearInterval(id)
  }, [hasUploading, tickUploads])

  // Read real duration from each file's metadata; flag files we can't read.
  const readMeta = (track: UploadedTrack) => {
    const probe = new Audio()
    probe.preload = 'metadata'
    probe.src = track.src
    probe.onloadedmetadata = () => {
      if (Number.isFinite(probe.duration) && probe.duration > 0) {
        updateTrack(track.id, { duration: Math.round(probe.duration) })
      }
    }
    probe.onerror = () => updateTrack(track.id, { status: 'error' })
  }

  const ingest = (files: FileList | null) => {
    if (!files || files.length === 0) return
    const list = multi ? Array.from(files) : [files[0]]
    const created = list.map((f) => makeTrack(f, draft.price))
    if (!multi) draft.tracks.forEach((t) => removeTrack(t.id)) // single → replace
    addTracks(created)
    created.forEach(readMeta)
  }

  return (
    <>
      <audio ref={audioRef} className="hidden" onEnded={() => setPlayingId(null)} />
      {multi ? (
        <AlbumUpload
          ingest={ingest}
          playingId={playingId}
          onTogglePlay={togglePlay}
          onRename={(id, title) => updateTrack(id, { title })}
          onRemove={removeTrack}
          onMove={moveTrack}
          onReorder={reorderTracks}
          onPrice={(id, price) => updateTrack(id, { price })}
          onBulkPrice={setAllPrices}
          onToggleExplicit={(id, explicit) => updateTrack(id, { explicit })}
        />
      ) : (
        <SingleUpload
          ingest={ingest}
          playingId={playingId}
          onTogglePlay={togglePlay}
          onRename={(id, title) => updateTrack(id, { title })}
          onRemove={removeTrack}
          onPrice={(price) => setField('price', price)}
          onCover={(url) => setField('coverImage', url)}
        />
      )}
    </>
  )
}

/* ----------------------------- Single mode ----------------------------- */

function SingleUpload({
  ingest, playingId, onTogglePlay, onRename, onRemove, onPrice, onCover,
}: {
  ingest: (f: FileList | null) => void
  playingId: string | null
  onTogglePlay: (t: UploadedTrack) => void
  onRename: (id: string, title: string) => void
  onRemove: (id: string) => void
  onPrice: (price: number) => void
  onCover: (url: string) => void
}) {
  const { draft } = useReleaseDraft()
  const audioInput = useRef<HTMLInputElement>(null)
  const coverInput = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const track = draft.tracks[0]

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-10 lg:gap-16 items-start">
      <div className="flex flex-col gap-6">
        <input ref={audioInput} type="file" accept="audio/*" className="hidden" onChange={(e) => ingest(e.target.files)} />

        {!track ? (
          <button
            type="button"
            onClick={() => audioInput.current?.click()}
            onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => { e.preventDefault(); setDragging(false); ingest(e.dataTransfer.files) }}
            className={cn(
              'w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center gap-3 py-20 transition-colors',
              dragging ? 'border-beatz-green bg-beatz-green/5' : 'border-gray-300 dark:border-white/15 hover:border-beatz-green/60',
            )}
          >
            <Upload size={30} className="text-beatz-green" />
            <span className="text-lg font-bold text-beatz-dark-bg dark:text-white">Drop your track here</span>
            <span className="text-xs text-gray-400 dark:text-gray-500">One audio file · WAV or FLAC · up to 200 MB</span>
          </button>
        ) : (
          <div className="flex flex-col gap-4">
            <span className={LABEL}>Your single</span>
            <div className="flex items-center gap-4 p-4 rounded-2xl border border-gray-200 dark:border-white/10 bg-beatz-light-surface dark:bg-white/5">
              <PlayButton playing={playingId === track.id} disabled={track.status !== 'ready'} onClick={() => onTogglePlay(track)} />
              <div className="flex flex-col flex-1 min-w-0 gap-2">
                <input
                  value={track.title}
                  onChange={(e) => onRename(track.id, e.target.value)}
                  className="bg-transparent font-bold text-beatz-dark-bg dark:text-white text-sm focus:outline-none focus:bg-white dark:focus:bg-white/10 rounded px-1 -mx-1"
                />
                <div className="flex items-center gap-3 text-xs">
                  <StatusPill track={track} />
                  <span className="font-mono text-gray-500 dark:text-gray-300">{clock(track.duration)}</span>
                </div>
              </div>
              <button onClick={() => onRemove(track.id)} aria-label="Remove track" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors">
                <X size={18} />
              </button>
            </div>
            <button onClick={() => audioInput.current?.click()} className="self-start text-xs font-bold text-beatz-green hover:underline">
              Replace file
            </button>
          </div>
        )}
      </div>

      {/* Cover + price */}
      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-3">
          <label className={LABEL}>Cover art</label>
          <input ref={coverInput} type="file" accept="image/*" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) onCover(URL.createObjectURL(f)) }} />
          <div className="w-full aspect-square rounded-2xl overflow-hidden bg-beatz-light-surface-2 dark:bg-white/5 border border-gray-200 dark:border-white/10 flex items-center justify-center">
            {draft.coverImage ? (
              <img src={draft.coverImage} alt="Cover" className="w-full h-full object-cover" />
            ) : (
              <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
                <ImageIcon size={26} />
                <span className="text-[11px] font-mono uppercase tracking-widest">Cover · 3000×3000</span>
              </div>
            )}
          </div>
          <button onClick={() => coverInput.current?.click()} className="h-9 px-4 self-start rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            {draft.coverImage ? 'Replace' : 'Add cover'}
          </button>
        </div>

        <div className="flex flex-col gap-3">
          <label className={LABEL}>Price</label>
          <div className="flex flex-wrap gap-2">
            {PRICE_OPTIONS.map((p) => {
              const active = draft.price === p
              return (
                <button key={p} type="button" onClick={() => onPrice(p)}
                  className={cn('h-10 px-4 rounded-full text-sm font-bold border transition-colors',
                    active ? 'bg-beatz-green/10 border-beatz-green text-beatz-green'
                      : 'bg-transparent border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20')}>
                  {p === 0 ? 'Free' : `₵${p.toFixed(2)}`}
                </button>
              )
            })}
          </div>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {draft.price === 0
              ? 'Free track · no charge to fans. Great for building reach.'
              : `You earn ${Math.round(CREATOR_REVENUE_SHARE * 100)}% per sale · ₵${(draft.price * CREATOR_REVENUE_SHARE).toFixed(2)} per track`}
          </p>
        </div>
      </div>
    </div>
  )
}

/* ------------------------------ Album mode ------------------------------ */

function AlbumUpload({
  ingest, playingId, onTogglePlay, onRename, onRemove, onMove, onReorder, onPrice, onBulkPrice, onToggleExplicit,
}: {
  ingest: (f: FileList | null) => void
  playingId: string | null
  onTogglePlay: (t: UploadedTrack) => void
  onRename: (id: string, title: string) => void
  onRemove: (id: string) => void
  onMove: (id: string, dir: -1 | 1) => void
  onReorder: (from: number, to: number) => void
  onPrice: (id: string, price: number) => void
  onBulkPrice: (price: number) => void
  onToggleExplicit: (id: string, explicit: boolean) => void
}) {
  const { draft } = useReleaseDraft()
  const fileInput = useRef<HTMLInputElement>(null)
  const dragFrom = useRef<number | null>(null)
  const [dragging, setDragging] = useState(false)
  const [reorderMode, setReorderMode] = useState(false)

  const tracks = draft.tracks
  const readyCount = tracks.filter((t) => t.status === 'ready').length
  const explicitCount = tracks.filter((t) => t.explicit).length
  const totalSeconds = tracks.reduce((sum, t) => sum + t.duration, 0)
  const bundle = tracks.reduce((sum, t) => sum + t.price, 0)
  const albumPrice = Math.round(bundle * (1 - BUNDLE_DISCOUNT) * 100) / 100

  return (
    <div className="flex flex-col gap-8">
      <input ref={fileInput} type="file" accept="audio/*" multiple className="hidden" onChange={(e) => ingest(e.target.files)} />

      {/* Drop hint */}
      <button
        type="button"
        onClick={() => fileInput.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); ingest(e.dataTransfer.files) }}
        className={cn(
          'w-full rounded-2xl border-2 border-dashed flex items-center justify-center gap-3 py-8 px-4 text-center transition-colors flex-wrap',
          dragging ? 'border-beatz-green bg-beatz-green/5' : 'border-gray-300 dark:border-white/15 hover:border-beatz-green/60',
        )}
      >
        <Upload size={20} className="text-beatz-green" />
        <span className="text-base font-bold text-beatz-green">Drop multiple tracks here</span>
        <span className="text-xs text-gray-400 dark:text-gray-500">or click to browse · WAV / FLAC · drag to reorder</span>
      </button>

      {/* Toolbar */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <span className={LABEL}>Tracklist · {readyCount} of {tracks.length}</span>
        <div className="flex items-center gap-2">
          <PriceDropdown
            label="Bulk price"
            onPick={onBulkPrice}
            triggerClass="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
          />
          <button
            onClick={() => setReorderMode((v) => !v)}
            className={cn('h-9 px-4 rounded-full text-xs font-bold transition-colors',
              reorderMode ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white hover:bg-gray-200 dark:hover:bg-white/15')}
          >
            Reorder
          </button>
          <button
            onClick={() => fileInput.current?.click()}
            className="h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-xs font-bold hover:bg-beatz-green/20 transition-colors"
          >
            + Add track
          </button>
        </div>
      </div>

      {tracks.length === 0 ? (
        <div className="py-16 text-center text-sm text-gray-400 dark:text-gray-500">No tracks yet — drop your album above.</div>
      ) : (
        <div className="flex flex-col">
          {/* Header */}
          <div className="flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
            <span className="w-5 shrink-0" />
            <span className="w-5 text-center shrink-0">#</span>
            <span className="flex-1">Track</span>
            <span className="w-14 text-right shrink-0">Length</span>
            <span className="w-28 text-center shrink-0">Status</span>
            <span className="w-16 text-right shrink-0">Price</span>
            <span className="w-16 shrink-0" />
          </div>

          {tracks.map((track, i) => (
            <AlbumRow
              key={track.id}
              track={track}
              index={i}
              isFirst={i === 0}
              isLast={i === tracks.length - 1}
              reorderMode={reorderMode}
              playing={playingId === track.id}
              onTogglePlay={() => onTogglePlay(track)}
              onRename={(title) => onRename(track.id, title)}
              onRemove={() => onRemove(track.id)}
              onMove={(dir) => onMove(track.id, dir)}
              onPrice={(p) => onPrice(track.id, p)}
              onToggleExplicit={() => onToggleExplicit(track.id, !track.explicit)}
              onDragStart={() => { dragFrom.current = i }}
              onDropRow={() => { if (dragFrom.current !== null) { onReorder(dragFrom.current, i); dragFrom.current = null } }}
            />
          ))}

          {/* Summary footer */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mt-6 pt-6 border-t border-gray-200 dark:border-white/10">
            <SummaryStat label="Album price" value={priceLabel(albumPrice)} sub={`auto · ${Math.round(BUNDLE_DISCOUNT * 100)}% off bundle`} accent />
            <SummaryStat label="Total runtime" value={clock(totalSeconds)} sub={`${readyCount} of ${tracks.length} tracks ready`} />
            <SummaryStat label="Explicit" value={`${explicitCount} of ${tracks.length}`} sub="flag tracks in the ··· menu" />
          </div>
        </div>
      )}
    </div>
  )
}

function AlbumRow({
  track, index, isFirst, isLast, reorderMode, playing,
  onTogglePlay, onRename, onRemove, onMove, onPrice, onToggleExplicit, onDragStart, onDropRow,
}: {
  track: UploadedTrack
  index: number
  isFirst: boolean
  isLast: boolean
  reorderMode: boolean
  playing: boolean
  onTogglePlay: () => void
  onRename: (title: string) => void
  onRemove: () => void
  onMove: (dir: -1 | 1) => void
  onPrice: (price: number) => void
  onToggleExplicit: () => void
  onDragStart: () => void
  onDropRow: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div
      draggable={reorderMode}
      onDragStart={onDragStart}
      onDragOver={(e) => reorderMode && e.preventDefault()}
      onDrop={onDropRow}
      className={cn(
        'flex items-center gap-4 px-2 py-3 border-b border-dashed border-gray-200 dark:border-white/5 group transition-colors',
        reorderMode ? 'cursor-grab active:cursor-grabbing hover:bg-gray-50 dark:hover:bg-white/5' : 'hover:bg-gray-50 dark:hover:bg-white/5',
      )}
    >
      {/* Drag handle / play */}
      <div className="w-5 shrink-0 flex items-center justify-center">
        {reorderMode ? (
          <GripVertical size={16} className="text-gray-400 dark:text-gray-500" />
        ) : (
          <PlayButton small playing={playing} disabled={track.status !== 'ready'} onClick={onTogglePlay} />
        )}
      </div>

      <span className="w-5 text-center text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{index + 1}</span>

      <div className="flex-1 min-w-0 flex items-center gap-2">
        <input
          value={track.title}
          onChange={(e) => onRename(e.target.value)}
          className="bg-transparent font-bold text-beatz-dark-bg dark:text-white text-sm focus:outline-none focus:bg-white dark:focus:bg-white/10 rounded px-1 -mx-1 min-w-0 flex-1"
        />
        {track.explicit && (
          <span className="shrink-0 w-4 h-4 rounded-sm bg-gray-300 dark:bg-white/20 text-[9px] font-bold flex items-center justify-center text-beatz-dark-bg dark:text-white" title="Explicit">E</span>
        )}
      </div>

      <span className="w-14 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0 tabular-nums">{clock(track.duration)}</span>

      <div className="w-28 flex justify-center shrink-0"><StatusPill track={track} /></div>

      <div className="w-16 flex justify-end shrink-0">
        <PriceDropdown
          label={priceLabel(track.price)}
          current={track.price}
          onPick={onPrice}
          align="right"
          triggerClass={cn('text-sm font-bold hover:underline', track.price === 0 ? 'text-beatz-green' : 'text-beatz-green')}
        />
      </div>

      <div className="w-16 flex items-center justify-end gap-1 shrink-0">
        <div className="relative">
          <button onClick={() => setMenuOpen((o) => !o)} aria-label="Track options" className="w-7 h-7 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <MoreHorizontal size={16} />
          </button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-8 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                <MenuItem icon={ArrowUp} label="Move up" disabled={isFirst} onClick={() => { onMove(-1); setMenuOpen(false) }} />
                <MenuItem icon={ArrowDown} label="Move down" disabled={isLast} onClick={() => { onMove(1); setMenuOpen(false) }} />
                <MenuItem icon={AlertTriangle} label={track.explicit ? 'Unmark explicit' : 'Mark explicit'} onClick={() => { onToggleExplicit(); setMenuOpen(false) }} />
                <MenuItem icon={Trash2} label="Remove" danger onClick={() => { onRemove(); setMenuOpen(false) }} />
              </div>
            </>
          )}
        </div>
        <button onClick={onRemove} aria-label="Remove track" className="w-7 h-7 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors">
          <X size={15} />
        </button>
      </div>
    </div>
  )
}

/* ------------------------------- Shared bits ------------------------------- */

function PlayButton({ playing, disabled, onClick, small }: { playing: boolean; disabled?: boolean; onClick: () => void; small?: boolean }) {
  const size = small ? 'w-7 h-7' : 'w-10 h-10'
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={playing ? 'Pause' : 'Play'}
      className={cn(
        size, 'rounded-full flex items-center justify-center shrink-0 transition-colors',
        disabled ? 'bg-gray-100 dark:bg-white/5 text-gray-300 dark:text-gray-600 cursor-not-allowed'
          : 'bg-beatz-green text-black hover:scale-105',
      )}
    >
      {playing ? <Pause size={small ? 13 : 18} fill="currentColor" /> : <Play size={small ? 13 : 18} fill="currentColor" className="ml-0.5" />}
    </button>
  )
}

function StatusPill({ track }: { track: UploadedTrack }) {
  if (track.status === 'error') {
    return <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-beatz-red/15 text-beatz-red">metadata missing</span>
  }
  if (track.status === 'uploading') {
    return <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300">uploading {track.progress}%</span>
  }
  return <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">ready</span>
}

function PriceDropdown({
  label, current, onPick, triggerClass, align = 'right',
}: {
  label: string
  current?: number
  onPick: (price: number) => void
  triggerClass: string
  align?: 'left' | 'right'
}) {
  const [open, setOpen] = useState(false)
  return (
    <div className="relative">
      <button onClick={() => setOpen((o) => !o)} className={triggerClass}>{label}</button>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className={cn('absolute top-9 z-50 w-32 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl', align === 'right' ? 'right-0' : 'left-0')}>
            {PRICE_OPTIONS.map((p) => (
              <button
                key={p}
                onClick={() => { onPick(p); setOpen(false) }}
                className={cn('w-full text-left px-3 py-2 text-sm font-bold hover:bg-gray-100 dark:hover:bg-white/5 transition-colors',
                  current === p ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}
              >
                {p === 0 ? 'Free' : `₵${p.toFixed(2)}`}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, disabled, danger }: {
  icon: typeof ArrowUp; label: string; onClick: () => void; disabled?: boolean; danger?: boolean
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        disabled ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
          : danger ? 'text-beatz-red hover:bg-beatz-red/10'
            : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}
    >
      <Icon size={15} /> {label}
    </button>
  )
}

function SummaryStat({ label, value, sub, accent }: { label: string; value: string; sub: string; accent?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className={LABEL}>{label}</span>
      <span className={cn('text-3xl font-bold', accent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{value}</span>
      <span className="text-[11px] text-gray-400 dark:text-gray-500">{sub}</span>
    </div>
  )
}
