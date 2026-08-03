/**
 * Reachability tests for the "no playable stream" recovery path.
 *
 * These exist because of a specific, twice-shipped regression. Every play control on every
 * transport surface is `disabled={unavailable}`, so the player's recovery path is reachable from
 * exactly one place: the "Try again" button inside {@link UnavailableNotice}. The first two
 * attempts at this feature put the retry inside `togglePlay`, which the disabled controls could
 * never invoke — the code was there, the reducer test passed, and the button did nothing.
 *
 * A test that calls `retry()` off the context proves the contract but NOT the reachability: it
 * would keep passing if a surface dropped the notice and left the fan in a dead end. So these
 * render the real surfaces and assert an *enabled* button is actually on screen.
 */

import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeAll, afterEach } from 'vitest'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api/client'
import { PlayerProvider, usePlayer } from '../player-context'
import { PlayerBar } from '../../../components/layout/player-bar'
import type { Track } from '../../../types'

vi.mock('../../../lib/api/client', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api/client')>(
    '../../../lib/api/client',
  )
  return { ...actual, apiFetch: vi.fn() }
})

// PlayerBar's chrome, not its transport. Stubbed so this file tests the notice, not the router.
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, ...rest }: { children?: ReactNode }) => <a {...rest}>{children}</a>,
}))
vi.mock('../../cart/use-buy-track', () => ({ useBuyTrack: () => vi.fn() }))
vi.mock('../../../components/ui/toast-provider', () => ({ useToast: () => ({ toast: vi.fn() }) }))

beforeAll(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
})

afterEach(cleanup)

const track: Track = {
  id: 't-1',
  title: 'Track One',
  artistId: 'a',
  artistName: 'A',
  duration: 204,
  image: '',
  ownership: 'for-sale',
} as Track

/** Starts playback of a track whose stream 503s, which is the state the notice exists for. */
function Harness({ children }: { children: ReactNode }) {
  return <>{children}</>
}

function renderWithFailedStream(ui: ReactNode) {
  vi.mocked(apiFetch).mockImplementation((() =>
    Promise.reject(new Error('MEDIA_UNAVAILABLE'))) as typeof apiFetch)

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  function Starter() {
    const { playQueue } = usePlayer()
    // The fan has to have asked for playback — the stream query is gated on it (I7).
    return (
      <button type="button" data-testid="start" onClick={() => playQueue([track], 0)}>
        start
      </button>
    )
  }

  render(
    <QueryClientProvider client={queryClient}>
      <PlayerProvider>
        <Harness>
          <Starter />
          {ui}
        </Harness>
      </PlayerProvider>
    </QueryClientProvider>,
  )

  screen.getByTestId('start').click()
}

describe('unavailable recovery is reachable from the surfaces that disable playback', () => {
  it('PlayerBar renders an enabled "Try again" when the stream is unavailable', async () => {
    renderWithFailedStream(<PlayerBar />)

    // If a refactor drops <UnavailableNotice/> from PlayerBar, every play control stays disabled
    // and this is the assertion that catches the resulting dead end.
    await waitFor(
      () => {
        const buttons = screen.getAllByRole('button', { name: /try again/i })
        expect(buttons.length).toBeGreaterThan(0)
        expect(buttons.some((b) => !(b as HTMLButtonElement).disabled)).toBe(true)
      },
      { timeout: 5000 },
    )
  })

  it('shows the unavailable message alongside the recovery control, not instead of it', async () => {
    renderWithFailedStream(<PlayerBar />)

    await waitFor(
      () => {
        expect(screen.getAllByText(/not available to play right now/i).length).toBeGreaterThan(0)
        expect(screen.getAllByRole('button', { name: /try again/i }).length).toBeGreaterThan(0)
      },
      { timeout: 5000 },
    )
  })
})
