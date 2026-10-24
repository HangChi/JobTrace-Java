import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { getAnalyticsReport, getAnalyticsSummary, resolveProgressReminder } from './api'
import type { AnalyticsReport, AnalyticsSummary } from './api'

const metricLabels: Record<string, string> = { applications: '投递数', interviewRate: '面试率', offerRate: 'Offer 率', medianDaysToFirstInterview: '首次面试中位天数', reviewCompletionRate: '面经完成率' }
const pct = new Set(['interviewRate', 'offerRate', 'reviewCompletionRate'])
const errorText = (error: unknown) => error instanceof ApiError ? error.message : '分析数据暂时无法加载。'
const periods: Record<string, string> = { '30d': '近 30 天', '90d': '近 90 天', '180d': '近 180 天', ytd: '今年', all: '全部历史', custom: '自定义范围' }
const types: Record<string, string> = { summer_internship: '暑期实习', daily_internship: '日常实习', spring_recruitment: '春招', early_campus_recruitment: '秋招提前批', campus_recruitment: '秋招', social_recruitment: '社招' }
const stages: Record<string, string> = { screening: '筛选', assessment: '测评', written_test: '笔试', interview_1: '一面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }

export default function AnalyticsPage({ onExpired, onOverview }: { onExpired: () => void; onOverview?: (value: { period: string; applications: number; interviews: number }) => void }) {
  const [period, setPeriod] = useState('90d')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [type, setType] = useState('__all__')
  const [city, setCity] = useState('__all__')
  const [filters, setFilters] = useState('period=90d')
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [report, setReport] = useState<AnalyticsReport | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([getAnalyticsSummary(), getAnalyticsReport(new URLSearchParams(filters))]).then(([overview, analysis]) => {
      if (active) { setSummary(overview); setReport(analysis); setError(''); const appliedPeriod = analysis.query?.period ?? new URLSearchParams(filters).get('period') ?? '90d'; onOverview?.({ period: periods[appliedPeriod] ?? periods['90d'], applications: analysis.metrics.applications?.value ?? 0, interviews: analysis.interviews?.total ?? 0 }) }
    }).catch(caught => {
      if (!active) return
      if (caught instanceof ApiError && caught.status === 401) onExpired()
      else setError(errorText(caught))
    })
    return () => { active = false }
  }, [filters, reload, onExpired, onOverview])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (period === 'custom' && (!from || !to || from > to)) {
      setError('请选择有效的开始与结束日期。')
      return
    }
    const next = new URLSearchParams({ period })
    if (period === 'custom') { next.set('from', from); next.set('to', to) }
    if (type !== '__all__') next.set('type', type)
    if (city !== '__all__') next.set('city', city)
    setReport(null)
    setFilters(next.toString())
    setReload(value => value + 1)
  }

  function resetFilters() {
    setPeriod('90d'); setFrom(''); setTo(''); setType('__all__'); setCity('__all__')
    setReport(null); setFilters('period=90d'); setReload(value => value + 1)
  }

  async function resolve(id: string, resolution: 'completed' | 'dismissed') {
    if (busy) return
    setBusy(id); setError('')
    try { await resolveProgressReminder(id, resolution); setReload(value => value + 1) }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) }
    finally { setBusy(null) }
  }

  const maxTrend = Math.max(1, ...(report?.trend.map(point => point.applications) ?? []))
  return <section className="analytics-content">
    <div className="feature-toolbar"><p>按时间、类型和城市查看投递趋势与转化。</p><a className="quiet-button" href="/api/exports/applications?scope=all&format=csv">导出投递 CSV</a></div>
    <form className="content-card analytics-filters" onSubmit={applyFilters}>
      <div className="analytics-filter-heading"><h2>分析范围</h2><button type="button" className="quiet-button" onClick={resetFilters}>恢复默认</button></div>
      <div className="analytics-filter-grid">
        <label>周期<select value={period} onChange={event => setPeriod(event.target.value)}>{Object.entries(periods).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>开始日期<input type="date" value={from} onChange={event => setFrom(event.target.value)} disabled={period !== 'custom'} /></label>
        <label>结束日期<input type="date" value={to} onChange={event => setTo(event.target.value)} disabled={period !== 'custom'} /></label>
        <label>求职类型<select value={type} onChange={event => setType(event.target.value)}><option value="__all__">全部类型</option>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>城市<select value={city} onChange={event => setCity(event.target.value)}><option value="__all__">全部城市</option><option value="">未填写</option>{(report?.availableCities ?? []).map(value => <option key={value} value={value}>{value}</option>)}</select></label>
        <button type="submit">应用筛选</button>
      </div>
      <p className="muted-note">以投递日期确定分析范围；后续阶段、面经和结果仍会计入。</p>
    </form>
    {error && <p className="error" role="alert">{error} <button className="quiet-button" onClick={() => setReload(value => value + 1)}>重试</button></p>}
    {!summary || !report ? !error && <p role="status">正在加载分析…</p> : <>
      <div className="metric-grid analytics-summary-strip">{Object.entries(metricLabels).map(([key, label]) => <article key={key}><span>{label}</span><strong>{report.metrics[key]?.value == null ? '—' : `${report.metrics[key].value}${pct.has(key) ? '%' : ''}`}</strong></article>)}</div>
      <div className="analytics-grid"><section className="content-card"><h2>趋势</h2>{report.trend.length === 0 ? <p className="muted-note">所选周期暂无投递。</p> : <div className="trend-list">{report.trend.map(point => <div key={point.periodStart} className="trend-row"><span>{point.label}</span><div className="trend-track"><span style={{ width: `${point.applications / maxTrend * 100}%` }} /></div><strong>{point.applications}</strong></div>)}</div>}</section><section className="content-card"><h2>关键指标</h2><dl className="report-metrics">{Object.entries(report.metrics).map(([key, value]) => <div key={key}><dt>{metricLabels[key] ?? key}</dt><dd>{value.value === null ? '—' : `${value.value}${pct.has(key) ? '%' : ''}`}</dd></div>)}</dl></section></div>
      <div className="analytics-grid"><section className="content-card"><h2>里程碑转化</h2>{report.biggestDrop?.count ? <p className="muted-note">最大流失 {report.biggestDrop.count} 个机会</p> : null}<div className="milestone-list">{report.milestones.map(item => <article key={item.key}><strong>{item.count}</strong><span>{item.label}</span>{item.conversionFromPrevious !== null && <small>上一步转化 {item.conversionFromPrevious}%</small>}</article>)}</div></section><section className="content-card"><h2>阶段到达率</h2>{report.stageReach?.length ? <ul className="stage-reach-list">{report.stageReach.map(item => <li key={item.stage}><span>{stages[item.stage] ?? item.stage}</span><strong>{item.count} 条 · {item.rate}%</strong><span className="stage-reach-track"><span style={{ width: `${item.rate}%` }} /></span></li>)}</ul> : <p className="muted-note">尚无阶段数据。</p>}</section></div>
      <div className="analytics-grid"><section className="content-card"><h2>招聘类型</h2><ul className="breakdown-list">{report.typeBreakdown.map(item => <li key={item.key}><span>{item.label}</span><strong>{item.applications}</strong><small>面试 {item.interviewRate}% · Offer {item.offerRate}%</small></li>)}</ul></section><section className="content-card"><h2>城市</h2><ul className="breakdown-list">{report.cityBreakdown.map(item => <li key={item.key}><span>{item.label}</span><strong>{item.applications}</strong><small>面试 {item.interviewRate}% · Offer {item.offerRate}%</small></li>)}</ul></section></div>
      <section className="content-card analytics-wide"><div className="analytics-filter-heading"><h2>面试复盘</h2><a href="/interviews/mine">查看个人面经</a></div>{report.interviews?.total ? <><div className="metric-grid"><article><span>面经数量</span><strong>{report.interviews.total}</strong></article><article><span>复盘完成率</span><strong>{report.interviews.completionRate}%</strong></article><article><span>通过率</span><strong>{report.interviews.resolved ? `${report.interviews.passRate}%` : '—'}</strong></article></div><ul className="breakdown-list">{report.interviews.byStage.map(item => <li key={item.stage}><span>{stages[item.stage] ?? item.stage}</span><small>通过 {item.results.passed} · 未通过 {item.results.failed} · 待反馈 {item.results.pending}</small></li>)}</ul></> : <p className="muted-note">还没有面经数据。记录面试内容和结果后，这里会展示复盘情况。 <a href="/interviews/new">记录面经</a></p>}</section>
      {(report.summary.length > 0 || report.dataQuality.length > 0) && <section className="content-card analytics-wide"><h2>报告解读</h2>{report.summary.map((line, index) => <p key={index}>{line}</p>)}{report.dataQuality.map((line, index) => <p className="reminder-warning" key={index}>{line}</p>)}</section>}
      <div className="analytics-grid"><section className="content-card"><h2>需要跟进</h2>{summary.followUps.length === 0 ? <p className="muted-note">目前没有待跟进的投递。</p> : <ul className="insight-list">{summary.followUps.map(item => <li key={item.id}><strong>{item.companyName} · {item.positionName}</strong><span>{item.followUpReason ?? `${item.followUpDays} 天未更新`}</span></li>)}</ul>}</section><section className="content-card"><h2>进度提醒</h2>{summary.progressReminders.length === 0 ? <p className="muted-note">目前没有待处理的进度提醒。</p> : <ul className="insight-list">{summary.progressReminders.map(item => <li key={item.id}><strong>{item.companyName} · {item.positionName}</strong><span>{item.stage} · {item.occurredOn}</span>{!item.completed && <span className="reminder-actions"><button disabled={busy === item.stageOccurrenceId} onClick={() => void resolve(item.stageOccurrenceId, 'completed')}>完成</button><button disabled={busy === item.stageOccurrenceId} onClick={() => void resolve(item.stageOccurrenceId, 'dismissed')}>忽略</button></span>}</li>)}</ul>}</section></div>
    </>}
  </section>
}
