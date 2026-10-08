import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { changeAdminAccess, getAdminSummary, listAdminAudit, listAdminUsers, listCandidates, listCatalog, listRuns, listSources, reviewCandidate, updateSource } from './api'
import type { AdminAudit, AdminSummary, AdminUser, Candidate, CatalogPage, RunPage, Source } from './api'

type Tab = 'overview' | 'users' | 'audit' | 'market'
const errorText = (error: unknown) => error instanceof ApiError ? error.message : '管理数据暂时无法加载。'

export default function AdminPage({ currentUserId, onExpired }: { currentUserId: string; onExpired: () => void }) {
  const [tab, setTab] = useState<Tab>('overview')
  const [summary, setSummary] = useState<AdminSummary | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [audit, setAudit] = useState<AdminAudit[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [catalog, setCatalog] = useState<CatalogPage | null>(null)
  const [runs, setRuns] = useState<RunPage | null>(null)
  const [sourceCandidates, setSourceCandidates] = useState<Candidate[]>([])
  const [companyCandidates, setCompanyCandidates] = useState<Candidate[]>([])
  const [query, setQuery] = useState('')
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
      : tab === 'users' ? listAdminUsers(query).then(value => { if (active) setUsers(value.items) })
        : tab === 'audit' ? listAdminAudit().then(value => { if (active) setAudit(value.items) })
          : Promise.all([listSources(), listCatalog(), listRuns(), listCandidates('discovery'), listCandidates('company-candidates')]).then(([sourceData, catalogData, runData, discovered, companies]) => { if (active) { setSources(sourceData.items); setCatalog(catalogData); setRuns(runData); setSourceCandidates(discovered.items); setCompanyCandidates(companies.items) } })
    load.then(() => { if (active) setError('') }).catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) } })
    return () => { active = false }
  }, [tab, query, reload, onExpired])

  async function execute(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await work() }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) }
    finally { setBusy(false) }
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

  return <section className="admin-content">
    <div className="segmented-tabs" role="group" aria-label="管理栏目">{([['overview', '总览'], ['users', '用户'], ['audit', '审计'], ['market', '岗位市场']] as const).map(([value, label]) => <button key={value} aria-pressed={tab === value} onClick={() => setTab(value)}>{label}</button>)}</div>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {tab === 'overview' && (!summary ? <p role="status">正在加载管理总览…</p> : <><div className="metric-grid"><article><span>用户</span><strong>{summary.counts.value?.users ?? '—'}</strong></article><article><span>活跃用户</span><strong>{summary.counts.value?.activeUsers ?? '—'}</strong></article><article><span>投递</span><strong>{summary.counts.value?.applications ?? '—'}</strong></article><article><span>面经</span><strong>{summary.counts.value?.interviews ?? '—'}</strong></article></div><section className="content-card"><h2>最近活跃</h2>{summary.activity.status !== 'available' ? <p>活跃数据暂不可用。</p> : <p>近 7 天注册 {summary.activity.windows?.registered7d ?? 0} 人，活跃 {summary.activity.windows?.active7d ?? 0} 人。</p>}</section></>)}
    {tab === 'users' && <><div className="filter-bar"><label>搜索用户<input value={query} onChange={event => setQuery(event.target.value)} placeholder="用户名或邮箱" /></label></div><div className="admin-user-list">{users.map(user => <article className="content-card" key={user.id}><div><h2>{user.username}</h2><p>{user.email ?? user.internalEmail ?? '未绑定邮箱'} · {user.role} · {user.disabled ? '已停用' : '正常'}</p><small>投递 {user.applicationCount} · 面经 {user.interviewCount} · 版本 {user.accessVersion}</small></div><button className="quiet-button" onClick={() => { setSelectedUser(user); setAction(user.disabled ? 'enable_user' : 'disable_user') }}>管理访问</button></article>)}</div>{selectedUser && <form className="content-card admin-access-form" onSubmit={changeAccess}><h2>管理 {selectedUser.username} 的访问权限</h2><label>操作<select value={action} onChange={event => setAction(event.target.value)}><option value="disable_user">停用账号</option><option value="enable_user">启用账号</option><option value="promote_admin">设为管理员</option><option value="demote_admin">取消管理员</option></select></label><label>原因（至少 10 字）<textarea minLength={10} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} required /></label><div className="form-actions"><button disabled={busy} type="submit">确认变更</button><button type="button" className="quiet-button" onClick={() => setSelectedUser(null)}>取消</button></div></form>}</>}
    {tab === 'audit' && <section className="content-card"><h2>审计记录</h2>{audit.length === 0 ? <p className="muted-note">暂无审计记录。</p> : <ul className="insight-list">{audit.map(item => <li key={item.id}><strong>{item.actorIdentifier} → {item.targetIdentifier}</strong><span>{item.eventType} · {item.outcome} · {new Date(item.createdAt).toLocaleString('zh-CN')}</span>{item.reason && <span>{item.reason}</span>}</li>)}</ul>}</section>}
    {tab === 'market' && <><div className="content-card"><h2>岗位市场数据归属</h2><p>Java 从共享远端数据库读取目录、来源和运行记录。岗位采集及同步执行仍由原有服务负责；这里不会启动 Java 爬取。</p></div><div className="analytics-grid"><section className="content-card"><h2>已收录公司 · {catalog?.total ?? '—'}</h2><ul className="insight-list">{catalog?.items.map(item => <li key={item.identityKey}><strong>{item.companyName}</strong><span>{item.channelLabel} · {item.adapter}</span></li>)}</ul></section><section className="content-card"><h2>来源状态</h2><ul className="insight-list">{sources.map(item => <li key={item.id}><strong>{item.company.name}</strong><span>{item.adapter} · {item.status} · 连续失败 {item.consecutiveFailures}</span><span className="reminder-actions"><button disabled={busy || item.status === 'active'} onClick={() => void execute(async () => { await updateSource(item.id, 'active'); setReload(value => value + 1); setNotice('来源状态已更新。') })}>启用</button><button disabled={busy || item.status === 'paused'} onClick={() => void execute(async () => { await updateSource(item.id, 'paused'); setReload(value => value + 1); setNotice('来源状态已更新。') })}>暂停</button></span></li>)}</ul></section></div><div className="analytics-grid"><section className="content-card"><h2>待审来源 · {sourceCandidates.length}</h2><ul className="insight-list">{sourceCandidates.map(item => <li key={item.id}><strong>{item.companyName}</strong><span>{item.entryUrl} · {item.diagnosticCode ?? '待审核'}</span><span className="reminder-actions"><button disabled={busy} onClick={() => review('discovery', item, 'approve')}>批准</button><button disabled={busy} onClick={() => review('discovery', item, 'ignore')}>忽略</button></span></li>)}</ul></section><section className="content-card"><h2>待审公司 · {companyCandidates.length}</h2><ul className="insight-list">{companyCandidates.map(item => <li key={item.id}><strong>{item.companyName}</strong><span>{item.articleUrl} · {item.detectedAdapter ?? '待识别'}</span><span className="reminder-actions"><button disabled={busy} onClick={() => review('company-candidates', item, 'approve')}>批准</button><button disabled={busy} onClick={() => review('company-candidates', item, 'ignore')}>忽略</button></span></li>)}</ul></section></div><section className="content-card"><h2>同步运行记录</h2>{runs?.items.length === 0 ? <p className="muted-note">暂无记录。</p> : <ul className="insight-list">{runs?.items.map(item => <li key={item.id}><strong>{item.status} · {new Date(item.startedAt).toLocaleString('zh-CN')}</strong><span>新增 {item.counts?.created ?? 0} · 更新 {item.counts?.updated ?? 0} · 拒绝 {item.counts?.rejected ?? 0}</span>{item.errorSummary && <span>{item.errorSummary}</span>}</li>)}</ul>}</section></>}
  </section>
}
