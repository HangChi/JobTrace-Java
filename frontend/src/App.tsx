import { useCallback, useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import { currentPath, guestRoutes, navigation, safePath } from './app/routes'
import AuthPage from './features/auth/AuthPage'
import ApplicationList from './features/applications/ApplicationList'
import ReminderPage from './features/reminders/ReminderPage'
import AnalyticsPage from './features/analytics/AnalyticsPage'
import ImportPage from './features/import/ImportPage'
import ProfilePage from './features/profile/ProfilePage'
import InterviewsPage from './features/interviews/InterviewsPage'
import AdminPage from './features/admin/AdminPage'
import { getSession, logout } from './features/auth/api'
import type { CurrentSession } from './features/auth/api'
import { ApiError } from './shared/api/client'
import './App.css'

type AuthState = { kind: 'checking' } | { kind: 'guest' } | { kind: 'ready'; session: CurrentSession } | { kind: 'error'; message: string }

function App() {
  const [path, setPath] = useState(currentPath)
  const [auth, setAuth] = useState<AuthState>({ kind: 'checking' })

  function navigate(target: string, replace = false) {
    const destination = safePath(target)
    window.history[replace ? 'replaceState' : 'pushState'](null, '', destination)
    setPath(destination)
  }

  function handleLink(event: MouseEvent<HTMLAnchorElement>, target: string) {
    if (event.button || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    event.preventDefault()
    navigate(target)
  }

  useEffect(() => {
    const onPop = () => setPath(currentPath())
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  useEffect(() => {
    let active = true
    getSession().then(session => {
      if (!active) return
      setAuth({ kind: 'ready', session })
      if (guestRoutes.has(window.location.pathname) || window.location.pathname === '/') navigate('/applications', true)
    }).catch(error => {
      if (!active) return
      if (error instanceof ApiError && error.status === 401) {
        setAuth({ kind: 'guest' })
        if (!guestRoutes.has(window.location.pathname)) navigate(`/login?returnTo=${encodeURIComponent(currentPath())}`, true)
      } else setAuth({ kind: 'error', message: error instanceof ApiError ? error.message : '暂时无法连接，请稍后重试。' })
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (auth.kind === 'checking' || auth.kind === 'error') return
    const pathname = new URL(path, window.location.origin).pathname
    const destination = auth.kind === 'guest' && !guestRoutes.has(pathname)
      ? `/login?returnTo=${encodeURIComponent(path)}`
      : auth.kind === 'ready' && (guestRoutes.has(pathname) || pathname === '/')
        ? '/applications' : null
    if (destination) queueMicrotask(() => navigate(destination, true))
  }, [auth, path])

  async function signOut() {
    try {
      await logout()
      setAuth({ kind: 'guest' })
      navigate('/login', true)
    } catch (error) {
      setAuth({ kind: 'error', message: error instanceof ApiError ? error.message : '退出失败，请重试。' })
    }
  }

  const expire = useCallback(() => {
    const destination = `/login?returnTo=${encodeURIComponent(currentPath())}`
    window.history.replaceState(null, '', destination)
    setPath(destination)
    setAuth({ kind: 'guest' })
  }, [])

  if (auth.kind === 'checking') return <main className="center-state" role="status">正在确认登录状态…</main>
  if (auth.kind === 'error') return <main className="center-state"><h1>暂时无法连接职迹</h1><p role="alert">{auth.message}</p><button onClick={() => window.location.reload()}>重试</button></main>

  const pathname = new URL(path, window.location.origin).pathname
  if (auth.kind === 'guest') {
    return <AuthPage key={pathname} path={pathname} query={new URL(path, window.location.origin).searchParams} navigate={navigate} onLogin={session => setAuth({ kind: 'ready', session })} onLink={handleLink} />
  }
  return <Workspace path={pathname} search={new URL(path, window.location.origin).search} session={auth.session} onLink={handleLink} onSignOut={signOut} navigate={navigate} onExpired={expire} />
}

function Workspace({ path, search, session, onLink, onSignOut, navigate, onExpired }: { path: string; search: string; session: CurrentSession; onLink: (event: MouseEvent<HTMLAnchorElement>, target: string) => void; onSignOut: () => Promise<void>; navigate: (path: string) => void; onExpired: () => void }) {
  const items = session.user.role === 'ADMIN' ? [...navigation, { path: '/admin', label: '管理后台' }] : navigation
  const current = items.find(item => item.path === path || (item.path === '/interviews' && path === '/interviews/public'))
  const title = current?.label ?? '页面未找到'
  return <div className="workspace">
    <aside className="sidebar">
      <a className="brand" href="/applications" onClick={event => onLink(event, '/applications')}>职迹 <span>JobTrace</span></a>
      <p className="nav-caption">工作台</p>
      <nav aria-label="主导航">{items.map(item => <a key={item.path} href={item.path} aria-current={current?.path === item.path ? 'page' : undefined} onClick={event => onLink(event, item.path)}>{item.label}</a>)}</nav>
      <div className="sidebar-user"><span className="avatar" aria-hidden="true">{session.user.displayName.slice(0, 1)}</span><span>{session.user.displayName}</span><button className="text-button" onClick={() => void onSignOut()}>退出登录</button></div>
    </aside>
    <main className="workspace-main"><header className="workspace-header"><div><p className="eyebrow">JOBTRACE / WORKSPACE</p><h1>{title}</h1></div><span className="header-user">{session.user.displayName}</span></header>
      {path === '/applications' ? <ApplicationList key={search} search={search} navigate={navigate} onExpired={onExpired} /> : path === '/reminders' ? <ReminderPage onExpired={onExpired} /> : path === '/analytics' ? <AnalyticsPage onExpired={onExpired} /> : path === '/import' ? <ImportPage onExpired={onExpired} /> : path === '/profile' ? <ProfilePage onExpired={onExpired} /> : path === '/interviews' || path === '/interviews/public' ? <InterviewsPage key={path} initialTab={path === '/interviews/public' ? 'public' : 'private'} onExpired={onExpired} /> : path === '/admin' && session.user.role !== 'ADMIN' ? <section className="content-card"><h2>无权访问</h2><p>此页面仅供管理员使用。</p></section> : path === '/admin' ? <AdminPage currentUserId={session.user.id} onExpired={onExpired} /> :
        <section className="content-card"><h2>页面未找到</h2><p>这个地址不存在，请从左侧导航选择要访问的页面。</p></section>}
    </main>
  </div>
}

export default App
