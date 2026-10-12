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
  fireEvent.click(screen.getByRole('button', { name: '用户管理' }))
  expect(await screen.findByRole('columnheader', { name: '投递 / 面经' })).toBeInTheDocument()
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
  fireEvent.click(screen.getByRole('button', { name: '招聘同步' }))
  expect(await screen.findByText(/岗位采集及同步执行仍由原有服务负责/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /爬取|同步执行/ })).not.toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalledWith(expect.stringContaining('/sync'), expect.objectContaining({ method: 'POST' }))
})

it('filters managed users and rejects a short access-change reason without writing', async () => {
  const fetcher = vi.fn(async (path: string) => path.startsWith('/api/admin/users')
    ? json({ items: [user], total: 1, page: 1, limit: 50 })
    : json({ counts: { status: 'unavailable', value: null }, activity: { status: 'unavailable', windows: null } }))
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" onExpired={vi.fn()} initialTab="users" />)
  fireEvent.change(screen.getByLabelText('搜索用户'), { target: { value: 'tester' } })
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/users?limit=50&q=tester', expect.anything()))
  fireEvent.click(await screen.findByRole('button', { name: '管理访问' }))
  fireEvent.change(screen.getByLabelText('原因（至少 10 字）'), { target: { value: 'too short' } })
  fireEvent.submit(screen.getByRole('button', { name: '确认变更' }).closest('form')!)
  expect(screen.getByRole('alert')).toHaveTextContent('操作原因至少填写 10 个字符')
  expect(fetcher).not.toHaveBeenCalledWith('/api/admin/users/u2', expect.anything())
  fireEvent.click(screen.getByRole('button', { name: '取消' }))
  expect(screen.queryByRole('heading', { name: /管理 tester/ })).not.toBeInTheDocument()
})

it('renders available overview and audit details', async () => {
  const fetcher = vi.fn(async (path: string) => path === '/api/admin/summary'
    ? json({ counts: { status: 'available', value: { users: 4, activeUsers: 3, disabledUsers: 1, administrators: 1, applications: 9, interviews: 2 } }, activity: { status: 'available', windows: { registered7d: 2, active7d: 3, registered30d: 4, active30d: 4 } } })
    : json({ items: [{ id: 'e1', actorIdentifier: 'admin', targetIdentifier: 'tester', eventType: 'disable_user', outcome: 'success', reason: '违规操作', createdAt: '2026-10-01T00:00:00Z' }] }))
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" onExpired={vi.fn()} />)
  expect(await screen.findByText('近 30 天注册')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '操作审计' }))
  expect(await screen.findByText('admin → tester')).toBeInTheDocument()
  expect(screen.getByText('违规操作')).toBeInTheDocument()
})

it('updates a marketplace source and approves a discovered candidate', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const source = { id: 's1', company: { id: 'c1', name: '示例公司', type: '企业', industry: '软件' }, adapter: 'ats', baseUrl: 'https://example.test', status: 'paused', lastSuccessAt: null, consecutiveFailures: 1 }
  const candidate = { id: 'd1', companyName: '待审公司', reviewStatus: 'pending', entryUrl: 'https://example.test/jobs' }
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/admin/job-market/sources/s1' && init?.method === 'PATCH') return json({ ...source, status: 'active' })
    if (path === '/api/admin/job-market/discovery/d1' && init?.method === 'PATCH') return json({ ...candidate, reviewStatus: 'approved' })
    if (path.endsWith('/sources')) return json({ items: [source] })
    if (path.includes('/catalog')) return json({ items: [], total: 0, page: 1, limit: 50 })
    if (path.includes('/sync-runs')) return json({ items: [], total: 0, page: 1, limit: 20 })
    if (path.includes('/discovery?')) return json({ items: [candidate], summary: {} })
    if (path.includes('/company-candidates?')) return json({ items: [], summary: {} })
    return json({ counts: { status: 'unavailable', value: null }, activity: { status: 'unavailable', windows: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" onExpired={vi.fn()} initialTab="market" />)
  fireEvent.click(await screen.findByRole('button', { name: '启用' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/job-market/sources/s1', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'active' }) })))
  fireEvent.click(screen.getByRole('button', { name: '批准' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/job-market/discovery/d1', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ action: 'approve' }) })))
})
