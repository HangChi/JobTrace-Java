import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AnalyticsPage from './AnalyticsPage'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } })
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
