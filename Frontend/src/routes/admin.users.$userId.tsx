import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, BadgeCheck, MoreHorizontal, Ban, RotateCcw, KeyRound, LogIn, Download,
  Monitor, Clock, ShieldCheck,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import { type UserStatus } from '../lib/admin-data'
import { apiFetch } from '../lib/api/client'
import { userDetailQuery, usersQuery, apiVerifyUser, apiSuspendUser, apiReactivateUser, apiExportUserData, apiImpersonateUser } from '../lib/api/queries/admin-users'
import { AdminLoadError } from '../components/admin/load-error'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/admin/users/$userId')({
  component: AdminUserDetail,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis = (n: number) => `₵${n.toLocaleString('en-US', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })}`

function AdminUserDetail() {
  const { userId } = Route.useParams()
  const { toast } = useToast()
  const navigate = useNavigate()
  const { startImpersonation } = useAuth()
  const queryClient = useQueryClient()
  const { data, isPending, isError, refetch } = useQuery(userDetailQuery(userId))

  const [suspendOpen, setSuspendOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [impersonateOpen, setImpersonateOpen] = useState(false)

  if (isError) {
    return (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this user." onRetry={() => refetch()} />
      </div>
    )
  }

  const user = data?.summary
  const log = data?.actionLog ?? []
  // Served by the API and documented there as always-empty for now. Read them anyway rather than
  // hardcoding [], so these sections light up on their own the day the endpoint starts filling them.
  const activity = (data?.activity ?? []) as { id: string; text: string; time: string }[]
  const orders = (data?.orders ?? []) as { id: string; item: string; date: string; amount: number }[]
  const devices = (data?.devices ?? []) as {
    id: string; device: string; location: string; lastActive: string; current?: boolean
  }[]

  if (!user) {
    return isPending ? (
      <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
    ) : (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">User not found.</p>
        <Link to="/admin/users" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to users</Link>
      </div>
    )
  }

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: userDetailQuery(userId).queryKey })
    await queryClient.invalidateQueries({ queryKey: usersQuery().queryKey })
  }
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string) => {
    try { await fn(); await invalidate(); toast(okMsg, 'success') }
    catch { toast(errMsg, 'error') }
  }
  const verify = () => runAction(() => apiVerifyUser(user.id), `${user.name} verified`, 'Could not verify user')

  /**
   * Records a DSAR request against the account.
   *
   * <p>The toast said "Data export started". That was not true. {@code ExportUserDataService} is an
   * honest stub — it verifies the account, mints a job id and audits the request, and its own
   * javadoc states there is no DSAR queue or worker anywhere in this codebase to process it.
   * Nothing runs; no file is produced.
   *
   * <p>That is GAP-19's pattern — a control reporting success while nothing happens — on a
   * statutory obligation, where someone may rely on it to answer a regulator inside a deadline. The
   * request and its audit entry are real and worth keeping. The claim that an export is under way
   * is not.
   */
  const exportData = async () => {
    try {
      await apiExportUserData(user.id)
      toast(`DSAR request logged for ${user.email} — no file is produced yet`, 'info')
    } catch { toast('Could not record the data-export request', 'error') }
  }

  /**
   * The reset request is non-enumerating by design — it answers 204 whether or not the address is
   * registered — so the copy says "if an account exists" rather than asserting a send.
   */
  const sendReset = async () => {
    try {
      await apiFetch<void>('/me/password/reset', { method: 'POST', body: { email: user.email } })
      toast(`If an account exists for ${user.email}, a reset link is on its way`, 'success')
    } catch { toast('Could not start the password reset', 'error') }
  }

  /**
   * Swaps this browser session to the target account (GAP-08).
   *
   * <p>This previously minted the token and only reported that it had been issued — truthful, but
   * the operator could not use it, because nothing applied it to the session. The stash-and-restore
   * in `features/auth/impersonation.ts` is what makes the swap safe to leave: the operator's own
   * token is kept, so Exit returns them here rather than to a login screen.
   *
   * <p>The token itself is never rendered. It is a live bearer credential, and the backend
   * deliberately keeps it out of the audit log; putting it on screen would undo that care.
   */
  const impersonate = async () => {
    setImpersonateOpen(false)
    try {
      const issued = await apiImpersonateUser(user.id)
      await startImpersonation(issued.token, {
        subjectId: user.id,
        subjectName: user.name,
        expiresAt: issued.expiresAt,
      })
      void navigate({ to: '/' })
    } catch { toast('Could not start an impersonation session', 'error') }
  }
  const reactivate = () => runAction(() => apiReactivateUser(user.id), 'Reactivated account', 'Could not reactivate user')
  const suspend = (reason: string) => runAction(() => apiSuspendUser(user.id, reason), `Suspended · ${reason}`, 'Could not suspend user')

  const isArtist = user.role === 'artist'

  /**
   * These tiles read "Releases 12 · Revenue ₵42K · Followers 412K" for every artist and
   * "Purchases 3 · Lifetime spend ₵312 · Playlists 7" for every fan — hardcoded literals, shown
   * against real accounts. Revenue in particular is the kind of number an admin would act on.
   *
   * No endpoint exposes per-user totals, so the tiles show an em dash and say so rather than
   * carrying a figure nobody measured.
   */
  const stats = isArtist
    ? [{ label: 'Releases', value: '—' }, { label: 'Revenue', value: '—' }, { label: 'Followers', value: '—' }]
    : [{ label: 'Purchases', value: '—' }, { label: 'Lifetime spend', value: '—' }, { label: 'Playlists', value: '—' }]

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex flex-col gap-4">
        <Link to="/admin/users" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
          <ArrowLeft size={14} /> Users
        </Link>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center shrink-0 text-xl font-bold text-gray-600 dark:text-gray-300">{user.initial}</div>
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-3xl font-bold tracking-tight text-beatz-dark-bg dark:text-white">{user.name}</h1>
                {isArtist && user.verified && <BadgeCheck size={18} className="text-beatz-green" />}
                <StatusPill status={user.status} />
              </div>
              <span className="text-sm text-gray-500 dark:text-gray-300">{user.email} · {isArtist ? 'Artist' : 'Fan'} · joined {user.joined} · active {user.lastActive}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {isArtist && !user.verified && (
              <button onClick={verify} className="h-10 px-4 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold flex items-center gap-2 hover:bg-beatz-green/20 transition-colors"><BadgeCheck size={15} /> Verify</button>
            )}
            {user.status === 'suspended'
              ? <button onClick={reactivate} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><RotateCcw size={15} /> Reactivate</button>
              : <button onClick={() => setSuspendOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><Ban size={15} /> Suspend</button>}
            <div className="relative">
              <button onClick={() => setMenuOpen((o) => !o)} aria-label="More" className="w-10 h-10 flex items-center justify-center rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><MoreHorizontal size={18} /></button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 top-12 z-50 w-48 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                    {/*
                      All four raised a toast and called nothing. Three had working, guarded
                      endpoints sitting behind them; the fourth has no backend at all.
                    */}
                    {/*
                      Impersonation goes through a confirmation step: it is the only control here
                      that puts the operator inside someone else's account, where every destructive
                      thing that account can do to itself is one click away.
                    */}
                    <MenuItem icon={LogIn} label="Log in as user" onClick={() => { setMenuOpen(false); setImpersonateOpen(true) }} />
                    <MenuItem icon={KeyRound} label="Send reset link" onClick={() => { setMenuOpen(false); void sendReset() }} />
                    {/* Renamed: it logs a request. No export runs — see exportData(). */}
                    <MenuItem icon={Download} label="Log DSAR request" onClick={() => { setMenuOpen(false); void exportData() }} />
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 max-w-xl">
        {stats.map((s) => (
          <div key={s.label} className={cn(CARD, '!p-5 flex flex-col gap-1')}>
            <span className={LABEL}>{s.label}</span>
            <span className="text-2xl font-bold text-beatz-dark-bg dark:text-white">{s.value}</span>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* Activity + orders */}
        <div className="flex flex-col gap-6">
          {/*
            activity / orders / devices come from the API and are documented there as always-empty
            (UserDetailDto, "Category B"). This page ignored that and rendered getUserDetail() — a
            hardcoded fixture — so a moderator viewing any real account saw purchases, tips and
            follows that never happened. The backend was honest; the UI filled its silence with
            fiction. Empty states now say so.
          */}
          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Recent activity</h2>
            {activity.length === 0 ? (
              <p className="py-6 text-center text-sm text-gray-400 dark:text-gray-500">Activity history isn't available yet.</p>
            ) : (
              <div className="flex flex-col">
                {activity.map((a) => (
                  <div key={a.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                    <span className="w-1.5 h-1.5 rounded-full bg-beatz-green shrink-0" />
                    <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{a.text}</span>
                    <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{a.time}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Purchases</h2>
            {orders.length === 0 ? (
              <p className="py-6 text-center text-sm text-gray-400 dark:text-gray-500">Purchase history isn't available yet.</p>
            ) : (
              <div className="flex flex-col">
                {orders.map((o) => (
                  <div key={o.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                    <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{o.item}</span>
                    <span className="text-sm font-mono text-gray-500 dark:text-gray-300">{o.date}</span>
                    <span className="w-16 text-right text-sm font-mono font-bold text-beatz-green">{cedis(o.amount)}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        {/* Devices + action history */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Devices</h2>
            {devices.length === 0 ? (
              /*
                Sessions are stateless JWTs with no session store, so there is nothing to list and
                nothing to revoke. The fabricated rows came with a "Sign out" button that called
                nothing — telling an operator handling a compromised account that they had ended a
                session they had not.
              */
              <p className="py-6 text-center text-sm text-gray-400 dark:text-gray-500">Device and session history isn't available yet.</p>
            ) : (
              <div className="flex flex-col gap-3">
                {devices.map((d) => (
                  <div key={d.id} className="flex items-center gap-3">
                    <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Monitor size={16} /></span>
                    <div className="flex flex-col flex-1 min-w-0">
                      <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{d.device}{d.current && <span className="text-beatz-green text-xs"> · this device</span>}</span>
                      <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{d.location} · {d.lastActive}</span>
                    </div>
                    {/*
                      Kept disabled rather than dropped. Devices are always empty today, so this
                      row is dormant — but if the endpoint ever populates it, a visibly-disabled
                      control with a reason is better than silently having none. Sessions are
                      stateless JWTs with no denylist, `jti` or session store, so there is nothing
                      to revoke; the original told an operator handling a compromised account that
                      the session had been terminated when it had not.
                    */}
                    {!d.current && <button disabled title="Sessions are stateless and cannot be revoked yet." className="text-xs font-bold text-gray-300 dark:text-gray-600 cursor-not-allowed shrink-0">Sign out</button>}
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="flex items-center gap-2 text-lg font-bold text-beatz-dark-bg dark:text-white"><ShieldCheck size={17} className="text-beatz-green" /> Action history</h2>
            <div className="flex flex-col">
              {log.map((l) => (
                <div key={l.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                  <Clock size={13} className="text-gray-400 shrink-0" />
                  <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{l.action}</span>
                  <span className="text-xs text-gray-500 dark:text-gray-400 shrink-0">{l.by}</span>
                  <span className="w-16 text-right text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{l.time}</span>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>

      <SuspendModal isOpen={suspendOpen} name={user.name} onClose={() => setSuspendOpen(false)}
        onConfirm={(reason) => { setSuspendOpen(false); suspend(reason) }} />

      <ImpersonateModal isOpen={impersonateOpen} name={user.name} email={user.email}
        onClose={() => setImpersonateOpen(false)} onConfirm={() => void impersonate()} />
    </div>
  )
}

/**
 * Confirms an impersonation before the session swaps.
 *
 * <p>Unlike suspend, this asks for no reason: the backend audits the actor, the target and the
 * token's expiry on its own, so a free-text field here would add a second, unverified account of
 * something already recorded. What the dialog owes the operator is a clear statement of what is
 * about to happen — that they leave the console, act as this person, and that anything they do
 * lands on the real account.
 */
function ImpersonateModal({ isOpen, name, email, onClose, onConfirm }: {
  isOpen: boolean; name: string; email: string; onClose: () => void; onConfirm: () => void
}) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Log in as ${name}`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">
          You will leave the console and browse BeatzClik as <span className="font-bold text-white">{name}</span> ({email}).
          Anything you do lands on their real account.
        </p>
        <p className="text-sm text-white/70">
          The session is short-lived and returns you here automatically when it expires. A banner
          stays on screen the whole time, with an Exit button. This is audited.
        </p>
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={onConfirm}
            className="flex-1 h-12 rounded-full bg-[#f6c644] text-black font-bold hover:brightness-110 transition-all">
            Log in as {name}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function StatusPill({ status }: { status: UserStatus }) {
  const cls = status === 'pending' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : status === 'suspended' ? 'bg-beatz-red/15 text-beatz-red' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof LogIn; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors', danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}

function SuspendModal({ isOpen, name, onClose, onConfirm }: { isOpen: boolean; name: string; onClose: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState('')
  const REASONS = ['Policy violation', 'Fraud / chargebacks', 'Spam', 'Impersonation', 'Other']
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Suspend ${name}`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The user will lose access immediately and be notified. A reason is required and logged.</p>
        <div className="flex flex-col gap-2">
          <label className="text-[11px] font-bold uppercase tracking-[0.15em] text-white/50">Reason</label>
          <div className="flex flex-wrap gap-2">
            {REASONS.map((r) => (
              <button key={r} onClick={() => setReason(r)} className={cn('h-9 px-3.5 rounded-full text-xs font-bold border transition-colors', reason === r ? 'border-beatz-green bg-beatz-green/10 text-beatz-green' : 'border-white/10 text-white/70 hover:border-white/20')}>{r}</button>
            ))}
          </div>
          <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Add a note…" className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60 mt-1" />
        </div>
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim()} className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">Suspend</button>
        </div>
      </div>
    </Modal>
  )
}
