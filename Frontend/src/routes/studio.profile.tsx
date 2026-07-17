import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMemo, useRef, useState } from 'react'
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import {
  ImagePlus, X, Plus, Trash2, BadgeCheck, Camera, AtSign, Play, Globe,
  Music2, Mail, Check, MapPin,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { studioArtist, type StudioProfile, type StudioShow } from '../lib/studio-data'
import { getArtistTracks } from '../lib/mock-data'
import { studioProfileQuery, apiSaveStudioProfile } from '../lib/api/queries/studio'
import { ApiError } from '../lib/api/errors'

export const Route = createFileRoute('/studio/profile')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioProfileQuery()),
  component: ProfileComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT =
  'w-full h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'
const BIO_MAX = 300

function ProfileComponent() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: storeProfile } = useSuspenseQuery(studioProfileQuery())
  const trackOptions = useMemo(() => getArtistTracks(studioArtist.id), [])

  const [p, setP] = useState<StudioProfile>(storeProfile)
  const [tagDraft, setTagDraft] = useState('')
  const avatarRef = useRef<HTMLInputElement>(null)
  const bannerRef = useRef<HTMLInputElement>(null)
  const pressRef = useRef<HTMLInputElement>(null)

  const dirty = useMemo(() => JSON.stringify(p) !== JSON.stringify(storeProfile), [p, storeProfile])

  const set = <K extends keyof StudioProfile>(key: K, value: StudioProfile[K]) => setP((prev) => ({ ...prev, [key]: value }))
  const setLink = (key: keyof StudioProfile['links'], value: string) => setP((prev) => ({ ...prev, links: { ...prev.links, [key]: value } }))

  const addTag = () => {
    const t = tagDraft.trim()
    if (t && !p.genres.includes(t)) set('genres', [...p.genres, t])
    setTagDraft('')
  }
  const removeTag = (t: string) => set('genres', p.genres.filter((g) => g !== t))

  const updateShow = (id: string, patch: Partial<StudioShow>) => set('shows', p.shows.map((s) => (s.id === id ? { ...s, ...patch } : s)))
  const removeShow = (id: string) => set('shows', p.shows.filter((s) => s.id !== id))
  const addShow = () => set('shows', [...p.shows, { id: `s-${Date.now()}`, venue: '', date: '', city: '' }])

  const addPress = (files: FileList | null) => {
    if (!files) return
    set('pressAssets', [...p.pressAssets, ...Array.from(files).map((f) => ({ id: `${Date.now()}-${f.name}`, name: f.name, url: URL.createObjectURL(f) }))])
  }
  const removePress = (id: string) => set('pressAssets', p.pressAssets.filter((a) => a.id !== id))

  const save = async () => {
    const key = studioProfileQuery().queryKey
    const previous = queryClient.getQueryData(key)
    queryClient.setQueryData(key, p)          // optimistic — updates editor baseline + sidebar
    try {
      const saved = await apiSaveStudioProfile(p)
      queryClient.setQueryData(key, saved)    // adopt server-canonical (e.g. normalized username)
      setP(saved)
      toast('Profile changes saved', 'success')
    } catch (err) {
      queryClient.setQueryData(key, previous) // rollback
      const code = err instanceof ApiError ? err.code : null
      toast(
        code === 'USERNAME_TAKEN' ? 'That username is already taken.'
        : code === 'INVALID_GENRE' ? 'One of your genres isn’t recognised.'
        : 'Could not save your profile. Please try again.',
        'error',
      )
    }
  }

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex items-center gap-3">
          <h1 className="text-display text-beatz-dark-bg dark:text-white">Profile</h1>
          {dirty && <span className="text-[11px] font-bold text-[#b8881f] dark:text-[#f6c644] bg-[#f6c644]/15 px-2.5 py-1 rounded-full">Unsaved changes</span>}
        </div>
        <div className="flex items-center gap-3">
          {dirty && (
            <button onClick={() => setP(storeProfile)} className="h-11 px-4 rounded-full text-gray-500 dark:text-gray-300 font-bold text-sm hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
              Discard
            </button>
          )}
          <button
            onClick={() => navigate({ to: '/artist/$artistId', params: { artistId: studioArtist.id } })}
            className="h-11 px-5 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white font-bold text-sm hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
          >
            Preview page
          </button>
          <button
            onClick={save}
            disabled={!dirty}
            className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold text-sm hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100"
          >
            Save changes
          </button>
        </div>
      </div>

      {/* Banner + avatar */}
      <div className="relative">
        <input ref={bannerRef} type="file" accept="image/*" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) set('banner', URL.createObjectURL(f)) }} />
        <input ref={avatarRef} type="file" accept="image/*" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) set('avatar', URL.createObjectURL(f)) }} />
        <div className="relative w-full aspect-[4/1] min-h-[160px] rounded-2xl overflow-hidden bg-beatz-light-surface-2 dark:bg-white/5 border border-gray-200 dark:border-white/10 flex items-center justify-center">
          {p.banner ? <img src={p.banner} alt="Banner" className="w-full h-full object-cover" /> : <span className="text-[11px] font-mono uppercase tracking-[0.2em] text-gray-400 dark:text-gray-500">Banner · 2400×600</span>}
          <button onClick={() => bannerRef.current?.click()} className="absolute right-4 bottom-4 h-9 px-4 rounded-full bg-white/90 dark:bg-black/60 backdrop-blur text-beatz-dark-bg dark:text-white text-xs font-bold flex items-center gap-2 hover:bg-white dark:hover:bg-black/80 transition-colors shadow-sm">
            <ImagePlus size={14} /> Upload banner
          </button>
        </div>
        <div className="absolute -bottom-8 left-6 flex items-end gap-4">
          <button onClick={() => avatarRef.current?.click()} className="w-28 h-28 rounded-full overflow-hidden border-4 border-beatz-light-bg dark:border-beatz-dark-bg bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-2 flex items-center justify-center shrink-0 group relative">
            {p.avatar ? <img src={p.avatar} alt="Avatar" className="w-full h-full object-cover" /> : <span className="text-2xl font-bold text-gray-500 dark:text-gray-300">{studioArtist.initials}</span>}
            <span className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity"><ImagePlus size={20} className="text-white" /></span>
          </button>
          <button onClick={() => avatarRef.current?.click()} className="mb-2 h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            Change avatar
          </button>
        </div>
      </div>

      {/* Editor + preview */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_380px] gap-8 items-start mt-12 lg:mt-10">
        {/* Editor */}
        <div className="flex flex-col gap-6 min-w-0">
          <section className={cn(CARD, 'flex flex-col gap-6')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Identity</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <Field label="Display name"><input className={INPUT} value={p.displayName} onChange={(e) => set('displayName', e.target.value)} /></Field>
              <Field label="Username"><input className={INPUT} value={p.username} onChange={(e) => set('username', e.target.value)} /></Field>
            </div>
            <Field label="Hometown"><input className={INPUT} value={p.hometown} onChange={(e) => set('hometown', e.target.value)} placeholder="City, Country" /></Field>
            <Field label="Genre tags">
              <div className="flex flex-wrap gap-2">
                {p.genres.map((g) => (
                  <span key={g} className="h-9 pl-3 pr-2 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-1.5">
                    {g}<button onClick={() => removeTag(g)} aria-label={`Remove ${g}`} className="hover:bg-beatz-green/20 rounded-full p-0.5"><X size={13} /></button>
                  </span>
                ))}
                <input value={tagDraft} onChange={(e) => setTagDraft(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag() } }} onBlur={addTag} placeholder="+ Add genre" className="h-9 px-3 rounded-full bg-transparent border border-dashed border-gray-300 dark:border-white/20 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60 w-32" />
              </div>
            </Field>
            <Field label="Bio">
              <textarea className={cn(INPUT, 'h-32 py-3 resize-none')} value={p.bio} onChange={(e) => set('bio', e.target.value)} placeholder="Tell fans your story…" />
              <span className={cn('text-[11px] self-end', p.bio.length > BIO_MAX ? 'text-beatz-red font-bold' : 'text-gray-400 dark:text-gray-500')}>{p.bio.length}/{BIO_MAX}</span>
            </Field>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Links</h2>
            <LinkField icon={Camera} label="Instagram" value={p.links.instagram} onChange={(v) => setLink('instagram', v)} />
            <LinkField icon={AtSign} label="Twitter / X" value={p.links.twitter} onChange={(v) => setLink('twitter', v)} />
            <LinkField icon={Play} label="YouTube" value={p.links.youtube} onChange={(v) => setLink('youtube', v)} />
            <LinkField icon={Globe} label="Website" value={p.links.website} onChange={(v) => setLink('website', v)} />
          </section>

          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Featured & booking</h2>
            <Field label="Pinned track">
              <select className={cn(INPUT, 'appearance-none cursor-pointer')} value={p.featuredTrackId ?? ''} onChange={(e) => set('featuredTrackId', e.target.value || null)}>
                <option value="">No pinned track</option>
                {trackOptions.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
              </select>
            </Field>
            <Field label="Booking / contact email"><input className={INPUT} type="email" value={p.bookingEmail} onChange={(e) => set('bookingEmail', e.target.value)} placeholder="bookings@example.com" /></Field>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Upcoming shows</h2>
            <div className="flex flex-col gap-3">
              {p.shows.map((s) => (
                <div key={s.id} className="flex items-center gap-3 p-3 rounded-xl border border-gray-200 dark:border-white/10">
                  <div className="flex-1 flex flex-col gap-2 min-w-0">
                    <input value={s.venue} onChange={(e) => updateShow(s.id, { venue: e.target.value })} placeholder="Venue" className="bg-transparent font-bold text-sm text-beatz-dark-bg dark:text-white focus:outline-none focus:bg-gray-50 dark:focus:bg-white/10 rounded px-1 -mx-1" />
                    <div className="flex items-center gap-2">
                      <input value={s.date} onChange={(e) => updateShow(s.id, { date: e.target.value })} placeholder="Date" className="w-24 bg-transparent text-xs text-gray-500 dark:text-gray-300 focus:outline-none focus:bg-gray-50 dark:focus:bg-white/10 rounded px-1" />
                      <span className="text-gray-300 dark:text-white/20">·</span>
                      <input value={s.city} onChange={(e) => updateShow(s.id, { city: e.target.value })} placeholder="City" className="flex-1 bg-transparent text-xs text-gray-500 dark:text-gray-300 focus:outline-none focus:bg-gray-50 dark:focus:bg-white/10 rounded px-1" />
                    </div>
                  </div>
                  <button onClick={() => removeShow(s.id)} aria-label="Remove show" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors shrink-0"><Trash2 size={15} /></button>
                </div>
              ))}
              <button onClick={addShow} className="self-start h-9 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors"><Plus size={15} /> Add show</button>
            </div>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Press kit</h2>
            <input ref={pressRef} type="file" multiple accept="image/*,application/pdf" className="hidden" onChange={(e) => addPress(e.target.files)} />
            <div className="flex flex-wrap gap-2">
              {p.pressAssets.map((a) => (
                <span key={a.id} className="h-9 pl-3 pr-2 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 max-w-[200px]">
                  <span className="truncate">{a.name}</span>
                  <button onClick={() => removePress(a.id)} aria-label="Remove" className="hover:bg-gray-200 dark:hover:bg-white/15 rounded-full p-0.5"><X size={13} /></button>
                </span>
              ))}
              <button onClick={() => pressRef.current?.click()} className="h-9 px-4 rounded-full border border-dashed border-gray-300 dark:border-white/20 text-gray-500 dark:text-gray-300 text-sm font-bold flex items-center gap-2 hover:border-beatz-green/60 hover:text-beatz-green transition-colors"><Plus size={15} /> Add assets</button>
            </div>
            <p className="text-xs text-gray-400 dark:text-gray-500">Logos, hi-res photos and one-sheets fans and press can download.</p>
          </section>
        </div>

        {/* Right: completeness + live preview */}
        <div className="flex flex-col gap-6 lg:sticky lg:top-2 self-start">
          <Completeness profile={p} />
          <LivePreview profile={p} featuredTitle={trackOptions.find((t) => t.id === p.featuredTrackId)?.title} />
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="flex flex-col gap-2.5"><label className={LABEL}>{label}</label>{children}</div>
}

