import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, ShieldCheck } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { Toggle } from '../components/ui/toggle'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'
import { platformSettingsQuery, apiSaveSettings } from '../lib/api/queries/admin-settings'
import { adminTeamQuery, apiInviteAdmin, apiChangeAdminRole, apiRemoveAdmin } from '../lib/api/queries/admin-team'
import { ADMIN_ROLES, type PlatformSettings, type AdminRole } from '../lib/admin-data'

export const Route = createFileRoute('/admin/settings')({
  component: AdminSettings,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT = 'h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white focus:outline-none focus:border-beatz-green/60'
const ROLE_OPTIONS = ADMIN_ROLES.map((r) => r.role)

function AdminSettings() {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, isPending, refetch } = useQuery(platformSettingsQuery())
  const [draft, setDraft] = useState<PlatformSettings | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  // The real admin team. This used to be `useState(() => getAdminTeam())` — four invented people
  // (Yaa Mensima, Kofi Annor, Adwoa Smart, Kwame DJ) hardcoded in admin-data.ts. GET /v1/admin/team
  // was already being fetched on this page and returns the actual members; the UI simply threw the
  // response away and rendered the fakes, so the console showed a staff roster that did not exist.
  const { data: team = [] } = useQuery(adminTeamQuery())
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<AdminRole>('Support')
  const [teamBusy, setTeamBusy] = useState(false)

  // The draft is derived from the server copy, never seeded in an effect: it stays authoritative
  // until the user edits, which keeps `dirty` a true comparison against what the server holds.
  const s = draft ?? data ?? null
  const dirty = useMemo(() => (s && data ? JSON.stringify(s) !== JSON.stringify(data) : false), [s, data])

  const setS = (fn: (p: PlatformSettings) => PlatformSettings) => setDraft((p) => (p ? fn(p) : data ? fn(data) : null))
  const setFlag = (k: keyof PlatformSettings['flags'], v: boolean) => setS((p) => ({ ...p, flags: { ...p.flags, [k]: v } }))
  const setProvider = (k: keyof PlatformSettings['providers'], v: boolean) =>
    setS((p) => ({ ...p, providers: { ...p.providers, [k]: v } }))

  const save = async () => {
    if (!s || inFlight.current) return
    inFlight.current = true
    setSaving(true)
    let ok = false
    try { await apiSaveSettings(s); ok = true }
    catch (e) { toast(e instanceof ApiError && e.message ? e.message : 'Could not save platform settings', 'error') }
    finally {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'settings'] })
      inFlight.current = false
      setSaving(false)
      // Only fall back to the refetched server copy on success — on failure, keep the draft so the
      // admin can see what was rejected and retry, instead of it silently vanishing.
      if (ok) setDraft(null)
      if (ok) toast('Platform settings saved', 'success')
    }
  }

  /**
   * All three of these used to mutate local React state and nothing else, so a role change or a
   * removal vanished on the next reload and an invite was never sent. The endpoints existed the
   * whole time (`AdminTeamResource`: POST /invite, PATCH /{id}, DELETE /{id}, all super-admin).
   * Each now calls the API, invalidates the team query so the list reflects the server, and
   * reports a failure instead of a success it did not earn.
   */
  const refreshTeam = () => queryClient.invalidateQueries({ queryKey: ['admin', 'team'] })

  const invite = async () => {
    const email = inviteEmail.trim()
    if (!email || teamBusy) return
    setTeamBusy(true)
    try {
      await apiInviteAdmin(email, inviteRole)
      setInviteEmail('')
      await refreshTeam()
      toast(`Invited ${email} as ${inviteRole}`, 'success')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not send the invite', 'error')
    } finally {
      setTeamBusy(false)
    }
  }

  const changeRole = async (id: string, role: AdminRole) => {
    try {
      await apiChangeAdminRole(id, role)
      await refreshTeam()
      toast(`Role updated to ${role}`, 'success')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not change the role', 'error')
      await refreshTeam() // undo the optimistic-looking select by re-reading the server
    }
  }

  const removeMember = async (id: string) => {
    try {
      await apiRemoveAdmin(id)
      await refreshTeam()
      toast('Admin removed', 'success')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not remove this admin', 'error')
    }
  }

  return (
    <div className="flex flex-col gap-6 max-w-4xl">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Settings</h1>
        <button onClick={save} disabled={!dirty || saving}
          className="h-11 px-6 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform shadow-lg shadow-beatz-green/20 disabled:opacity-40 disabled:hover:scale-100">
          Save changes
        </button>
      </div>

      {isError ? (
        <AdminLoadError label="Couldn't load platform settings." onRetry={() => refetch()} />
      ) : isPending || !s ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* Platform */}
          <Section title="Platform" desc="Core commerce and availability settings.">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <Field label="Platform fee (%)">
                <input type="number" min={0} max={100} step={1} className={cn(INPUT, 'w-full')} value={s.platformFeePct} onChange={(e) => setS((p) => ({ ...p, platformFeePct: Math.min(100, Math.max(0, Math.round(Number(e.target.value) || 0))) }))} />
              </Field>
              <Field label="Payout day">
                <select className={cn(INPUT, 'w-full appearance-none cursor-pointer')} value={s.payoutDay} onChange={(e) => setS((p) => ({ ...p, payoutDay: e.target.value }))}>
                  {['Monday', 'Wednesday', 'Friday'].map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              </Field>
              <Field label="Minimum payout (₵)">
                <input type="number" min={0} step={0.01} className={cn(INPUT, 'w-full')} value={s.payoutMinimum} onChange={(e) => setS((p) => ({ ...p, payoutMinimum: Math.max(0, Number(e.target.value) || 0) }))} />
              </Field>
              <Field label="Default currency">
                <input className={cn(INPUT, 'w-full opacity-60 cursor-not-allowed')} value={s.defaultCurrency} disabled />
              </Field>
            </div>
            <Divider />
            <ToggleRow label="Maintenance mode" desc="Take the apps offline with a maintenance notice." checked={s.maintenanceMode} onChange={(v) => setS((p) => ({ ...p, maintenanceMode: v }))} last />
          </Section>

          {/*
            GAP-13: these were rendered `disabled` with the caption "Not yet configurable" — honest,
            but a control that could not do the thing it depicted. They are real now: switching one
            off stops new charges on that rail immediately, and the removal is named in the audit
            entry so "why did MoMo stop working on the 14th?" has an answer.

            "Vodafone Cash" is now "Telecel Cash" — the brand changed in 2023, and checkout and
            payouts already said Telecel. The admin console was the last place still using the old
            name, which meant an operator disabling "Vodafone" was toggling a rail the rest of the
            system called something else.
          */}
          <Section title="Payment providers" desc="Which methods fans can pay with. Switching one off stops new charges on that rail immediately; payouts are unaffected.">
            <ToggleRow label="MTN MoMo" checked={s.providers.mtn} onChange={(v) => setProvider('mtn', v)} />
            <ToggleRow label="Telecel Cash" checked={s.providers.telecel} onChange={(v) => setProvider('telecel', v)} />
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
          <Section title="Admin team & roles" desc="Who can access the console and what they can do. Only a super-admin may invite, change roles or remove.">
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
        </>
      )}
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
function ToggleRow({ label, desc, checked, onChange, last, disabled }: { label: string; desc?: string; checked: boolean; onChange: (v: boolean) => void; last?: boolean; disabled?: boolean }) {
  return <Row label={label} desc={desc} last={last}><Toggle checked={checked} onChange={onChange} label={label} disabled={disabled} /></Row>
}
