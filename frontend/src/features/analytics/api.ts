import { apiRequest } from '../../shared/api/client'

export type AnalyticsSummary = {
  total: number
  submitted: number
  refused: number
  offers: number
  addedThisWeek: number
  stageDistribution: Record<string, number>
  followUps: { id: string; companyName: string; positionName: string; followUpDays: number; followUpReason: string | null }[]
  progressReminders: { id: string; companyName: string; positionName: string; stageOccurrenceId: string; stage: string; occurredOn: string; reviewStatus: string | null; completed: boolean }[]
}

export type AnalyticsReport = {
  query?: { period: string; from?: string; to?: string; type?: string; city?: string; hasCityFilter?: boolean }
  availableCities?: string[]
  metrics: Record<string, { value: number | null; previous: number | null; delta: number | null; deltaKind: string }>
  trend: { periodStart: string; label: string; applications: number; interviewed: number; offers: number }[]
  milestones: { key: string; label: string; count: number; conversionFromPrevious: number | null }[]
  biggestDrop?: { count: number; from: string; to: string } | null
  stageReach?: { stage: string; count: number; rate: number }[]
  interviews?: { total: number; completed: number; completionRate: number; resolved: number; passed: number; passRate: number; byStage: { stage: string; total: number; results: { passed: number; failed: number; pending: number } }[] }
  typeBreakdown: { key: string; label: string; applications: number; interviewRate: number; offerRate: number }[]
  cityBreakdown: { key: string; label: string; applications: number; interviewRate: number; offerRate: number }[]
  summary: string[]
  dataQuality: string[]
  sampleSufficient: boolean
}

export const getAnalyticsSummary = () => apiRequest<AnalyticsSummary>('/api/analytics/summary')
export const getAnalyticsReport = (filters: URLSearchParams) => apiRequest<AnalyticsReport>(`/api/analytics/report?${filters.toString()}`)
export const resolveProgressReminder = (stageOccurrenceId: string, resolution: 'completed' | 'dismissed') =>
  apiRequest<{ resolved: boolean; resolution: string }>(`/api/analytics/progress-reminders/${encodeURIComponent(stageOccurrenceId)}/resolve`, { method: 'POST', body: { resolution } })
