import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import {
  User, Lock, Bell, Tag, Wallet, Eye, Link2, BadgeCheck, Users, CreditCard, AlertTriangle,
  Search, Trash2, Plus, Check, Monitor, LogOut, Palette, Sun, Moon, type LucideIcon,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { Toggle } from '../components/ui/toggle'
import { useToast } from '../components/ui/toast-provider'
import { useTheme, type Theme } from '../components/theme-provider'
import {
  STUDIO_LANGUAGES, PRICE_OPTIONS,
  type StudioSettings, type TeamMember,
} from '../lib/studio-data'
import { studioSettingsQuery, apiSaveStudioSettings } from '../lib/api/queries/studio'

export const Route = createFileRoute('/studio/settings')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioSettingsQuery()),
  component: SettingsComponent,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none scroll-mt-24'
const INPUT =
  'w-full h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'

interface SectionDef { id: string; label: string; icon: LucideIcon; keywords: string }
const SECTIONS: SectionDef[] = [
  { id: 'account', label: 'Account', icon: User, keywords: 'account email phone country language timezone' },
  { id: 'appearance', label: 'Appearance', icon: Palette, keywords: 'appearance theme dark light system display mode' },
  { id: 'security', label: 'Security', icon: Lock, keywords: 'security password two factor 2fa sessions devices sign out login' },
  { id: 'notifications', label: 'Notifications', icon: Bell, keywords: 'notifications sale tip follower payout summary comments marketing email' },
  { id: 'sales', label: 'Sales & releases', icon: Tag, keywords: 'sales releases default price visibility explicit offers discounts' },
  { id: 'payouts', label: 'Payouts', icon: Wallet, keywords: 'payouts auto withdraw threshold currency tax tin' },
  { id: 'privacy', label: 'Privacy', icon: Eye, keywords: 'privacy visibility discover search real name booking messages dms' },
  { id: 'apps', label: 'Connected apps', icon: Link2, keywords: 'connected apps instagram audiomack ghamro youtube content id integrations' },
  { id: 'verification', label: 'Verification', icon: BadgeCheck, keywords: 'verification verified identity kyc payout rights ownership' },
  { id: 'team', label: 'Team', icon: Users, keywords: 'team access members manager label invite collaborators' },
  { id: 'billing', label: 'Billing', icon: CreditCard, keywords: 'billing subscription plan studio pro invoice renew upgrade' },
  { id: 'danger', label: 'Danger zone', icon: AlertTriangle, keywords: 'danger deactivate delete account' },
]

const THEME_OPTIONS: { value: Theme; label: string; icon: LucideIcon }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

function ThemeSwatch({ variant }: { variant: Theme }) {
  const Panel = ({ dark }: { dark: boolean }) => (
    <div className={cn('flex-1 h-full flex flex-col gap-1 p-2', dark ? 'bg-[#121212]' : 'bg-[#FAF7F2]')}>
      <span className="w-7 h-1 rounded-full bg-beatz-green" />
      <span className={cn('w-9 h-1 rounded-full', dark ? 'bg-white/30' : 'bg-black/20')} />
      <span className={cn('w-5 h-1 rounded-full', dark ? 'bg-white/15' : 'bg-black/10')} />
    </div>
  )
  return (
    <div className="w-full h-16 rounded-lg overflow-hidden border border-black/10 dark:border-white/10 flex">
      {variant === 'system' ? (<><Panel dark={false} /><Panel dark={true} /></>) : <Panel dark={variant === 'dark'} />}
    </div>
  )
}

function findScrollParent(node: HTMLElement | null): HTMLElement | null {
  let el = node?.parentElement ?? null
  while (el) {
    const oy = getComputedStyle(el).overflowY
    if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight) return el
    el = el.parentElement
  }
  return null
}

