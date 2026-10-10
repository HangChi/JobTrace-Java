import { useEffect, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { getAdminUser } from './api'
import type { AdminUserDetail } from './api'

export default function AdminUserDetailPage({ id, navigate, onExpired }: { id: string; navigate: (path: string) => void; onExpired: () => void }) {
  const [user, setUser] = useState<AdminUserDetail | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    getAdminUser(id).then(value => { if (active) { setUser(value); setError('') } })
      .catch(caught => { if (!active) return; if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(caught instanceof ApiError ? caught.message : '用户档案暂时无法加载。') })
    return () => { active = false }
  }, [id, onExpired, reload])

  return <section className="admin-user-detail-page">
    <button className="quiet-button" onClick={() => navigate('/admin/users')}>← 返回用户目录</button>
    {error && <div className="content-card"><h1>{error === '用户档案暂时无法加载。' ? '档案无法加载' : '无法打开用户档案'}</h1><p role="alert">{error}</p><button className="quiet-button" onClick={() => setReload(value => value + 1)}>重试</button></div>}
    {!user && !error && <p role="status">正在加载用户档案…</p>}
    {user && <>
      <header className="content-card admin-user-hero"><div><p className="eyebrow">账号目录 · 只读档案</p><h1>{user.username}</h1><p>{user.email ?? user.internalEmail ?? '未绑定邮箱'}</p></div><span className={user.disabled ? 'status-pill status-refused' : 'status-pill'}>{user.disabled ? '已停用' : '正常'}</span></header>
      <dl className="admin-user-facts"><div><dt>账号角色</dt><dd>{user.role === 'ADMIN' ? '管理员' : '普通用户'}</dd></div><div><dt>投递记录</dt><dd>{user.applicationCount}</dd></div><div><dt>面经记录</dt><dd>{user.interviewCount}</dd></div><div><dt>最近登录</dt><dd>{user.lastSignInAt ? new Date(user.lastSignInAt).toLocaleString('zh-CN') : '暂无记录'}</dd></div></dl>
      <section className="content-card"><h2>投递记录</h2>{user.applications.items.length === 0 ? <p className="muted-note">暂无投递记录。</p> : <ul className="admin-detail-list">{user.applications.items.map(item => <li key={item.id}><div><strong>{item.companyName}</strong><span>{item.positionName}{item.city ? ` · ${item.city}` : ''}</span></div><small>{item.appliedDate} · {item.status}</small><details><summary>查看投递内容</summary><p>{item.notes || '未填写备注。'}</p>{item.stages.length > 0 && <ol>{item.stages.map((stage, index) => <li key={`${stage.stage}-${index}`}>{stage.stage} · {stage.occurredOn}</li>)}</ol>}</details></li>)}</ul>}</section>
      <section className="content-card"><h2>面经记录</h2>{user.interviews.items.length === 0 ? <p className="muted-note">暂无面经记录。</p> : <ul className="admin-detail-list">{user.interviews.items.map(item => <li key={item.id}><div><strong>{item.companyName}</strong><span>{item.positionName} · {item.stage}</span></div><small>{item.interviewedOn} · {item.status}</small><details><summary>查看面经内容</summary><p>{item.highlights || '未填写亮点。'}</p><p>问题 {item.questions.length} 个</p></details></li>)}</ul>}</section>
      <section className="content-card"><h2>近期操作</h2>{user.recentAuditEvents.length === 0 ? <p className="muted-note">暂无相关审计记录。</p> : <ul className="insight-list">{user.recentAuditEvents.map(item => <li key={item.id}><strong>{item.eventType}</strong><span>{item.outcome} · {new Date(item.createdAt).toLocaleString('zh-CN')}</span></li>)}</ul>}</section>
    </>}
  </section>
}
