import { expect, it, vi } from 'vitest'
import { listApplications } from './api'

it('passes URL filters to the Java application list with same-origin credentials', async () => {
  const fetcher = vi.fn(async () => new Response(JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }), { status: 200 }))
  const result = await listApplications(new URLSearchParams('q=engineer&status=submitted&page=2'), fetcher)
  expect(result.total).toBe(0)
  expect(fetcher).toHaveBeenCalledWith('/api/applications?q=engineer&status=submitted&page=2', expect.objectContaining({ credentials: 'same-origin' }))
})
