import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ArrowRight, UserPlus } from 'lucide-react'
import { useState } from 'react'
import logo from "../assets/logos/logo-with-name-flex.svg"
import { useAuth } from '../features/auth/auth-context'
import { SocialButtons } from '../components/auth/social-buttons'
import { useToast } from '../components/ui/toast-provider'

export const Route = createFileRoute('/signup')({
  component: SignUpComponent,
})

function SignUpComponent() {
  const navigate = useNavigate()
  const { signup } = useAuth()
  const { toast } = useToast()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const canSubmit = email.trim() !== '' && password.length >= 8 && name.trim() !== ''

  const submit = async () => {
    if (!canSubmit) return
    setError('')
    try {
      await signup(name, email, password)
      navigate({ to: '/' })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create your account.')
    }
  }

  return (
    <div className="min-h-screen w-full flex bg-[#0a0a0a]">
      {/* Right Side: Visuals (Swapped for variety) */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden  p-16 flex-col justify-between">
        <div className="absolute inset-0 z-0 m-4">
          <img 
            src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=1200&auto=format&fit=crop" 
            className="w-full h-full object-cover  rounded-2xl"
            alt="Crowd Background"
          />
          {/* <div className="absolute inset-0 bg-gradient-to-tl from-[#0a0a0a] via-transparent to-transparent" /> */}
        </div>

        <div className="relative z-10 flex justify-end">
          <Link to="/" className="flex items-center gap-3 hover:opacity-80 transition-opacity">
             <img src={logo} alt="Beatzclik" className="h-12 w-auto" />
          </Link>
        </div>

        <div className="relative z-10 max-w-md ml-auto text-right">
           <h1 className="text-6xl font-bold text-white tracking-tighter mb-6 leading-tight">
             Support the <span className="text-beatz-green">creators</span> you love.
           </h1>
           <p className="text-lg text-gray-400 font-medium">
             From Konongo to the world. Be the first to own exclusive releases and support Ghanaian music directly.
           </p>
        </div>

        <div className="relative z-10 flex items-center justify-end gap-6">
           <span className="text-sm font-bold text-white/60 uppercase tracking-widest text-right">Join the movement</span>
           <div className="w-12 h-12 rounded-full border-2 border-beatz-green/30 flex items-center justify-center animate-bounce">
              <ArrowRight className="text-beatz-green rotate-90" size={24} />
           </div>
        </div>
      </div>

      {/* Left Side: Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 lg:p-16 order-1">
        <div className="w-full max-w-lg flex flex-col gap-10 dark:bg-beatz-dark-surface-2 rounded-2xl p-8">
          <div className="flex flex-col gap-2">
            <h2 className="text-4xl font-bold text-white tracking-tight">Create account</h2>
            <p className="text-gray-500 dark:text-gray-300 font-medium">Join thousands of fans supporting local music.</p>
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">Full name</label>
              <input 
                type="text" 
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="John Doe"
                className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 focus:bg-white/[0.07] transition-all"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">Email address</label>
              <input 
                type="email" 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 focus:bg-white/[0.07] transition-all"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-xs font-bold text-gray-400 uppercase tracking-widest ml-1">Password</label>
              <input 
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') submit() }}
                placeholder="Minimum 8 characters"
                className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 focus:bg-white/[0.07] transition-all"
              />
            </div>

            {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}

            <button onClick={submit} disabled={!canSubmit} className="w-full h-14 bg-beatz-green text-black font-bold rounded-2xl mt-4 flex items-center justify-center gap-2 hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 group disabled:opacity-40 disabled:hover:scale-100">
              Get started <UserPlus size={18} className="group-hover:scale-110 transition-transform" />
            </button>
          </div>

          <div className="flex items-center gap-4">
             <div className="h-px bg-white/10 flex-1" />
             <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Or sign up with</span>
             <div className="h-px bg-white/10 flex-1" />
          </div>

          <SocialButtons onSelect={() => toast('Social sign-in is coming soon — use email for now.', 'info')} />

          <p className="text-center text-sm text-gray-100 font-medium">
            Already have an account? <Link to="/login" className="text-beatz-green font-bold hover:underline">Log in</Link>
          </p>

          <p className="text-[10px] text-gray-100 text-center uppercase tracking-widest leading-relaxed">
            By signing up, you agree to our <span className="text-gray-200 cursor-pointer hover:underline">Terms of Service</span> and <span className="text-gray-200 cursor-pointer hover:underline">Privacy Policy</span>.
          </p>
        </div>
      </div>
    </div>
  )
}
