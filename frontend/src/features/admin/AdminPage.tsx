import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import './AdminSurfaces.css'
import Pagination from '../../shared/ui/Pagination'
import { ApiError } from '../../shared/api/client'
import { changeAdminAccess, getAdminSummary, listAdminAudit, listAdminUsers, listCandidates, listCatalog, listRuns, listSources, reviewCandidate, updateSource } from './api'
import type { AdminAudit, AdminSummary, AdminUser, Candidate, CatalogPage, RunPage, Source } from './api'

export type Tab = 'overview' | 'users' | 'audit' | 'market'
const errorText = (error: unknown) => error instanceof ApiError ? error.message : '管理数据暂时无法加载。'

const sections = [
  { value: 'overview', label: '运营概览', hint: '账号与使用情况', icon: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z' },
  { value: 'users', label: '用户管理', hint: '账号与访问权限', icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0' },
  { value: 'audit', label: '操作审计', hint: '追踪权限变更', icon: 'M9 3H5v18h14V3h-4M9 2h6v4H9zM8 11h8M8 15h5' },
  { value: 'market', label: '招聘同步', hint: '来源与候选审核', icon: 'M20 7h-8m8 0-3-3m3 3-3 3M4 17h8m-8 0 3-3m-3 3 3 3M4 11a8 8 0 0 1 13-6M20 13a8 8 0 0 1-13 6' },
] as const

function AdminIcon({ path }: { path: string }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={path} /></svg>
}

export default function AdminPage({ currentUserId, onExpired, initialTab = 'overview', onTabChange, navigate }: { currentUserId: string; onExpired: () => void; initialTab?: Tab; onTabChange?: (tab: Tab) => void; navigate?: (path: string) => void }) {
  const [tab, setTab] = useState<Tab>(initialTab)
  const [summary, setSummary] = useState<AdminSummary | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [audit, setAudit] = useState<AdminAudit[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [catalog, setCatalog] = useState<CatalogPage | null>(null)
  const [runs, setRuns] = useState<RunPage | null>(null)
  const [sourceCandidates, setSourceCandidates] = useState<Candidate[]>([])
  const [companyCandidates, setCompanyCandidates] = useState<Candidate[]>([])
  const [query, setQuery] = useState('')
  const userTable = useRef<HTMLDivElement>(null)
  const [userPage, setUserPage] = useState(1)
  const [userLimit, setUserLimit] = useState(20)
  const [userTotal, setUserTotal] = useState(0)
  const [userResultKey, setUserResultKey] = useState<string | null>(null)
  const userRequestKey = JSON.stringify([query, userPage, userLimit])
  const usersLoading = tab === 'users' && userResultKey !== userRequestKey
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)
  const [action, setAction] = useState('disable_user')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    const load = tab === 'overview' ? getAdminSummary().then(value => { if (active) setSummary(value) })
      : tab === 'users' ? listAdminUsers(query, userPage, userLimit).then(value => { if (active) { setUsers(value.items); setUserTotal(value.total) } })
        : tab === 'audit' ? listAdminAudit().then(value => { if (active) setAudit(value.items) })
          : Promise.all([listSources(), listCatalog(), listRuns(), listCandidates('discovery'), listCandidates('company-candidates')]).then(([sourceData, catalogData, runData, discovered, companies]) => { if (active) { setSources(sourceData.items); setCatalog(catalogData); setRuns(runData); setSourceCandidates(discovered.items); setCompanyCandidates(companies.items) } })
    load.then(() => { if (active) setError('') }).catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) } }).finally(() => { if (active && tab === 'users') setUserResultKey(userRequestKey) })
    return () => { active = false }
  }, [tab, query, userPage, userLimit, userRequestKey, reload, onExpired])

  async function execute(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await work() }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) }
    finally { setBusy(false) }
  }

  function changeUserPage(page: number) {
    setUserPage(page); setSelectedUser(null)
    if (userTable.current) userTable.current.scrollTop = 0
  }

  function changeAccess(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUser || reason.trim().length < 10) { setError('操作原因至少填写 10 个字符。'); return }
    const self = selectedUser.id === currentUserId
    if (!window.confirm(`确定对 ${selectedUser.username} 执行此操作？`)) return
    void execute(async () => { const value = await changeAdminAccess(selectedUser, action, reason.trim(), self); setUsers(current => current.map(user => user.id === value.user.id ? value.user : user)); setSelectedUser(null); setReason(''); setNotice('账号权限已更新，审计事件已记录。') })
  }

  function review(kind: 'discovery' | 'company-candidates', item: Candidate, decision: 'approve' | 'ignore') {
    if (!window.confirm(`${decision === 'approve' ? '批准' : '忽略'}候选「${item.companyName}」？`)) return
    void execute(async () => { await reviewCandidate(kind, item.id, decision); setReload(value => value + 1); setNotice('候选审核已保存。') })
  }

  const headings: Record<Tab, { title: string; description: string }> = { overview: { title: '运营概览', description: '查看账号状态和系统使用情况。' }, users: { title: '账号目录', description: '检索账号、核对访问状态并查看只读档案。' }, audit: { title: '操作审计', description: '查看不可修改的账号访问操作记录。' }, market: { title: '来源与同步', description: '管理合规来源、检查同步健康度并处理待审核入口。' } }
  function changeTab(value: Tab) {
    if (onTabChange) onTabChange(value)
    else setTab(value)
  }

  return <section className={`admin-layout admin-view-${tab}`} aria-label="管理员后台">
    <aside className="admin-side">
      <div className="admin-side-brand"><span className="admin-side-mark"><AdminIcon path="M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4ZM8 12l3 3 5-6" /></span><div><strong>管理工作台</strong><span>JobTrace Admin</span></div></div>
      <p className="admin-side-caption">工作空间</p>
      <div className="segmented-tabs admin-nav" role="group" aria-label="管理栏目">{sections.map(item => <button key={item.value} aria-pressed={tab === item.value} onClick={() => changeTab(item.value)}><AdminIcon path={item.icon} /><span>{item.label}<small aria-hidden="true">{item.hint}</small></span><span className="admin-nav-arrow" aria-hidden="true">›</span></button>)}</div>
      <div className="admin-side-note"><span className="admin-side-note-dot" /><div><strong>管理员工作空间</strong><p>权限变更将记录在操作审计中。</p></div></div>
    </aside>
    <div className="admin-content"><header className="admin-page-header"><div><p className="admin-breadcrumb">管理后台<span aria-hidden="true">/</span>{headings[tab].title}</p><h1>{headings[tab].title}</h1><p>{headings[tab].description}</p></div><span className="admin-workspace-badge"><AdminIcon path="M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4ZM8 12l3 3 5-6" />管理员工作台</span></header>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {tab === 'overview' && (!summary ? <p className="admin-empty" role="status">正在加载管理总览…</p> : <>
      <div className="metric-grid admin-summary-strip">{[
        { label: '用户总数', value: summary.counts.value?.users, icon: sections[1].icon },
        { label: '活跃用户', value: summary.counts.value?.activeUsers, icon: 'M3 12h4l3-8 4 16 3-8h4' },
        { label: '管理员', value: summary.counts.value?.administrators, icon: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4ZM8 12l3 3 5-6' },
        { label: '投递记录', value: summary.counts.value?.applications, icon: 'M8 6V3h8v3M3 6h18v15H3zM3 12h18M10 12v3h4v-3' },
        { label: '面经记录', value: summary.counts.value?.interviews, icon: 'M4 3h16v14H9l-5 4V3ZM8 7h8M8 11h5' },
        { label: '停用账号', value: summary.counts.value?.disabledUsers, icon: 'M9 11V7a3 3 0 0 1 6 0v4M5 11h14v10H5zM12 15v2' },
      ].map(metric => <article key={metric.label}><div className="admin-metric-top"><span>{metric.label}</span><span className="admin-metric-icon"><AdminIcon path={metric.icon} /></span></div><strong>{metric.value?.toLocaleString('zh-CN') ?? '—'}</strong></article>)}</div>
      <div className="admin-overview-bottom"><section className="content-card admin-activity-card"><div className="admin-card-heading"><div><h2>最近活跃</h2><p>注册与访问情况</p></div><span className="admin-card-label">近 7 / 30 天</span></div>{summary.activity.status !== 'available' ? <p className="admin-empty">活跃数据暂不可用。</p> : <div className="admin-activity-grid"><div><span>近 7 天注册</span><strong>{summary.activity.windows?.registered7d ?? 0}</strong></div><div><span>近 7 天活跃</span><strong>{summary.activity.windows?.active7d ?? 0}</strong></div><div><span>近 30 天注册</span><strong>{summary.activity.windows?.registered30d ?? 0}</strong></div><div><span>近 30 天活跃</span><strong>{summary.activity.windows?.active30d ?? 0}</strong></div></div>}</section>
      <section className="content-card admin-shortcuts"><div className="admin-card-heading"><div><h2>常用操作</h2><p>继续处理管理事项</p></div></div>{sections.slice(1).map(item => <button key={item.value} aria-label={`前往${item.label}`} onClick={() => changeTab(item.value)}><AdminIcon path={item.icon} /><span>{item.label}<small aria-hidden="true">{item.hint}</small></span><span aria-hidden="true">↗</span></button>)}</section></div>
    </>)}
    {tab === 'users' && <><div className="filter-bar admin-user-filter"><label>搜索用户<input value={query} onChange={event => { setQuery(event.target.value); changeUserPage(1) }} placeholder="用户名或邮箱" /></label><span className="admin-result-count" aria-live="polite">共 <strong>{userTotal}</strong> 位用户 · 本页 {users.length} 位</span></div><div ref={userTable} className="admin-user-table-wrap" role="region" aria-label="用户列表" tabIndex={0} aria-busy={usersLoading}>{usersLoading && <p className="admin-users-loading" role="status">正在加载用户…</p>}<table className="admin-user-table"><caption className="sr-only">用户账号与访问权限</caption><thead><tr><th>用户</th><th>身份</th><th>状态</th><th>投递 / 面经</th><th>最近登录</th><th>操作</th></tr></thead><tbody>{users.map(user => <tr key={user.id}><td data-label="用户"><div className="admin-table-identity"><span className="admin-table-avatar" aria-hidden="true">{user.username.slice(0, 1).toUpperCase()}</span><div><strong>{user.username}</strong><small>{user.email ?? user.internalEmail ?? '未绑定邮箱'}</small></div></div></td><td data-label="身份"><span className={user.role === 'ADMIN' ? 'admin-role admin-role-elevated' : 'admin-role'}>{user.role === 'ADMIN' ? '管理员' : '普通用户'}</span></td><td data-label="状态"><span className={user.disabled ? 'admin-status-disabled' : 'admin-status-active'}>{user.disabled ? '已停用' : '正常'}</span></td><td data-label="投递 / 面经">{user.applicationCount} / {user.interviewCount}</td><td data-label="最近登录">{user.lastSignInAt ? new Date(user.lastSignInAt).toLocaleDateString('zh-CN') : '—'}</td><td data-label="操作"><div className="admin-user-actions">{navigate && <button disabled={usersLoading || busy} className="quiet-button" onClick={() => navigate(`/admin/users/${encodeURIComponent(user.id)}`)}>查看档案</button>}<button disabled={usersLoading || busy} className="quiet-button" onClick={() => { setSelectedUser(user); setAction(user.disabled ? 'enable_user' : 'disable_user') }}>管理访问</button></div></td></tr>)}</tbody></table>{!usersLoading && users.length === 0 && <p className="muted-note admin-empty">暂无匹配用户。</p>}</div><div className="admin-user-pagination"><label className="admin-user-page-size">每页<select aria-label="每页用户数" disabled={usersLoading} value={userLimit} onChange={event => { setUserLimit(Number(event.target.value)); changeUserPage(1) }}><option value={20}>20 位</option><option value={50}>50 位</option><option value={100}>100 位</option></select></label><fieldset disabled={usersLoading}><Pagination page={userPage} totalPages={Math.max(1, Math.ceil(userTotal / userLimit))} label="用户列表分页" onChange={changeUserPage} /></fieldset></div>{selectedUser && <form aria-label="管理访问权限" className="content-card admin-access-form" onSubmit={changeAccess}><div className="admin-card-heading"><div><p className="admin-card-label">账号访问设置</p><h2>管理 {selectedUser.username} 的访问权限</h2></div></div><p className="admin-form-hint">请确认操作对象并填写原因，变更后将保留审计记录。</p><label>操作<select value={action} onChange={event => setAction(event.target.value)}><option value="disable_user">停用账号</option><option value="enable_user">启用账号</option><option value="promote_admin">设为管理员</option><option value="demote_admin">取消管理员</option></select></label><label>原因（至少 10 字）<textarea minLength={10} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} required /></label><div className="form-actions"><button disabled={busy} type="submit">确认变更</button><button type="button" className="quiet-button" onClick={() => setSelectedUser(null)}>取消</button></div></form>}</>}
    {tab === 'audit' && <section className="content-card admin-audit-card"><div className="admin-card-heading"><div><h2>审计记录</h2><p>保留每一次账号访问变更</p></div><span className="admin-card-label">只读记录</span></div>{audit.length === 0 ? <p className="muted-note admin-empty">暂无审计记录。</p> : <ul className="insight-list">{audit.map(item => <li key={item.id}><strong>{item.actorIdentifier} → {item.targetIdentifier}</strong><span>{item.eventType} · {item.outcome} · {new Date(item.createdAt).toLocaleString('zh-CN')}</span>{item.reason && <span>{item.reason}</span>}</li>)}</ul>}</section>}
    {tab === 'market' && <><div className="content-card admin-market-notice"><AdminIcon path="M12 8v4m0 4h.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18" /><div><h2>招聘来源管理</h2><p>查看来源状态与同步记录，审核待收录的招聘入口。</p><small>岗位采集及同步执行仍由原有服务负责。</small></div></div><div className="analytics-grid"><section className="content-card"><h2>已收录公司 · {catalog?.total ?? '—'}</h2>{catalog?.items.length === 0 && <p className="admin-empty">暂无收录公司。</p>}<div className="admin-market-scroll" role="region" aria-label="已收录公司列表" tabIndex={0}><ul className="insight-list">{catalog?.items.map(item => <li key={item.identityKey}><strong>{item.companyName}</strong><span>{item.channelLabel} · {item.adapter}</span></li>)}</ul></div></section><section className="content-card admin-source-card"><div className="admin-card-heading"><h2>来源状态</h2><span className="admin-card-label">共 {sources.length} 个来源</span></div>{sources.length === 0 && <p className="admin-empty">暂无招聘来源。</p>}<div className="admin-source-scroll admin-market-scroll" role="region" aria-label="来源状态列表" tabIndex={0}><ul className="insight-list admin-source-list">{sources.map(item => <li key={item.id}><strong>{item.company.name}</strong><span>{item.adapter} · {item.status} · 连续失败 {item.consecutiveFailures}</span><span className="reminder-actions"><button disabled={busy || item.status === 'active'} onClick={() => void execute(async () => { await updateSource(item.id, 'active'); setReload(value => value + 1); setNotice('来源状态已更新。') })}>启用</button><button disabled={busy || item.status === 'paused'} onClick={() => void execute(async () => { await updateSource(item.id, 'paused'); setReload(value => value + 1); setNotice('来源状态已更新。') })}>暂停</button></span></li>)}</ul></div></section></div><div className="analytics-grid"><section className="content-card"><h2>待审来源 · {sourceCandidates.length}</h2>{sourceCandidates.length === 0 && <p className="admin-empty">暂无待审来源。</p>}<div className="admin-market-scroll" role="region" aria-label="待审来源列表" tabIndex={0}><ul className="insight-list">{sourceCandidates.map(item => <li key={item.id}><strong>{item.companyName}</strong><span>{item.entryUrl} · {item.diagnosticCode ?? '待审核'}</span><span className="reminder-actions"><button disabled={busy} onClick={() => review('discovery', item, 'approve')}>批准</button><button disabled={busy} onClick={() => review('discovery', item, 'ignore')}>忽略</button></span></li>)}</ul></div></section><section className="content-card"><h2>待审公司 · {companyCandidates.length}</h2>{companyCandidates.length === 0 && <p className="admin-empty">暂无待审公司。</p>}<div className="admin-market-scroll" role="region" aria-label="待审公司列表" tabIndex={0}><ul className="insight-list">{companyCandidates.map(item => <li key={item.id}><strong>{item.companyName}</strong><span>{item.articleUrl} · {item.detectedAdapter ?? '待识别'}</span><span className="reminder-actions"><button disabled={busy} onClick={() => review('company-candidates', item, 'approve')}>批准</button><button disabled={busy} onClick={() => review('company-candidates', item, 'ignore')}>忽略</button></span></li>)}</ul></div></section></div><section className="content-card"><h2>同步运行记录</h2>{runs?.items.length === 0 ? <p className="muted-note admin-empty">暂无记录。</p> : <div className="admin-market-scroll" role="region" aria-label="同步运行记录列表" tabIndex={0}><ul className="insight-list">{runs?.items.map(item => <li key={item.id}><strong>{item.status} · {new Date(item.startedAt).toLocaleString('zh-CN')}</strong><span>新增 {item.counts?.created ?? 0} · 更新 {item.counts?.updated ?? 0} · 拒绝 {item.counts?.rejected ?? 0}</span>{item.errorSummary && <span>{item.errorSummary}</span>}</li>)}</ul></div>}</section></>}
    </div>
  </section>
}
