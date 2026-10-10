import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, apiRequest } from '../../shared/api/client'

type Campaign = {
  id: string
  listingKind: 'synced_jobs' | 'recruitment_directory'
  company: { name: string; type: string | null; industry: string | null }
  recruitmentType: string | null
  positions: string[]
  positionCount: number
  locations: { name: string }[]
  status: 'open' | 'stale' | 'closed'
  primaryApplyUrl: string | null
  source: { name: string; url: string | null }
  publishedAt: string | null
  lastConfirmedAt: string | null
  isFavorite: boolean
}
type CampaignPage = { items: Campaign[]; page: number; limit: number; total: number }
const labels = { open: '在招', stale: '待确认', closed: '已失效' }
function safeExternalUrl(value: string | null): string | null {
  if (!value) return null
  try { const url = new URL(value); return url.protocol === 'https:' ? url.href : null }
  catch { return null }
}

export default function JobMarketPage({ search, navigate, onExpired }: { search: string; navigate: (path: string) => void; onExpired: () => void }) {
  const params = new URLSearchParams(search)
  const [query, setQuery] = useState(params.get('q') ?? '')
  const [location, setLocation] = useState(params.get('location') ?? '')
  const [status, setStatus] = useState(params.get('status') ?? '')
  const [postedFrom, setPostedFrom] = useState(params.get('postedFrom') ?? '')
  const [page, setPage] = useState<CampaignPage | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const favoriteOnly = params.get('favorite') === 'true'

  useEffect(() => {
    const controller = new AbortController()
    const allowed = new Set(['q', 'company', 'location', 'status', 'postedFrom', 'favorite', 'page', 'limit'])
    const request = new URLSearchParams()
    new URLSearchParams(search).forEach((value, key) => { if (allowed.has(key)) request.append(key, value) })
    apiRequest<CampaignPage>(`/api/job-market/campaigns${request.size ? `?${request}` : ''}`, { signal: controller.signal }).then(result => {
      if (!Array.isArray(result?.items) || typeof result.total !== 'number') throw new ApiError(200, 'invalid_response', '招聘数据格式异常，请重试。')
      setPage(result); setError('')
    }).catch(caught => {
      if (controller.signal.aborted) return
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(caught instanceof ApiError ? caught.message : '招聘数据暂时无法加载，请重试。')
    })
    return () => controller.abort()
  }, [search, reload, onExpired])

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (query.trim()) next.set('q', query.trim())
    if (location.trim()) next.set('location', location.trim())
    if (status) next.set('status', status)
    if (postedFrom) next.set('postedFrom', postedFrom)
    if (favoriteOnly) next.set('favorite', 'true')
    navigate(`/${next.size ? `?${next}` : ''}`)
  }
  function withParams(change: (params: URLSearchParams) => void) {
    const next = new URLSearchParams(search)
    change(next)
    navigate(`/${next.size ? `?${next}` : ''}`)
  }
  async function favorite(item: Campaign) {
    if (busy) return
    setBusy(item.id); setError('')
    try {
      const result = await apiRequest<{ isFavorite: boolean }>(`/api/job-market/campaigns/${encodeURIComponent(item.id)}/favorite`, { method: item.isFavorite ? 'DELETE' : 'PUT' })
      setPage(current => current ? { ...current, items: current.items.map(value => value.id === item.id ? { ...value, isFavorite: result.isFavorite } : value) } : current)
      if (favoriteOnly && !result.isFavorite) setReload(value => value + 1)
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(caught instanceof ApiError ? caught.message : '收藏未保存，请重试。')
    } finally { setBusy(null) }
  }

  function track(item: Campaign, url: string | null) {
    const next = new URLSearchParams()
    next.set('company', item.company.name)
    if (item.positions[0]) next.set('position', item.positions[0])
    if (item.locations[0]) next.set('city', item.locations[0].name)
    if (url) next.set('jobUrl', url)
    navigate(`/applications/new?${next}`)
  }

  const totalPages = page ? Math.max(1, Math.ceil(page.total / page.limit)) : 1
  return <section className="job-market-page">
    <header className="market-hero"><div><p className="eyebrow">自动更新</p><h1>招聘广场</h1><p>集中查看企业岗位、招聘地点与官方投递入口。</p></div><div className="market-hero-actions"><span className="market-count">{page ? `共 ${page.total} 家招聘企业` : '正在统计招聘企业'}</span><button type="button" className={favoriteOnly ? 'market-favorite-filter is-active' : 'market-favorite-filter'} aria-pressed={favoriteOnly} onClick={() => withParams(next => { next.delete('page'); if (favoriteOnly) next.delete('favorite'); else next.set('favorite', 'true') })}>仅看收藏</button></div></header>
    <form className="market-filters" role="search" onSubmit={filter}><label>关键词<input value={query} onChange={event => setQuery(event.target.value)} placeholder="公司或岗位" /></label><label>地点<input value={location} onChange={event => setLocation(event.target.value)} placeholder="城市或远程" /></label><label>状态<select value={status} onChange={event => setStatus(event.target.value)}><option value="">全部</option><option value="open">有效</option><option value="stale">待确认</option><option value="closed">已失效</option></select></label><label>发布时间<input type="date" value={postedFrom} onChange={event => setPostedFrom(event.target.value)} /></label><div className="market-filter-actions"><button type="button" className="quiet-button" onClick={() => navigate('/')}>清除筛选</button><button type="submit">筛选</button></div></form>
    {error && <div className="content-card market-message"><p role="alert">{error}</p><button type="button" className="quiet-button" onClick={() => setReload(value => value + 1)}>重试加载</button></div>}
    {!page && !error && <p className="market-loading" role="status">正在加载招聘信息…</p>}
    {page && page.items.length === 0 && <div className="content-card market-message"><h2>没有找到符合条件的招聘信息</h2><p>试试调整关键词、地点或状态筛选。</p></div>}
    {page && page.items.length > 0 && <><div className="campaign-table-shell"><table className="campaign-table"><caption className="sr-only">企业招聘岗位、地点、来源与投递入口</caption><thead><tr><th scope="col">企业</th><th scope="col">岗位</th><th scope="col">地点</th><th scope="col">招聘渠道</th><th scope="col">操作</th></tr></thead><tbody>{page.items.map(item => {
      const directory = item.listingKind === 'recruitment_directory'
      const sourceUrl = safeExternalUrl(item.primaryApplyUrl ?? item.source.url)
      return <tr key={item.id}><th scope="row"><div className="market-company"><span className="market-company-mark" aria-hidden="true">{item.company.name.trim().slice(0, 1)}</span><span><strong>{item.company.name}</strong><small>{[item.company.type, item.company.industry].filter(Boolean).join(' · ')}</small></span><span className={`market-status status-${item.status}`}>{labels[item.status]}</span></div></th><td data-label="岗位"><strong>{directory ? item.recruitmentType || '招聘入口' : `${item.positionCount} 个岗位`}</strong><small>{directory ? '岗位以官方发布为准' : item.positions.slice(0, 2).join(' · ') || '岗位未提供'}</small></td><td data-label="地点"><strong>{directory ? '以招聘原文为准' : `${item.locations.length} 个地点`}</strong><small>{item.locations.slice(0, 2).map(value => value.name).join(' · ')}</small></td><td data-label="招聘渠道"><strong>{item.source.name || '官方渠道'}</strong><small>{item.publishedAt ? `发布 ${item.publishedAt.slice(0, 10)}` : item.lastConfirmedAt ? `更新 ${item.lastConfirmedAt.slice(0, 10)}` : '暂无更新日期'}</small></td><td data-label="操作"><div className="market-row-actions"><button type="button" className="market-save" disabled={busy === item.id} aria-label={item.isFavorite ? `取消收藏 ${item.company.name}` : `收藏 ${item.company.name}`} aria-pressed={item.isFavorite} onClick={() => void favorite(item)}>{item.isFavorite ? '已收藏' : '收藏'}</button>{!directory && <button type="button" onClick={() => track(item, sourceUrl)}>记录投递</button>}{sourceUrl ? <a href={sourceUrl} target="_blank" rel="noopener noreferrer">{directory ? '查看招聘原文' : '立即投递'} <span aria-hidden="true">↗</span></a> : <span className="muted-note">暂无入口</span>}</div></td></tr>
    })}</tbody></table></div>{totalPages > 1 && <nav className="market-pagination" aria-label="招聘记录分页"><span>{page.page} / {totalPages} 页</span><button type="button" className="quiet-button" disabled={page.page <= 1} onClick={() => withParams(next => { if (page.page <= 2) next.delete('page'); else next.set('page', String(page.page - 1)) })}>上一页</button><button type="button" className="quiet-button" disabled={page.page >= totalPages} onClick={() => withParams(next => next.set('page', String(page.page + 1)))}>下一页</button></nav>}</>}
  </section>
}
