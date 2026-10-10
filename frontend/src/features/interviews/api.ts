import { apiRequest } from '../../shared/api/client'

export type InterviewSummary = { id: string; applicationId: string; companyName: string; positionName: string; stage: string; interviewedOn: string; status: 'draft' | 'pending_review' | 'completed'; roundResult: 'pending' | 'passed' | 'failed'; questionCount: number; visibility: 'private' | 'public'; authorMode: 'anonymous' | 'attributed'; publishedAt: string | null }
export type InterviewQuestion = { id?: string; category: string; question: string; originalAnswer: string | null; followUpNotes: string | null; improvedAnswer: string | null; selfRating: number | null }
export type InterviewAction = { id?: string; content: string; completed: boolean }
export type InterviewDetail = InterviewSummary & { version: number; format: string | null; durationMinutes: number | null; interviewerNotes: string | null; highlights: string | null; gaps: string | null; questions: InterviewQuestion[]; actionItems: InterviewAction[] }
export type InterviewPage = { items: InterviewSummary[]; nextCursor: string | null; total: number; limit: number }
export type PublicInterview = { id: string; companyName: string; positionName: string; city: string | null; stage: string; interviewedOn: string; publishedAt: string; questionCount: number; author: { username: string; image: string | null } | null; engagement: { likeCount: number; commentCount: number; viewCount: number; likedByViewer: boolean }; highlights: string | null; gaps: string | null; questions: { category: string; question: string; originalAnswer: string | null; followUpNotes: string | null; improvedAnswer: string | null }[]; recentComments: Comment[] }
export type Comment = { id: string; content: string; createdAt: string; author: { username: string; image: string | null } }
export type PublicInterviewPage = { items: PublicInterview[]; nextCursor: string | null; total: number; limit: number; facets: { cities: string[]; positions: string[] } }

const privatePath = (id: string) => `/api/interviews/${encodeURIComponent(id)}`
const publicPath = (id: string) => `/api/interviews/public/${encodeURIComponent(id)}`
export const listPrivateInterviews = (cursor?: string) => apiRequest<InterviewPage>(`/api/interviews?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
export const getPrivateInterview = (id: string) => apiRequest<InterviewDetail>(privatePath(id))
export const createPrivateInterview = (body: { applicationId: string; stage: string; interviewedOn: string; roundResult: string }) => apiRequest<InterviewDetail>('/api/interviews', { method: 'POST', body })
export const updatePrivateInterview = (id: string, body: Record<string, unknown>) => apiRequest<InterviewDetail>(privatePath(id), { method: 'PATCH', body })
export const deletePrivateInterview = (id: string) => apiRequest<void>(privatePath(id), { method: 'DELETE' })
export const listPublicInterviews = (params: URLSearchParams) => apiRequest<PublicInterviewPage>(`/api/interviews/public?${params}`)
export const getPublicInterview = (id: string) => apiRequest<PublicInterview>(publicPath(id))
export const togglePublicLike = (id: string) => apiRequest<PublicInterview['engagement']>(`${publicPath(id)}/like`, { method: 'POST' })
export const listPublicComments = (id: string) => apiRequest<Comment[]>(`${publicPath(id)}/comments`)
export const addPublicComment = (id: string, content: string) => apiRequest<{ comment: Comment; engagement: PublicInterview['engagement'] }>(`${publicPath(id)}/comments`, { method: 'POST', body: { content } })
