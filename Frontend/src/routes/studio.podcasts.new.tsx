import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useRef, useState } from 'react'
import { ArrowLeft, Upload, Play, Pause, ImagePlus, X } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { useStudio } from '../features/studio/studio-context'
import { getStudioShows, STUDIO_PODCAST_CATEGORIES, type StudioEpisode } from '../lib/studio-data'
import { formatDuration } from '../lib/format'

export const Route = createFileRoute('/studio/podcasts/new')({
  component: NewEpisode,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT = 'w-full h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'
const PRICES = [2, 5, 10]
const NEW_SHOW = '__new__'

function NewEpisode() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const { addEpisode } = useStudio()
  const shows = getStudioShows()

  const [showId, setShowId] = useState(shows[0]?.id ?? NEW_SHOW)
  const [newShowTitle, setNewShowTitle] = useState('')
  const [newShowCat, setNewShowCat] = useState<string>(STUDIO_PODCAST_CATEGORIES[0])
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [cover, setCover] = useState<string | null>(null)
  const [audio, setAudio] = useState<{ src: string; name: string; duration: number } | null>(null)
  const [playing, setPlaying] = useState(false)
  const [visibility, setVisibility] = useState<'public' | 'scheduled'>('public')
  const [date, setDate] = useState('')
  const [premium, setPremium] = useState(false)
  const [price, setPrice] = useState(5)
  const [earlyAccess, setEarlyAccess] = useState(false)

  const audioRef = useRef<HTMLAudioElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const coverRef = useRef<HTMLInputElement>(null)

  const onAudio = (f?: File) => {
    if (!f) return
    const src = URL.createObjectURL(f)
    setAudio({ src, name: f.name, duration: 0 })
    const probe = new Audio()
    probe.preload = 'metadata'
    probe.src = src
    probe.onloadedmetadata = () => { if (Number.isFinite(probe.duration)) setAudio((a) => (a ? { ...a, duration: Math.round(probe.duration) } : a)) }
  }
  const togglePlay = () => {
    const el = audioRef.current
    if (!el || !audio) return
    if (playing) { el.pause(); setPlaying(false) } else { el.src = audio.src; el.play().catch(() => {}); setPlaying(true) }
  }

  const showTitle = showId === NEW_SHOW ? newShowTitle.trim() : (shows.find((s) => s.id === showId)?.title ?? '')
  const canSubmit = title.trim() !== '' && !!audio && showTitle !== '' && (visibility === 'public' || date !== '')

  const submit = () => {
    if (!canSubmit || !audio) { toast('Add an episode title, audio and a show to publish', 'error'); return }
    const ep: StudioEpisode = {
      id: `ep-${Date.now()}`,
      showId: showId === NEW_SHOW ? `sh-${Date.now()}` : showId,
      showTitle,
      title: title.trim(),
      duration: audio.duration,
      status: visibility === 'scheduled' ? 'scheduled' : 'published',
      premium,
      price: premium ? price : 0,
      publishedAt: visibility === 'scheduled' && date ? new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      plays: 0,
    }
    addEpisode(ep)
    toast(visibility === 'scheduled' ? 'Episode scheduled' : 'Episode published 🎙️', 'success')
    navigate({ to: '/studio/podcasts' })
  }

  return (
    <div className="flex flex-col gap-8">
      <audio ref={audioRef} className="hidden" onEnded={() => setPlaying(false)} />

      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-2">
          <Link to="/studio/podcasts" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit"><ArrowLeft size={14} /> Podcasts</Link>
          <h1 className="text-display text-beatz-dark-bg dark:text-white">New episode</h1>
        </div>
        <button onClick={submit} disabled={!canSubmit} className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold text-sm hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100">
          {visibility === 'scheduled' ? 'Schedule' : 'Publish'}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-8 items-start">
        {/* Main */}
        <div className="flex flex-col gap-6">
          {/* Audio */}
          <input ref={fileRef} type="file" accept="audio/*" className="hidden" onChange={(e) => onAudio(e.target.files?.[0])} />
          {!audio ? (
            <button type="button" onClick={() => fileRef.current?.click()}
              className="w-full rounded-2xl border-2 border-dashed border-gray-300 dark:border-white/15 flex flex-col items-center justify-center gap-3 py-16 hover:border-beatz-green/60 transition-colors">
              <Upload size={30} className="text-beatz-green" />
              <span className="text-lg font-bold text-beatz-dark-bg dark:text-white">Drop episode audio</span>
              <span className="text-xs text-gray-400 dark:text-gray-500">MP3, WAV or M4A · up to 500 MB</span>
            </button>
          ) : (
            <div className={cn(CARD, 'flex items-center gap-4')}>
              <button onClick={togglePlay} className="w-11 h-11 rounded-full bg-beatz-green text-black flex items-center justify-center shrink-0 hover:scale-105 transition-transform">{playing ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" className="ml-0.5" />}</button>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{audio.name}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400">{audio.duration ? formatDuration(audio.duration) : 'reading…'}</span>
              </div>
              <button onClick={() => { setAudio(null); setPlaying(false) }} aria-label="Remove" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors"><X size={18} /></button>
            </div>
          )}

          <section className={cn(CARD, 'flex flex-col gap-5')}>
            <Field label="Show">
              <select className={cn(INPUT, 'appearance-none cursor-pointer')} value={showId} onChange={(e) => setShowId(e.target.value)}>
                {shows.map((s) => <option key={s.id} value={s.id}>{s.title}</option>)}
                <option value={NEW_SHOW}>+ New show…</option>
              </select>
            </Field>
            {showId === NEW_SHOW && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <Field label="Show name"><input className={INPUT} value={newShowTitle} onChange={(e) => setNewShowTitle(e.target.value)} placeholder="e.g. Konongo Diaries" /></Field>
                <Field label="Category">
                  <select className={cn(INPUT, 'appearance-none cursor-pointer')} value={newShowCat} onChange={(e) => setNewShowCat(e.target.value)}>
                    {STUDIO_PODCAST_CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                  </select>
                </Field>
              </div>
            )}
            <Field label="Episode title"><input className={INPUT} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Ep 14 · The Breakthrough" /></Field>
            <Field label="Description"><textarea className={cn(INPUT, 'h-28 py-3 resize-none')} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What's this episode about?" /></Field>
          </section>
        </div>

        {/* Side: cover + visibility + monetization */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <span className={LABEL}>Episode art</span>
            <input ref={coverRef} type="file" accept="image/*" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) setCover(URL.createObjectURL(f)) }} />
            <button type="button" onClick={() => coverRef.current?.click()} className="w-full aspect-square rounded-2xl overflow-hidden bg-beatz-light-surface-2 dark:bg-white/5 border border-gray-200 dark:border-white/10 flex items-center justify-center hover:border-beatz-green/60 transition-colors">
              {cover ? <img src={cover} alt="Cover" className="w-full h-full object-cover" /> : <span className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500"><ImagePlus size={26} /><span className="text-[11px] font-mono uppercase tracking-widest">3000×3000</span></span>}
            </button>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <span className={LABEL}>Visibility</span>
            <div className="grid grid-cols-2 gap-2">
              {(['public', 'scheduled'] as const).map((v) => (
                <button key={v} onClick={() => setVisibility(v)} className={cn('h-10 rounded-xl text-xs font-bold border transition-colors', visibility === v ? 'bg-beatz-green/10 border-beatz-green text-beatz-green' : 'border-gray-200 dark:border-white/10 text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-white/20')}>{v === 'public' ? 'Publish now' : 'Schedule'}</button>
              ))}
            </div>
            {visibility === 'scheduled' && <input type="date" className={INPUT} value={date} onChange={(e) => setDate(e.target.value)} />}
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <span className={LABEL}>Monetization</span>
            <div className="grid grid-cols-2 gap-2">
              {[false, true].map((p) => (
                <button key={String(p)} onClick={() => setPremium(p)} className={cn('h-10 rounded-xl text-xs font-bold border transition-colors', premium === p ? 'bg-beatz-green/10 border-beatz-green text-beatz-green' : 'border-gray-200 dark:border-white/10 text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-white/20')}>{p ? 'Premium' : 'Free'}</button>
              ))}
            </div>
            {premium && (
              <>
                <div className="flex flex-wrap gap-2 pt-1">
                  {PRICES.map((p) => (
                    <button key={p} onClick={() => setPrice(p)} className={cn('h-9 px-4 rounded-full text-sm font-bold border transition-colors', price === p ? 'bg-beatz-green/10 border-beatz-green text-beatz-green' : 'border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20')}>₵{p.toFixed(2)}</button>
                  ))}
                </div>
                <label className="flex items-center justify-between gap-3 pt-1">
                  <span className="text-sm text-gray-600 dark:text-gray-300">Early access · free later</span>
                  <button onClick={() => setEarlyAccess((v) => !v)} role="switch" aria-checked={earlyAccess} className={cn('w-11 h-6 rounded-full p-0.5 transition-colors shrink-0', earlyAccess ? 'bg-beatz-green' : 'bg-gray-300 dark:bg-white/20')}>
                    <span className={cn('block w-5 h-5 rounded-full bg-white shadow-sm transition-transform', earlyAccess ? 'translate-x-5' : 'translate-x-0')} />
                  </button>
                </label>
                <p className="text-xs text-gray-500 dark:text-gray-400">You earn 70% per sale · ₵{(price * 0.7).toFixed(2)} per listener.</p>
              </>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="flex flex-col gap-2.5"><label className={LABEL}>{label}</label>{children}</div>
}
