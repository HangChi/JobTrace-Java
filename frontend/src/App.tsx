import AccountAvatar from './shared/ui/AccountAvatar'
import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import { currentPath, guestRoutes, navigation, safePath } from './app/routes'
import { getSession, logout } from './features/auth/api'
import type { CurrentSession } from './features/auth/api'
import { ApiError } from './shared/api/client'
import './App.css'
import './AppShell.css'
import './Polish.css'
import './ReferenceLayout.css'
import './FormSurfaces.css'
import './Insights.css'
import './PrivateInterviews.css'
import './ProfileSurfaces.css'
import './ColorBalance.css'
import './PublicInterviews.css'

const AuthPage = lazy(() => import('./features/auth/AuthPage'))
const ApplicationList = lazy(() => import('./features/applications/ApplicationList'))
const ReminderPage = lazy(() => import('./features/reminders/ReminderPage'))
const AnalyticsPage = lazy(() => import('./features/analytics/AnalyticsPage'))
const ImportPage = lazy(() => import('./features/import/ImportPage'))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))
const InterviewsPage = lazy(() => import('./features/interviews/InterviewsPage'))
const AdminPage = lazy(() => import('./features/admin/AdminPage'))
const AdminUserDetailPage = lazy(() => import('./features/admin/AdminUserDetailPage'))
const JobMarketPage = lazy(() => import('./features/job-market/JobMarketPage'))

type AuthState = { kind: 'checking' } | { kind: 'guest' } | { kind: 'ready'; session: CurrentSession } | { kind: 'error'; message: string }

function routeId(path: string, prefix: string): string | undefined {
  if (!path.startsWith(prefix)) return undefined
  const encoded = path.slice(prefix.length)
  if (!encoded || encoded.includes('/')) return undefined
  try { return decodeURIComponent(encoded) } catch { return undefined }
}

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
      if (guestRoutes.has(window.location.pathname)) navigate('/', true)
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
      : auth.kind === 'ready' && guestRoutes.has(pathname)
        ? '/' : null
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
    return <Suspense fallback={<main className="center-state" role="status">正在加载页面…</main>}><AuthPage key={pathname} path={pathname} query={new URL(path, window.location.origin).searchParams} navigate={navigate} onLogin={session => setAuth({ kind: 'ready', session })} onLink={handleLink} /></Suspense>
  }
  return <Workspace path={pathname} search={new URL(path, window.location.origin).search} session={auth.session} onLink={handleLink} onSignOut={signOut} navigate={navigate} onExpired={expire} />
}

