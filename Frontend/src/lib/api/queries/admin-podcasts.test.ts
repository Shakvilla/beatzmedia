import { describe, expect, it, vi } from 'vitest'
import * as client from '../client'
import { adminPodcastEpisodesQuery } from './admin-podcasts'

vi.mock('../client')

describe('admin podcast uploads', () => {
  it('fetches a paginated inventory and refreshes processing status', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [], page: 2, size: 50, total: 75 })
    const query = adminPodcastEpisodesQuery(2)

    const page = await query.queryFn!({} as never)

    expect(client.apiFetch).toHaveBeenCalledWith('/admin/podcasts/episodes?page=2&size=50')
    expect(page.total).toBe(75)
    expect(query.refetchInterval).toBe(15_000)
  })
})
