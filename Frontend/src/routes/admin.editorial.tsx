import { createFileRoute } from '@tanstack/react-router'
import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, MoreHorizontal, ArrowUp, ArrowDown, Trash2, Bell, Disc3, Music2 } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import type { FeaturedSlot, PushItem, CuratedPlaylist } from '../lib/admin-data'
import { featuredQuery, pushScheduleQuery, curatedPlaylistsQuery, apiSaveFeatured, apiCreatePlaylist, apiSchedulePush } from '../lib/api/queries/admin-editorial'
import { AdminLoadError } from '../components/admin/load-error'

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
  const queryClient = useQueryClient()
  const featuredQ = useQuery(featuredQuery())
  const pushQ = useQuery(pushScheduleQuery())
  const playlistsQ = useQuery(curatedPlaylistsQuery())
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  const [playlistOpen, setPlaylistOpen] = useState(false)
  const [pushOpen, setPushOpen] = useState(false)

  const createPlaylist = async (name: string) => {
    try {
      await apiCreatePlaylist(name)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'editorial', 'playlists'] })
      toast(`Playlist “${name}” created`, 'success')
      setPlaylistOpen(false)
    } catch {
      toast('Could not create the playlist', 'error')
    }
  }

  const schedulePush = async (input: {
    day: string
    timeLabel: string
    title: string
    audience: string
  }) => {
    try {
      await apiSchedulePush(input)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'editorial', 'push'] })
      toast(`Push scheduled for ${input.day}, ${input.timeLabel}`, 'success')
      setPushOpen(false)
    } catch {
      toast('Could not schedule the push', 'error')
    }
  }

  const featured = featuredQ.data ?? []

  /**
   * `PUT /featured` replaces the whole list, so every edit sends the complete array in display
   * order. On failure we invalidate rather than keeping the local edit, so the UI snaps back to
   * the server's truth instead of showing a change that never landed.
   */
  const saveOrder = async (next: FeaturedSlot[], okMsg?: string) => {
    // A ref, not the `saving` state: a state read inside this closure is stale for a second call
    // in the same render, and on a WHOLE-LIST replace two racing PUTs can land out of order and
    // resurrect a slot the user just removed.
    if (inFlight.current) return
    inFlight.current = true
    setSaving(true)
    try {
      const saved = await apiSaveFeatured(next)
      queryClient.setQueryData(['admin', 'editorial', 'featured'], saved)
      if (okMsg) toast(okMsg, 'success')
    } catch {
      toast('Could not save the featured order', 'error')
    } finally {
      inFlight.current = false
      setSaving(false)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'editorial', 'featured'] })
    }
  }

  const move = (id: string, dir: -1 | 1) => {
    const i = featured.findIndex((s) => s.id === id)
    const j = i + dir
    if (i === -1 || j < 0 || j >= featured.length) return
    const next = [...featured]
    ;[next[i], next[j]] = [next[j], next[i]]
    void saveOrder(next)
  }

  const remove = (id: string) => void saveOrder(featured.filter((s) => s.id !== id), 'Removed from featured')

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Editorial</h1>
          <span className="text-sm text-gray-500 dark:text-gray-300">Featured slots · curated playlists · push notifications</span>
        </div>
        <button onClick={() => setPlaylistOpen(true)} className="h-11 px-5 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20">
          <Plus size={18} /> New playlist
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* Home featured */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <div className="flex flex-col gap-0.5">
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Home featured · Ghana</h2>
            {/*
              GAP-14: this read "Drag to reorder · live in 2h". Both halves were false. There is no
              drag-and-drop anywhere on this page — ordering is the Move up / Move down actions in
              each row's menu — and the "2h" was a literal, not a countdown: a slot has no scheduled
              go-live time at all (FeaturedSlot is { id, title, note, sponsored }), and reordering
              takes effect on save. Describing the control that exists costs nothing; inventing a
              deadline an operator might plan around costs trust.
            */}
            <span className="text-xs text-gray-400 dark:text-gray-500">Use each row's menu to reorder · changes go live on save</span>
          </div>
          <div className="flex flex-col">
            {featuredQ.isError ? (
              <AdminLoadError label="Couldn't load featured slots." onRetry={() => featuredQ.refetch()} />
            ) : featuredQ.isPending ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : (
              <>
                {featured.map((s, i) => (
                  <FeaturedRow key={s.id} slot={s} index={i} isFirst={i === 0} isLast={i === featured.length - 1}
                    disabled={saving}
                    onMove={(d) => move(s.id, d)} onRemove={() => remove(s.id)} onReplace={() => toast(`Replace “${s.title}”`, 'info')} />
                ))}
                {featured.length === 0 && <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">No featured slots.</div>}
              </>
            )}
          </div>
        </section>

        {/* Push schedule */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Push schedule · this week</h2>
          <div className="flex flex-col">
            {pushQ.isError ? (
              <AdminLoadError label="Couldn't load the push schedule." onRetry={() => pushQ.refetch()} />
            ) : pushQ.isPending ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : (pushQ.data ?? []).length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Nothing scheduled.</div>
            ) : (
              (pushQ.data ?? []).map((p) => <PushRow key={p.id} push={p} />)
            )}
          </div>
          <button onClick={() => setPushOpen(true)} className="self-start h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors">
            <Plus size={15} /> Schedule push
          </button>
        </section>
      </div>

      {/* Curated playlists */}
      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Curated playlists</h2>
        {playlistsQ.isError ? (
          <AdminLoadError label="Couldn't load curated playlists." onRetry={() => playlistsQ.refetch()} />
        ) : playlistsQ.isPending ? (
          <div className="py-8 text-sm text-center text-gray-400 dark:text-gray-500">Loading…</div>
        ) : (playlistsQ.data ?? []).length === 0 ? (
          <p className="py-8 text-sm text-center text-gray-400 dark:text-gray-500">No curated playlists yet.</p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {(playlistsQ.data ?? []).map((p) => <PlaylistCard key={p.id} playlist={p} onOpen={() => toast(`Open “${p.name}”`, 'info')} />)}
          </div>
        )}
      </section>

      {playlistOpen && (
        <EditorialDialog title="New curated playlist" onClose={() => setPlaylistOpen(false)}
          fields={[{ name: 'name', label: 'Playlist name', placeholder: 'e.g. Accra After Dark' }]}
          submitLabel="Create playlist"
          onSubmit={(v) => createPlaylist(v.name)} />
      )}

      {pushOpen && (
        <EditorialDialog title="Schedule push notification" onClose={() => setPushOpen(false)}
          fields={[
            { name: 'title', label: 'Message', placeholder: 'New drops from artists you follow' },
            { name: 'day', label: 'Day', placeholder: 'e.g. Friday' },
            { name: 'timeLabel', label: 'Time', placeholder: 'e.g. 18:00' },
            { name: 'audience', label: 'Audience', placeholder: 'e.g. All fans in Ghana' },
          ]}
          submitLabel="Schedule"
          onSubmit={(v) => schedulePush({ title: v.title, day: v.day, timeLabel: v.timeLabel, audience: v.audience })} />
      )}
    </div>
  )
}

