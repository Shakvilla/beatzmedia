import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { UserCheck, LogOut } from 'lucide-react'
import { useAuth } from '../../features/auth/auth-context'

/**
 * Persistent banner shown while an operator is impersonating a user (GAP-08).
 *
 * <p>Rendered at the app shell rather than inside the console, because impersonation takes the
 * operator *out* of the console and into the fan app — which is the entire point, and also exactly
 * where it is easiest to forget you are not yourself. Every destructive thing a fan can do to their
 * own account is reachable from those screens.
 *
 * <p>It shows the remaining time because the token is short-lived: an operator who knows they have
 * two minutes left behaves differently from one who does not know there is a clock at all.
 */
export function ImpersonationBanner() {
  const { impersonation, stopImpersonation } = useAuth()
  const navigate = useNavigate()
  const [now, setNow] = useState(() => Date.now())

  /*
    Tick only while the banner is up; no timer runs in the normal case.

    The `visibilitychange` re-sync is not belt-and-braces. Browsers throttle setInterval on a hidden
    tab, so the countdown freezes at whatever it last rendered — observed live: the banner held at
    "3:24 left" while the real remaining time fell to 2:59. Each tick reads the clock, so it corrects
    itself within a second of the tab coming back, but that second is exactly when an operator
    glances at the banner after switching windows. Re-reading on the visibility change makes it right
    before they look rather than just after.

    The session's actual lifetime never depended on this: the server rejects the expired token, and
    the 401 handler restores the operator's own session.
  */
  useEffect(() => {
    if (!impersonation) return
    const sync = () => setNow(Date.now())
    const id = setInterval(sync, 1000)
    document.addEventListener('visibilitychange', sync)
    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', sync)
    }
  }, [impersonation])

  if (!impersonation) return null

  const exit = async () => {
    const back = impersonation.subjectId
    await stopImpersonation()
    void navigate({ to: '/admin/users/$userId', params: { userId: back } })
  }

  return (
    <div role="status"
      className="sticky top-0 z-[60] flex items-center justify-center gap-3 flex-wrap px-4 py-2 bg-[#f6c644] text-black text-sm font-bold">
      <span className="flex items-center gap-2">
        <UserCheck size={16} />
        You are viewing BeatzClik as <span className="underline underline-offset-2">{impersonation.subjectName}</span>
      </span>
      <span className="font-mono text-xs px-2 py-0.5 rounded-full bg-black/10">
        {remaining(impersonation.expiresAt, now)}
      </span>
      <button onClick={() => void exit()}
        className="h-7 px-3 rounded-full bg-black text-white text-xs font-bold flex items-center gap-1.5 hover:bg-black/80 transition-colors">
        <LogOut size={13} /> Exit
      </button>
    </div>
  )
}

/** `m:ss` remaining, or `expiring…` once the clock runs out but before the session swaps back. */
function remaining(expiresAt: string, now: number): string {
  const ms = Date.parse(expiresAt) - now
  if (!Number.isFinite(ms) || ms <= 0) return 'expiring…'
  const total = Math.floor(ms / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')} left`
}
