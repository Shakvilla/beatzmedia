import { createFileRoute, Link } from '@tanstack/react-router'
import { ShieldAlert, ArrowLeft } from 'lucide-react'
import { AdminShell } from '../components/admin/admin-shell'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/admin')({
  component: AdminGate,
})

function AdminGate() {
  const { account } = useAuth()
  // Signed-out users are redirected to /login by AppShell.
  if (!account) return null
  if (!account.isAdmin) return <NotAuthorized />
  return <AdminShell />
}

function NotAuthorized() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white font-sans px-6 text-center gap-6">
      <div className="w-16 h-16 rounded-2xl bg-beatz-red/15 flex items-center justify-center text-beatz-red">
        <ShieldAlert size={30} />
      </div>
      <div className="flex flex-col gap-2 max-w-sm">
        <h1 className="text-2xl font-bold tracking-tight">Admin access only</h1>
        <p className="text-sm text-gray-500 dark:text-gray-300">This is the Beatzclik platform admin console. Your account doesn't have admin permissions.</p>
      </div>
      <Link to="/" className="flex items-center gap-2 text-sm font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
        <ArrowLeft size={15} /> Back to BeatzClik
      </Link>
    </div>
  )
}
