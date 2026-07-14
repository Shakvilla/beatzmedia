import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import './index.css'

// Import the generated route tree
import { routeTree } from './routeTree.gen'

// Create a client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 1,
    },
  },
})

// Create a new router instance, with the query client available to every loader
const router = createRouter({ routeTree, context: { queryClient } })

// Register the router instance for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

import { ThemeProvider } from './components/theme-provider'
import { ToastProvider } from './components/ui/toast-provider'
import { AuthProvider } from './features/auth/auth-context'
import { NotificationsProvider } from './features/notifications/notifications-context'
import { PlayerProvider } from './features/player/player-context'
import { CartProvider } from './features/cart/cart-context'
import { CollectionProvider } from './features/collection/collection-context'

// Initialize the root
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider defaultTheme="dark">
      <ToastProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <NotificationsProvider>
              <CartProvider>
                <CollectionProvider>
                  <PlayerProvider>
                    <RouterProvider router={router} />
                  </PlayerProvider>
                </CollectionProvider>
              </CartProvider>
            </NotificationsProvider>
          </AuthProvider>
        </QueryClientProvider>
      </ToastProvider>
    </ThemeProvider>
  </StrictMode>,
)
