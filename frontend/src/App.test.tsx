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
    expect(await screen.findByRole('heading', { name: '欢迎回到职迹' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
    fireEvent.change(screen.getByLabelText('用户名或邮箱'), { target: { value: 'tester' } })
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
    fireEvent.change(await screen.findByLabelText('用户名或邮箱'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('账号或密码错误')
    expect(screen.getByLabelText('用户名或邮箱')).toHaveValue('tester')
    expect(screen.getByRole('link', { name: '忘记密码？' })).toHaveAttribute('href', '/forgot-password')
  })

  it('restores a session and signs out without protected content', async () => {
    window.history.replaceState(null, '', '/applications')
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path: string) => path === '/api/auth/session' ? json(session) : path === '/api/auth/logout' ? new Response(null, { status: 204 }) : json(emptyApplications)))
    render(<App />)
    expect(await screen.findByRole('button', { name: '退出登录' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))
    expect(await screen.findByRole('heading', { name: '欢迎回到职迹' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '投递记录' })).not.toBeInTheDocument()
  })

  it('never follows an external returnTo', async () => {
    window.history.replaceState(null, '', '/login?returnTo=https%3A%2F%2Fevil.example')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json({ code: 'unauthorized' }, 401)).mockResolvedValueOnce(json({ redirectTarget: 'https://evil.example' })).mockResolvedValueOnce(json(session)))
    render(<App />)
    fireEvent.change(await screen.findByLabelText('用户名或邮箱'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    await waitFor(() => expect(window.location.pathname).toBe('/applications'))
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
    expect(screen.getByRole('heading', { name: '管理后台' })).toBeInTheDocument()
    expect(await screen.findByText('活跃数据暂不可用。')).toBeInTheDocument()
  })

  it('opens the public interview deep link and shows an explicit not-found page', async () => {
    window.history.replaceState(null, '', '/interviews/public')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/auth/session' ? json(session) : path.startsWith('/api/interviews/public?') ? json({ items: [], nextCursor: null, total: 0, limit: 20, facets: { cities: [], positions: [] } }) : json({ items: [], nextCursor: null, total: 0, limit: 20 })))
    render(<App />)
    expect(await screen.findByRole('button', { name: '公开面经', pressed: true })).toBeInTheDocument()
    window.history.pushState(null, '', '/no-such-page')
    window.dispatchEvent(new PopStateEvent('popstate'))
    expect(await screen.findByRole('heading', { name: '页面未找到', level: 1 })).toBeInTheDocument()
  })
})
