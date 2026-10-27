import { apiRequest } from '../../shared/api/client'

export type AdminSummary = { generatedAt: string; timeZone: string; counts: { status: string; value: { users: number; activeUsers: number; disabledUsers: number; administrators: number; applications: number; interviews: number } | null }; activity: { status: string; windows: { registered7d: number; active7d: number; registered30d: number; active30d: number } | null } }
export type AdminUser = { id: string; username: string; email: string | null; internalEmail: string | null; role: string; disabled: boolean; accessVersion: number; createdAt: string; lastSignInAt: string | null; applicationCount: number; interviewCount: number }
export type AdminAudit = { id: string; actorIdentifier: string; targetIdentifier: string; eventType: string; outcome: string; reason: string | null; createdAt: string }
export type Page<T> = { items: T[]; total: number; page: number; limit: number; totalPages: number }
export type AdminUserDetail = AdminUser & {
  recentAuditEvents: AdminAudit[]
  applications: Page<{ id: string; companyName: string; positionName: string; city: string | null; status: string; appliedDate: string; notes: string | null; stages: { stage: string; occurredOn: string }[] }>
  interviews: Page<{ id: string; companyName: string; positionName: string; stage: string; interviewedOn: string; status: string; roundResult: string; highlights: string | null; questions: { question: string }[] }>
}
export type Source = { id: string; company: { id: string; name: string; type: string; industry: string }; adapter: string; baseUrl: string; status: string; lastSuccessAt: string | null; consecutiveFailures: number }
export type Candidate = { id: string; companyName: string; reviewStatus: string; entryUrl?: string; articleUrl?: string; diagnosticCode?: string; detectedAdapter?: string }
export type CandidateList = { items: Candidate[]; summary: Record<string, number> }
export type CatalogPage = { items: { identityKey: string; companyName: string; adapter: string; industry: string; websiteUrl: string; channelLabel: string }[]; total: number; page: number; limit: number }
export type RunPage = { items: { id: string; sourceId: string; status: string; startedAt: string; finishedAt: string | null; counts: { created: number; updated: number; rejected: number } | null; errorSummary: string | null }[]; total: number; page: number; limit: number }

export const getAdminSummary = () => apiRequest<AdminSummary>('/api/admin/summary')
export const listAdminUsers = (query = '', page = 1, limit = 20) => apiRequest<Page<AdminUser>>(`/api/admin/users?limit=${limit}&page=${page}${query ? `&q=${encodeURIComponent(query)}` : ''}`)
export const getAdminUser = (id: string) => apiRequest<AdminUserDetail>(`/api/admin/users/${encodeURIComponent(id)}`)
export const listAdminAudit = () => apiRequest<Page<AdminAudit>>('/api/admin/audit-events?limit=30')
export const changeAdminAccess = (user: AdminUser, action: string, reason: string, confirmSelf: boolean) =>
  apiRequest<{ user: AdminUser; auditEventId: string }>(`/api/admin/users/${encodeURIComponent(user.id)}`, { method: 'PATCH', body: { requestId: crypto.randomUUID(), expectedVersion: user.accessVersion, action, reason, confirmSelf } })
export const listSources = () => apiRequest<{ items: Source[] }>('/api/admin/job-market/sources')
export const updateSource = (id: string, status: string) => apiRequest<Source>(`/api/admin/job-market/sources/${encodeURIComponent(id)}`, { method: 'PATCH', body: { status } })
export const listCatalog = () => apiRequest<CatalogPage>('/api/admin/job-market/catalog?limit=50')
export const listRuns = () => apiRequest<RunPage>('/api/admin/job-market/sync-runs?limit=20')
export const listCandidates = (kind: 'discovery' | 'company-candidates') => apiRequest<CandidateList>(`/api/admin/job-market/${kind}?status=pending`)
export const reviewCandidate = (kind: 'discovery' | 'company-candidates', id: string, action: 'approve' | 'ignore') => apiRequest<Candidate>(`/api/admin/job-market/${kind}/${encodeURIComponent(id)}`, { method: 'PATCH', body: { action } })
