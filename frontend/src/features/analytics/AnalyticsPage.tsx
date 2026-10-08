import { useEffect, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { getAnalyticsReport, getAnalyticsSummary, resolveProgressReminder } from './api'
import type { AnalyticsReport, AnalyticsSummary } from './api'

const metricLabels: Record<string, string> = { applications: '投递数', interviewRate: '面试率', offerRate: 'Offer 率', medianDaysToFirstInterview: '首次面试中位天数', reviewCompletionRate: '面经完成率' }
const pct = new Set(['interviewRate', 'offerRate', 'reviewCompletionRate'])
const errorText = (error: unknown) => error instanceof ApiError ? error.message : '分析数据暂时无法加载。'

export default function AnalyticsPage({ onExpired }: { onExpired: () => void }) {
  const [period, setPeriod] = useState('90d')
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [report, setReport] = useState<AnalyticsReport | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([getAnalyticsSummary(), getAnalyticsReport(period)]).then(([overview, analysis]) => {
      if (active) { setSummary(overview); setReport(analysis); setError('') }
    }).catch(caught => {
      if (!active) return
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(errorText(caught))
    })
    return () => { active = false }
  }, [period, reload, onExpired])

  async function resolve(id: string, resolution: 'completed' | 'dismissed') {
    if (busy) return
    setBusy(id); setError('')
    try { await resolveProgressReminder(id, resolution); setReload(value => value + 1) }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) }
    finally { setBusy(null) }
  }

  const maxTrend = Math.max(1, ...(report?.trend.map(point => point.applications) ?? []))
  return <section className="analytics-content">
    <div className="feature-toolbar"><p>看见投递节奏与转化，不靠印象猜测进展。</p><div className="analytics-toolbar-actions"><label>报告周期<select value={period} onChange={event => { setPeriod(event.target.value); setReport(null) }}><option value="30d">近 30 天</option><option value="90d">近 90 天</option><option value="180d">近 180 天</option><option value="ytd">今年以来</option><option value="all">全部时间</option></select></label><a className="quiet-button" href="/api/exports/applications?scope=all&format=csv">导出投递 CSV</a></div></div>
    {error && <p className="error" role="alert">{error} <button className="quiet-button" onClick={() => setReload(value => value + 1)}>重试</button></p>}
    {!summary || !report ? !error && <p role="status">正在加载分析…</p> : <>
      <div className="metric-grid"><article><span>总投递</span><strong>{summary.total}</strong></article><article><span>仍在流程中</span><strong>{summary.submitted}</strong></article><article><span>Offer</span><strong>{summary.offers}</strong></article><article><span>本周新增</span><strong>{summary.addedThisWeek}</strong></article></div>
      <div className="analytics-grid"><section className="content-card"><h2>趋势</h2>{report.trend.length === 0 ? <p className="muted-note">所选周期暂无投递。</p> : <div className="trend-list">{report.trend.map(point => <div key={point.periodStart} className="trend-row"><span>{point.label}</span><div className="trend-track"><span style={{ width: `${point.applications / maxTrend * 100}%` }} /></div><strong>{point.applications}</strong></div>)}</div>}</section><section className="content-card"><h2>关键指标</h2><dl className="report-metrics">{Object.entries(report.metrics).map(([key, value]) => <div key={key}><dt>{metricLabels[key] ?? key}</dt><dd>{value.value === null ? '—' : `${value.value}${pct.has(key) ? '%' : ''}`}</dd></div>)}</dl></section></div>
      <section className="content-card analytics-wide"><h2>转化路径</h2><div className="milestone-list">{report.milestones.map(item => <article key={item.key}><strong>{item.count}</strong><span>{item.label}</span>{item.conversionFromPrevious !== null && <small>上一步转化 {item.conversionFromPrevious}%</small>}</article>)}</div></section>
      <div className="analytics-grid"><section className="content-card"><h2>招聘类型</h2><ul className="breakdown-list">{report.typeBreakdown.map(item => <li key={item.key}><span>{item.label}</span><strong>{item.applications}</strong><small>面试 {item.interviewRate}% · Offer {item.offerRate}%</small></li>)}</ul></section><section className="content-card"><h2>城市</h2><ul className="breakdown-list">{report.cityBreakdown.map(item => <li key={item.key}><span>{item.label}</span><strong>{item.applications}</strong><small>面试 {item.interviewRate}% · Offer {item.offerRate}%</small></li>)}</ul></section></div>
      {(report.summary.length > 0 || report.dataQuality.length > 0) && <section className="content-card analytics-wide"><h2>报告解读</h2>{report.summary.map((line, index) => <p key={index}>{line}</p>)}{report.dataQuality.map((line, index) => <p className="reminder-warning" key={index}>{line}</p>)}</section>}
      <div className="analytics-grid"><section className="content-card"><h2>需要跟进</h2>{summary.followUps.length === 0 ? <p className="muted-note">目前没有待跟进的投递。</p> : <ul className="insight-list">{summary.followUps.map(item => <li key={item.id}><strong>{item.companyName} · {item.positionName}</strong><span>{item.followUpReason ?? `${item.followUpDays} 天未更新`}</span></li>)}</ul>}</section><section className="content-card"><h2>进度提醒</h2>{summary.progressReminders.length === 0 ? <p className="muted-note">目前没有待处理的进度提醒。</p> : <ul className="insight-list">{summary.progressReminders.map(item => <li key={item.id}><strong>{item.companyName} · {item.positionName}</strong><span>{item.stage} · {item.occurredOn}</span>{!item.completed && <span className="reminder-actions"><button disabled={busy === item.stageOccurrenceId} onClick={() => void resolve(item.stageOccurrenceId, 'completed')}>完成</button><button disabled={busy === item.stageOccurrenceId} onClick={() => void resolve(item.stageOccurrenceId, 'dismissed')}>忽略</button></span>}</li>)}</ul>}</section></div>
    </>}
  </section>
}
