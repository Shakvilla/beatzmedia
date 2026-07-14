import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ArrowRight } from 'lucide-react'
import { useState } from 'react'
import logo from "../assets/logos/logo-with-name-flex.svg"
import { useAuth } from '../features/auth/auth-context'
import { SocialButtons } from '../components/auth/social-buttons'
import { useToast } from '../components/ui/toast-provider'

export const Route = createFileRoute('/login')({
  component: LoginComponent,
})

function LoginComponent() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const { toast } = useToast()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const canSubmit = email.trim() !== '' && password !== ''

  const submit = async () => {
    if (!canSubmit) return
    setError('')
    try {
      await login(email, password)
      navigate({ to: '/' })
    } catch {
      setError('Incorrect email or password.')
    }
  }

  return (
    <div className="min-h-screen w-full flex bg-[#0a0a0a]">
      {/* Left Side: Branding/Visuals */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden p-16 flex-col justify-between">
        <div className="absolute inset-0 z-0 m-4">
          <img 
            src="https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=1200&auto=format&fit=crop" 
            className="w-full h-full object-cover opacity-50 grayscale rounded-2xl"
            alt="Music Background"
          />
          <div className="absolute inset-0 bg-gradient-to-tr from-[#0a0a0a] via-transparent to-transparent" />
        </div>

        <div className="relative z-10">
          <Link to="/" className="flex items-center gap-3 hover:opacity-80 transition-opacity">
             <img src={logo} alt="Beatzclik" className="h-12 w-auto" />
          </Link>
        </div>

        <div className="relative z-10 max-w-md">
           <h1 className="text-6xl font-bold text-white tracking-tighter mb-6 leading-tight">
             Connect with the <span className="text-beatz-green">rhythm</span> of Ghana.
           </h1>
           <p className="text-lg text-gray-400 font-medium">
             Discover, support, and own music from your favorite local artists. Join the community today.
           </p>
        </div>

        <div className="relative z-10 flex items-center gap-6">
           <div className="flex -space-x-3">
              {[1,2,3,4].map(i => (
                <div key={i} className="w-10 h-10 rounded-full border-2 border-[#0a0a0a] overflow-hidden">
                   <img src={`https://i.pravatar.cc/100?img=${i+10}`} alt="User" className="w-full h-full object-cover" />
                </div>
              ))}
           </div>
           <span className="text-sm font-bold text-white/60 uppercase tracking-widest">Joined by 10k+ fans</span>
        </div>
      </div>

      {/* Right Side: Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 lg:p-16">
        <div className="w-full max-w-md flex flex-col gap-10">
          <div className="flex flex-col gap-2">
            <h2 className="text-4xl font-bold text-white tracking-tight">Welcome back</h2>
            <p className="text-gray-500 dark:text-gray-300 font-medium">Enter your details to access your collection.</p>
          </div>

          <div className="flex flex-col gap-4">
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
              <div className="flex items-center justify-between ml-1">
                <label className="text-xs font-bold text-gray-400 uppercase tracking-widest">Password</label>
                <Link to="/login" className="text-[10px] font-bold text-beatz-green uppercase hover:underline">Forgot password?</Link>
              </div>
              <input 
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') submit() }}
                placeholder="••••••••"
                className="w-full h-14 bg-white/5 border border-white/10 rounded-2xl px-6 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-green/50 focus:bg-white/[0.07] transition-all"
              />
            </div>

            {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}

            <button onClick={submit} disabled={!canSubmit} className="w-full h-14 bg-beatz-green text-black font-bold rounded-2xl mt-4 flex items-center justify-center gap-2 hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 group disabled:opacity-40 disabled:hover:scale-100">
              Log in <ArrowRight size={18} className="group-hover:translate-x-1 transition-transform" />
            </button>
          </div>

          <div className="flex items-center gap-4">
             <div className="h-px bg-white/10 flex-1" />
             <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Or continue with</span>
             <div className="h-px bg-white/10 flex-1" />
          </div>

          <SocialButtons onSelect={() => toast('Social sign-in is coming soon — use email for now.', 'info')} />

          <p className="text-center text-sm text-gray-500 dark:text-gray-300 font-medium">
            Don't have an account? <Link to="/signup" className="text-beatz-green font-bold hover:underline">Sign up for free</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
