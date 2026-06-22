import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import {
  ArrowLeft, BadgeCheck, MoreHorizontal, Ban, RotateCcw, KeyRound, LogIn, Mail, Download,
  Monitor, Clock, ShieldCheck,
} from 'lucide-react'
import { cn } from '../utils/cn'
import { Modal } from '../components/ui/modal'
import { useToast } from '../components/ui/toast-provider'
import { getAdminUsers, getUserDetail, type AdminUserRow, type UserStatus, type UserActionLog } from '../lib/admin-data'

export const Route = createFileRoute('/admin/users/$userId')({
  component: AdminUserDetail,
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const cedis = (n: number) => `₵${n.toLocaleString('en-US', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })}`

function AdminUserDetail() {
  const { userId } = Route.useParams()
  const { toast } = useToast()
  const found = useMemo(() => getAdminUsers().find((u) => u.id === userId), [userId])
  const detail = useMemo(() => getUserDetail(), [])

  const [user, setUser] = useState<AdminUserRow | undefined>(found)
  const [suspendOpen, setSuspendOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [log, setLog] = useState<UserActionLog[]>(() =>
    found ? [{ id: 'l0', action: `Joined as ${found.role}`, by: 'system', time: found.joined }] : [],
  )

  if (!user) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">User not found.</p>
        <Link to="/admin/users" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to users</Link>
      </div>
    )
  }

  const addLog = (action: string) => setLog((l) => [{ id: `l-${Date.now()}`, action, by: 'Admin · Yaa', time: 'just now' }, ...l])
  const setStatus = (status: UserStatus, action: string) => { setUser((u) => (u ? { ...u, status } : u)); addLog(action); toast(action, 'success') }
  const verify = () => { setUser((u) => (u ? { ...u, verified: true } : u)); addLog('Verified artist'); toast(`${user.name} verified`, 'success') }

  const isArtist = user.role === 'artist'
  const stats = isArtist
    ? [{ label: 'Releases', value: '12' }, { label: 'Revenue', value: '₵42K' }, { label: 'Followers', value: '412K' }]
    : [{ label: 'Purchases', value: `${detail.orders.length}` }, { label: 'Lifetime spend', value: '₵312' }, { label: 'Playlists', value: '7' }]

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
              ? <button onClick={() => setStatus('active', 'Reactivated account')} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><RotateCcw size={15} /> Reactivate</button>
              : <button onClick={() => setSuspendOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><Ban size={15} /> Suspend</button>}
            <div className="relative">
              <button onClick={() => setMenuOpen((o) => !o)} aria-label="More" className="w-10 h-10 flex items-center justify-center rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><MoreHorizontal size={18} /></button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 top-12 z-50 w-48 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                    <MenuItem icon={LogIn} label="Log in as user" onClick={() => { addLog('Impersonated account'); toast('Opening an impersonation session', 'info'); setMenuOpen(false) }} />
                    <MenuItem icon={KeyRound} label="Reset password" onClick={() => { addLog('Sent password reset'); toast('Password reset link sent', 'success'); setMenuOpen(false) }} />
                    <MenuItem icon={Mail} label="Email user" onClick={() => { toast(`Compose email to ${user.email}`, 'info'); setMenuOpen(false) }} />
                    <MenuItem icon={Download} label="Export data (GDPR)" onClick={() => { addLog('Exported user data'); toast('Preparing data export', 'success'); setMenuOpen(false) }} />
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
          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Recent activity</h2>
            <div className="flex flex-col">
              {detail.activity.map((a) => (
                <div key={a.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                  <span className="w-1.5 h-1.5 rounded-full bg-beatz-green shrink-0" />
                  <span className="flex-1 text-sm text-beatz-dark-bg dark:text-white truncate">{a.text}</span>
                  <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 shrink-0">{a.time}</span>
                </div>
              ))}
            </div>
          </section>

          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Purchases</h2>
            <div className="flex flex-col">
              {detail.orders.map((o) => (
                <div key={o.id} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0">
                  <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{o.item}</span>
                  <span className="text-sm font-mono text-gray-500 dark:text-gray-300">{o.date}</span>
                  <span className="w-16 text-right text-sm font-mono font-bold text-beatz-green">{cedis(o.amount)}</span>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Devices + action history */}
        <div className="flex flex-col gap-6">
          <section className={cn(CARD, 'flex flex-col gap-4')}>
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Devices</h2>
            <div className="flex flex-col gap-3">
              {detail.devices.map((d) => (
                <div key={d.id} className="flex items-center gap-3">
                  <span className="w-9 h-9 rounded-full bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><Monitor size={16} /></span>
                  <div className="flex flex-col flex-1 min-w-0">
                    <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{d.device}{d.current && <span className="text-beatz-green text-xs"> · this device</span>}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{d.location} · {d.lastActive}</span>
                  </div>
                  {!d.current && <button onClick={() => toast('Signed out of device', 'success')} className="text-xs font-bold text-gray-400 hover:text-beatz-red transition-colors shrink-0">Sign out</button>}
                </div>
              ))}
            </div>
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
        onConfirm={(reason) => { setSuspendOpen(false); setStatus('suspended', `Suspended · ${reason}`) }} />
    </div>
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
