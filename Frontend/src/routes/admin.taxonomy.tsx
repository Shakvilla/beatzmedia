import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Eye, EyeOff, Loader2 } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'
import {
  adminTaxonomyQuery,
  apiCreateTerm,
  apiDeleteTerm,
  apiUpdateTerm,
  type AdminTaxonomyTerm,
  type TaxonomyKind,
} from '../lib/api/queries/taxonomy'

export const Route = createFileRoute('/admin/taxonomy')({
  component: AdminTaxonomy,
})

const CARD =
  'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT =
  'h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white focus:outline-none focus:border-beatz-green/60'

/**
 * The four lists an operator can edit. Each was previously unmanageable: genres were a TypeScript
 * union AND two Java enums, podcast/event categories were Postgres CHECK constraints, browse tiles
 * had their own table.
 */
const KINDS: { kind: TaxonomyKind; label: string; blurb: string; colored?: boolean }[] = [
  {
    kind: 'genre',
    label: 'Genres',
    blurb: 'Used by releases, the store, artist profiles and the studio upload wizard.',
  },
  {
    kind: 'podcast_category',
    label: 'Podcast categories',
    blurb: 'Used by podcast shows and the podcasts browse filters.',
  },
  {
    kind: 'event_category',
    label: 'Event categories',
    blurb: 'Used by events and ticketing.',
  },
  {
    kind: 'browse_category',
    label: 'Browse tiles',
    blurb: 'The "Browse by mood & genre" tiles on the fan home page. These carry a colour.',
    colored: true,
  },
]

function AdminTaxonomy() {
  const [kind, setKind] = useState<TaxonomyKind>('genre')
  const active = KINDS.find((k) => k.kind === kind)!

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Genres &amp; categories</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">
          Controlled lists used across the platform. Changes take effect everywhere immediately.
        </span>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        {KINDS.map((k) => (
          <button
            key={k.kind}
            onClick={() => setKind(k.kind)}
            className={cn(
              'px-4 py-2 rounded-full text-sm font-bold transition-colors',
              kind === k.kind
                ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                : 'bg-gray-100 dark:bg-white/5 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/10',
            )}>
            {k.label}
          </button>
        ))}
      </div>

      {/* Remount per kind so create-form state never leaks between lists. */}
      <TermList key={kind} kind={kind} blurb={active.blurb} colored={active.colored} />
    </div>
  )
}

