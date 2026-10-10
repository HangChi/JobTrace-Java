import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { getAnalyticsSummary } from '../analytics/api'
import type { AnalyticsSummary } from '../analytics/api'
import { deleteApplication, listApplications, updateApplicationStatus } from './api'
import type { ApplicationPage, ApplicationSummary } from './api'
import ApplicationEditor from './ApplicationEditor'
import ApplicationOverview from './ApplicationOverview'

const types: Record<string, string> = { summer_internship: '暑期实习', daily_internship: '日常实习', spring_recruitment: '春招', early_campus_recruitment: '秋招提前批', campus_recruitment: '秋招', social_recruitment: '社招' }
const stages: Record<string, string> = { screening: '简历筛选', assessment: '测评/AI测评', written_test: '笔试', interview_1: '一面/AI面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }
const pageSizes = [10, 20, 50, 100]

function safeJobUrl(value: string | null) {
  if (!value) return null
  try { return /^https?:$/.test(new URL(value).protocol) ? value : null } catch { return null }
}

function jobLinkLabel(value: string) {
  try { return new URL(value).hostname.replace(/^www\./, '') } catch { return '职位链接' }
}

export default function ApplicationList({ search, navigate, onExpired, routeMode = false, initialEditingId }: { search: string; navigate: (path: string) => void; onExpired: () => void; routeMode?: boolean; initialEditingId?: string | null }) {
  const params = new URLSearchParams(search)
  const [query, setQuery] = useState(params.get('q') ?? '')
  const [status, setStatus] = useState(params.get('status') ?? '')
  const [type, setType] = useState(params.get('type') ?? '')
  const [sort, setSort] = useState(params.get('sort') ?? '')
  const [page, setPage] = useState<ApplicationPage | null>(null)
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [editingId, setEditingId] = useState<string | null | undefined>(initialEditingId)
  const requestedLimit = Number(params.get('limit') ?? 10)
  const pageSize = pageSizes.includes(requestedLimit) ? requestedLimit : 10

  useEffect(() => {
    let active = true
    const request = new URLSearchParams(search)
    if (!request.has('limit')) request.set('limit', '10')
    listApplications(request).then(result => {
      if (active) { setPage(result); setError('') }
    }).catch(caught => {
      if (!active) return
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(caught instanceof ApiError ? caught.message : '暂时无法加载投递记录。')
    })
    return () => { active = false }
  }, [search, reload, onExpired])

  useEffect(() => {
    if (routeMode && initialEditingId !== undefined) return
    let active = true
    getAnalyticsSummary().then(result => {
      if (active && typeof result?.total === 'number' && Array.isArray(result.followUps) && result.stageDistribution) setSummary(result)
    }).catch(caught => {
      if (active && caught instanceof ApiError && caught.status === 401) onExpired()
    })
    return () => { active = false }
  }, [reload, onExpired, routeMode, initialEditingId])

  function goTo(next: URLSearchParams) {
    navigate(`/applications${next.size ? `?${next}` : ''}`)
  }

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (query.trim()) next.set('q', query.trim())
    if (status) next.set('status', status)
    if (type) next.set('type', type)
    if (sort) {
      next.set('sort', sort)
      next.set('direction', sort === params.get('sort') && params.get('direction') === 'asc' ? 'desc' : 'asc')
    }
    if (pageSize !== 10) next.set('limit', String(pageSize))
    goTo(next)
  }

  function showItem(item: ApplicationSummary) {
    if (routeMode) navigate(`/applications/${encodeURIComponent(item.id)}`)
    else setEditingId(item.id)
  }

  async function changeStatus(item: ApplicationSummary, nextStatus: ApplicationSummary['status']) {
    if (item.status === nextStatus || pendingId) return
    setPendingId(item.id)
    setActionError('')
    try {
      const updated = await updateApplicationStatus(item, nextStatus)
      setPage(current => current ? { ...current, items: current.items.map(row => row.id === item.id ? { ...row, status: updated.status, latestDate: updated.latestDate, version: updated.version } : row) } : current)
      setReload(value => value + 1)
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setActionError(caught instanceof ApiError ? caught.message : '状态更新失败，请重试。')
    } finally { setPendingId(null) }
  }

  async function removeItem(item: ApplicationSummary) {
    if (!window.confirm(`确定删除 ${item.companyName} · ${item.positionName} 的投递记录？`)) return
    setPendingId(item.id)
    setActionError('')
    try {
      await deleteApplication(item.id)
      setPage(current => current ? { ...current, total: Math.max(0, current.total - 1), items: current.items.filter(row => row.id !== item.id) } : current)
      setReload(value => value + 1)
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setActionError(caught instanceof ApiError ? caught.message : '删除失败，请重试。')
    } finally { setPendingId(null) }
  }

  if (routeMode && initialEditingId !== undefined) {
    const prefill = new URLSearchParams(search)
    const jobUrl = prefill.get('jobUrl') ?? ''
    return <section className="applications-content application-detail-view"><a className="back-link" href="/applications" onClick={event => { event.preventDefault(); navigate('/applications') }}>← 返回投递记录</a><h1>{initialEditingId === null ? '新增投递' : '投递详情'}</h1><ApplicationEditor key={initialEditingId ?? 'new'} id={initialEditingId} initialDraft={initialEditingId === null ? { companyName: (prefill.get('company') ?? '').slice(0, 200), positionName: (prefill.get('position') ?? '').slice(0, 200), city: (prefill.get('city') ?? '').slice(0, 100), jobUrl: /^https:\/\//i.test(jobUrl) ? jobUrl.slice(0, 2048) : '' } : undefined} onClose={() => navigate('/applications')} onChanged={() => setReload(value => value + 1)} onExpired={onExpired} /></section>
  }

  const totalPages = page ? Math.max(1, Math.ceil(page.total / page.limit)) : 1
  const currentPage = page?.page ?? 1
  const filtered = Boolean(params.get('q') || params.get('status') || params.get('type'))
  const exportQuery = new URLSearchParams(search)
  exportQuery.delete('cursor')
  exportQuery.delete('page')
  exportQuery.delete('limit')
  exportQuery.set('scope', 'filtered')

  return <section className="applications-content application-dashboard">
    <div className="application-dashboard-actions"><button type="button" onClick={() => routeMode ? navigate('/applications/new') : setEditingId(null)}>新建投递</button><details className="application-export-menu"><summary>导出⌄</summary><div><p>选择导出格式</p><a href={`/api/exports/applications?${exportQuery}&format=xlsx`}>Excel 工作簿 <small>.xlsx</small></a><a href={`/api/exports/applications?${exportQuery}&format=csv`}>CSV 文件 <small>.csv</small></a></div></details></div>
    {editingId !== undefined && <ApplicationEditor key={editingId ?? 'new'} id={editingId} onClose={() => setEditingId(undefined)} onChanged={() => setReload(value => value + 1)} onExpired={onExpired} />}
    <ApplicationOverview summary={summary} onOpen={id => routeMode ? navigate(`/applications/${encodeURIComponent(id)}`) : setEditingId(id)} />
    <form className="application-filters" aria-label="筛选投递记录" onSubmit={filter}>
      <label>搜索投递<input value={query} maxLength={200} onChange={event => setQuery(event.target.value)} placeholder="例如：产品经理" /></label>
      <label>状态<select value={status} onChange={event => setStatus(event.target.value)}><option value="">全部状态</option><option value="submitted">已投递</option><option value="offer">Offer</option><option value="refused">拒绝</option></select></label>
      <label>类型<select value={type} onChange={event => setType(event.target.value)}><option value="">全部类型</option>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>排序<select value={sort} onChange={event => setSort(event.target.value)}><option value="">默认排序（已投递优先）</option><option value="latestDate">最新日期</option><option value="appliedDate">投递日期</option><option value="company">公司名称</option><option value="position">岗位名称</option></select></label>
      <div className="application-filter-actions"><button type="submit">筛选</button><button type="button" onClick={() => goTo(new URLSearchParams())}>清空</button></div>
    </form>
    {actionError && <p className="error" role="alert">{actionError}</p>}
    {error ? <div className="content-card"><p role="alert">{error}</p><button onClick={() => { setPage(null); setReload(value => value + 1) }}>重试加载</button></div> :
      !page ? <p role="status">正在加载投递记录…</p> : page.items.length === 0 ? <div className="content-card application-empty"><h2>还没有投递记录</h2><p>{filtered ? '没有符合当前条件的投递，试试清空筛选。' : '开始记录你的下一次投递。'}</p><button type="button" onClick={() => filtered ? goTo(new URLSearchParams()) : routeMode ? navigate('/applications/new') : setEditingId(null)}>{filtered ? '清空筛选' : '新建投递'}</button></div> :
      <section className="application-list-panel" aria-labelledby="application-list-title"><div className="application-list-heading"><div><h2 id="application-list-title">投递记录</h2><p>点击公司与岗位可查看详情</p></div><span className="application-record-count">共 {page.total} 条</span></div>
        <div className="application-table-wrap"><table className="application-table"><thead><tr><th scope="col">公司与岗位</th><th scope="col">投递链接</th><th scope="col">类型</th><th scope="col">阶段</th><th scope="col">状态</th><th scope="col">投递日期</th><th scope="col">最新日期</th><th scope="col">操作</th></tr></thead><tbody>{page.items.map(item => {
          const link = safeJobUrl(item.jobUrl)
          const stage = item.stages.at(-1)
          return <tr key={item.id}>
            <td data-label="公司与岗位"><button type="button" className="application-name-button" onClick={() => showItem(item)}><strong>{item.companyName}{item.city ? ` · ${item.city}` : ''}</strong><span>{item.positionName}</span></button></td>
            <td data-label="投递链接">{link ? <a className="application-job-link" href={link} target="_blank" rel="noopener noreferrer">{jobLinkLabel(link)} <span aria-hidden="true">↗</span></a> : <span className="application-muted">未填写</span>}</td>
            <td data-label="类型"><span className={`application-type-pill type-${item.type}`}>{types[item.type] ?? item.type}</span></td>
            <td data-label="阶段"><span className={`application-stage-pill ${stage ? `stage-${stage}` : 'stage-none'}`}><i aria-hidden="true" />{stage ? stages[stage] ?? stage : '尚未开始'}</span></td>
            <td data-label="状态"><select className={`application-status-select status-${item.status}`} aria-label={`更新 ${item.companyName} ${item.positionName} 的状态`} value={item.status} disabled={pendingId === item.id} onChange={event => void changeStatus(item, event.target.value as ApplicationSummary['status'])}><option value="submitted">已投递</option><option value="offer">Offer</option><option value="refused">拒绝</option></select>{item.needsFollowUp && <small className="follow-up">{item.followUpReason === 'timeline' ? `时间线 ${item.followUpDays} 天未更新` : `投递记录 ${item.followUpDays} 天未更新`}</small>}</td>
            <td data-label="投递日期"><time dateTime={item.appliedDate}>{item.appliedDate}</time></td><td data-label="最新日期"><time dateTime={item.latestDate ?? item.appliedDate}>{item.latestDate ?? item.appliedDate}</time></td>
            <td data-label="操作"><div className="application-row-actions"><button type="button" disabled={pendingId === item.id} onClick={() => showItem(item)}>编辑</button><button type="button" disabled={pendingId === item.id} onClick={() => void removeItem(item)}>删除</button></div></td>
          </tr>
        })}</tbody></table></div>
        <div className="application-pagination"><label>每页显示<select aria-label="每页显示" value={pageSize} onChange={event => { const next = new URLSearchParams(search); next.delete('page'); next.delete('cursor'); next.set('limit', event.target.value); goTo(next) }}>{pageSizes.map(size => <option key={size} value={size}>{size} 条</option>)}</select></label><span>第 {currentPage} / {totalPages} 页</span><div><button type="button" disabled={currentPage <= 1} onClick={() => { const next = new URLSearchParams(search); next.set('page', String(currentPage - 1)); next.delete('cursor'); goTo(next) }}>上一页</button><button type="button" disabled={currentPage >= totalPages} onClick={() => { const next = new URLSearchParams(search); next.set('page', String(currentPage + 1)); next.delete('cursor'); goTo(next) }}>下一页</button></div></div>
      </section>}
  </section>
}
