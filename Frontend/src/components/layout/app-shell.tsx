import { Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { Sidebar } from './sidebar'
import { MobileNav } from './mobile-nav'
import { Header } from './header'
import { PlayerBar } from './player-bar'
import { QueueDrawer } from '../music/queue-drawer'
import { useAuth } from '../../features/auth/auth-context'

const AUTH_ROUTES = ['/login', '/signup']

export function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const onAuthRoute = AUTH_ROUTES.some((route) => location.pathname.startsWith(route))

  // Gate the whole app: signed-out users are sent to the login screen.
  useEffect(() => {
    if (!isAuthenticated && !onAuthRoute) navigate({ to: '/login' })
  }, [isAuthenticated, onAuthRoute, navigate])

  if (!isAuthenticated && !onAuthRoute) return null

  const fullScreenRoutes = ['/lyrics', '/login', '/signup', '/studio', '/admin']
  const isFullScreen = fullScreenRoutes.some(route => location.pathname.startsWith(route))

  return (
    <div className="flex h-screen bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white overflow-hidden font-sans transition-colors duration-300">
      {!isFullScreen && <Sidebar />}

      {/* Main Content */}
      <main className="flex-1 flex flex-col relative overflow-hidden">
        {!isFullScreen && <Header />}

        <div className={`flex-1 overflow-y-auto bg-beatz-light-bg dark:bg-[#121212] transition-colors duration-300 no-scrollbar relative z-10 ${!isFullScreen ? 'pb-44 md:pb-28' : ''}`}>
          <div className={`${!isFullScreen ? 'px-4 md:px-8 pt-20 pb-8 max-w-8xl mx-auto' : 'h-full'}`}>
            <Outlet />
          </div>
        </div>
      </main>

      {!isFullScreen && <PlayerBar />}

      {!isFullScreen && <MobileNav />}

      {!isFullScreen && <QueueDrawer />}

      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </div>
  )
}
