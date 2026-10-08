import { apiRequest, apiUpload } from '../../shared/api/client'

export type Profile = { id: string; displayName: string; image: string | null; recoveryEmail: string | null; emailVerified: boolean }
export type AccountSession = { id: string; createdAt: string; expiresAt: string; userAgent: string | null; current: boolean }

export const getProfile = () => apiRequest<Profile>('/api/profile')
export const updateProfile = (displayName: string, image: string | null) => apiRequest<Profile>('/api/profile', { method: 'PATCH', body: { displayName, image } })
export const uploadAvatar = (file: File) => { const body = new FormData(); body.append('file', file); return apiUpload<{ url: string }>('/api/profile/avatar', body) }
export const changePassword = (currentPassword: string, newPassword: string) => apiRequest<{ message: string }>('/api/profile/password', { method: 'POST', body: { currentPassword, newPassword } })
export const requestEmailCode = (email: string) => apiRequest<{ message: string }>('/api/profile/email/code', { method: 'POST', body: { email } })
export const bindEmail = (email: string, verificationCode: string, currentPassword: string) => apiRequest<{ email: string; emailVerified: boolean }>('/api/profile/email', { method: 'PATCH', body: { email, verificationCode, currentPassword } })
export const unbindEmail = (currentPassword: string) => apiRequest<{ email: null; emailVerified: false }>('/api/profile/email', { method: 'DELETE', body: { currentPassword } })
export const listSessions = () => apiRequest<AccountSession[]>('/api/profile/sessions')
export const revokeSession = (sessionId: string) => apiRequest<{ revoked: boolean }>('/api/profile/sessions', { method: 'DELETE', body: { id: sessionId } })
