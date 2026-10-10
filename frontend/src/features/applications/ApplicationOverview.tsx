import type { AnalyticsSummary } from '../analytics/api'

const stageLabels: Record<string, string> = { screening: '简历筛选', assessment: '测评/AI测评', written_test: '笔试', interview_1: '一面/AI面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }
const stageColors: Record<string, string> = { screening: '#3182a0', assessment: '#7a64bd', written_test: '#d48a24', interview_1: '#0d9b84', interview_2: '#087f73', interview_3: '#3865a8', hr_interview: '#c45f82', final_interview: '#6e8f24' }
const stageOrder = ['screening', 'assessment', 'written_test', 'interview_1', 'interview_2', 'interview_3', 'hr_interview', 'final_interview']

export default function ApplicationOverview({ summary, onOpen }: { summary: AnalyticsSummary | null; onOpen: (id: string) => void }) {
  const metrics = summary ? [
    ['全部投递', summary.total, 'total'], ['已投递', summary.submitted, 'active'], ['Offer', summary.offers, 'offer'], ['本周新增', summary.addedThisWeek, 'week'], ['拒绝', summary.refused, 'rejected'],
  ] as const : []
  const distribution = summary ? stageOrder.map(stage => [stage, summary.stageDistribution[stage] ?? 0] as const).filter(([, count]) => count > 0) : []
  const stageTotal = distribution.reduce((sum, [, count]) => sum + count, 0)
  const stageMax = Math.max(1, ...distribution.map(([, count]) => count))
  const segments = distribution.map(([stage, count], index) => {
    const start = distribution.slice(0, index).reduce((sum, [, previous]) => sum + previous, 0) / stageTotal * 100
    const end = start + count / stageTotal * 100
    return `${stageColors[stage] ?? '#8aa9a4'} ${start}% ${end}%`
  })

  return <section className="application-overview" aria-labelledby="application-overview-title"><div className="application-section-heading"><h2 id="application-overview-title">求职概览</h2><p>当前进展与待办</p></div>
    {summary && <><div className="application-summary-grid">{metrics.map(([label, value, tone]) => <article className={`application-summary-card summary-${tone}`} key={label}><span aria-hidden="true" /><div><strong>{value}</strong><small>{label}</small></div></article>)}</div>
      <div className="application-overview-grid"><article className="application-overview-panel"><p className="application-panel-kicker">招聘流程</p><h3>阶段分布</h3>{distribution.length ? <div className="application-distribution"><div className="application-distribution-ring" aria-label={`共 ${stageTotal} 次阶段记录`} style={{ background: `radial-gradient(circle at center, white 54%, transparent 56%), conic-gradient(${segments.join(', ')})` }}><strong>{stageTotal}</strong><span>阶段记录</span></div><ul>{distribution.map(([stage, count]) => <li key={stage}><div><span><i aria-hidden="true" style={{ backgroundColor: stageColors[stage] ?? '#8aa9a4' }} />{stageLabels[stage] ?? stage}</span><strong>{count} <small>次</small></strong></div><span className="application-distribution-track" aria-hidden="true"><span style={{ width: `${Math.max(count / stageMax * 100, 10)}%`, backgroundColor: stageColors[stage] ?? '#8aa9a4' }} /></span></li>)}</ul></div> : <p>尚未记录招聘阶段。</p>}</article>
        <article className="application-overview-panel"><div className="application-panel-heading"><h3>需要跟进</h3><span>{summary.followUps.length}</span></div>{summary.followUps.length ? <ul className="application-follow-up-list">{summary.followUps.slice(0, 5).map(item => <li key={item.id}><button type="button" onClick={() => onOpen(item.id)} aria-label={`查看 ${item.companyName} ${item.positionName} 详情`}><span className="application-company-avatar" aria-hidden="true">{item.companyName.slice(0, 1)}</span><span className="application-follow-up-name"><strong>{item.companyName}</strong><small>{item.positionName}</small></span><span className="follow-up">{item.followUpReason === 'timeline' ? `时间线 ${item.followUpDays} 天未更新` : `投递记录 ${item.followUpDays} 天未更新`}</span></button></li>)}</ul> : <p className="application-follow-up-empty">目前都跟进得很好。时间线或投递记录长期未更新会出现在这里。</p>}</article></div>
    </>}
  </section>
}
