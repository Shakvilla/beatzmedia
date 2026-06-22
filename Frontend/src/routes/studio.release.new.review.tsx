import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { Check, AlertCircle } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { useReleaseDraft } from '../features/studio/release-draft-context'
import { isMultiTrack, releaseTypeLabel, BUNDLE_DISCOUNT, studioArtist } from '../lib/studio-data'

export const Route = createFileRoute('/studio/release/new/review')({
  component: ReviewStep,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const money = (n: number) => `₵${n.toFixed(2)}`

function formatDate(iso: string): string {
  if (!iso) return 'Not scheduled yet'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

interface CheckItem {
  label: string
  done: boolean
  fix?: () => void
}

function ReviewStep() {
  const { draft, setField } = useReleaseDraft()
  const { toast } = useToast()
  const navigate = useNavigate()

  const multi = isMultiTrack(draft.releaseType)
  const tracks = draft.tracks
  const count = tracks.length
  const bundle = tracks.reduce((sum, t) => sum + t.price, 0)
  const albumPrice = Math.round(bundle * (1 - BUNDLE_DISCOUNT) * 100) / 100

  const metaMissing = tracks.filter((t) => t.status === 'error')
  const stillUploading = tracks.some((t) => t.status === 'uploading')
  const splitsComplete = tracks.length > 0 && tracks.every((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) === 100)

  const pending: { name: string; email: string }[] = []
  const seen = new Set<string>()
  for (const t of tracks) {
    for (const s of draft.splits[t.id] ?? []) {
      const key = s.email || s.name
      if (s.confirmation === 'pending' && key && !seen.has(key)) {
        seen.add(key)
        pending.push({ name: s.name, email: s.email })
      }
    }
  }

  const goTracks = () => navigate({ to: '/studio/release/new/tracks' })
  const goSplits = () => navigate({ to: '/studio/release/new/splits' })
  const goDetails = () => navigate({ to: '/studio/release/new/details' })

  const checklist: CheckItem[] = [
    { label: 'Cover art uploaded · 3000×3000', done: !!draft.coverImage, fix: draft.coverImage ? undefined : goDetails },
    {
      label: multi ? `All ${count} track${count === 1 ? '' : 's'} uploaded` : 'Track uploaded',
      done: count > 0 && !stillUploading,
      fix: count > 0 && !stillUploading ? undefined : goTracks,
    },
    metaMissing.length > 0
      ? { label: `${metaMissing.length} track${metaMissing.length === 1 ? '' : 's'} missing metadata · “${metaMissing[0].title}”`, done: false, fix: goTracks }
      : { label: 'All track metadata present', done: true },
    { label: 'Splits set on all tracks', done: splitsComplete, fix: splitsComplete ? undefined : goSplits },
    pending.length > 0
      ? { label: `${pending.length} collaborator${pending.length === 1 ? '' : 's'} pending · ${pending[0].email || pending[0].name}`, done: false, fix: goSplits }
      : { label: 'All collaborators confirmed', done: true },
    {
      label: multi
        ? `Pricing · ${money(draft.price)} per track / ${money(albumPrice)} album`
        : draft.price === 0 ? 'Pricing · Free' : `Pricing · ${money(draft.price)}`,
      done: true,
    },
    {
      label: 'Distribution agreement accepted',
      done: draft.agreementAccepted,
      fix: draft.agreementAccepted ? undefined : () => { setField('agreementAccepted', true); toast('Distribution agreement accepted', 'success') },
    },
    {
      label: draft.presaveGenerated || draft.visibility === 'public' ? 'Pre-save link generated' : 'Pre-save link not yet generated',
      done: draft.presaveGenerated || draft.visibility === 'public',
      fix: draft.presaveGenerated || draft.visibility === 'public' ? undefined : () => { setField('presaveGenerated', true); toast('Pre-save link generated', 'success') },
    },
  ]

  const subline = [
    draft.primaryArtist.trim() || studioArtist.name,
    draft.featuredArtists.trim() ? `feat ${draft.featuredArtists.trim()}` : null,
    releaseTypeLabel(draft.releaseType),
    `${count} track${count === 1 ? '' : 's'}`,
  ].filter(Boolean).join(' · ')

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-start">
      {/* Summary */}
      <div className="flex flex-col gap-6 max-w-md">
        <div className="w-full aspect-square rounded-2xl overflow-hidden bg-beatz-light-surface-2 dark:bg-white/5 border border-gray-200 dark:border-white/10 flex items-center justify-center">
          {draft.coverImage ? (
            <img src={draft.coverImage} alt="Cover" className="w-full h-full object-cover" />
          ) : (
            <span className="text-[11px] font-mono uppercase tracking-[0.2em] text-gray-400 dark:text-gray-500">
              {(draft.title || 'Untitled')} · Cover
            </span>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <h2 className="text-3xl font-bold text-beatz-dark-bg dark:text-white tracking-tight">{draft.title || 'Untitled release'}</h2>
          <p className="text-sm text-gray-500 dark:text-gray-300">{subline}</p>
          <p className="text-xs text-gray-400 dark:text-gray-500">Releases · {formatDate(draft.releaseDate)}</p>
        </div>

        <div className="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-white/10">
          <span className={LABEL}>{multi ? 'Album price' : 'Price'}</span>
          <span className="text-2xl font-bold text-beatz-green">{draft.price === 0 && !multi ? 'Free' : money(multi ? albumPrice : draft.price)}</span>
        </div>
      </div>

      {/* Checklist */}
      <div className="flex flex-col gap-1">
        <span className={cn(LABEL, 'mb-3')}>Checklist</span>
        {checklist.map((item, i) => (
          <div key={i} className="flex items-center gap-4 py-3 border-b border-dashed border-gray-200 dark:border-white/5">
            {item.done ? (
              <span className="w-7 h-7 rounded-full bg-beatz-green flex items-center justify-center shrink-0">
                <Check size={15} strokeWidth={3} className="text-black" />
              </span>
            ) : (
              <AlertCircle size={26} className="text-[#f6c644] shrink-0" />
            )}
            <span className={cn('flex-1 text-sm', item.done ? 'text-beatz-dark-bg dark:text-white' : 'text-beatz-dark-bg dark:text-white font-medium')}>
              {item.label}
            </span>
            {item.fix && (
              <button
                onClick={item.fix}
                className="shrink-0 px-3 py-1 rounded-full bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644] text-xs font-bold hover:bg-[#f6c644]/30 transition-colors"
              >
                Fix
              </button>
            )}
          </div>
        ))}

        <div className="flex flex-col gap-1 mt-6">
          <span className="text-sm font-bold text-beatz-green">Review SLA · 24–48h</span>
          <p className="text-xs text-gray-500 dark:text-gray-400 max-w-md leading-relaxed">
            Beatzclik checks copyright, metadata, and content. You'll get an email when approved.
          </p>
        </div>
      </div>
    </div>
  )
}
