import { apiRequest } from '../../shared/api/client'

export type CurrentSession = {
  session: { id: string; expiresAt: string }
  user: { id: string; displayName: string; image: string | null; role: string }
}

export const getSession = () => apiRequest<CurrentSession>('/api/auth/session')
export const login = (identifier: string, password: string, returnTo: string) =>
  apiRequest<{ redirectTarget: string }>('/api/auth/login', { method: 'POST', body: { identifier, password, returnTo } })
export const logout = () => apiRequest<void>('/api/auth/logout', { method: 'POST' })
export const requestEmailCode = (email: string) =>
  apiRequest<{ message: string }>('/api/auth/email-code', { method: 'POST', body: { email } })
export const register = (body: { username: string; email: string; verificationCode: string; password: string; displayName: string }) =>
  apiRequest<{ message: string }>('/api/auth/register', { method: 'POST', body })
export const requestPasswordReset = (email: string) =>
  apiRequest<{ message: string }>('/api/auth/request-password-reset', { method: 'POST', body: { email } })
export const resetPassword = (token: string, newPassword: string) =>
  apiRequest<{ status: boolean }>('/api/auth/reset-password', { method: 'POST', body: { token, newPassword } })
