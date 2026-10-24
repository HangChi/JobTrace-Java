import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AnalyticsPage from './AnalyticsPage'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })
const summary = { total: 8, submitted: 5, refused: 2, offers: 1, addedThisWeek: 2, stageDistribution: {}, followUps: [], progressReminders: [{ id: 'p1', companyName: '测试公司', positionName: '开发', stageOccurrenceId: 'stage1', stage: '一面', occurredOn: '2026-10-08', reviewStatus: null, completed: false }] }
const report = { query: { period: '90d' }, availableCities: ['上海'], metrics: { applications: { value: 8, previous: 4, delta: 100, deltaKind: 'percent' } }, trend: [{ periodStart: '2026-10-01', label: '10/01', applications: 8, interviewed: 2, offers: 1 }], milestones: [{ key: 'applications', label: '投递', count: 8, conversionFromPrevious: null }], stageReach: [{ stage: 'interview_1', count: 2, rate: 25 }], interviews: { total: 2, completed: 1, completionRate: 50, resolved: 1, passed: 1, passRate: 100, byStage: [] }, typeBreakdown: [], cityBreakdown: [], summary: ['本周期投递量上升。'], dataQuality: [], sampleSufficient: true }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows Java analytics and resolves a progress reminder with CSRF', async () => {
  const onOverview = vi.fn()
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'safe-token' })
    if (path === '/api/analytics/summary') return json(summary)
    if (path.startsWith('/api/analytics/report')) return json(report)
    return json({ resolved: true, resolution: 'completed' })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} onOverview={onOverview} />)
  expect(await screen.findByText('本周期投递量上升。')).toBeInTheDocument()
  expect(onOverview).toHaveBeenCalledWith({ period: '近 90 天', applications: 8, interviews: 2 })
  expect(screen.getByText('Offer 率')).toBeInTheDocument()
  expect(screen.getByText('阶段到达率')).toBeInTheDocument()
  expect(screen.getByText('面试复盘')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '完成' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/analytics/progress-reminders/stage1/resolve', expect.objectContaining({ method: 'POST', body: JSON.stringify({ resolution: 'completed' }), headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'safe-token' }) })))
})

it('applies the old report range and dimension filters', async () => {
  const fetcher = vi.fn(async (path: string) => path === '/api/analytics/summary' ? json(summary) : json(report))
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} />)
  await screen.findByText('本周期投递量上升。')
  fireEvent.change(screen.getByLabelText('周期'), { target: { value: '30d' } })
  fireEvent.change(screen.getByLabelText('城市'), { target: { value: '上海' } })
  fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/analytics/report?period=30d&city=%E4%B8%8A%E6%B5%B7', expect.objectContaining({ method: 'GET' })))
  fireEvent.click(screen.getByRole('button', { name: '恢复默认' }))
  await waitFor(() => expect(fetcher.mock.calls.filter(([path]) => path === '/api/analytics/report?period=90d')).toHaveLength(2))
})

it('rejects invalid custom ranges before querying and applies a valid range with type', async () => {
  const fetcher = vi.fn(async (path: string) => path === '/api/analytics/summary' ? json(summary) : json({ ...report, query: { period: 'custom' } }))
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} />)
  await screen.findByText('本周期投递量上升。')
  fireEvent.change(screen.getByLabelText('周期'), { target: { value: 'custom' } })
  fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
  expect(screen.getByRole('alert')).toHaveTextContent('请选择有效的开始与结束日期')
  fireEvent.change(screen.getByLabelText('开始日期'), { target: { value: '2026-10-10' } })
  fireEvent.change(screen.getByLabelText('结束日期'), { target: { value: '2026-10-01' } })
  fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
  expect(fetcher.mock.calls.filter(([path]) => path.startsWith('/api/analytics/report'))).toHaveLength(1)
  fireEvent.change(screen.getByLabelText('结束日期'), { target: { value: '2026-10-12' } })
  fireEvent.change(screen.getByLabelText('求职类型'), { target: { value: 'summer_internship' } })
  fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/analytics/report?period=custom&from=2026-10-10&to=2026-10-12&type=summer_internship', expect.anything()))
})

it('shows empty insights and recovers after a failed report request', async () => {
  let fail = true
  const empty = { ...report, metrics: { applications: { value: 0 } }, trend: [], milestones: [], stageReach: [], interviews: { ...report.interviews, total: 0 }, summary: [], dataQuality: ['部分记录缺少城市。'], biggestDrop: null }
  const fetcher = vi.fn(async (path: string) => path === '/api/analytics/summary' ? json({ ...summary, progressReminders: [] }) : fail ? json({ code: 'unavailable', detail: '分析服务暂不可用' }, 503) : json(empty))
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('分析服务暂不可用')
  fail = false
  fireEvent.click(screen.getByRole('button', { name: '重试' }))
  expect(await screen.findByText('所选周期暂无投递。')).toBeInTheDocument()
  expect(screen.getByText('尚无阶段数据。')).toBeInTheDocument()
  expect(screen.getByText('部分记录缺少城市。')).toBeInTheDocument()
  expect(screen.getByText(/还没有面经数据/)).toBeInTheDocument()
  expect(screen.getByText('目前没有待处理的进度提醒。')).toBeInTheDocument()
})

it('reports reminder resolution failure and leaves the reminder actionable', async () => {
  const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : path === '/api/analytics/summary' ? json({ ...summary, followUps: [{ id: 'a1', companyName: '跟进公司', positionName: '工程师', followUpReason: null, followUpDays: 9 }] }) : path.startsWith('/api/analytics/report') ? json(report) : json({ code: 'conflict', detail: '提醒状态已变化' }, 409))
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} />)
  expect(await screen.findByText('跟进公司 · 工程师')).toBeInTheDocument()
  expect(screen.getByText('9 天未更新')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '忽略' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('提醒状态已变化')
  expect(screen.getByRole('button', { name: '忽略' })).toBeEnabled()
  expect(fetcher).toHaveBeenCalledWith('/api/analytics/progress-reminders/stage1/resolve', expect.objectContaining({ body: JSON.stringify({ resolution: 'dismissed' }) }))
})