function LinkField({ icon: Icon, label, value, onChange }: { icon: typeof Globe; label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-9 h-9 rounded-lg bg-gray-100 dark:bg-white/10 flex items-center justify-center shrink-0 text-gray-500 dark:text-gray-300"><Icon size={16} /></span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400 dark:text-gray-500">{label}</span>
        <input value={value} onChange={(e) => onChange(e.target.value)} placeholder="—" className="bg-transparent text-sm font-medium text-beatz-dark-bg dark:text-white focus:outline-none" />
      </div>
      {value.trim() && <Check size={15} className="text-beatz-green shrink-0" />}
    </div>
  )
}

function Completeness({ profile: p }: { profile: StudioProfile }) {
  const checks = [
    { label: 'Display name', ok: !!p.displayName.trim() },
    { label: 'Username', ok: !!p.username.trim() },
    { label: 'Hometown', ok: !!p.hometown.trim() },
    { label: 'Bio (20+ chars)', ok: p.bio.trim().length >= 20 },
    { label: 'Avatar', ok: !!p.avatar },
    { label: 'Banner', ok: !!p.banner },
    { label: 'A genre tag', ok: p.genres.length > 0 },
    { label: 'A social link', ok: Object.values(p.links).some((v) => v.trim()) },
    { label: 'Pinned track', ok: !!p.featuredTrackId },
    { label: 'Booking email', ok: !!p.bookingEmail.trim() },
    { label: 'An upcoming show', ok: p.shows.length > 0 },
  ]
  const done = checks.filter((c) => c.ok).length
  const pct = Math.round((done / checks.length) * 100)
  const missing = checks.filter((c) => !c.ok).slice(0, 3)

  return (
    <section className={cn(CARD, 'flex flex-col gap-4')}>
      <div className="flex items-center justify-between">
        <span className={LABEL}>Profile strength</span>
        <span className={cn('text-sm font-bold', pct === 100 ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>{pct}%</span>
      </div>
      <div className="h-2 rounded-full bg-gray-200 dark:bg-white/10 overflow-hidden">
        <div className="h-full rounded-full bg-beatz-green transition-[width] duration-500" style={{ width: `${pct}%` }} />
      </div>
      {missing.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          {missing.map((m) => (
            <span key={m.label} className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span className="w-1.5 h-1.5 rounded-full bg-[#f6c644]" /> Add {m.label.toLowerCase()}
            </span>
          ))}
        </div>
      ) : (
        <span className="flex items-center gap-2 text-xs font-bold text-beatz-green"><Check size={14} /> Your profile is complete</span>
      )}
    </section>
  )
}

function LivePreview({ profile: p, featuredTitle }: { profile: StudioProfile; featuredTitle?: string }) {
  const links: { icon: typeof Globe; value: string }[] = [
    { icon: Camera, value: p.links.instagram }, { icon: AtSign, value: p.links.twitter },
    { icon: Play, value: p.links.youtube }, { icon: Globe, value: p.links.website },
  ].filter((l) => l.value.trim())

  return (
    <section className={cn(CARD, 'flex flex-col gap-0 p-0 overflow-hidden')}>
      <div className="px-5 pt-5 pb-3"><span className={LABEL}>Live preview</span></div>
      {/* Banner */}
      <div className="relative h-24 bg-beatz-light-surface-2 dark:bg-white/5">
        {p.banner && <img src={p.banner} alt="" className="w-full h-full object-cover" />}
        <div className="absolute -bottom-7 left-5 w-16 h-16 rounded-full overflow-hidden border-4 border-white dark:border-beatz-dark-surface bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-2 flex items-center justify-center">
          {p.avatar ? <img src={p.avatar} alt="" className="w-full h-full object-cover" /> : <span className="text-sm font-bold text-gray-500 dark:text-gray-300">{studioArtist.initials}</span>}
        </div>
      </div>
      <div className="px-5 pt-9 pb-5 flex flex-col gap-3">
        <div className="flex flex-col gap-0.5">
          <span className="flex items-center gap-1.5 text-lg font-bold text-beatz-dark-bg dark:text-white">
            {p.displayName || 'Your name'}
            {studioArtist.verified && <BadgeCheck size={16} className="text-beatz-green" />}
          </span>
          <span className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
            {p.username || '@username'}{p.hometown && <><span>·</span><MapPin size={11} />{p.hometown}</>}
          </span>
        </div>

        {p.genres.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {p.genres.map((g) => <span key={g} className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300">{g}</span>)}
          </div>
        )}

        {p.bio.trim() && <p className="text-xs text-gray-600 dark:text-gray-300 leading-relaxed line-clamp-3">{p.bio}</p>}

        {featuredTitle && (
          <div className="flex items-center gap-2 p-2 rounded-lg bg-beatz-green/10">
            <span className="w-7 h-7 rounded bg-beatz-green/20 flex items-center justify-center text-beatz-green"><Music2 size={14} /></span>
            <div className="flex flex-col min-w-0">
              <span className="text-[9px] font-bold uppercase tracking-wider text-beatz-green">Pinned</span>
              <span className="text-xs font-bold text-beatz-dark-bg dark:text-white truncate">{featuredTitle}</span>
            </div>
          </div>
        )}

        {links.length > 0 && (
          <div className="flex items-center gap-2">
            {links.map((l, i) => <span key={i} className="w-7 h-7 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-600 dark:text-gray-300"><l.icon size={14} /></span>)}
          </div>
        )}

        {p.shows.length > 0 && (
          <div className="flex flex-col gap-1.5 pt-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400 dark:text-gray-500">Upcoming</span>
            {p.shows.slice(0, 3).map((s) => (
              <div key={s.id} className="flex items-center justify-between text-xs">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate pr-2">{s.venue || 'Venue'}</span>
                <span className="text-gray-400 dark:text-gray-500 shrink-0">{[s.date, s.city].filter(Boolean).join(' · ')}</span>
              </div>
            ))}
          </div>
        )}

        {p.bookingEmail.trim() && (
          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 pt-1">
            <Mail size={13} /> {p.bookingEmail}
          </div>
        )}
      </div>
    </section>
  )
}
