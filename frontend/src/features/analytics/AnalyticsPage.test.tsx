import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AnalyticsPage from './AnalyticsPage'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } })
const summary = { total: 8, submitted: 5, refused: 2, offers: 1, addedThisWeek: 2, stageDistribution: {}, followUps: [], progressReminders: [{ id: 'p1', companyName: '测试公司', positionName: '开发', stageOccurrenceId: 'stage1', stage: '一面', occurredOn: '2026-10-08', reviewStatus: null, completed: false }] }
const report = { metrics: { applications: { value: 8, previous: 4, delta: 100, deltaKind: 'percent' } }, trend: [{ periodStart: '2026-10-01', label: '10/01', applications: 8, interviewed: 2, offers: 1 }], milestones: [{ key: 'applications', label: '投递', count: 8, conversionFromPrevious: null }], typeBreakdown: [], cityBreakdown: [], summary: ['本周期投递量上升。'], dataQuality: [], sampleSufficient: true }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows Java analytics and resolves a progress reminder with CSRF', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'safe-token' })
    if (path === '/api/analytics/summary') return json(summary)
    if (path.startsWith('/api/analytics/report')) return json(report)
    return json({ resolved: true, resolution: 'completed' })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<AnalyticsPage onExpired={vi.fn()} />)
  expect(await screen.findByText('本周期投递量上升。')).toBeInTheDocument()
  expect(screen.getByText('Offer')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '完成' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/analytics/progress-reminders/stage1/resolve', expect.objectContaining({ method: 'POST', body: JSON.stringify({ resolution: 'completed' }), headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'safe-token' }) })))
})
