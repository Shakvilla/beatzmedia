import { createFileRoute, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { ArrowRight, ArrowLeft, Check } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { ReleaseDraftProvider, useReleaseDraft, type ReleaseDraft } from '../features/studio/release-draft-context'
import { RELEASE_WIZARD_STEPS, releaseTypeLabel, isMultiTrack, type ReleaseStepSlug } from '../lib/studio-data'
import { useStudio } from '../features/studio/studio-context'
import { studioSettingsQuery } from '../lib/api/queries/studio'

/** Format an ISO date (yyyy-mm-dd) as 'Mon DD, YYYY', or a fallback label. */
function releaseDateLabel(iso: string): string {
  if (!iso) return 'TBD'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? 'TBD' : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

/** Step title shown in the wizard header — the Tracks step adapts to release type. */
function wizardTitle(slug: ReleaseStepSlug, draft: ReleaseDraft): string {
  if (slug !== 'tracks') {
    return RELEASE_WIZARD_STEPS.find((s) => s.slug === slug)?.title ?? ''
  }
  if (!isMultiTrack(draft.releaseType)) return 'Upload your single'
  const name = draft.title.trim() || 'Untitled'
  return `Build ${releaseTypeLabel(draft.releaseType).toLowerCase() === 'ep' ? 'EP' : releaseTypeLabel(draft.releaseType).toLowerCase()} · “${name}”`
}

export const Route = createFileRoute('/studio/release/new')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioSettingsQuery()),
  component: WizardRoot,
})

function WizardRoot() {
  // Seed the draft from the artist's saved release defaults.
  const { data: settings } = useSuspenseQuery(studioSettingsQuery())
  return (
    <ReleaseDraftProvider initial={{ price: settings.defaults.trackPrice, visibility: settings.defaults.releaseVisibility }}>
      <WizardChrome />
    </ReleaseDraftProvider>
  )
}

const STEP_PATHS = {
  details: '/studio/release/new/details',
  tracks: '/studio/release/new/tracks',
  splits: '/studio/release/new/splits',
  review: '/studio/release/new/review',
} as const

function WizardChrome() {
  const location = useLocation()
  const navigate = useNavigate()
  const { toast } = useToast()
  const { draft, reset } = useReleaseDraft()
  const { addRelease } = useStudio()

  const slug = (location.pathname.split('/').pop() ?? 'details') as ReleaseStepSlug
  const stepIndex = Math.max(0, RELEASE_WIZARD_STEPS.findIndex((s) => s.slug === slug))
  const isLast = stepIndex === RELEASE_WIZARD_STEPS.length - 1

  const goTo = (s: ReleaseStepSlug) => navigate({ to: STEP_PATHS[s] })

  // Guard against deep-linking to a step whose prerequisites aren't met.
  useEffect(() => {
    if (slug === 'details') return
    if (!draft.title.trim()) { goTo('details'); return }
    if ((slug === 'splits' || slug === 'review') && draft.tracks.length === 0) { goTo('tracks'); return }
    if (slug === 'review') {
      const badSplit = draft.tracks.some((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) !== 100)
      if (badSplit) goTo('splits')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug])

  const handleContinue = () => {
    // Light per-step validation.
    if (slug === 'details' && !draft.title.trim()) {
      toast('Add a release title to continue', 'error')
      return
    }
    if (slug === 'tracks' && draft.tracks.length === 0) {
      toast('Upload at least one track to continue', 'error')
      return
    }
    if (slug === 'splits') {
      const bad = draft.tracks.find((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) !== 100)
      if (bad) {
        toast(`Splits for “${bad.title || 'a track'}” must total 100%`, 'error')
        return
      }
    }
    if (isLast) {
      if (!draft.coverImage) { toast('Add cover art before submitting', 'error'); return }
      if (draft.tracks.length === 0) { toast('Upload at least one track', 'error'); return }
      const badSplit = draft.tracks.find((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) !== 100)
      if (badSplit) { toast(`Splits for “${badSplit.title || 'a track'}” must total 100%`, 'error'); return }
      if (!draft.agreementAccepted) { toast('Accept the distribution agreement to submit', 'error'); return }
    }
    if (isLast) {
      addRelease({
        id: `rel-${Date.now()}`,
        title: draft.title.trim() || 'Untitled release',
        type: draft.releaseType,
        status: 'in_review',
        date: releaseDateLabel(draft.releaseDate),
        trackCount: draft.tracks.length,
        streams: 0,
        revenue: 0,
        price: draft.price,
      })
      toast('Release submitted for review 🎉', 'success')
      reset()
      navigate({ to: '/studio/releases' })
      return
    }
    goTo(RELEASE_WIZARD_STEPS[stepIndex + 1].slug)
  }

  return (
    <div className="flex flex-col gap-10">
      {/* Top bar */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex flex-col gap-2">
          <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-gray-400 dark:text-gray-500">
            New release · Step {stepIndex + 1} of {RELEASE_WIZARD_STEPS.length}
            {slug === 'tracks' && ` · ${releaseTypeLabel(draft.releaseType)}`}
          </span>
          <h1 className="text-display text-beatz-dark-bg dark:text-white">{wizardTitle(slug, draft)}</h1>
        </div>

        <div className="flex items-center gap-3">
          {stepIndex > 0 && (
            <button
              onClick={() => goTo(RELEASE_WIZARD_STEPS[stepIndex - 1].slug)}
              className="h-11 px-5 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white font-bold text-sm flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
            >
              <ArrowLeft size={16} /> Back
            </button>
          )}
          {!isLast && (
            <button
              onClick={() => toast('Draft saved', 'success')}
              className="h-11 px-5 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white font-bold text-sm hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
            >
              Save draft
            </button>
          )}
          <button
            onClick={handleContinue}
            className={cn(
              'h-11 px-6 rounded-full font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform',
              isLast ? 'bg-beatz-green text-black shadow-lg shadow-beatz-green/20' : 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black',
            )}
          >
            {isLast ? 'Submit for review' : 'Continue'}
            {!isLast && <ArrowRight size={16} />}
          </button>
        </div>
      </div>

      {/* Step indicator */}
      <div className="flex items-center gap-2 sm:gap-4">
        {RELEASE_WIZARD_STEPS.map((s, i) => {
          const done = i < stepIndex
          const active = i === stepIndex
          return (
            <div key={s.slug} className="flex items-center gap-2 sm:gap-4 min-w-0">
              <button
                onClick={() => goTo(s.slug)}
                className="flex items-center gap-2 min-w-0 group"
              >
                <span
                  className={cn(
                    'w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold shrink-0 transition-colors',
                    active && 'bg-beatz-green text-black',
                    done && 'bg-beatz-green/20 text-beatz-green',
                    !active && !done && 'bg-gray-200 dark:bg-white/10 text-gray-500 dark:text-gray-400',
                  )}
                >
                  {done ? <Check size={13} strokeWidth={3} /> : i + 1}
                </span>
                <span
                  className={cn(
                    'text-xs font-bold truncate transition-colors',
                    active ? 'text-beatz-dark-bg dark:text-white' : 'text-gray-400 dark:text-gray-500 group-hover:text-gray-600 dark:group-hover:text-gray-300',
                  )}
                >
                  {s.label}
                </span>
              </button>
              {i < RELEASE_WIZARD_STEPS.length - 1 && (
                <span className="hidden sm:block w-8 h-px bg-gray-200 dark:bg-white/10" />
              )}
            </div>
          )
        })}
      </div>

      <Outlet />
    </div>
  )
}
