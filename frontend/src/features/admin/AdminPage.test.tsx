import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } })
const user = { id: 'u2', username: 'tester', email: 'tester@example.test', internalEmail: null, role: 'USER', disabled: false, accessVersion: 3, createdAt: '2026-01-01T00:00:00Z', lastSignInAt: null, applicationCount: 2, interviewCount: 1 }

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

it('updates a managed user with reason, expected version and CSRF', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path.startsWith('/api/admin/users/u2')) return json({ user: { ...user, disabled: true, accessVersion: 4 }, auditEventId: 'audit-1' })
    if (path.startsWith('/api/admin/users')) return json({ items: [user], total: 1, page: 1, limit: 50, totalPages: 1 })
    return json({ counts: { status: 'unavailable', value: null }, activity: { status: 'unavailable', windows: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" onExpired={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: '用户' }))
  fireEvent.click(await screen.findByRole('button', { name: '管理访问' }))
  fireEvent.change(screen.getByLabelText('原因（至少 10 字）'), { target: { value: '账户访问需要暂停处理' } })
  fireEvent.submit(screen.getByRole('button', { name: '确认变更' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/users/u2', expect.objectContaining({ method: 'PATCH', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }), body: expect.stringContaining('"expectedVersion":3') })))
})

it('shows marketplace read data without offering Java ingestion', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path.endsWith('/sources')) return json({ items: [] })
    if (path.includes('/catalog')) return json({ items: [], total: 0, page: 1, limit: 50 })
    if (path.includes('/sync-runs')) return json({ items: [], total: 0, page: 1, limit: 20 })
    if (path.includes('/discovery') || path.includes('/company-candidates')) return json({ items: [], summary: {} })
    return json({ counts: { status: 'unavailable', value: null }, activity: { status: 'unavailable', windows: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" onExpired={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: '岗位市场' }))
  expect(await screen.findByText(/岗位采集及同步执行仍由原有服务负责/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /爬取|同步执行/ })).not.toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalledWith(expect.stringContaining('/sync'), expect.objectContaining({ method: 'POST' }))
})
