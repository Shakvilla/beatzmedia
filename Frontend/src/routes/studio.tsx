import { createFileRoute } from '@tanstack/react-router'
import { StudioShell } from '../components/studio/studio-shell'
import { StudioProvider } from '../features/studio/studio-context'
import { ArtistGate } from '../components/studio/artist-gate'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/studio')({
  component: StudioGate,
})

function StudioGate() {
  const { account } = useAuth()
  // Signed-out users are redirected to /login by AppShell.
  if (!account) return null
  // Signed-in fans see the upgrade gate instead of the studio.
  if (!account.isArtist) return <ArtistGate />
  return (
    <StudioProvider>
      <StudioShell />
    </StudioProvider>
  )
}
