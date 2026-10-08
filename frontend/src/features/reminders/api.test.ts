import { afterEach, expect, it, vi } from 'vitest'
import { createReminder, listReminders, transitionReminder } from './api'

afterEach(() => vi.unstubAllGlobals())

it('reads persisted reminder groups without requesting a CSRF token', async () => {
  const fetcher = vi.fn(async () => new Response(JSON.stringify({ overdue: [], upcoming: [], history: [], email: { available: false, address: null } })))
  vi.stubGlobal('fetch', fetcher)
  expect((await listReminders('completed')).history).toEqual([])
  expect(fetcher).toHaveBeenCalledWith('/api/reminders?status=completed', expect.objectContaining({ method: 'GET' }))
  expect(fetcher).toHaveBeenCalledTimes(1)
})

it('creates and transitions with the Java version contract and CSRF token', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => path === '/api/security/csrf'
    ? new Response(JSON.stringify({ token: 'test-token' }))
    : new Response(JSON.stringify({ id: 'r1', status: 'due', version: 2, body: init?.body })))
  vi.stubGlobal('fetch', fetcher)
  await createReminder({ applicationId: 'a1', title: '准备复盘', eventAt: '2026-10-09T10:00:00Z', notifyAt: '2026-10-09T09:00:00Z', emailEnabled: false })
  expect(fetcher).toHaveBeenCalledWith('/api/reminders', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'test-token' }) }))
  await transitionReminder({ id: 'r1', applicationId: 'a1', companyName: '公司', positionName: '岗位', title: '复盘', eventAt: '', notifyAt: '', emailEnabled: false, status: 'due', version: 2, emailStatus: null }, 'complete')
  expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1/complete', expect.objectContaining({ body: JSON.stringify({ version: 2 }) }))
})
