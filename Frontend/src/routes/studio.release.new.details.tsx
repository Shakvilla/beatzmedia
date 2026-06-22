import { createFileRoute } from '@tanstack/react-router'
import { useRef } from 'react'
import { UploadCloud, CalendarClock } from 'lucide-react'
import { cn } from '../utils/cn'
import { useReleaseDraft } from '../features/studio/release-draft-context'
import { releaseTypes, studioGenres, studioArtist } from '../lib/studio-data'
import type { Genre } from '../types'

export const Route = createFileRoute('/studio/release/new/details')({
  component: ReleaseDetailsStep,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const INPUT =
  'w-full h-12 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'

function ReleaseDetailsStep() {
  const { draft, setField } = useReleaseDraft()
  const fileRef = useRef<HTMLInputElement>(null)

  const onCover = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) setField('coverImage', URL.createObjectURL(file))
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-10 lg:gap-16 items-start">
      {/* Form column */}
      <div className="flex flex-col gap-8 max-w-2xl">
        {/* Release type */}
        <Field label="Release type">
          <div className="flex flex-wrap gap-2">
            {releaseTypes.map((rt) => {
              const active = draft.releaseType === rt.value
              return (
                <button
                  key={rt.value}
                  type="button"
                  onClick={() => setField('releaseType', rt.value)}
                  className={cn(
                    'h-10 px-4 rounded-full text-sm font-bold border transition-colors',
                    active
                      ? 'bg-beatz-green text-black border-beatz-green'
                      : 'bg-transparent border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20',
                  )}
                >
                  {rt.label}
                  {rt.hint && <span className={cn('ml-1 font-medium', active ? 'text-black/60' : 'text-gray-400')}>({rt.hint})</span>}
                </button>
              )
            })}
          </div>
        </Field>

        <Field label="Release title">
          <input
            className={INPUT}
            value={draft.title}
            onChange={(e) => setField('title', e.target.value)}
            placeholder="Name your release"
          />
        </Field>

        <Field label="Primary artist">
          <input
            className={INPUT}
            value={draft.primaryArtist}
            onChange={(e) => setField('primaryArtist', e.target.value)}
            placeholder={studioArtist.name}
          />
        </Field>

        <Field label="Featured artists">
          <input
            className={INPUT}
            value={draft.featuredArtists}
            onChange={(e) => setField('featuredArtists', e.target.value)}
            placeholder="e.g. Burna Boy, Smallgod"
          />
        </Field>

        <Field label="Label">
          <input
            className={INPUT}
            value={draft.label}
            onChange={(e) => setField('label', e.target.value)}
            placeholder="e.g. Empire · Konongo Zongo Records"
          />
        </Field>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <Field label="Release date">
            <input
              type="date"
              className={INPUT}
              value={draft.releaseDate}
              onChange={(e) => setField('releaseDate', e.target.value)}
            />
          </Field>
          <Field label="Genre">
            <select
              className={cn(INPUT, 'appearance-none cursor-pointer')}
              value={draft.genre}
              onChange={(e) => setField('genre', e.target.value as Genre | '')}
            >
              <option value="">Select a genre</option>
              {studioGenres.map((g) => (
                <option key={g} value={g}>{g}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Description">
          <textarea
            className={cn(INPUT, 'h-32 py-3 resize-none')}
            value={draft.description}
            onChange={(e) => setField('description', e.target.value)}
            placeholder="Tell fans the story of this release…"
          />
        </Field>
      </div>

      {/* Cover art + visibility column */}
      <div className="flex flex-col gap-8">
        <Field label="Cover art">
          <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={onCover} />
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="group relative w-full aspect-square rounded-2xl border-2 border-dashed border-gray-300 dark:border-white/15 flex flex-col items-center justify-center gap-3 overflow-hidden hover:border-beatz-green/60 transition-colors"
          >
            {draft.coverImage ? (
              <>
                <img src={draft.coverImage} alt="Cover preview" className="absolute inset-0 w-full h-full object-cover" />
                <span className="absolute bottom-3 right-3 z-10 text-[10px] font-bold uppercase tracking-wider bg-black/60 text-white px-2.5 py-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity">
                  Change
                </span>
              </>
            ) : (
              <>
                <UploadCloud size={30} className="text-gray-400 dark:text-gray-500" />
                <span className="text-xs text-gray-400 dark:text-gray-500">Drop image · 3000×3000 min</span>
              </>
            )}
          </button>
        </Field>

        <Field label="Visibility">
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-2">
              {(['public', 'scheduled'] as const).map((v) => {
                const active = draft.visibility === v
                return (
                  <button
                    key={v}
                    type="button"
                    onClick={() => setField('visibility', v)}
                    className={cn(
                      'h-10 rounded-xl text-xs font-bold border transition-colors capitalize',
                      active
                        ? 'bg-beatz-green/10 border-beatz-green text-beatz-green'
                        : 'bg-transparent border-gray-200 dark:border-white/10 text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-white/20',
                    )}
                  >
                    {v === 'public' ? 'Public now' : 'Schedule'}
                  </button>
                )
              })}
            </div>
            <p className="flex items-start gap-2 text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
              <CalendarClock size={14} className="mt-0.5 shrink-0" />
              {draft.visibility === 'scheduled'
                ? draft.releaseDate
                  ? `Goes public on ${formatDate(draft.releaseDate)}. A pre-save link will be generated.`
                  : 'Pick a release date above to schedule. A pre-save link will be generated.'
                : 'This release goes live for fans as soon as you publish.'}
            </p>
          </div>
        </Field>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <label className={LABEL}>{label}</label>
      {children}
    </div>
  )
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })
}
