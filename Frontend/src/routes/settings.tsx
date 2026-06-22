import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { Edit2, Sun, Moon, Monitor, LogOut, type LucideIcon } from 'lucide-react'
import { cn } from '../utils/cn'
import { useAuth, initialsOfAccount } from '../features/auth/auth-context'
import { useCollection } from '../features/collection/collection-context'
import { useTheme, type Theme } from '../components/theme-provider'
import { useToast } from '../components/ui/toast-provider'
import { Toggle } from '../components/ui/toggle'

export const Route = createFileRoute('/settings')({
  component: SettingsComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const SELECT = 'h-10 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-3 text-sm font-bold text-beatz-dark-bg dark:text-white focus:outline-none focus:border-beatz-green/60 cursor-pointer appearance-none'

interface FanPrefs {
  country: string
  phone: string
  streamingQuality: string
  downloadQuality: string
  crossfade: string
  dataSaver: boolean
  notif: { newReleases: boolean; playlistUpdates: boolean; dropsOffers: boolean }
}
const PREFS_KEY = 'beatzclik-fan-settings'
const DEFAULTS: FanPrefs = {
  country: 'Ghana',
  phone: '0244 ··· 9210',
  streamingQuality: 'High (256 kbps)',
  downloadQuality: 'Very high (320 kbps)',
  crossfade: 'Off',
  dataSaver: false,
  notif: { newReleases: true, playlistUpdates: true, dropsOffers: false },
}
function loadPrefs(): FanPrefs {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PREFS_KEY) : null
    return raw ? { ...DEFAULTS, ...(JSON.parse(raw) as Partial<FanPrefs>) } : DEFAULTS
  } catch { return DEFAULTS }
}

const THEMES: { value: Theme; label: string; icon: LucideIcon }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

