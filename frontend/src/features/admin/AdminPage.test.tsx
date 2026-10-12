import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })
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

it('requires confirmation for a self access change and opens a user archive', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
  const navigate = vi.fn()
  const self = { ...user, id: 'u1', role: 'ADMIN', email: null, internalEmail: 'admin@internal.test', disabled: true, lastSignInAt: '2026-10-01T00:00:00Z' }
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : init?.method === 'PATCH' ? json({ user: { ...self, disabled: false, accessVersion: 4 }, auditEventId: 'audit-2' }) : json({ items: [self], total: 1, page: 1, limit: 50 }))
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" initialTab="users" navigate={navigate} onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '查看档案' }))
  expect(navigate).toHaveBeenCalledWith('/admin/users/u1')
  expect(screen.getByText('admin@internal.test')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '管理访问' }))
  expect(screen.getByLabelText('操作')).toHaveValue('enable_user')
  fireEvent.change(screen.getByLabelText('原因（至少 10 字）'), { target: { value: '管理员恢复自用账号访问' } })
  fireEvent.submit(screen.getByRole('button', { name: '确认变更' }).closest('form')!)
  expect(fetcher).not.toHaveBeenCalledWith('/api/admin/users/u1', expect.objectContaining({ method: 'PATCH' }))
  fireEvent.submit(screen.getByRole('button', { name: '确认变更' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/users/u1', expect.objectContaining({ body: expect.stringContaining('"confirmSelf":true') })))
  expect(confirm).toHaveBeenCalledTimes(2)
  expect(await screen.findByText('账号权限已更新，审计事件已记录。')).toBeInTheDocument()
  vi.restoreAllMocks()
})

it('shows marketplace run failures and can pause a source or ignore a company candidate', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const source = { id: 's1', company: { id: 'c1', name: '示例公司', type: '企业', industry: '软件' }, adapter: 'ats', baseUrl: 'https://example.test', status: 'active', lastSuccessAt: null, consecutiveFailures: 2 }
  const company = { id: 'c2', companyName: '待审公司', reviewStatus: 'pending', articleUrl: 'https://example.test/post', detectedAdapter: null }
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/admin/job-market/sources/s1' && init?.method === 'PATCH') return json({ ...source, status: 'paused' })
    if (path === '/api/admin/job-market/company-candidates/c2' && init?.method === 'PATCH') return json({ ...company, reviewStatus: 'ignored' })
    if (path.endsWith('/sources')) return json({ items: [source] })
    if (path.includes('/catalog')) return json({ items: [{ identityKey: 'c1', companyName: '示例公司', channelLabel: '官网', adapter: 'ats' }], total: 1, page: 1, limit: 50 })
    if (path.includes('/sync-runs')) return json({ items: [{ id: 'r1', sourceId: 's1', status: 'failed', startedAt: '2026-10-01T00:00:00Z', finishedAt: null, counts: null, errorSummary: '请求超时' }], total: 1, page: 1, limit: 20 })
    if (path.includes('/discovery?')) return json({ items: [], summary: {} })
    return json({ items: [company], summary: {} })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" initialTab="market" onExpired={vi.fn()} />)
  expect(await screen.findByText('请求超时')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '暂停' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/job-market/sources/s1', expect.objectContaining({ body: JSON.stringify({ status: 'paused' }) })))
  fireEvent.click(screen.getByRole('button', { name: '忽略' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/job-market/company-candidates/c2', expect.objectContaining({ body: JSON.stringify({ action: 'ignore' }) })))
  vi.restoreAllMocks()
})

it('reports an admin request failure and expires an unauthorized request', async () => {
  const onExpired = vi.fn()
  const fetcher = vi.fn().mockResolvedValueOnce(json({ code: 'unavailable', detail: '管理服务暂不可用' }, 503)).mockResolvedValueOnce(json({ code: 'unauthorized', detail: '请登录' }, 401))
  vi.stubGlobal('fetch', fetcher)
  render(<AdminPage currentUserId="u1" initialTab="users" onExpired={onExpired} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('管理服务暂不可用')
  fireEvent.change(screen.getByLabelText('搜索用户'), { target: { value: 'tester' } })
  await waitFor(() => expect(onExpired).toHaveBeenCalledOnce())
})
