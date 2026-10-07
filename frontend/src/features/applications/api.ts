import { ApiError, apiRequest } from '../../shared/api/client'

export type ApplicationSummary = {
  id: string
  companyName: string
  positionName: string
  city: string | null
  appliedDate: string
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