function Workspace({ path, search, session, onLink, onSignOut, navigate, onExpired }: { path: string; search: string; session: CurrentSession; onLink: (event: MouseEvent<HTMLAnchorElement>, target: string) => void; onSignOut: () => Promise<void>; navigate: (path: string) => void; onExpired: () => void }) {
  const [accountProfile, setAccountProfile] = useState<{ id: string; displayName: string; image: string | null } | null>(null)
  const accountUser = accountProfile?.id === session.user.id ? { ...session.user, ...accountProfile } : session.user
  const receiveProfile = useCallback((value: { displayName: string; image: string | null }) => setAccountProfile({ id: session.user.id, displayName: value.displayName, image: value.image }), [session.user.id])
  const [analyticsOverview, setAnalyticsOverview] = useState<{ period: string; applications: number; interviews: number } | null>(null)
  const receiveAnalyticsOverview = useCallback((value: { period: string; applications: number; interviews: number }) => setAnalyticsOverview(value), [])
  const current = navigation.find(item => item.path === path || (item.path === '/applications' && path.startsWith('/applications/')) || (item.path === '/interviews' && path.startsWith('/interviews/shared/')) || (item.path === '/analytics' && path.startsWith('/analytics/')))
  const pageHeader: Record<string, { kicker: string; title: string; description: string }> = {
    '/applications': { kicker: '岗位进展', title: '投递记录', description: '查看岗位进展并处理待跟进事项。' },
    '/analytics': { kicker: '数据概览', title: '求职分析', description: '按时间、类型和城市查看投递趋势与转化。' },
    '/interviews/mine': { kicker: '面试记录', title: '个人面经', description: '管理自己的面试记录、复盘状态和分享设置。' },
    '/import': { kicker: '数据管理', title: '导入投递记录', description: '选择 CSV 或 XLSX 文件，预检无误后再确认导入。' },
    '/reminders': { kicker: '待办事项', title: '提醒', description: '管理与投递关联的日程和跟进事项。' },
  }
  const adminTab = path === '/admin/users' ? 'users' : path === '/admin/audit' ? 'audit' : path === '/admin/job-market' ? 'market' : 'overview'
  const applicationDetail = routeId(path, '/applications/')
  const publicInterviewId = routeId(path, '/interviews/shared/')
  const privateInterviewId = !['/interviews/mine', '/interviews/new', '/interviews/public'].includes(path) && !publicInterviewId ? routeId(path, '/interviews/') : undefined
  const adminUserId = routeId(path, '/admin/users/')
  const exportQuery = new URLSearchParams(search)
  exportQuery.delete('cursor'); exportQuery.delete('page'); exportQuery.delete('limit'); exportQuery.set('scope', 'filtered')
  const accountLink = (event: MouseEvent<HTMLAnchorElement>, target: string) => {
    event.currentTarget.closest('details')?.removeAttribute('open')
    onLink(event, target)
  }
  return <div className="workspace">
    <a className="skip-link" href="#main-content">跳到主要内容</a>
    <header className="app-header">
      <a className="app-brand" href="/" aria-label="JobTrace 首页" onClick={event => onLink(event, '/')}>
        <img className="app-brand-mark" src="/jobtrace-icon.svg" alt="" width="38" height="38" />
        <span><strong>JobTrace</strong><small>投递与面试</small></span>
      </a>
      <nav className="app-primary-nav" aria-label="主要功能">{navigation.map(item => <a key={item.path} href={item.path} aria-current={current?.path === item.path ? 'page' : undefined} onClick={event => onLink(event, item.path)}>{item.label}</a>)}</nav>
      <details className="account-menu">
        <summary aria-label="打开用户菜单"><AccountAvatar name={accountUser.displayName} image={accountUser.image} /><span className="user-summary"><strong>{accountUser.displayName}</strong><small>{session.user.role === 'ADMIN' ? '管理员' : '普通用户'}</small></span><svg className="menu-chevron" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 8 4 4 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg></summary>
        <div className="account-popover">
          <div className="account-identity"><strong>{accountUser.displayName}</strong><small>{session.user.role === 'ADMIN' ? '管理员账号' : '个人账号'}</small></div>
          <a href="/profile" onClick={event => accountLink(event, '/profile')}>个人中心</a>
          <a href="/interviews/mine" onClick={event => accountLink(event, '/interviews/mine')}>个人面经</a>
          <a href="/import" onClick={event => accountLink(event, '/import')}>导入投递记录</a>
          {session.user.role === 'ADMIN' && <a href="/admin" onClick={event => accountLink(event, '/admin')}>管理后台</a>}
          <button type="button" onClick={() => void onSignOut()}>退出登录</button>
        </div>
      </details>
    </header>
    <main id="main-content" className="workspace-main">
      {pageHeader[path] && <header className={`workspace-header ${path === '/analytics' ? 'tone-analytics' : path === '/interviews/mine' ? 'tone-interviews' : ''}`}><div><p className="eyebrow">{pageHeader[path].kicker}</p><h1>{pageHeader[path].title}</h1><p className="workspace-description">{pageHeader[path].description}</p></div>{path === '/applications' && <div className="workspace-header-actions"><button type="button" onClick={() => navigate('/applications/new')}>＋ 新增投递</button><details className="application-export-menu"><summary><span>导出</span><svg className="export-menu-chevron" aria-hidden="true" viewBox="0 0 20 20"><path d="m6 8 4 4 4-4" /></svg></summary><div><p>选择导出格式</p><a href={`/api/exports/applications?${exportQuery}&format=xlsx`}>Excel 工作簿 <small>.xlsx</small></a><a href={`/api/exports/applications?${exportQuery}&format=csv`}>CSV 文件 <small>.csv</small></a></div></details></div>}{path === '/analytics' && analyticsOverview && <div className="analytics-hero-pills"><span>{analyticsOverview.period}</span><span>{analyticsOverview.applications} 次投递</span><span>{analyticsOverview.interviews} 次进入面试</span></div>}{path === '/interviews/mine' && <button className="workspace-header-cta interview-hero-create" type="button" onClick={() => navigate('/interviews/new')}>＋ 新增复盘</button>}</header>}
      <Suspense fallback={<p role="status">正在加载页面…</p>}>{path === '/' ? <JobMarketPage key={search} search={search} navigate={navigate} onExpired={onExpired} /> : path === '/applications' || applicationDetail ? <ApplicationList key={`${path}${search}`} search={search} navigate={navigate} onExpired={onExpired} routeMode initialEditingId={applicationDetail === 'new' ? null : applicationDetail} /> : path === '/reminders' ? <ReminderPage onExpired={onExpired} /> : path === '/analytics' ? <AnalyticsPage onExpired={onExpired} onOverview={receiveAnalyticsOverview} /> : path === '/import' ? <ImportPage onExpired={onExpired} /> : path === '/profile' ? <ProfilePage onExpired={onExpired} role={session.user.role} onProfileChange={receiveProfile} /> : path === '/interviews' || path === '/interviews/public' || path === '/interviews/mine' || path === '/interviews/new' || publicInterviewId || privateInterviewId ? <InterviewsPage key={path} initialTab={path === '/interviews/mine' || path === '/interviews/new' || Boolean(privateInterviewId) ? 'private' : 'public'} initialPrivateId={privateInterviewId} initialPublicId={publicInterviewId} startCreating={path === '/interviews/new'} navigate={navigate} onExpired={onExpired} /> : path.startsWith('/admin') && session.user.role !== 'ADMIN' ? <section className="content-card"><h1>无权访问</h1><p>此页面仅供管理员使用。</p></section> : adminUserId ? <AdminUserDetailPage key={adminUserId} id={adminUserId} navigate={navigate} onExpired={onExpired} /> : (path === '/admin' || path === '/admin/users' || path === '/admin/audit' || path === '/admin/job-market') ? <AdminPage key={path} currentUserId={session.user.id} onExpired={onExpired} initialTab={adminTab} onTabChange={tab => navigate(tab === 'overview' ? '/admin' : tab === 'market' ? '/admin/job-market' : `/admin/${tab}`)} navigate={navigate} /> :
        <section className="content-card"><h1>页面未找到</h1><p>这个地址不存在，请从顶部导航选择要访问的页面。</p></section>}
      </Suspense>
    </main>
  </div>
}

export default App
