import { Link } from '@tanstack/react-router'
import { Disc3, ArrowLeft, Upload, BarChart3, Wallet } from 'lucide-react'
import { useAuth } from '../../features/auth/auth-context'

/** Shown when a signed-in fan visits the studio — funnels them into becoming an artist. */
export function ArtistGate() {
  const { becomeArtist } = useAuth()

  const perks = [
    { icon: Upload, label: 'Release & sell your music', desc: 'Singles, EPs and albums with buy-to-own pricing.' },
    { icon: BarChart3, label: 'Know your audience', desc: 'Streams, top cities and listener insights.' },
    { icon: Wallet, label: 'Get paid to MoMo', desc: 'Withdraw earnings every Friday.' },
  ]

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white font-sans px-6 py-16">
      <div className="w-full max-w-md flex flex-col items-center text-center gap-8">
        <div className="w-16 h-16 rounded-2xl bg-beatz-green/15 flex items-center justify-center text-beatz-green">
          <Disc3 size={30} />
        </div>
        <div className="flex flex-col gap-2">
          <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-beatz-green">Beatzclik for Artists</span>
          <h1 className="text-3xl font-bold tracking-tight">Start selling your music</h1>
          <p className="text-sm text-gray-500 dark:text-gray-300">
            Turn your account into an artist studio to upload releases, track your audience and get paid — free to set up.
          </p>
        </div>

        <div className="w-full flex flex-col gap-3">
          {perks.map((p) => (
            <div key={p.label} className="flex items-center gap-3 p-3 rounded-xl border border-gray-200 dark:border-white/10 text-left">
              <span className="w-9 h-9 rounded-lg bg-gray-100 dark:bg-white/10 flex items-center justify-center text-beatz-green shrink-0"><p.icon size={17} /></span>
              <div className="flex flex-col">
                <span className="text-sm font-bold">{p.label}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400">{p.desc}</span>
              </div>
            </div>
          ))}
        </div>

        <button
          onClick={becomeArtist}
          className="w-full h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform shadow-lg shadow-beatz-green/20"
        >
          Set up artist studio
        </button>
        <Link to="/" className="flex items-center gap-2 text-sm font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
          <ArrowLeft size={15} /> Back to BeatzClik
        </Link>
      </div>
    </div>
  )
}
