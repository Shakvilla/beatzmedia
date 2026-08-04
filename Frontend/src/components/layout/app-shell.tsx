import { Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { Sidebar } from './sidebar'
import { MobileNav } from './mobile-nav'
import { Header } from './header'
import { PlayerBar } from './player-bar'
import { QueueDrawer } from '../music/queue-drawer'
import { useAuth } from '../../features/auth/auth-context'
import { fanPreferencesQuery } from '../../lib/api/queries/fan-preferences'

const AUTH_ROUTES = ['/login', '/signup']

export function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, isLoading } = useAuth()
  const onAuthRoute = AUTH_ROUTES.some((route) => location.pathname.startsWith(route))
  const onOnboarding = location.pathname.startsWith('/onboarding')

  /**
   * Onboarding state. Only fetched once signed in — an anonymous visitor has no preferences and
   * asking would just 401. Admin and studio surfaces are exempt: the gate is a fan-taste step, and
   * an operator signing in to fix something should not be made to pick three genres first.
   */
  const exemptFromOnboarding =
    onAuthRoute || onOnboarding
    || location.pathname.startsWith('/admin')
    || location.pathname.startsWith('/studio')
  const { data: prefs, isLoading: prefsLoading } = useQuery({
    ...fanPreferencesQuery(),
    enabled: isAuthenticated && !exemptFromOnboarding,
  })

  // Gate the whole app: signed-out users are sent to the login screen. Wait for the
  // initial session hydration (GET /v1/me) before deciding — otherwise a valid,
  // already-logged-in session briefly bounces to /login on every page load.
  useEffect(() => {
    if (!isLoading && !isAuthenticated && !onAuthRoute) {
      // Carry where they were so login can send them back, and flag that this was an
      // expiry rather than a deliberate sign-out. Access tokens are short-lived with no
      // refresh (OQ-3), so this fires during normal use — silently dumping someone on
      // /login with no explanation and no way back is the part that isn't acceptable.
      // location.search is a parsed object in TanStack Router; href is the string form.
      const from = location.href
      navigate({ to: '/login', search: { redirect: from, expired: true }, replace: true })
    }
  }, [isLoading, isAuthenticated, onAuthRoute, navigate, location.href])

  // Send a fan who has never onboarded to the picker, once. `prefs` is undefined while the query
  // is disabled or in flight, so this only fires on a definite `onboarded: false`.
  useEffect(() => {
    if (isAuthenticated && !exemptFromOnboarding && prefs && !prefs.onboarded) {
      navigate({ to: '/onboarding', replace: true })
    }
  }, [isAuthenticated, exemptFromOnboarding, prefs, navigate])

  if (isLoading) return null
  if (!isAuthenticated && !onAuthRoute) return null
  // Hold the app back while we find out whether this fan has onboarded. Rendering the home page
  // first and redirecting a beat later would flash content the gate is meant to sit in front of.
  if (isAuthenticated && !onAuthRoute && !onOnboarding && prefsLoading) return null

  const fullScreenRoutes = ['/lyrics', '/login', '/signup', '/onboarding', '/studio', '/admin']
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
