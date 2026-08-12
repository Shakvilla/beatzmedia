import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ArrowRight, Loader2 } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { useCollection } from '../features/collection/collection-context'
import { taxonomyQuery } from '../lib/api/queries/taxonomy'
import { homeQuery } from '../lib/api/queries/catalog'
import {
  apiCompleteOnboarding,
  MIN_ONBOARDING_GENRES,
  MIN_ONBOARDING_ARTISTS,
} from '../lib/api/queries/fan-preferences'
import { ApiError } from '../lib/api/errors'

export const Route = createFileRoute('/onboarding')({
  component: Onboarding,
})

const FALLBACK_TILE = 'bg-gradient-to-br from-gray-600 to-gray-400'

function Onboarding() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const { followedArtists, toggleFollowedArtist } = useCollection()

  const { data: genres = [], isPending: genresPending } = useQuery(taxonomyQuery('genre'))
  const { data: home } = useQuery(homeQuery())
  const artists = home?.rails.popularArtists ?? []

  const [step, setStep] = useState<1 | 2>(1)
  const [picked, setPicked] = useState<string[]>([])
  const [saving, setSaving] = useState(false)

  const toggleGenre = (label: string) =>
    setPicked((p) => (p.includes(label) ? p.filter((g) => g !== label) : [...p, label]))

  /**
   * A fresh install has no artists at all, and a blocking gate that demands three of them would
   * dead-end the very first fan who signs up. The requirement is therefore whatever the catalogue
   * can actually satisfy — never more than it holds.
   */
  const artistTarget = Math.min(MIN_ONBOARDING_ARTISTS, artists.length)
  const genresDone = picked.length >= MIN_ONBOARDING_GENRES
  const artistsDone = followedArtists.length >= artistTarget

  const finish = async () => {
    if (!genresDone || saving) return
    setSaving(true)
    try {
      const saved = await apiCompleteOnboarding(picked)
      // Write the server's answer straight into the cache rather than invalidating it. Invalidation
      // only MARKS the entry stale: during the refetch the app shell still reads the old
      // `onboarded: false` and its redirect effect fires, bouncing the fan back here the instant
      // they finish. Seeding the value makes the gate pass synchronously.
      queryClient.setQueryData(['me', 'preferences'], saved)
      toast('Welcome to BeatzClik', 'success')
      navigate({ to: '/' })
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not save your picks', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-8 max-w-4xl mx-auto py-8">
      <div className="flex flex-col gap-2">
        <span className="text-[11px] font-bold uppercase tracking-[0.15em] text-beatz-green">
          Step {step} of 2
        </span>
        <h1 className="text-display text-beatz-dark-bg dark:text-white">
          {step === 1 ? 'What do you listen to?' : 'Follow a few artists'}
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-300">
          {step === 1
            ? `Pick at least ${MIN_ONBOARDING_GENRES} genres so we can shape your home page.`
            : artists.length === 0
              ? 'No artists have joined yet — you can skip this and follow people as they arrive.'
              : `Follow at least ${artistTarget} to fill your feed.`}
        </p>
      </div>

      {step === 1 ? (
        <>
          {genresPending ? (
            <span className="text-sm text-gray-500 dark:text-gray-400">Loading genres…</span>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {genres.map((g) => {
                const on = picked.includes(g.label)
                return (
                  <button
                    key={g.id}
                    onClick={() => toggleGenre(g.label)}
                    aria-pressed={on}
                    className={cn(
                      'relative overflow-hidden rounded-xl p-4 aspect-[2/1] flex items-start shadow-md transition-transform duration-200',
                      g.colorClass ?? FALLBACK_TILE,
                      on ? 'ring-4 ring-beatz-green scale-[0.97]' : 'hover:scale-[1.02]',
                    )}>
                    <h3 className="text-white font-bold text-lg z-10 relative text-left">{g.label}</h3>
                    {on && (
                      <span className="absolute top-2 right-2 z-10 h-7 w-7 rounded-full bg-beatz-green text-black flex items-center justify-center">
                        <Check size={16} strokeWidth={3} />
                      </span>
                    )}
                    <div className="absolute -right-3 -bottom-3 w-16 h-16 bg-black/20 rounded-lg rotate-[25deg]" />
                  </button>
                )
              })}
            </div>
          )}

          <div className="flex items-center justify-between gap-4">
            <span className="text-sm text-gray-500 dark:text-gray-400">
              {picked.length} of {MIN_ONBOARDING_GENRES} selected
            </span>
            <button
              onClick={() => setStep(2)}
              disabled={!genresDone}
              className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100">
              Continue <ArrowRight size={16} />
            </button>
          </div>
        </>
      ) : (
        <>
          {artists.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-gray-300 dark:border-white/15 p-10 text-center">
              <p className="text-sm text-gray-500 dark:text-gray-400">
                There are no artists on BeatzClik yet.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
              {artists.map((a) => {
                const on = followedArtists.includes(a.id)
                return (
                  <button
                    key={a.id}
                    onClick={() => toggleFollowedArtist(a.id)}
                    aria-pressed={on}
                    className="flex flex-col items-center gap-3 group">
                    <div
                      className={cn(
                        'relative w-full aspect-square rounded-full overflow-hidden bg-beatz-light-surface-2 dark:bg-white/5',
                        on && 'ring-4 ring-beatz-green',
                      )}>
                      {a.image && <img src={a.image} alt={a.name} className="w-full h-full object-cover" />}
                      {on && (
                        <span className="absolute bottom-1 right-1 h-7 w-7 rounded-full bg-beatz-green text-black flex items-center justify-center">
                          <Check size={16} strokeWidth={3} />
                        </span>
                      )}
                    </div>
                    <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate w-full text-center">
                      {a.name}
                    </span>
                  </button>
                )
              })}
            </div>
          )}

          <div className="flex items-center justify-between gap-4">
            <button
              onClick={() => setStep(1)}
              className="h-12 px-5 rounded-full text-gray-500 dark:text-gray-300 font-bold text-sm hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
              Back
            </button>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-500 dark:text-gray-400">
                {followedArtists.length} of {artistTarget} followed
              </span>
              <button
                onClick={() => void finish()}
                disabled={!artistsDone || saving}
                className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2 hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100">
                {saving ? <Loader2 size={16} className="animate-spin" /> : null}
                Start listening
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
