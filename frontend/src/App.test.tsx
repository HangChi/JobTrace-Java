import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
const session = { session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '小李', image: null, role: 'USER' } }
const emptyApplications = { items: [], nextCursor: null, total: 0, page: 1, limit: 50 }

describe('App authentication', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

  it('keeps a safe protected deep link and signs in with the Java cookie', async () => {
    window.history.replaceState(null, '', '/applications?status=APPLIED')
    const fetcher = vi.fn().mockResolvedValueOnce(json({ code: 'unauthorized' }, 401)).mockResolvedValueOnce(json({ redirectTarget: '/applications?status=APPLIED' })).mockResolvedValueOnce(json(session))
    vi.stubGlobal('fetch', fetcher)
    render(<App />)
    expect(await screen.findByRole('heading', { name: '登录 JobTrace' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
    fireEvent.change(screen.getByLabelText('邮箱或用户名'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    await waitFor(() => expect(window.location.pathname).toBe('/applications'))
    expect(fetcher).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ credentials: 'same-origin', method: 'POST' }))
    expect(screen.getByRole('heading', { name: '投递记录' })).toBeInTheDocument()
  })

  it('preserves values and shows recovery after invalid credentials', async () => {
    window.history.replaceState(null, '', '/login')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json({ code: 'unauthorized' }, 401)).mockResolvedValueOnce(json({ code: 'invalid_credentials', detail: '账号或密码错误' }, 401)))
    render(<App />)
    fireEvent.change(await screen.findByLabelText('邮箱或用户名'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('账号或密码错误')
    expect(screen.getByLabelText('邮箱或用户名')).toHaveValue('tester')
    expect(screen.getByRole('link', { name: '忘记密码？' })).toHaveAttribute('href', '/forgot-password')
  })

  it('restores a session and signs out without protected content', async () => {
    window.history.replaceState(null, '', '/applications')
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path: string) => path === '/api/auth/session' ? json(session) : path === '/api/auth/logout' ? new Response(null, { status: 204 }) : json(emptyApplications)))
    render(<App />)
    expect(await screen.findByRole('button', { name: '退出登录' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))
    expect(await screen.findByRole('heading', { name: '登录 JobTrace' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '投递记录' })).not.toBeInTheDocument()
  })

  it('opens the legacy recruitment home and keeps the four primary modules', async () => {
    window.history.replaceState(null, '', '/')
    const market = { items: [{ id: 'c1', listingKind: 'synced_jobs', company: { name: '示例公司', type: '企业', industry: '软件' }, recruitmentType: null, positions: ['Java 工程师'], positionCount: 1, locations: [{ name: '上海' }], status: 'open', primaryApplyUrl: 'https://example.com/jobs', source: { name: '招聘官网', url: 'https://example.com/jobs' }, publishedAt: '2026-10-01', lastConfirmedAt: null, isFavorite: false }], page: 1, limit: 20, total: 1 }
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json(session) : path.startsWith('/api/job-market/campaigns') ? json(market) : json(emptyApplications)))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '招聘广场' })).toBeInTheDocument()
    expect(await screen.findByText('示例公司')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '主要功能' })).toHaveTextContent('招聘广场我的投递面经广场求职分析')
    fireEvent.click(screen.getByRole('link', { name: '我的投递' }))
    expect(await screen.findByRole('heading', { name: '投递记录' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/applications')
  })

  it('never follows an external returnTo', async () => {
    window.history.replaceState(null, '', '/login?returnTo=https%3A%2F%2Fevil.example')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json({ code: 'unauthorized' }, 401)).mockResolvedValueOnce(json({ redirectTarget: 'https://evil.example' })).mockResolvedValueOnce(json(session)))
    render(<App />)
    fireEvent.change(await screen.findByLabelText('邮箱或用户名'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    await waitFor(() => expect(window.location.pathname).toBe('/'))
    expect(window.location.host).not.toBe('evil.example')
  })

  it('shows a retry state if the session service is unavailable', async () => {
    window.history.replaceState(null, '', '/applications')
    vi.stubGlobal('fetch', vi.fn(async () => json({ code: 'session_unavailable', detail: 'Session service is unavailable.' }, 503)))
    render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Session service is unavailable.')
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
  })

  it('hides admin navigation from a regular user, including a direct URL', async () => {
    window.history.replaceState(null, '', '/admin')
    vi.stubGlobal('fetch', vi.fn(async () => json(session)))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '无权访问' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '管理后台' })).not.toBeInTheDocument()
  })

  it('allows admins to navigate to the live Java management view', async () => {
    window.history.replaceState(null, '', '/applications')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json({ ...session, user: { ...session.user, role: 'ADMIN' } }) : path === '/api/admin/summary' ? json({ counts: { status: 'available', value: { users: 3, activeUsers: 2, applications: 8, interviews: 4 } }, activity: { status: 'unavailable', windows: null } }) : json(emptyApplications)))
    render(<App />)
    const adminLink = await screen.findByRole('link', { name: '管理后台' })
    fireEvent.click(adminLink)
    expect(window.location.pathname).toBe('/admin')
    expect(await screen.findByRole('heading', { name: '运营概览' })).toBeInTheDocument()
    expect(await screen.findByText('活跃数据暂不可用。')).toBeInTheDocument()
  })

  it('opens an administrator deep link at the matching legacy section', async () => {
    window.history.replaceState(null, '', '/admin/users')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json({ ...session, user: { ...session.user, role: 'ADMIN' } }) : json({ items: [], total: 0, page: 1, limit: 50 })))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '账号目录' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '用户管理', pressed: true })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '操作审计' }))
    expect(window.location.pathname).toBe('/admin/audit')
  })

  it('opens the public interview deep link and shows an explicit not-found page', async () => {
    window.history.replaceState(null, '', '/interviews/public')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json(session) : path.startsWith('/api/interviews/public?') ? json({ items: [], nextCursor: null, total: 0, limit: 20, facets: { cities: [], positions: [] } }) : json({ items: [], nextCursor: null, total: 0, limit: 20 })))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '面经分享' })).toBeInTheDocument()
    window.history.pushState(null, '', '/no-such-page')
    window.dispatchEvent(new PopStateEvent('popstate'))
    expect(await screen.findByRole('heading', { name: '页面未找到', level: 1 })).toBeInTheDocument()
  })

  it('restores the legacy application create URL as its own page', async () => {
    window.history.replaceState(null, '', '/applications/new?company=%E7%A4%BA%E4%BE%8B%E5%85%AC%E5%8F%B8&position=Java')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json(session) : json(emptyApplications)))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '新增投递', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /返回投递记录/ })).toHaveAttribute('href', '/applications')
    expect(screen.getByLabelText('公司名称')).toBeInTheDocument()
    expect(screen.getByLabelText('公司名称')).toHaveValue('示例公司')
    expect(screen.getByLabelText('岗位名称')).toHaveValue('Java')
  })

  it('opens a shared interview detail directly instead of returning to the list', async () => {
    window.history.replaceState(null, '', '/interviews/shared/p1')
    const interview = { id: 'p1', companyName: '公开公司', positionName: '开发', stage: 'interview_1', city: null, publishedAt: '2026-10-09T00:00:00Z', interviewedOn: '2026-10-08', author: { username: '匿名' }, highlights: '经验总结', gaps: null, questions: [], engagement: { likedByViewer: false, likeCount: 2, commentCount: 0, viewCount: 0 } }
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json(session) : path === '/api/interviews/public/p1' ? json(interview) : path === '/api/interviews/public/p1/comments' ? json([]) : path.startsWith('/api/interviews/public?') ? json({ items: [], total: 0, nextCursor: null, limit: 20 }) : json(emptyApplications)))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '公开公司 · 开发' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /返回面经广场/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '面经分享' })).not.toBeInTheDocument()
  })

  it('opens the administrator user archive on its legacy URL', async () => {
    window.history.replaceState(null, '', '/admin/users/u2')
    const user = { id: 'u2', username: 'candidate', email: 'candidate@example.com', internalEmail: null, role: 'USER', disabled: false, accessVersion: 1, createdAt: '2026-01-01', lastSignInAt: null, applicationCount: 0, interviewCount: 0, recentAuditEvents: [], applications: { items: [], total: 0, page: 1, limit: 20, totalPages: 0 }, interviews: { items: [], total: 0, page: 1, limit: 20, totalPages: 0 } }
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json({ ...session, user: { ...session.user, role: 'ADMIN' } }) : json(user)))
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'candidate', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /返回用户目录/ })).toBeInTheDocument()
  })
})