function useScrollSpy(ids: string[], rootRef: React.RefObject<HTMLElement | null>) {
  const [active, setActive] = useState(ids[0])
  const key = ids.join(',')
  useEffect(() => {
    const scroller = findScrollParent(rootRef.current)
    if (!scroller) return
    let raf = 0
    const onScroll = () => {
      cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => {
        const threshold = scroller.getBoundingClientRect().top + 120
        let current = ids[0]
        for (const id of ids) {
          const el = document.getElementById(id)
          if (el && el.getBoundingClientRect().top <= threshold) current = id
        }
        setActive(current)
      })
    }
    scroller.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
    return () => { scroller.removeEventListener('scroll', onScroll); cancelAnimationFrame(raf) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, rootRef])
  return active
}

function SettingsComponent() {
  const { toast } = useToast()
  const { theme, setTheme } = useTheme()
  const queryClient = useQueryClient()
  const { data: storeSettings } = useSuspenseQuery(studioSettingsQuery())
  const [s, setS] = useState<StudioSettings>(storeSettings)
  const [query, setQuery] = useState('')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<TeamMember['role']>('Manager')
  const [deleteOpen, setDeleteOpen] = useState(false)

  const rootRef = useRef<HTMLDivElement>(null)
  const dirty = useMemo(() => JSON.stringify(s) !== JSON.stringify(storeSettings), [s, storeSettings])

  const q = query.trim().toLowerCase()
  const visible = SECTIONS.filter((sec) => !q || sec.keywords.includes(q) || sec.label.toLowerCase().includes(q))
  const show = (id: string) => visible.some((v) => v.id === id)
  const activeId = useScrollSpy(visible.map((v) => v.id), rootRef)

  const set = <K extends keyof StudioSettings>(k: K, v: StudioSettings[K]) => setS((p) => ({ ...p, [k]: v }))
  const setNotif = (k: keyof StudioSettings['notifications'], v: boolean) => setS((p) => ({ ...p, notifications: { ...p.notifications, [k]: v } }))
  const setDefault = <K extends keyof StudioSettings['defaults']>(k: K, v: StudioSettings['defaults'][K]) => setS((p) => ({ ...p, defaults: { ...p.defaults, [k]: v } }))
  const setPayout = <K extends keyof StudioSettings['payouts']>(k: K, v: StudioSettings['payouts'][K]) => setS((p) => ({ ...p, payouts: { ...p.payouts, [k]: v } }))
  const setPrivacy = (k: keyof StudioSettings['privacy'], v: boolean) => setS((p) => ({ ...p, privacy: { ...p.privacy, [k]: v } }))

  const jump = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  const invite = () => {
    const email = inviteEmail.trim()
    if (!email) return
    set('team', [...s.team, { id: `u-${Date.now()}`, name: email.split('@')[0], email, role: inviteRole }])
    setInviteEmail('')
    toast(`Invite sent to ${email}`, 'success')
  }
  const removeMember = (id: string) => set('team', s.team.filter((m) => m.id !== id))
  const signOutSession = (id: string) => { set('sessions', s.sessions.filter((x) => x.id !== id)); toast('Signed out of device', 'success') }
  const toggleApp = (id: string) => set('connectedApps', s.connectedApps.map((a) => a.id === id ? { ...a, connected: !a.connected } : a))

  // NOTE: PUT /v1/studio/settings persists only Category-A settings
  // (notifications, sales defaults, payouts, privacy, team). Category-B
  // controls below — account email/phone/country/language, 2FA, active
  // sessions, connected apps, verification, billing — have no persistence
  // endpoint yet; their edits are session-local and revert on refetch.
  // TODO(studio-slice-later): wire 2FA/sessions to identity, connected
  // apps to integrations, billing to subscription when those land.
  const save = async () => {
    const key = studioSettingsQuery().queryKey
    const previous = queryClient.getQueryData(key)
    queryClient.setQueryData(key, s)
    try {
      const saved = await apiSaveStudioSettings(s)
      // Server echoes the full shape; merge so Category-B local edits in `s`
      // are not clobbered mid-session while Category-A becomes canonical.
      queryClient.setQueryData(key, { ...s, ...saved })
      toast('Settings saved', 'success')
    } catch {
      queryClient.setQueryData(key, previous)
      toast('Could not save your settings. Please try again.', 'error')
    }
  }

  return (
    <div ref={rootRef} className="flex flex-col gap-6">
      {/* Sticky save bar */}
      <div className="sticky top-14 md:top-0 z-30 -mx-4 sm:-mx-6 md:-mx-10 lg:-mx-14 px-4 sm:px-6 md:px-10 lg:px-14 py-4 bg-beatz-light-bg/90 dark:bg-beatz-dark-bg/90 backdrop-blur-xl flex items-center justify-between gap-4 flex-wrap border-b border-gray-200 dark:border-white/5">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl lg:text-3xl font-bold tracking-tight text-beatz-dark-bg dark:text-white">Settings</h1>
          {dirty && <span className="text-[11px] font-bold text-[#b8881f] dark:text-[#f6c644] bg-[#f6c644]/15 px-2.5 py-1 rounded-full">Unsaved</span>}
        </div>
        <div className="flex items-center gap-3">
          <div className="relative w-40 sm:w-56">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search settings"
              className="w-full h-10 pl-9 pr-3 rounded-full bg-gray-100 dark:bg-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none" />
          </div>
          {dirty && <button onClick={() => setS(storeSettings)} className="h-10 px-4 rounded-full text-gray-500 dark:text-gray-300 font-bold text-sm hover:text-beatz-dark-bg dark:hover:text-white transition-colors">Discard</button>}
          <button onClick={save} disabled={!dirty}
            className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100">
            Save
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[210px_1fr] gap-8 items-start">
        {/* Section nav */}
        <nav className="hidden lg:flex flex-col gap-1 lg:sticky lg:top-24 self-start">
          {visible.map((sec) => (
            <button key={sec.id} onClick={() => jump(sec.id)}
              className={cn('flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-bold text-left transition-colors',
                activeId === sec.id ? 'bg-gray-100 dark:bg-white/10 text-beatz-green' : 'text-gray-500 dark:text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-50 dark:hover:bg-white/5')}>
              <sec.icon size={16} /> {sec.label}
            </button>
          ))}
          {visible.length === 0 && <span className="text-xs text-gray-400 px-3">No matches</span>}
        </nav>

        {/* Sections */}
        <div className="flex flex-col gap-6 min-w-0">
          {visible.length === 0 && (
            <div className="py-16 text-center text-sm text-gray-400 dark:text-gray-500">No settings match “{query}”.</div>
          )}

          {show('account') && (
            <Section id="account" icon={User} title="Account" desc="Your contact details.">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <Field label="Email"><input className={INPUT} type="email" value={s.email} onChange={(e) => set('email', e.target.value)} /></Field>
                <Field label="Phone (MoMo)"><input className={INPUT} value={s.phone} onChange={(e) => set('phone', e.target.value)} /></Field>
                <Field label="Country"><input className={INPUT} value={s.country} onChange={(e) => set('country', e.target.value)} /></Field>
                <Field label="Language">
                  <select className={cn(INPUT, 'appearance-none cursor-pointer')} value={s.language} onChange={(e) => set('language', e.target.value)}>
                    {STUDIO_LANGUAGES.map((l) => <option key={l} value={l}>{l}</option>)}
                  </select>
                </Field>
              </div>
            </Section>
          )}

          {show('appearance') && (
            <Section id="appearance" icon={Palette} title="Appearance" desc="Choose how the studio looks. Applies instantly across BeatzClik.">
              <div className="grid grid-cols-3 gap-3">
                {THEME_OPTIONS.map((opt) => {
                  const active = theme === opt.value
                  return (
                    <button key={opt.value} onClick={() => setTheme(opt.value)}
                      className={cn('flex flex-col items-center gap-3 p-4 rounded-xl border transition-colors',
                        active ? 'border-beatz-green bg-beatz-green/5' : 'border-gray-200 dark:border-white/10 hover:border-gray-300 dark:hover:border-white/20')}>
                      <ThemeSwatch variant={opt.value} />
                      <span className="flex items-center gap-1.5 text-sm font-bold">
                        <opt.icon size={14} className={active ? 'text-beatz-green' : 'text-gray-500 dark:text-gray-300'} />
                        <span className={active ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white'}>{opt.label}</span>
                      </span>
                    </button>
                  )
                })}
              </div>
            </Section>
          )}

          {show('security') && (
            <Section id="security" icon={Lock} title="Security" desc="Protect your account and review where you're signed in.">
              <Row label="Password" desc="Last changed 3 months ago">
                <button onClick={() => toast('Password reset link sent to your email', 'success')} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">Change</button>
              </Row>
              <ToggleRow label="Two-factor authentication" desc="Require a code on login. Recommended for creators handling payouts." checked={s.twoFactor} onChange={(v) => set('twoFactor', v)} />
              <div className="flex items-center justify-between pt-2">
                <span className={LABEL}>Active sessions</span>
                <button onClick={() => { set('sessions', s.sessions.filter((x) => x.current)); toast('Signed out of all other devices', 'success') }} className="text-xs font-bold text-beatz-red hover:underline">Sign out all others</button>
              </div>
              <div className="flex flex-col gap-3">
                {s.sessions.map((sess) => (
                  <div key={sess.id} className="flex items-center gap-3 py-1">
                    <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Monitor size={16} /></span>
                    <div className="flex flex-col flex-1 min-w-0">
                      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{sess.device} {sess.current && <span className="text-beatz-green text-xs">· this device</span>}</span>
                      <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{sess.location} · {sess.lastActive}</span>
                    </div>
                    {!sess.current && <button onClick={() => signOutSession(sess.id)} aria-label="Sign out" className="h-8 px-3 rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 text-xs font-bold flex items-center gap-1.5 transition-colors"><LogOut size={13} /> Sign out</button>}
                  </div>
                ))}
              </div>
            </Section>
          )}

          {show('notifications') && (
            <Section id="notifications" icon={Bell} title="Notifications" desc="How Beatzclik keeps you posted.">
              <ToggleRow label="New sale" desc="When a fan buys one of your tracks." checked={s.notifications.sales} onChange={(v) => setNotif('sales', v)} />
              <ToggleRow label="New tip" desc="When a fan sends you a tip." checked={s.notifications.tips} onChange={(v) => setNotif('tips', v)} />
              <ToggleRow label="New follower" desc="When someone follows your profile." checked={s.notifications.followers} onChange={(v) => setNotif('followers', v)} />
              <ToggleRow label="Payout processed" desc="When a withdrawal is sent." checked={s.notifications.payouts} onChange={(v) => setNotif('payouts', v)} />
              <ToggleRow label="Weekly summary" desc="A digest of streams, sales and earnings." checked={s.notifications.weeklySummary} onChange={(v) => setNotif('weeklySummary', v)} />
              <ToggleRow label="Comments & mentions" desc="Replies and mentions on your releases." checked={s.notifications.comments} onChange={(v) => setNotif('comments', v)} />
              <ToggleRow label="Product & marketing emails" desc="Tips, features and promos." checked={s.notifications.marketing} onChange={(v) => setNotif('marketing', v)} last />
            </Section>
          )}

          {show('sales') && (
            <Section id="sales" icon={Tag} title="Sales & releases" desc="Pre-filled when you create a new release.">
              <Row label="Default track price" desc="Applied to new tracks.">
                <select className={cn(INPUT, 'w-32 appearance-none cursor-pointer')} value={s.defaults.trackPrice} onChange={(e) => setDefault('trackPrice', Number(e.target.value))}>
                  {PRICE_OPTIONS.map((p) => <option key={p} value={p}>{p === 0 ? 'Free' : `₵${p.toFixed(2)}`}</option>)}
                </select>
              </Row>
              <Row label="Default visibility" desc="Publish immediately or schedule.">
                <select className={cn(INPUT, 'w-36 appearance-none cursor-pointer')} value={s.defaults.releaseVisibility} onChange={(e) => setDefault('releaseVisibility', e.target.value as 'public' | 'scheduled')}>
                  <option value="scheduled">Scheduled</option>
                  <option value="public">Public now</option>
                </select>
              </Row>
              <ToggleRow label="Auto-flag explicit content" desc="Tag explicit tracks automatically." checked={s.defaults.autoExplicit} onChange={(v) => setDefault('autoExplicit', v)} />
              <ToggleRow label="Allow offers & discounts" desc="Let fans request bundle discounts." checked={s.defaults.allowOffers} onChange={(v) => setDefault('allowOffers', v)} last />
            </Section>
          )}

          {show('payouts') && (
            <Section id="payouts" icon={Wallet} title="Payouts" desc="Defaults for how you get paid.">
              <ToggleRow label="Auto-withdraw" desc="Cash out automatically past a threshold." checked={s.payouts.autoWithdraw} onChange={(v) => setPayout('autoWithdraw', v)} />
              {s.payouts.autoWithdraw && (
                <Row label="Auto-withdraw threshold" desc="Cash out once balance exceeds this.">
                  <div className="relative w-36">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₵</span>
                    <input type="number" className={cn(INPUT, 'pl-7')} value={s.payouts.autoWithdrawThreshold} onChange={(e) => setPayout('autoWithdrawThreshold', Number(e.target.value) || 0)} />
                  </div>
                </Row>
              )}
              <Row label="Currency" desc="Earnings and payouts are in Ghana Cedis."><span className="text-sm font-bold text-beatz-dark-bg dark:text-white">GHS (₵)</span></Row>
              <Field label="Tax ID (TIN)"><input className={cn(INPUT, 'max-w-xs')} value={s.payouts.taxId} onChange={(e) => setPayout('taxId', e.target.value)} placeholder="Optional · for tax reporting" /></Field>
            </Section>
          )}

          {show('privacy') && (
            <Section id="privacy" icon={Eye} title="Privacy & visibility" desc="Control how fans find and reach you.">
              <ToggleRow label="Show in search & discovery" desc="Appear in search and recommendations." checked={s.privacy.discoverable} onChange={(v) => setPrivacy('discoverable', v)} />
              <ToggleRow label="Show real name" desc="Display your legal name alongside your artist name." checked={s.privacy.showRealName} onChange={(v) => setPrivacy('showRealName', v)} />
              <ToggleRow label="Accept booking requests" desc="Let promoters reach you via your booking email." checked={s.privacy.acceptBookings} onChange={(v) => setPrivacy('acceptBookings', v)} />
              <ToggleRow label="Allow messages from fans" desc="Receive direct messages from listeners." checked={s.privacy.allowDms} onChange={(v) => setPrivacy('allowDms', v)} last />
            </Section>
          )}

          {show('apps') && (
            <Section id="apps" icon={Link2} title="Connected apps" desc="Integrations that extend your studio.">
              <div className="flex flex-col gap-3">
                {s.connectedApps.map((a) => (
                  <div key={a.id} className="flex items-center gap-3 py-1">
                    <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Link2 size={15} /></span>
                    <div className="flex flex-col flex-1 min-w-0">
                      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{a.name}</span>
                      <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{a.description}</span>
                    </div>
                    <button onClick={() => toggleApp(a.id)} className={cn('h-9 px-4 rounded-full text-xs font-bold transition-colors shrink-0',
                      a.connected ? 'bg-beatz-green/10 text-beatz-green hover:bg-beatz-green/20' : 'bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white hover:bg-gray-200 dark:hover:bg-white/15')}>
                      {a.connected ? 'Connected' : 'Connect'}
                    </button>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {show('verification') && (
            <Section id="verification" icon={BadgeCheck} title="Verification" desc="Status of your artist and payout verification.">
              <div className="flex flex-col gap-3">
                <VerifyRow label="Verified artist" desc="Blue check on your profile." ok={s.verification.artist} />
                <VerifyRow label="Identity verified" desc="Government ID confirmed." ok={s.verification.identity} />
                <VerifyRow label="Payout details verified" desc="MoMo / bank account confirmed." ok={s.verification.payout} />
                <VerifyRow label="Rights & ownership" desc="Confirm you own the rights to your catalog." ok={s.verification.rights} onFix={() => toast('Rights verification started — check your email', 'info')} />
              </div>
            </Section>
          )}

          {show('team') && (
            <Section id="team" icon={Users} title="Team access" desc="People who can manage this studio.">
              <div className="flex flex-col gap-3">
                {s.team.map((m) => (
                  <div key={m.id} className="flex items-center gap-3 py-1">
                    <div className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-xs font-bold text-gray-600 dark:text-gray-300 shrink-0">{(m.name[0] ?? '?').toUpperCase()}</div>
                    <div className="flex flex-col flex-1 min-w-0">
                      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{m.name}</span>
                      <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{m.email}</span>
                    </div>
                    <span className={cn('text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full',
                      m.role === 'Owner' ? 'bg-beatz-green/15 text-beatz-green' : m.role === 'Invited' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300')}>{m.role}</span>
                    {m.role !== 'Owner' && <button onClick={() => removeMember(m.id)} aria-label="Remove member" className="w-7 h-7 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors shrink-0"><Trash2 size={14} /></button>}
                  </div>
                ))}
              </div>
              <Divider />
              <div className="flex items-end gap-3 flex-wrap">
                <div className="flex flex-col gap-2 flex-1 min-w-[200px]">
                  <label className={LABEL}>Invite by email</label>
                  <input className={INPUT} type="email" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} placeholder="name@example.com" onKeyDown={(e) => { if (e.key === 'Enter') invite() }} />
                </div>
                <select className={cn(INPUT, 'w-36 appearance-none cursor-pointer')} value={inviteRole} onChange={(e) => setInviteRole(e.target.value as TeamMember['role'])}>
                  <option value="Manager">Manager</option>
                  <option value="Label">Label</option>
                </select>
                <button onClick={invite} disabled={!inviteEmail.trim()} className="h-11 px-5 rounded-full bg-beatz-green/10 text-beatz-green font-bold text-sm flex items-center gap-2 hover:bg-beatz-green/20 transition-colors disabled:opacity-40"><Plus size={16} /> Invite</button>
              </div>
            </Section>
          )}

          {show('billing') && (
            <Section id="billing" icon={CreditCard} title="Billing & subscription" desc="Your studio plan.">
              <div className="flex items-center justify-between gap-4 p-4 rounded-xl bg-beatz-green/5 border border-beatz-green/20">
                <div className="flex flex-col">
                  <span className="text-base font-bold text-beatz-dark-bg dark:text-white">{s.billing.plan} · ₵{s.billing.price}/mo</span>
                  <span className="text-xs text-gray-500 dark:text-gray-400">Renews {s.billing.renews}</span>
                </div>
                <button onClick={() => toast('Opening billing portal', 'info')} className="h-9 px-4 rounded-full bg-beatz-green text-black text-xs font-bold hover:scale-105 transition-transform">Manage</button>
              </div>
              <Row label="Invoices" desc="Download past receipts." last>
                <button onClick={() => toast('Exporting invoices', 'success')} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">View invoices</button>
              </Row>
            </Section>
          )}

          {show('danger') && (
            <section id="danger" className="rounded-2xl border border-beatz-red/30 p-6 flex flex-col gap-5 scroll-mt-24">
              <div className="flex flex-col gap-1">
                <h2 className="text-lg font-bold text-beatz-red flex items-center gap-2"><AlertTriangle size={18} /> Danger zone</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400">Irreversible actions for this artist account.</p>
              </div>
              <Row label="Deactivate profile" desc="Temporarily hide your profile and catalog.">
                <button onClick={() => toast('Profile deactivated — reactivate any time', 'info')} className="h-9 px-4 rounded-full border border-beatz-red/40 text-beatz-red text-xs font-bold hover:bg-beatz-red/10 transition-colors">Deactivate</button>
              </Row>
              <Row label="Delete studio account" desc="Permanently remove your studio, releases and earnings." last>
                <button onClick={() => setDeleteOpen(true)} className="h-9 px-4 rounded-full bg-beatz-red text-white text-xs font-bold hover:bg-beatz-red-light transition-colors">Delete</button>
              </Row>
            </section>
          )}
        </div>
      </div>

      <DeleteModal isOpen={deleteOpen} onClose={() => setDeleteOpen(false)} onConfirm={() => { setDeleteOpen(false); toast('Account deletion requested', 'error') }} />
    </div>
  )
}

function Section({ id, icon: Icon, title, desc, children }: { id: string; icon: LucideIcon; title: string; desc?: string; children: React.ReactNode }) {
  return (
    <section id={id} className={cn(CARD, 'flex flex-col gap-5')}>
      <div className="flex items-start gap-3">
        <span className="w-9 h-9 rounded-lg bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Icon size={17} /></span>
        <div className="flex flex-col gap-0.5">
          <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white leading-tight">{title}</h2>
          {desc && <p className="text-sm text-gray-500 dark:text-gray-400">{desc}</p>}
        </div>
      </div>
      {children}
    </section>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="flex flex-col gap-2.5"><label className={LABEL}>{label}</label>{children}</div>
}

function Divider() { return <div className="h-px bg-gray-200 dark:bg-white/10" /> }

function Row({ label, desc, children, last }: { label: string; desc?: string; children: React.ReactNode; last?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between gap-4 py-3', !last && 'border-b border-dashed border-gray-200 dark:border-white/5')}>
      <div className="flex flex-col min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{label}</span>
        {desc && <span className="text-xs text-gray-500 dark:text-gray-400">{desc}</span>}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  )
}

function ToggleRow({ label, desc, checked, onChange, last }: { label: string; desc?: string; checked: boolean; onChange: (v: boolean) => void; last?: boolean }) {
  return <Row label={label} desc={desc} last={last}><Toggle checked={checked} onChange={onChange} label={label} /></Row>
}

function VerifyRow({ label, desc, ok, onFix }: { label: string; desc: string; ok: boolean; onFix?: () => void }) {
  return (
    <div className="flex items-center gap-3 py-1">
      <span className={cn('w-7 h-7 rounded-full flex items-center justify-center shrink-0', ok ? 'bg-beatz-green text-black' : 'border-2 border-[#f6c644] text-[#f6c644]')}>
        {ok ? <Check size={14} strokeWidth={3} /> : <AlertTriangle size={13} />}
      </span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{label}</span>
        <span className="text-xs text-gray-500 dark:text-gray-400">{desc}</span>
      </div>
      {!ok && onFix && <button onClick={onFix} className="h-8 px-3 rounded-full bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644] text-xs font-bold hover:bg-[#f6c644]/30 transition-colors shrink-0">Verify</button>}
    </div>
  )
}

function DeleteModal({ isOpen, onClose, onConfirm }: { isOpen: boolean; onClose: () => void; onConfirm: () => void }) {
  const [text, setText] = useState('')
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Delete studio account">
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70 leading-relaxed">This permanently deletes your studio, all releases, splits and earnings history. This cannot be undone. Type <span className="font-bold text-white">DELETE</span> to confirm.</p>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="DELETE" className="w-full h-12 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={onConfirm} disabled={text !== 'DELETE'} className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">Delete account</button>
        </div>
      </div>
    </Modal>
  )
}
