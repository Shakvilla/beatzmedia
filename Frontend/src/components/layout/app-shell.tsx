import { Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { Sidebar } from './sidebar'
import { MobileNav } from './mobile-nav'
import { Header } from './header'
import { PlayerBar } from './player-bar'
import { QueueDrawer } from '../music/queue-drawer'
import { ImpersonationBanner } from './impersonation-banner'
import { useAuth } from '../../features/auth/auth-context'
import { fanPreferencesQuery } from '../../lib/api/queries/fan-preferences'

/**
 * Routes reachable without a session.
 *
 * The password-recovery pages belong here for the obvious reason: the only person who needs them
 * is someone who cannot sign in. Leaving them out sent a locked-out user to
 * `/login?redirect=/forgot-password` — the very screen they were trying to escape.
 */
const AUTH_ROUTES = ['/login', '/signup', '/forgot-password', '/reset-password']

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

  // AUTH_ROUTES is spread in rather than repeated: these two lists previously both hardcoded
  // /login and /signup, and adding a route to one but not the other renders an auth page wrapped
  // in the sidebar, header and player bar — which is exactly what happened to the recovery pages.
  const fullScreenRoutes = ['/lyrics', ...AUTH_ROUTES, '/onboarding', '/studio', '/admin']
  const isFullScreen = fullScreenRoutes.some(route => location.pathname.startsWith(route))

  /*
    Column, not row: the impersonation banner is a full-width bar ABOVE the sidebar + main row.
    Dropping it in as a sibling of <Sidebar> made it a flex item in the row, where a `sticky top-0`
    bar renders as a narrow column beside the nav. The inner div restores the original row layout,
    so nothing else about the shell changes.

    The banner sits outside the `isFullScreen` guard on purpose: an operator must not lose sight of
    acting as someone else just because they opened a full-screen player.
  */
  return (
    <div className="flex flex-col h-screen bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white overflow-hidden font-sans transition-colors duration-300">
      <ImpersonationBanner />

      <div className="flex flex-1 min-h-0 overflow-hidden">
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
      </div>

      {!isFullScreen && <PlayerBar />}

      {!isFullScreen && <MobileNav />}

      {!isFullScreen && <QueueDrawer />}

      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </div>
  )
}
