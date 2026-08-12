import { describe, it, expect, vi, beforeAll, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { User } from 'lucide-react'
import { AdminCommand } from './admin-command'

/**
 * GAP-24 replaced the palette's hardcoded arrays with real API queries. GAP-29: the `useMemo` that
 * builds the result list kept the dependency array from when the data was synchronous — `[q,
 * sections, navigate]`. `sections` is a module-level constant and `navigate` is stable, so `q` was
 * the only dependency that ever changed, and results computed before the fetch resolved were never
 * recomputed once it did.
 *
 * The test drives the real ordering an operator produces: open the palette, type the whole query
 * before the network answers, then let it answer. Nothing types again afterwards — that is the
 * point. Adding a keystroke after the data lands would change `q` and hide the bug.
 */

// Stable identity, exactly like the real `NAV` constant in admin-shell.tsx — the memo can only be
// invalidated by `q` and by the query data, which is what makes the missing deps load-bearing.
const SECTIONS = [{ to: '/admin/users', icon: User, label: 'Users' }]

const navigate = vi.fn()
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => navigate }))

let resolveUsers: (v: unknown) => void
let resolveCatalog: (v: unknown) => void

vi.mock('../../lib/api/queries/admin-users', () => ({
  usersQuery: () => ({
    queryKey: ['admin', 'users', 'list'],
    queryFn: () => new Promise((r) => { resolveUsers = r }),
  }),
}))
vi.mock('../../lib/api/queries/admin-catalog', () => ({
  catalogQuery: () => ({
    queryKey: ['admin', 'catalog', 'all'],
    queryFn: () => new Promise((r) => { resolveCatalog = r }),
  }),
}))

function renderPalette() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminCommand open onClose={() => {}} sections={SECTIONS} />
    </QueryClientProvider>,
  )
}

// jsdom implements no layout, so it ships no scrollIntoView. The palette calls it to keep the
// highlighted row visible; that is real behaviour worth keeping, not something to work around in
// the component.
beforeAll(() => { Element.prototype.scrollIntoView = () => {} })

// vitest runs without `globals`, so testing-library never registers its automatic afterEach —
// without this the second render finds the first test's palette still mounted.
afterEach(cleanup)

describe('AdminCommand', () => {
  it('shows a user typed before the fetch resolved, without a further keystroke', async () => {
    renderPalette()

    // Typed while both queries are still in flight — the memo runs against two empty arrays.
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Abdul' } })
    expect(screen.queryByText('Abdul Shakur')).toBeNull()

    // The network answers. No further typing: `q` does not change again.
    resolveUsers({ users: [{ id: 'u1', name: 'Abdul Shakur', email: 'abdul@example.com' }] })
    resolveCatalog({ items: [] })

    await waitFor(() => expect(screen.getByText('Abdul Shakur')).toBeTruthy())
  })

  it('shows a release typed before the fetch resolved, without a further keystroke', async () => {
    renderPalette()

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Iron' } })

    resolveUsers({ users: [] })
    resolveCatalog({ items: [{ id: 'c1', title: 'Iron Boy', artist: 'Black Sherif' }] })

    await waitFor(() => expect(screen.getByText('Iron Boy')).toBeTruthy())
  })
})