function TermList({
  kind,
  blurb,
  colored,
}: {
  kind: TaxonomyKind
  blurb: string
  colored?: boolean
}) {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isPending, isError, refetch } = useQuery(adminTaxonomyQuery(kind))
  const [label, setLabel] = useState('')
  const [colorClass, setColorClass] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'taxonomy', kind] })

  /**
   * Also drop the PUBLIC cache for this kind. Without it a newly created genre would not appear in
   * the studio wizard or the podcast filter chips until their 5-minute staleTime expired — the
   * admin would have made a change that appeared to do nothing.
   */
  const refreshEverywhere = async () => {
    await refresh()
    await queryClient.invalidateQueries({ queryKey: ['taxonomy', kind] })
  }

  const message = (err: unknown, fallback: string) =>
    err instanceof ApiError ? err.message : fallback

  const create = async () => {
    const value = label.trim()
    if (!value || creating) return
    setCreating(true)
    try {
      await apiCreateTerm(kind, {
        label: value,
        colorClass: colored && colorClass.trim() ? colorClass.trim() : null,
      })
      setLabel('')
      setColorClass('')
      await refreshEverywhere()
      toast(`“${value}” added`, 'success')
    } catch (err) {
      toast(message(err, 'Could not add that term'), 'error')
    } finally {
      setCreating(false)
    }
  }

  const rename = async (term: AdminTaxonomyTerm, next: string) => {
    const value = next.trim()
    if (!value || value === term.label) return
    setBusyId(term.id)
    try {
      await apiUpdateTerm(term.id, { label: value })
      await refreshEverywhere()
      // Renames repoint existing content server-side; say so, because the operator is about to
      // wonder what happened to the releases that used the old spelling.
      toast(
        term.usageCount > 0
          ? `Renamed to “${value}” · ${term.usageCount} item${term.usageCount === 1 ? '' : 's'} updated`
          : `Renamed to “${value}”`,
        'success',
      )
    } catch (err) {
      toast(message(err, 'Could not rename that term'), 'error')
      await refresh() // put the input back to what the server holds
    } finally {
      setBusyId(null)
    }
  }

  const toggleActive = async (term: AdminTaxonomyTerm) => {
    setBusyId(term.id)
    try {
      await apiUpdateTerm(term.id, { active: !term.active })
      await refreshEverywhere()
      toast(term.active ? `“${term.label}” hidden from pickers` : `“${term.label}” re-enabled`, 'success')
    } catch (err) {
      toast(message(err, 'Could not update that term'), 'error')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (term: AdminTaxonomyTerm) => {
    setBusyId(term.id)
    try {
      await apiDeleteTerm(term.id)
      await refreshEverywhere()
      toast(`“${term.label}” deleted`, 'success')
    } catch (err) {
      // The 409 body names the usage count, which is more useful than anything we could invent.
      toast(message(err, 'Could not delete that term'), 'error')
    } finally {
      setBusyId(null)
    }
  }

  if (isError) return <AdminLoadError label="genres & categories" onRetry={() => void refetch()} />

  return (
    <div className={cn(CARD, 'flex flex-col gap-5')}>
      <span className="text-sm text-gray-500 dark:text-gray-300">{blurb}</span>

      {/* Create */}
      <div className="flex items-end gap-3 flex-wrap">
        <div className="flex flex-col gap-1.5">
          <label className="text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400">
            New entry
          </label>
          <input
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void create() }}
            placeholder="e.g. Afro-fusion"
            className={cn(INPUT, 'w-56')}
          />
        </div>
        {colored && (
          <div className="flex flex-col gap-1.5">
            <label className="text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400">
              Tailwind gradient
            </label>
            <input
              value={colorClass}
              onChange={(e) => setColorClass(e.target.value)}
              placeholder="from-orange-500 to-amber-400"
              className={cn(INPUT, 'w-72')}
            />
          </div>
        )}
        <button
          onClick={() => void create()}
          disabled={!label.trim() || creating}
          className="h-11 px-5 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100">
          {creating ? <Loader2 size={15} className="animate-spin" /> : <Plus size={15} />} Add
        </button>
      </div>

      {/* List */}
      {isPending ? (
        <span className="text-sm text-gray-500 dark:text-gray-400">Loading…</span>
      ) : data.length === 0 ? (
        <span className="text-sm text-gray-500 dark:text-gray-400">Nothing in this list yet.</span>
      ) : (
        <div className="flex flex-col">
          {data.map((term) => (
            <TermRow
              key={term.id}
              term={term}
              busy={busyId === term.id}
              onRename={(next) => void rename(term, next)}
              onToggle={() => void toggleActive(term)}
              onDelete={() => void remove(term)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function TermRow({
  term,
  busy,
  onRename,
  onToggle,
  onDelete,
}: {
  term: AdminTaxonomyTerm
  busy: boolean
  onRename: (next: string) => void
  onToggle: () => void
  onDelete: () => void
}) {
  const [draft, setDraft] = useState(term.label)

  return (
    <div className="flex items-center gap-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
      <input
        value={draft}
        disabled={busy}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => onRename(draft)}
        onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
        className={cn(
          INPUT,
          'flex-1 min-w-0 !h-10',
          !term.active && 'opacity-50 line-through',
        )}
      />
      <span className="text-xs text-gray-400 dark:text-gray-500 font-mono shrink-0 hidden sm:inline">
        {term.slug}
      </span>
      {/*
        The usage count is why deletion is never a surprise: the button is disabled with the reason
        on it, instead of firing a request that comes back 409.
      */}
      <span className="text-xs text-gray-500 dark:text-gray-400 shrink-0 w-20 text-right">
        {term.usageCount === 0 ? 'unused' : `${term.usageCount} in use`}
      </span>
      <button
        onClick={onToggle}
        disabled={busy}
        title={term.active ? 'Hide from pickers (keeps existing content)' : 'Show in pickers again'}
        className="h-9 w-9 rounded-full flex items-center justify-center text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/5 disabled:opacity-40">
        {term.active ? <Eye size={15} /> : <EyeOff size={15} />}
      </button>
      <button
        onClick={onDelete}
        disabled={busy || term.usageCount > 0}
        title={
          term.usageCount > 0
            ? `Used by ${term.usageCount} item${term.usageCount === 1 ? '' : 's'} — reassign them first, or hide it instead`
            : 'Delete'
        }
        className="h-9 w-9 rounded-full flex items-center justify-center text-beatz-red hover:bg-beatz-red/10 disabled:opacity-30 disabled:hover:bg-transparent disabled:cursor-not-allowed">
        {busy ? <Loader2 size={15} className="animate-spin" /> : <Trash2 size={15} />}
      </button>
    </div>
  )
}