function SettingsComponent() {
  const { account, logout } = useAuth()
  const { theme, setTheme } = useTheme()
  const { ownedTracks, userPlaylists } = useCollection()
  const { toast } = useToast()

  const [prefs, setPrefs] = useState<FanPrefs>(loadPrefs)
  useEffect(() => { try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)) } catch { /* ignore */ } }, [prefs])
  const set = <K extends keyof FanPrefs>(k: K, v: FanPrefs[K]) => setPrefs((p) => ({ ...p, [k]: v }))
  const setNotif = (k: keyof FanPrefs['notif'], v: boolean) => setPrefs((p) => ({ ...p, notif: { ...p.notif, [k]: v } }))

  const name = account?.name ?? 'Listener'
  const email = account?.email ?? ''

  return (
    <div className="flex flex-col gap-8 pb-20">
      <h1 className="text-display text-beatz-dark-bg dark:text-white">Settings</h1>

      <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-8 items-start">
        {/* Profile + premium */}
        <div className="flex flex-col gap-4">
          <div className={cn(CARD, 'flex flex-col items-center text-center !p-8')}>
            <div className="w-28 h-28 rounded-full overflow-hidden mb-5 flex items-center justify-center bg-beatz-light-surface-2 dark:bg-white/5">
              {account?.avatar ? <img src={account.avatar} alt={name} className="w-full h-full object-cover" /> : <span className="text-3xl font-bold text-gray-500 dark:text-gray-300">{initialsOfAccount(name)}</span>}
            </div>
            <h2 className="text-2xl font-bold text-beatz-dark-bg dark:text-white">{name}</h2>
            <span className="text-sm text-gray-500 dark:text-gray-400 mb-6">{email}</span>
            <button onClick={() => toast('Edit your profile', 'info')} className="h-10 px-6 rounded-full border border-gray-300 dark:border-white/20 text-xs font-bold text-beatz-dark-bg dark:text-white flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-white/5 transition-colors mb-8">
              <Edit2 size={14} /> Edit profile
            </button>
            <div className="w-full pt-6 border-t border-gray-200 dark:border-white/5 grid grid-cols-3 gap-2">
              <Stat label="Owned" value={ownedTracks.length.toString()} />
              <Stat label="Playlists" value={userPlaylists.length.toString()} />
              <Stat label="Spent" value="₵312" />
            </div>
          </div>
          <div className={cn(CARD, 'flex flex-col gap-4')}>
            <div className="flex flex-col gap-1">
              <span className="text-[10px] font-bold text-[#f6c644] uppercase tracking-widest">Beatzclik Premium</span>
              <p className="text-sm font-bold text-beatz-dark-bg dark:text-white leading-snug">Unlimited streams · 15% off all buys</p>
            </div>
            <button onClick={() => toast('Upgrade to Premium', 'info')} className="h-12 w-full rounded-full bg-[#f6c644] text-black font-bold hover:bg-[#e5b83d] transition-colors">Upgrade · ₵40/mo</button>
          </div>
        </div>

        {/* Settings groups */}
        <div className="flex flex-col gap-6">
          <Group title="Appearance">
            <div className="grid grid-cols-3 gap-2">
              {THEMES.map((t) => {
                const active = theme === t.value
                return (
                  <button key={t.value} onClick={() => setTheme(t.value)}
                    className={cn('flex items-center justify-center gap-2 h-11 rounded-xl border text-sm font-bold transition-colors',
                      active ? 'border-beatz-green bg-beatz-green/10 text-beatz-green' : 'border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-white/20')}>
                    <t.icon size={15} /> {t.label}
                  </button>
                )
              })}
            </div>
          </Group>

          <Group title="Account">
            <Row label="Email" desc={email} />
            <Row label="Plan" desc="Free"><span className="text-sm font-bold text-[#f6c644]">Upgrade</span></Row>
            <Row label="Country">
              <select className={SELECT} value={prefs.country} onChange={(e) => set('country', e.target.value)}>
                {['Ghana', 'Nigeria', 'Côte d’Ivoire', 'UK', 'USA'].map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </Row>
            <Row label="Phone (MoMo)" last>
              <input value={prefs.phone} onChange={(e) => set('phone', e.target.value)} className={cn(SELECT, 'w-40 text-right')} />
            </Row>
          </Group>

          <Group title="Audio">
            <Row label="Streaming quality">
              <select className={SELECT} value={prefs.streamingQuality} onChange={(e) => set('streamingQuality', e.target.value)}>
                {['Normal (128 kbps)', 'High (256 kbps)', 'Very high (320 kbps)'].map((q) => <option key={q} value={q}>{q}</option>)}
              </select>
            </Row>
            <Row label="Download quality">
              <select className={SELECT} value={prefs.downloadQuality} onChange={(e) => set('downloadQuality', e.target.value)}>
                {['High (256 kbps)', 'Very high (320 kbps)', 'Lossless'].map((q) => <option key={q} value={q}>{q}</option>)}
              </select>
            </Row>
            <Row label="Crossfade" last>
              <select className={SELECT} value={prefs.crossfade} onChange={(e) => set('crossfade', e.target.value)}>
                {['Off', '3s', '6s', '12s'].map((q) => <option key={q} value={q}>{q}</option>)}
              </select>
            </Row>
          </Group>

          <Group title="Data & storage">
            <ToggleRow label="Data saver" desc="Lower quality on mobile data" checked={prefs.dataSaver} onChange={(v) => set('dataSaver', v)} />
            <Row label="Downloads" desc={`${ownedTracks.length} tracks · 1.4 GB`} />
            <Row label="Cache" desc="420 MB" last><button onClick={() => toast('Cache cleared', 'success')} className="text-sm font-bold text-beatz-green hover:underline">Clear</button></Row>
          </Group>

          <Group title="Notifications">
            <ToggleRow label="New releases" desc="From artists you follow" checked={prefs.notif.newReleases} onChange={(v) => setNotif('newReleases', v)} />
            <ToggleRow label="Playlist updates" desc="When followed playlists change" checked={prefs.notif.playlistUpdates} onChange={(v) => setNotif('playlistUpdates', v)} />
            <ToggleRow label="Drops & offers" desc="Promos and exclusive drops" checked={prefs.notif.dropsOffers} onChange={(v) => setNotif('dropsOffers', v)} last />
          </Group>

          <button onClick={logout} className="self-start h-11 px-5 rounded-full bg-beatz-red/10 text-beatz-red font-bold text-sm flex items-center gap-2 hover:bg-beatz-red/20 transition-colors">
            <LogOut size={16} /> Log out
          </button>
        </div>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col items-center">
      <span className="text-2xl font-bold text-beatz-dark-bg dark:text-white leading-none">{value}</span>
      <span className="text-[10px] font-bold text-gray-500 dark:text-gray-400 uppercase tracking-widest mt-2">{label}</span>
    </div>
  )
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className={cn(CARD, 'flex flex-col gap-1')}>
      <h3 className={cn(LABEL, 'mb-2')}>{title}</h3>
      {children}
    </section>
  )
}

function Row({ label, desc, children, last }: { label: string; desc?: string; children?: React.ReactNode; last?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between gap-4 py-3', !last && 'border-b border-dashed border-gray-200 dark:border-white/5')}>
      <div className="flex flex-col min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{label}</span>
        {desc && <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{desc}</span>}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  )
}

function ToggleRow({ label, desc, checked, onChange, last }: { label: string; desc?: string; checked: boolean; onChange: (v: boolean) => void; last?: boolean }) {
  return (
    <Row label={label} desc={desc} last={last}>
      <Toggle checked={checked} onChange={onChange} label={label} />
    </Row>
  )
}
