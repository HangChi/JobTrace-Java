import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { listApplications } from './api'
import type { ApplicationPage } from './api'

const statuses: Record<string, string> = { submitted: '已投递', offer: '已录用', refused: '未通过' }

export default function ApplicationList({ search, navigate, onExpired }: { search: string; navigate: (path: string) => void; onExpired: () => void }) {
  const params = new URLSearchParams(search)
  const [query, setQuery] = useState(params.get('q') ?? '')
  const [status, setStatus] = useState(params.get('status') ?? '')
  const [page, setPage] = useState<ApplicationPage | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    listApplications(new URLSearchParams(search)).then(result => {
      if (active) { setPage(result); setError('') }
    }).catch(caught => {
      if (!active) return
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(caught instanceof ApiError ? caught.message : '暂时无法加载投递记录。')
    })
    return () => { active = false }
  }, [search, reload, onExpired])

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (query.trim()) next.set('q', query.trim())
    if (status) next.set('status', status)
    navigate(`/applications${next.size ? `?${next}` : ''}`)
  }

  return <section className="applications-content">
    <form className="filter-bar" onSubmit={filter}>
      <label>搜索投递<input value={query} onChange={event => setQuery(event.target.value)} placeholder="公司或岗位" /></label>
      <label>状态<select value={status} onChange={event => setStatus(event.target.value)}><option value="">全部状态</option><option value="submitted">已投递</option><option value="offer">已录用</option><option value="refused">未通过</option></select></label>
      <button type="submit">筛选</button>
    </form>
    {error ? <div className="content-card"><p role="alert">{error}</p><button onClick={() => { setPage(null); setReload(value => value + 1) }}>重试加载</button></div> :
      !page ? <p role="status">正在加载投递记录…</p> : page.items.length === 0 ? <div className="content-card"><h2>还没有投递记录</h2><p>调整筛选条件，或开始记录你的下一次投递。</p></div> : <>
        <p className="result-count" role="status">共 {page.total} 条投递</p>
        <div className="application-list">{page.items.map(item => <article key={item.id} className="application-card"><div><h2>{item.companyName}</h2><p>{item.positionName}{item.city ? ` · ${item.city}` : ''}</p></div><div className="application-meta"><span className={`status-pill status-${item.status}`}>{statuses[item.status] ?? item.status}</span><time dateTime={item.appliedDate}>{item.appliedDate}</time>{item.needsFollowUp && <span className="follow-up">{item.followUpReason || '待跟进'}</span>}</div></article>)}</div>
        {page.nextCursor && <button className="secondary-action" onClick={() => { const next = new URLSearchParams(search); next.set('cursor', page.nextCursor!); navigate(`/applications?${next}`) }}>下一页</button>}
      </>}
  </section>
}
