import { createFileRoute, Link } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { MoreHorizontal, Trash2, Mail, Plus } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { useReleaseDraft, type SplitEntry } from '../features/studio/release-draft-context'
import { CREATOR_REVENUE_SHARE, studioArtist } from '../lib/studio-data'

export const Route = createFileRoute('/studio/release/new/splits')({
  component: SplitsStep,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const PLATFORM_FEE = 1 - CREATOR_REVENUE_SHARE
const money = (n: number) => `₵${n.toFixed(2)}`
const genId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`

function SplitsStep() {
  const { draft, setTrackSplits, applySplitsToAll } = useReleaseDraft()
  const { toast } = useToast()
  const tracks = draft.tracks

  const selfEntry = (percent: number): SplitEntry => ({
    id: genId(),
    name: draft.primaryArtist.trim() || studioArtist.name,
    email: 'me',
    role: 'Performer · Writer',
    percent,
    confirmation: 'self',
  })

  const [selectedId, setSelectedId] = useState<string>(tracks[0]?.id ?? '')
  const [allMode, setAllMode] = useState(false)

  // Keep a valid selection as tracks change.
  useEffect(() => {
    if (!allMode && !tracks.some((t) => t.id === selectedId) && tracks[0]) {
      setSelectedId(tracks[0].id)
    }
  }, [tracks, selectedId, allMode])

  // Seed any track that has no splits yet with the artist at 100%.
  useEffect(() => {
    for (const t of tracks) {
      if (!draft.splits[t.id]) setTrackSplits(t.id, [selfEntry(100)])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tracks, draft.splits])

  if (tracks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Upload your tracks before setting up splits.</p>
        <Link to="/studio/release/new/tracks" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">
          Back to tracks
        </Link>
      </div>
    )
  }

  const scopeTrack = allMode ? tracks[0] : tracks.find((t) => t.id === selectedId) ?? tracks[0]
  const splits = draft.splits[scopeTrack.id] ?? []
  const total = splits.reduce((sum, s) => sum + s.percent, 0)
  const valid = total === 100
  const price = scopeTrack.price

  const commit = (next: SplitEntry[]) => {
    if (allMode) applySplitsToAll(next)
    else setTrackSplits(scopeTrack.id, next)
  }

  const patch = (id: string, p: Partial<SplitEntry>) => commit(splits.map((s) => (s.id === id ? { ...s, ...p } : s)))
  const remove = (id: string) => commit(splits.filter((s) => s.id !== id))
  const addCollaborator = () =>
    commit([...splits, { id: genId(), name: '', email: '', role: 'Collaborator', percent: 0, confirmation: 'pending' }])

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-10 lg:gap-16 items-start">
      {/* Left: editor */}
      <div className="flex flex-col gap-6">
        <p className="text-sm text-gray-500 dark:text-gray-300 -mt-2 max-w-2xl">
          Royalties auto-split on every sale, stream, and tip. Each collaborator confirms via email before the release goes live.
        </p>

        <span className={LABEL}>
          {allMode ? 'All tracks · splits' : `Per-track splits · “${scopeTrack.title || 'Untitled'}”`}
        </span>

        {/* Track chips */}
        <div className="flex flex-wrap gap-2">
          {tracks.map((t) => {
            const active = !allMode && t.id === scopeTrack.id
            return (
              <button
                key={t.id}
                onClick={() => { setAllMode(false); setSelectedId(t.id) }}
                className={cn('h-9 px-4 rounded-full text-sm font-bold border transition-colors max-w-[180px] truncate',
                  active ? 'bg-beatz-green/15 border-beatz-green text-beatz-green'
                    : 'bg-transparent border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20')}
              >
                {t.title || 'Untitled'}
              </button>
            )
          })}
          {tracks.length > 1 && (
            <button
              onClick={() => setAllMode((v) => !v)}
              className={cn('h-9 px-4 rounded-full text-sm font-bold border transition-colors',
                allMode ? 'bg-beatz-green/15 border-beatz-green text-beatz-green'
                  : 'bg-transparent border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20')}
            >
              + All tracks
            </button>
          )}
        </div>

        {/* Table */}
        <div className="flex flex-col">
          <div className="flex items-center gap-4 px-2 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
            <span className="flex-1">Collaborator</span>
            <span className="w-32 shrink-0">Role</span>
            <span className="w-16 text-right shrink-0">Split</span>
            <span className="w-28 text-center shrink-0">Confirmed</span>
            <span className="w-8 shrink-0" />
          </div>

          {splits.map((s) => (
            <SplitRow
              key={s.id}
              entry={s}
              onName={(name) => patch(s.id, { name })}
              onRole={(role) => patch(s.id, { role })}
              onPercent={(percent) => patch(s.id, { percent })}
              onRemove={() => remove(s.id)}
              onResend={() => toast(`Invite re-sent to ${s.name || 'collaborator'}`, 'success')}
            />
          ))}

          {/* Total */}
          <div className="flex items-center gap-4 px-2 py-3 mt-1">
            <span className="flex-1 text-base font-bold text-beatz-dark-bg dark:text-white">Total</span>
            <span className={cn('w-16 text-right text-base font-bold', valid ? 'text-beatz-green' : 'text-beatz-red')}>{total}%</span>
            <span className="w-28 shrink-0" />
            <span className="w-8 shrink-0" />
          </div>
          {!valid && (
            <span className="px-2 text-xs text-beatz-red">Splits must add up to 100% (currently {total}%).</span>
          )}
        </div>

        <button
          onClick={addCollaborator}
          className="self-start h-10 px-5 rounded-full bg-beatz-green/10 text-beatz-green font-bold text-sm flex items-center gap-2 hover:bg-beatz-green/20 transition-colors"
        >
          <Plus size={16} /> Add collaborator
        </button>
      </div>

      {/* Right: breakdown + presets */}
      <div className="flex flex-col gap-10">
        <div className="flex flex-col gap-3">
          <span className={LABEL}>{price === 0 ? 'Free track' : `Per ${money(price)} sale`} · breakdown</span>
          {price === 0 ? (
            <p className="text-xs text-gray-500 dark:text-gray-400">This track is free — there's no sale revenue to split. Streams and tips still split by the percentages above.</p>
          ) : (
            <div className="flex flex-col gap-2.5 text-sm">
              <div className="flex items-center justify-between text-gray-400 dark:text-gray-500">
                <span>Beatzclik fee ({Math.round(PLATFORM_FEE * 100)}%)</span>
                <span className="font-mono">{money(price * PLATFORM_FEE)}</span>
              </div>
              {splits.map((s) => (
                <div key={s.id} className="flex items-center justify-between">
                  <span className="text-gray-600 dark:text-gray-300 truncate pr-2">{s.name || 'Collaborator'} ({s.percent}%)</span>
                  <span className="font-mono font-bold text-beatz-green shrink-0">{money(price * CREATOR_REVENUE_SHARE * (s.percent / 100))}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="flex flex-col gap-3">
          <span className={LABEL}>Presets</span>
          <div className="flex flex-col gap-2 items-start">
            <PresetButton label="Solo (100% me)" onClick={() => commit([selfEntry(100)])} />
            <PresetButton label="Producer 50/50" onClick={() => commit([
              selfEntry(50),
              { id: genId(), name: '', email: '', role: 'Producer', percent: 50, confirmation: 'pending' },
            ])} />
            <PresetButton label="Apply to all tracks" onClick={() => { applySplitsToAll(splits); toast('Splits applied to all tracks', 'success') }} />
          </div>
        </div>
      </div>
    </div>
  )
}

function SplitRow({
  entry, onName, onRole, onPercent, onRemove, onResend,
}: {
  entry: SplitEntry
  onName: (v: string) => void
  onRole: (v: string) => void
  onPercent: (v: number) => void
  onRemove: () => void
  onResend: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const isSelf = entry.email === 'me'
  const initial = (entry.name.trim()[0] ?? '?').toUpperCase()

  return (
    <div className="flex items-center gap-4 px-2 py-3 border-b border-dashed border-gray-200 dark:border-white/5 group">
      {/* Collaborator */}
      <div className="flex items-center gap-3 flex-1 min-w-0">
        <div className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center shrink-0 text-xs font-bold text-gray-600 dark:text-gray-300">
          {initial}
        </div>
        <div className="flex flex-col min-w-0 flex-1">
          <input
            value={entry.name}
            onChange={(e) => onName(e.target.value)}
            placeholder="Name"
            className="bg-transparent font-bold text-beatz-dark-bg dark:text-white text-sm focus:outline-none focus:bg-white dark:focus:bg-white/10 rounded px-1 -mx-1"
          />
          <span className="text-xs text-gray-400 dark:text-gray-500 truncate px-1 -mx-1">{isSelf ? 'me' : entry.email || 'no email yet'}</span>
        </div>
      </div>

      {/* Role */}
      <input
        value={entry.role}
        onChange={(e) => onRole(e.target.value)}
        placeholder="Role"
        className="w-32 shrink-0 bg-transparent text-sm text-gray-600 dark:text-gray-300 focus:outline-none focus:bg-white dark:focus:bg-white/10 rounded px-1"
      />

      {/* Split % */}
      <div className="w-16 shrink-0 flex items-center justify-end gap-0.5">
        <input
          type="number"
          min={0}
          max={100}
          value={entry.percent}
          onChange={(e) => onPercent(Math.max(0, Math.min(100, Number(e.target.value) || 0)))}
          className="w-10 bg-transparent text-right font-bold text-beatz-green text-sm focus:outline-none focus:bg-white dark:focus:bg-white/10 rounded tabular-nums [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
        />
        <span className="text-beatz-green font-bold text-sm">%</span>
      </div>

      {/* Confirmed */}
      <div className="w-28 flex justify-center shrink-0">
        <ConfirmBadge state={entry.confirmation} />
      </div>

      {/* Menu */}
      <div className="w-8 shrink-0 relative flex justify-end">
        <button onClick={() => setMenuOpen((o) => !o)} aria-label="Options" className="w-7 h-7 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
          <MoreHorizontal size={16} />
        </button>
        {menuOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 top-8 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
              {entry.confirmation === 'pending' && (
                <button onClick={() => { onResend(); setMenuOpen(false) }} className="w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
                  <Mail size={15} /> Resend invite
                </button>
              )}
              <button
                onClick={() => { if (!isSelf) { onRemove() } setMenuOpen(false) }}
                disabled={isSelf}
                className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
                  isSelf ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed' : 'text-beatz-red hover:bg-beatz-red/10')}
              >
                <Trash2 size={15} /> Remove
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function ConfirmBadge({ state }: { state: SplitEntry['confirmation'] }) {
  const map = {
    self: { label: 'self', cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' },
    confirmed: { label: '✓ confirmed', cls: 'bg-beatz-green/15 text-beatz-green' },
    pending: { label: 'pending', cls: 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' },
    auto: { label: 'auto', cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' },
  }[state]
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', map.cls)}>{map.label}</span>
}

function PresetButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
    >
      {label}
    </button>
  )
}
