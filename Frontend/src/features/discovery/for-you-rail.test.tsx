import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ForYouRail } from './for-you-rail'

const state = vi.hoisted(() => ({
  authenticated: true,
  query: {} as Record<string, unknown>,
  options: null as { enabled?: boolean } | null,
  refetch: vi.fn(),
}))

vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ isAuthenticated: state.authenticated }),
}))

vi.mock('@tanstack/react-query', () => ({
  queryOptions: (options: unknown) => options,
  useQuery: (options: { enabled?: boolean }) => {
    state.options = options
    return state.query
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <a href="#recommendation">{children}</a>,
}))

vi.mock('../discover/components/media-rail', () => ({
  MediaRail: ({ title, children }: { title: string; children: ReactNode }) => (
    <section><h2>{title}</h2>{children}</section>
  ),
}))

describe('ForYouRail', () => {
  beforeEach(() => {
    state.authenticated = true
    state.refetch.mockReset()
    state.query = { isPending: false, isError: false, data: { items: [] }, refetch: state.refetch }
  })

  it('does not enable or render the authenticated query while signed out', () => {
    state.authenticated = false
    const { container } = render(<ForYouRail />)

    expect(state.options?.enabled).toBe(false)
    expect(container.innerHTML).toBe('')
  })

  it('renders singles, albums, EPs, and podcast shows with explanations', () => {
    state.query = {
      isPending: false,
      isError: false,
      refetch: state.refetch,
      data: {
        items: [
          { kind: 'single', contentId: 'track-1', creatorId: 'artist-1', title: 'Fire', subtitle: 'Ama', image: null, route: '/track/track-1', reason: 'Because you follow Ama' },
          { kind: 'album', contentId: 'album-1', creatorId: 'artist-1', title: 'Gold', subtitle: 'Ama', image: null, route: '/album/album-1', reason: 'More from Ama' },
          { kind: 'ep', contentId: 'ep-1', creatorId: 'artist-2', title: 'Night Bus', subtitle: 'Kojo', image: null, route: '/album/ep-1', reason: 'Popular on BeatzClik' },
          { kind: 'podcast', contentId: 'show-1', creatorId: 'show-1', title: 'Accra After Dark', subtitle: '233 Media', image: null, route: '/podcast/show-1', reason: 'Because you follow Accra After Dark' },
        ],
      },
    }

    render(<ForYouRail />)

    expect(screen.getByText('Fire')).toBeTruthy()
    expect(screen.getByText('Gold')).toBeTruthy()
    expect(screen.getByText('Night Bus')).toBeTruthy()
    expect(screen.getByText('Accra After Dark')).toBeTruthy()
    expect(screen.getByText('Because you follow Accra After Dark')).toBeTruthy()
    expect(screen.getByText('Single')).toBeTruthy()
    expect(screen.getByText('Album')).toBeTruthy()
    expect(screen.getByText('EP')).toBeTruthy()
    expect(screen.getByText('Podcast')).toBeTruthy()
  })

  it('isolates an endpoint error and offers a retry', () => {
    state.query = { isPending: false, isError: true, refetch: state.refetch }

    render(<ForYouRail />)
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(state.refetch).toHaveBeenCalledOnce()
    expect(screen.getByText(/rest of Home is still available/)).toBeTruthy()
  })
})
