import { ApiError, apiRequest } from '../../shared/api/client'

export type ApplicationSummary = {
  id: string
  companyName: string
  positionName: string
  city: string | null
  jobUrl: string | null
  appliedDate: string
  latestDate: string
  status: 'submitted' | 'offer' | 'refused'
  type: string
  stages: string[]
  needsFollowUp: boolean
  followUpDays: number
  followUpReason: string | null
  version: number
}

export type ApplicationPage = {
  items: ApplicationSummary[]
  nextCursor: string | null
  total: number
  page: number
  limit: number
}

export type StageOccurrence = { id: string; stage: string; occurredOn: string }
export type ApplicationDetail = ApplicationSummary & {
  jobUrl: string | null
  notes: string | null
  stageOccurrences: StageOccurrence[]
  events: { id: string; type: string; occurredOn: string }[]
}
export type ApplicationDraft = {
  companyName: string
  positionName: string
  city: string
  jobUrl: string
  appliedDate: string
  type: string
  status: ApplicationSummary['status']
  notes: string
  stages: { stage: string; occurredOn: string }[]
}

const filters = new Set(['q', 'status', 'type', 'stage', 'city', 'appliedFrom', 'appliedTo', 'sort', 'direction', 'cursor', 'page', 'limit'])

export async function listApplications(search: URLSearchParams, fetcher?: typeof fetch) {
  const params = new URLSearchParams()
  search.forEach((value, key) => { if (filters.has(key)) params.append(key, value) })
  const page = await apiRequest<ApplicationPage>(`/api/applications${params.size ? `?${params}` : ''}`, { fetcher })
  if (!page || !Array.isArray(page.items) || typeof page.total !== 'number') {
    throw new ApiError(200, 'invalid_response', '投递记录返回的数据无效，请重试。')
  }
  return page
}

const path = (id: string) => `/api/applications/${encodeURIComponent(id)}`
export const getApplication = (id: string) => apiRequest<ApplicationDetail>(path(id))
export const createApplication = (draft: ApplicationDraft) => apiRequest<ApplicationDetail>('/api/applications', { method: 'POST', body: draft })
export const updateApplication = (item: ApplicationDetail, draft: ApplicationDraft, changeDate: string) =>
  apiRequest<ApplicationDetail>(path(item.id), { method: 'PATCH', body: { ...draft, version: item.version, changeDate } })
export const updateApplicationStatus = (item: Pick<ApplicationSummary, 'id' | 'version'>, status: ApplicationSummary['status']) =>
  apiRequest<{ id: string; status: ApplicationSummary['status']; latestDate: string; version: number }>(`${path(item.id)}/status`, { method: 'PATCH', body: { status, version: item.version } })
export const deleteApplication = (id: string) => apiRequest<void>(path(id), { method: 'DELETE' })
export const deleteApplications = (ids: string[]) => apiRequest<{ deletedCount: number }>('/api/applications', { method: 'DELETE', body: { ids } })
export const addApplicationStage = (item: ApplicationDetail, stage: string, occurredOn: string) =>
  apiRequest<ApplicationDetail>(`${path(item.id)}/stages`, { method: 'POST', body: { stage, occurredOn } })
export const updateApplicationStage = (item: ApplicationDetail, occurrenceId: string, stage: string, occurredOn: string, changeDate: string) =>
  apiRequest<ApplicationDetail>(`${path(item.id)}/stages/${encodeURIComponent(occurrenceId)}`, { method: 'PATCH', body: { version: item.version, stage, occurredOn, changeDate } })
export const removeApplicationStage = (item: ApplicationDetail, occurrenceId: string, changeDate: string) =>
  apiRequest<ApplicationDetail>(`${path(item.id)}/stages/${encodeURIComponent(occurrenceId)}`, { method: 'DELETE', body: { changeDate } })
