import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ArrowRight, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import logo from '../assets/logos/logo-with-name-flex.svg'
import { useToast } from '../components/ui/toast-provider'
import {
  apiResetPassword,
  ResetLinkExpiredError,
  WeakPasswordError,
} from '../lib/api/queries/password-reset'

interface ResetSearch {
  /** The single-use token from the emailed link. */
  token?: string
}

export const Route = createFileRoute('/reset-password')({
  validateSearch: (search: Record<string, unknown>): ResetSearch => ({
    token: typeof search.token === 'string' ? search.token : undefined,
  }),
  component: ResetPassword,
})

/** Mirrors the backend minimum so the user is told before a round trip, not after. */
const MIN_LENGTH = 8

/**
 * Step two of account recovery: redeem the token and choose a new password.
 *
 * The link in the email points here with `?token=`. Nothing consumed a reset token before — no
 * endpoint, no page — so every link the system could theoretically have sent led nowhere.
 */
function ResetPassword() {
  const { token } = Route.useSearch()
  const navigate = useNavigate()
  const { toast } = useToast()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const tooShort = password.length > 0 && password.length < MIN_LENGTH
  const mismatch = confirm.length > 0 && confirm !== password
  const canSubmit =
    !!token && password.length >= MIN_LENGTH && confirm === password && !submitting

  const submit = async () => {
    if (!canSubmit || !token) return
    setSubmitting(true)
    setError('')
    try {
      await apiResetPassword(token, password)
      toast('Password updated — sign in with your new password', 'success')
      navigate({ to: '/login' })
    } catch (err) {
      if (err instanceof ResetLinkExpiredError) {
        // Terminal for this link: no amount of retrying will help, so swap the whole form for the
        // one action that can (request a fresh link).
        setExpired(true)
      } else if (err instanceof WeakPasswordError) {
        setError(`Please choose a password of at least ${MIN_LENGTH} characters.`)
      } else {
        setError('Could not reset your password. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  // A link with no token at all is the same dead end as an expired one.
  if (!token || expired) {
    return (
      <div className="min-h-screen w-full flex items-center justify-center bg-[#0a0a0a] p-8">
        <div className="w-full max-w-md flex flex-col gap-6">
          <Link to="/" className="hover:opacity-80 transition-opacity">
            <img src={logo} alt="Beatzclik" className="h-12 w-auto" />
          </Link>
          <div className="w-12 h-12 rounded-full bg-beatz-red/15 text-beatz-red flex items-center justify-center">
            <ShieldAlert size={22} />
          </div>
          <h2 className="text-4xl font-bold text-white tracking-tight">This link has expired</h2>
          <p className="text-gray-400 font-medium">
            Reset links work once and expire after 30 minutes. Request a new one and we'll email it
            straight away.
          </p>
          <Link
            to="/forgot-password"
            className="w-full h-14 bg-beatz-green text-black font-bold rounded-2xl flex items-center justify-center gap-2 hover:scale-[1.02] transition-all"
          >
            Request a new link <ArrowRight size={18} />
          </Link>
          <Link
            to="/login"
            className="text-center text-sm font-bold text-gray-500 hover:text-white transition-colors"
          >
            Back to log in
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-[#0a0a0a] p-8">
      <div className="w-full max-w-md flex flex-col gap-10">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <img src={logo} alt="Beatzclik" className="h-12 w-auto" />
        </Link>

        <div className="flex flex-col gap-2">
          <h2 className="text-4xl font-bold text-white tracking-tight">Choose a new password</h2>
          <p className="text-gray-400 font-medium">
            Pick something you haven't used before. You'll be signed out everywhere else.
          </p>
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">
              New password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 transition-all"
            />
            {tooShort && (
              <p className="text-xs font-medium text-gray-500 ml-1">
                At least {MIN_LENGTH} characters.
              </p>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">
              Confirm password
            </label>
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
              placeholder="••••••••"
              className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 transition-all"
            />
            {mismatch && (
              <p className="text-xs font-medium text-beatz-red ml-1">Passwords don't match.</p>
            )}
          </div>

          {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}

          <button
            onClick={() => void submit()}
            disabled={!canSubmit}
            className="w-full h-14 bg-beatz-green text-black font-bold rounded-2xl mt-2 flex items-center justify-center gap-2 hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 group disabled:opacity-40 disabled:hover:scale-100"
          >
            {submitting ? 'Updating…' : 'Update password'}
            <ArrowRight size={18} className="group-hover:translate-x-1 transition-transform" />
          </button>
        </div>
      </div>
    </div>
  )
}
