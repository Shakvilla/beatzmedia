import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ArrowLeft, Disc3, Check, Flag, ShieldX, Clock, Play } from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import { getCatalog, type CatalogItem, type CatalogStatus } from '../lib/admin-data'

export const Route = createFileRoute('/admin/catalog/$itemId')({
  component: AdminCatalogDetail,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'

function coverGradient(t: string): string {
  let h = 0
  for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 50% 44%), hsl(${(h + 48) % 360} 55% 32%))`
}
const dur = (i: number) => `${2 + ((i * 37) % 3)}:${(10 + ((i * 17) % 49)).toString().padStart(2, '0')}`

interface Log { id: string; action: string; time: string }

function AdminCatalogDetail() {
  const { itemId } = Route.useParams()
  const { toast } = useToast()
  const found = useMemo(() => getCatalog().find((c) => c.id === itemId), [itemId])
  const [item, setItem] = useState<CatalogItem | undefined>(found)
  const [takedownOpen, setTakedownOpen] = useState(false)
  const [log, setLog] = useState<Log[]>(found ? [{ id: 'l0', action: `Submitted by ${found.artist}`, time: found.note ?? '—' }] : [])

  if (!item) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Release not found.</p>
        <Link to="/admin/catalog" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to catalog</Link>
      </div>
    )
  }

  const addLog = (action: string) => setLog((l) => [{ id: `l-${Date.now()}`, action, time: 'just now' }, ...l])
  const setStatus = (status: CatalogStatus, action: string) => { setItem((c) => (c ? { ...c, status } : c)); addLog(action); toast(action, 'success') }
  const reviewable = item.status === 'pending' || item.status === 'flagged'
  const tracks = Array.from({ length: item.tracks }, (_, i) => ({ n: i + 1, title: item.tracks === 1 ? item.title : `${item.title} · ${i + 1}`, duration: dur(i) }))
  const isrc = (n: number) => `GHA-26-${(1000 + n).toString()}`
  const splits = [{ name: item.artist, role: 'Primary artist', pct: 70 }, { name: 'Producer', role: 'Production', pct: 20 }, { name: 'Label', role: 'Label cut', pct: 10 }]

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-4">
        <Link to="/admin/catalog" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
          <ArrowLeft size={14} /> Catalog
        </Link>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4">
            <div className="w-20 h-20 rounded-xl shrink-0 flex items-center justify-center" style={{ backgroundImage: coverGradient(item.title) }}><Disc3 size={30} className="text-white/70" /></div>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-3xl font-bold tracking-tight text-beatz-dark-bg dark:text-white">{item.title}</h1>
                <StatusPill status={item.status} />
              </div>
              <span className="text-sm text-gray-500 dark:text-gray-300">{item.artist} · {item.type} · {item.tracks} track{item.tracks === 1 ? '' : 's'}{item.note ? ` · ${item.note}` : ''}</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {reviewable && <button onClick={() => setStatus('published', 'Approved & published')} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform"><span className="flex items-center gap-2"><Check size={15} /> Approve</span></button>}
            {item.status !== 'flagged' && <button onClick={() => setStatus('flagged', 'Flagged for review')} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><Flag size={15} /> Flag</button>}
            <button onClick={() => setTakedownOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><ShieldX size={15} /> Take down</button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-6 items-start">
        {/* Tracklist */}
        <section className={cn(CARD, 'flex flex-col gap-4')}>
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Tracklist</h2>
          <div className="flex flex-col">
            {tracks.map((t) => (
              <div key={t.n} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 group">
                <span className="w-5 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{t.n}</span>
                <button onClick={() => toast(`Previewing “${t.title}”`, 'info')} className="w-7 h-7 rounded-full bg-beatz-green/10 text-beatz-green flex items-center justify-center shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"><Play size={12} fill="currentColor" /></button>
                <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.title}</span>
                <span className="text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0">{isrc(t.n)}</span>
                <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{t.duration}</span>
              </div>
            ))}
          </div>
        </section>

        {/* Metadata + splits + history */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Metadata</h2>
            <Meta label="UPC" value={`BZ${(900000 + item.title.length * 137).toString()}`} />
            <Meta label="Primary genre" value="Hiplife / Drill" />
            <Meta label="Label" value={item.artist === 'Various' ? 'Beatzclik Compilations' : 'Independent'} />
            <Meta label="Tracks" value={`${item.tracks}`} last />
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Rights & splits</h2>
            {splits.map((s) => (
              <div key={s.name} className="flex items-center gap-3 py-1.5">
                <div className="flex flex-col flex-1 min-w-0">
                  <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{s.name}</span>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{s.role}</span>
                </div>
                <span className="text-sm font-mono font-bold text-beatz-green">{s.pct}%</span>
              </div>
            ))}
          </section>

          <section className={cn(CARD, 'flex flex-col gap-3')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Action history</h2>
            {log.map((l) => (
              <div key={l.id} className="flex items-center gap-3 py-2 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                <Clock size={13} className="text-gray-400 shrink-0" />
                <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{l.action}</span>
                <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{l.time}</span>
              </div>
            ))}
          </section>
        </div>
      </div>

      <TakedownModal isOpen={takedownOpen} title={item.title} onClose={() => setTakedownOpen(false)}
        onConfirm={(reason) => { setTakedownOpen(false); setStatus('takedown', `Taken down · ${reason}`) }} />
    </div>
  )
}

function Meta({ label, value, last }: { label: string; value: string; last?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between py-1.5', !last && 'border-b border-dashed border-gray-200 dark:border-white/5')}>
      <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
      <span className="text-sm font-mono font-bold text-beatz-dark-bg dark:text-white">{value}</span>
    </div>
  )
}

function StatusPill({ status }: { status: CatalogStatus }) {
  const cls = status === 'flagged' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : status === 'published' ? 'bg-beatz-green/15 text-beatz-green' : status === 'takedown' ? 'bg-beatz-red/15 text-beatz-red' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function TakedownModal({ isOpen, title, onClose, onConfirm }: { isOpen: boolean; title: string; onClose: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState('')
  const REASONS = ['Copyright claim', 'Metadata mismatch', 'Duplicate ISRC', 'Policy violation', 'Other']
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Take down “${title}”`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The release will be removed from BeatzClik and the artist notified. A reason is required and logged.</p>
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => <button key={r} onClick={() => setReason(r)} className={cn('h-9 px-3.5 rounded-full text-xs font-bold border transition-colors', reason === r ? 'border-beatz-red bg-beatz-red/10 text-beatz-red' : 'border-white/10 text-white/70 hover:border-white/20')}>{r}</button>)}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Add a note…" className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim()} className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">Take down</button>
        </div>
      </div>
    </Modal>
  )
}
