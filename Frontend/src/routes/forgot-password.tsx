import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowRight, MailCheck } from 'lucide-react'
import { useState } from 'react'
import logo from '../assets/logos/logo-with-name-flex.svg'
import { apiRequestPasswordReset } from '../lib/api/queries/password-reset'

export const Route = createFileRoute('/forgot-password')({
  component: ForgotPassword,
})

/**
 * Step one of account recovery: ask for a reset link.
 *
 * This page did not exist. The login screen's "Forgot password?" was a `<Link to="/login">` — it
 * navigated to the page you were already on — so there was no way to reach the endpoint at all.
 *
 * <p>The confirmation is deliberately identical whether or not the address is registered. The API
 * answers 204 either way (non-enumeration), and a UI that said "no account with that email" would
 * hand back exactly the information the API is careful not to reveal.
 */
function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const submit = async () => {
    if (!email.trim() || submitting) return
    setSubmitting(true)
    setError('')
    try {
      await apiRequestPasswordReset(email.trim())
      setSent(true)
    } catch {
      // Only a transport/server failure can land here — a wrong email is a 204.
      setError('Could not send the reset link. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-[#0a0a0a] p-8">
      <div className="w-full max-w-md flex flex-col gap-10">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <img src={logo} alt="Beatzclik" className="h-12 w-auto" />
        </Link>

        {sent ? (
          <div className="flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-beatz-green/15 text-beatz-green flex items-center justify-center">
              <MailCheck size={22} />
            </div>
            <h2 className="text-4xl font-bold text-white tracking-tight">Check your email</h2>
            <p className="text-gray-400 font-medium">
              If an account exists for <span className="text-white">{email.trim()}</span>, we've sent
              a link to reset your password. It works once and expires in 30 minutes.
            </p>
            <Link
              to="/login"
              className="w-full h-14 bg-white/5 border border-white/10 text-white font-bold rounded-2xl mt-2 flex items-center justify-center hover:bg-white/10 transition-colors"
            >
              Back to log in
            </Link>
          </div>
        ) : (
          <>
            <div className="flex flex-col gap-2">
              <h2 className="text-4xl font-bold text-white tracking-tight">Reset your password</h2>
              <p className="text-gray-400 font-medium">
                Enter the email on your account and we'll send you a link to choose a new password.
              </p>
            </div>

            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">
                  Email address
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
                  placeholder="name@example.com"
                  className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 focus:bg-white/[0.07] transition-all"
                />
              </div>

              {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}

              <button
                onClick={() => void submit()}
                disabled={!email.trim() || submitting}
                className="w-full h-14 bg-beatz-green text-black font-bold rounded-2xl mt-2 flex items-center justify-center gap-2 hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 group disabled:opacity-40 disabled:hover:scale-100"
              >
                {submitting ? 'Sending…' : 'Send reset link'}
                <ArrowRight size={18} className="group-hover:translate-x-1 transition-transform" />
              </button>

              <Link
                to="/login"
                className="text-center text-sm font-bold text-gray-500 hover:text-white transition-colors"
              >
                Back to log in
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
