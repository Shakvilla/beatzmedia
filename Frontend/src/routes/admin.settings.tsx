import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Plus, Trash2, ShieldCheck } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { Toggle } from '../components/ui/toggle'
import { getPlatformSettings, getAdminTeam, ADMIN_ROLES, type PlatformSettings, type AdminMember, type AdminRole } from '../lib/admin-data'

export const Route = createFileRoute('/admin/settings')({
  component: AdminSettings,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT = 'h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white focus:outline-none focus:border-beatz-green/60'
const ROLE_OPTIONS = ADMIN_ROLES.map((r) => r.role)

function AdminSettings() {
  const { toast } = useToast()
  const baseline = useMemo(() => getPlatformSettings(), [])
  const [s, setS] = useState<PlatformSettings>(baseline)
  const [team, setTeam] = useState<AdminMember[]>(() => getAdminTeam())
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<AdminRole>('Support')

  const dirty = useMemo(() => JSON.stringify(s) !== JSON.stringify(baseline), [s, baseline])
  const setProvider = (k: keyof PlatformSettings['providers'], v: boolean) => setS((p) => ({ ...p, providers: { ...p.providers, [k]: v } }))
  const setFlag = (k: keyof PlatformSettings['flags'], v: boolean) => setS((p) => ({ ...p, flags: { ...p.flags, [k]: v } }))

  const invite = () => {
    const email = inviteEmail.trim()
    if (!email) return
    setTeam((t) => [...t, { id: `a-${Date.now()}`, name: email.split('@')[0], email, role: inviteRole, lastActive: 'invited' }])
    setInviteEmail('')
    toast(`Invited ${email} as ${inviteRole}`, 'success')
  }
  const changeRole = (id: string, role: AdminRole) => setTeam((t) => t.map((m) => (m.id === id ? { ...m, role } : m)))
  const removeMember = (id: string) => setTeam((t) => t.filter((m) => m.id !== id))

  return (
    <div className="flex flex-col gap-6 max-w-4xl">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Settings</h1>
        <button onClick={() => toast('Platform settings saved', 'success')} disabled={!dirty}
          className="h-11 px-6 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100">
          Save changes
        </button>
      </div>

      {/* Platform */}
      <Section title="Platform" desc="Core commerce and availability settings.">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          <Field label="Platform fee (%)">
            <input type="number" className={cn(INPUT, 'w-full')} value={s.platformFeePct} onChange={(e) => setS((p) => ({ ...p, platformFeePct: Number(e.target.value) || 0 }))} />
          </Field>
          <Field label="Payout day">
            <select className={cn(INPUT, 'w-full appearance-none cursor-pointer')} value={s.payoutDay} onChange={(e) => setS((p) => ({ ...p, payoutDay: e.target.value }))}>
              {['Monday', 'Wednesday', 'Friday'].map((d) => <option key={d} value={d}>{d}</option>)}
            </select>
          </Field>
          <Field label="Minimum payout (₵)">
            <input type="number" className={cn(INPUT, 'w-full')} value={s.payoutMinimum} onChange={(e) => setS((p) => ({ ...p, payoutMinimum: Number(e.target.value) || 0 }))} />
          </Field>
          <Field label="Default currency">
            <input className={cn(INPUT, 'w-full opacity-60 cursor-not-allowed')} value={s.defaultCurrency} disabled />
          </Field>
        </div>
        <Divider />
        <ToggleRow label="Maintenance mode" desc="Take the apps offline with a maintenance notice." checked={s.maintenanceMode} onChange={(v) => setS((p) => ({ ...p, maintenanceMode: v }))} last />
      </Section>

      {/* Payment providers */}
      <Section title="Payment providers" desc="Which methods fans can pay with.">
        <ToggleRow label="MTN MoMo" checked={s.providers.momo} onChange={(v) => setProvider('momo', v)} />
        <ToggleRow label="Vodafone Cash" checked={s.providers.vodafone} onChange={(v) => setProvider('vodafone', v)} />
        <ToggleRow label="AirtelTigo Money" checked={s.providers.airteltigo} onChange={(v) => setProvider('airteltigo', v)} />
        <ToggleRow label="Card" checked={s.providers.card} onChange={(v) => setProvider('card', v)} />
        <ToggleRow label="Bank transfer" checked={s.providers.bank} onChange={(v) => setProvider('bank', v)} last />
      </Section>

      {/* Feature flags */}
      <Section title="Feature flags" desc="Roll features on or off platform-wide.">
        <ToggleRow label="New artist signups" checked={s.flags.artistSignups} onChange={(v) => setFlag('artistSignups', v)} />
        <ToggleRow label="Podcasts" checked={s.flags.podcasts} onChange={(v) => setFlag('podcasts', v)} />
        <ToggleRow label="Events & ticketing" checked={s.flags.events} onChange={(v) => setFlag('events', v)} />
        <ToggleRow label="Tipping" checked={s.flags.tipping} onChange={(v) => setFlag('tipping', v)} />
        <ToggleRow label="Fan messaging" desc="Direct messages from fans to artists." checked={s.flags.fanMessaging} onChange={(v) => setFlag('fanMessaging', v)} last />
      </Section>

      {/* Admin team & roles */}
      <Section title="Admin team & roles" desc="Who can access the console and what they can do.">
        <div className="flex flex-col gap-3">
          {team.map((m) => (
            <div key={m.id} className="flex items-center gap-3 py-2">
              <div className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-xs font-bold text-gray-600 dark:text-gray-300 shrink-0">{(m.name[0] ?? '?').toUpperCase()}</div>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{m.name}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{m.email} · {m.lastActive}</span>
              </div>
              {m.role === 'Super-admin' ? (
                <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-beatz-green/15 text-beatz-green flex items-center gap-1"><ShieldCheck size={11} /> Super-admin</span>
              ) : (
                <select value={m.role} onChange={(e) => changeRole(m.id, e.target.value as AdminRole)} className="h-8 px-3 rounded-full bg-gray-100 dark:bg-white/10 text-xs font-bold text-beatz-dark-bg dark:text-white focus:outline-none cursor-pointer appearance-none">
                  {ROLE_OPTIONS.filter((r) => r !== 'Super-admin').map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
              )}
              {m.role !== 'Super-admin' && (
                <button onClick={() => removeMember(m.id)} aria-label="Remove" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-red hover:bg-beatz-red/10 transition-colors shrink-0"><Trash2 size={14} /></button>
              )}
            </div>
          ))}
        </div>
        <Divider />
        <div className="flex items-end gap-3 flex-wrap">
          <div className="flex flex-col gap-2 flex-1 min-w-[200px]">
            <label className={LABEL}>Invite admin</label>
            <input className={cn(INPUT, 'w-full')} type="email" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} placeholder="name@beatzclik.com" onKeyDown={(e) => { if (e.key === 'Enter') invite() }} />
          </div>
          <select className={cn(INPUT, 'w-40 appearance-none cursor-pointer')} value={inviteRole} onChange={(e) => setInviteRole(e.target.value as AdminRole)}>
            {ROLE_OPTIONS.filter((r) => r !== 'Super-admin').map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
          <button onClick={invite} disabled={!inviteEmail.trim()} className="h-11 px-5 rounded-full bg-beatz-green/10 text-beatz-green font-bold text-sm flex items-center gap-2 hover:bg-beatz-green/20 transition-colors disabled:opacity-40"><Plus size={16} /> Invite</button>
        </div>
        <Divider />
        <div className="flex flex-col gap-2">
          <span className={LABEL}>Role permissions</span>
          {ADMIN_ROLES.map((r) => (
            <div key={r.role} className="flex items-center gap-3 py-1.5 text-sm">
              <span className="w-28 font-bold text-beatz-dark-bg dark:text-white shrink-0">{r.role}</span>
              <span className="text-gray-500 dark:text-gray-400">{r.scope}</span>
            </div>
          ))}
        </div>
      </Section>
    </div>
  )
}

function Section({ title, desc, children }: { title: string; desc?: string; children: React.ReactNode }) {
  return (
    <section className={cn(CARD, 'flex flex-col gap-5')}>
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">{title}</h2>
        {desc && <p className="text-sm text-gray-500 dark:text-gray-400">{desc}</p>}
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
      <div className="flex flex-col min-w-0"><span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{label}</span>{desc && <span className="text-xs text-gray-500 dark:text-gray-400">{desc}</span>}</div>
      <div className="shrink-0">{children}</div>
    </div>
  )
}
function ToggleRow({ label, desc, checked, onChange, last }: { label: string; desc?: string; checked: boolean; onChange: (v: boolean) => void; last?: boolean }) {
  return <Row label={label} desc={desc} last={last}><Toggle checked={checked} onChange={onChange} label={label} /></Row>
}