/**
 * Minimal dialog for the two editorial create actions.
 *
 * <p>Both buttons previously raised a toast and called nothing, while
 * `POST /admin/editorial/playlists` and `/push` sat guarded and working behind them. The backend
 * validates every field as `@NotBlank`, so submit stays disabled until each one has a value —
 * a 422 here would be the UI's fault, not the operator's.
 */
function EditorialDialog({ title, fields, submitLabel, onSubmit, onClose }: {
  title: string
  fields: { name: string; label: string; placeholder: string }[]
  submitLabel: string
  onSubmit: (values: Record<string, string>) => void | Promise<void>
  onClose: () => void
}) {
  const [values, setValues] = useState<Record<string, string>>(
    Object.fromEntries(fields.map((f) => [f.name, ''])),
  )
  const [submitting, setSubmitting] = useState(false)
  const complete = fields.every((f) => (values[f.name] ?? '').trim().length > 0)

  const submit = async () => {
    if (!complete || submitting) return
    setSubmitting(true)
    try {
      await onSubmit(Object.fromEntries(fields.map((f) => [f.name, values[f.name].trim()])))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
      <div role="dialog" aria-label={title}
        className="relative w-full max-w-md rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-white/10 p-6 flex flex-col gap-4 shadow-xl">
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">{title}</h2>
        {fields.map((f) => (
          <label key={f.name} className="flex flex-col gap-1.5">
            <span className="text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400">{f.label}</span>
            <input
              value={values[f.name]}
              onChange={(e) => setValues((v) => ({ ...v, [f.name]: e.target.value }))}
              onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
              placeholder={f.placeholder}
              className="h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60"
            />
          </label>
        ))}
        <div className="flex items-center justify-end gap-2 pt-2">
          <button onClick={onClose}
            className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            Cancel
          </button>
          <button onClick={() => void submit()} disabled={!complete || submitting}
            className="h-10 px-5 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100">
            {submitting ? 'Saving…' : submitLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

function FeaturedRow({ slot: s, index, isFirst, isLast, disabled, onMove, onRemove, onReplace }: {
  slot: FeaturedSlot; index: number; isFirst: boolean; isLast: boolean; disabled?: boolean
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
              <MenuItem icon={ArrowUp} label="Move up" disabled={isFirst || disabled} onClick={() => { onMove(-1); setMenuOpen(false) }} />
              <MenuItem icon={ArrowDown} label="Move down" disabled={isLast || disabled} onClick={() => { onMove(1); setMenuOpen(false) }} />
              <MenuItem icon={Music2} label="Replace" onClick={() => { onReplace(); setMenuOpen(false) }} />
              <MenuItem icon={Trash2} label="Remove" danger disabled={disabled} onClick={() => { onRemove(); setMenuOpen(false) }} />
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
