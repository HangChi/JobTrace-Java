import { expect, it, vi } from 'vitest'
import { addApplicationStage, createApplication, deleteApplication, listApplications, updateApplicationStatus } from './api'
import type { ApplicationDetail } from './api'

it('passes URL filters to the Java application list with same-origin credentials', async () => {
  const fetcher = vi.fn(async () => new Response(JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }), { status: 200 }))
  const result = await listApplications(new URLSearchParams('q=engineer&status=submitted&page=2'), fetcher)
  expect(result.total).toBe(0)
  expect(fetcher).toHaveBeenCalledWith('/api/applications?q=engineer&status=submitted&page=2', expect.objectContaining({ credentials: 'same-origin' }))
})

it('uses CSRF protection and the Java contracts for application commands', async () => {
  const fetcher = vi.fn(async (path: string) => new Response(JSON.stringify(path === '/api/security/csrf' ? { token: 'csrf-token' } : { id: 'a1', version: 3 })))
  vi.stubGlobal('fetch', fetcher)
  const draft = { companyName: '测试公司', positionName: '开发', city: '', jobUrl: '', appliedDate: '2026-10-08', type: 'campus_recruitment', status: 'submitted' as const, notes: '', stages: [] }
  const detail = { id: 'a1', version: 2 } as ApplicationDetail
  await createApplication(draft)
  expect(fetcher).toHaveBeenCalledWith('/api/applications', expect.objectContaining({ method: 'POST', body: JSON.stringify(draft), headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }) }))
  await updateApplicationStatus(detail, 'offer')
  expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'offer', version: 2 }) }))
  await addApplicationStage(detail, 'interview_1', '2026-10-09')
  expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/stages', expect.objectContaining({ method: 'POST', body: JSON.stringify({ stage: 'interview_1', occurredOn: '2026-10-09' }) }))
  await deleteApplication('a1')
  expect(fetcher).toHaveBeenCalledWith('/api/applications/a1', expect.objectContaining({ method: 'DELETE' }))
})
