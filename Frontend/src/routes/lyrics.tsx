import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { LyricsView } from '../components/music/lyrics-view'

export const Route = createFileRoute('/lyrics')({
  component: LyricsPageComponent,
})

function LyricsPageComponent() {
  const navigate = useNavigate()
  
  return (
    <LyricsView onClose={() => navigate({ to: '..' })} />
  )
}
