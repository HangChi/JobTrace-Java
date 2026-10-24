import { apiRequest } from '../../shared/api/client'

export type ReminderItem = {
  id: string
  applicationId: string
  companyName: string
  positionName: string
  title: string
  eventAt: string
  notifyAt: string
  emailEnabled: boolean
  status: 'pending' | 'due' | 'completed' | 'cancelled'
  version: number
  emailStatus: 'claimed' | 'sent' | 'failed' | 'skipped' | null
}

export type ReminderSummary = {
  overdue: ReminderItem[]
  upcoming: ReminderItem[]
  history: ReminderItem[]
  email: { available: boolean; address: string | null }
}

export type ReminderPreferences = {
  homeEnabled: boolean
  homeView: 'scheduled' | 'suggestions'
  defaultLead: 'on_time' | '30m' | '1h' | '1d'
  defaultSnoozeMinutes: 30 | 60 | 1440
  emailDefault: boolean
}

export type ReminderDraft = {
  applicationId: string
  title: string
  eventAt: string
  notifyAt: string
  emailEnabled: boolean
}

export const listReminders = (status: 'active' | 'completed' | 'cancelled' = 'active') =>
  apiRequest<ReminderSummary>(`/api/reminders?status=${status}`)

export const getReminderPreferences = () => apiRequest<ReminderPreferences>('/api/reminder-settings')
export const updateReminderPreferences = (body: ReminderPreferences) =>
  apiRequest<ReminderPreferences>('/api/reminder-settings', { method: 'PATCH', body })

export const createReminder = (body: ReminderDraft) =>
  apiRequest<ReminderItem>('/api/reminders', { method: 'POST', body })
export const updateReminder = (id: string, body: ReminderDraft & { version: number }) =>
  apiRequest<ReminderItem>(`/api/reminders/${encodeURIComponent(id)}`, { method: 'PATCH', body })

export const transitionReminder = (item: ReminderItem, action: 'complete' | 'reopen' | 'snooze' | 'cancel' | 'retry-email', body: Record<string, unknown> = {}) => {
  const path = `/api/reminders/${encodeURIComponent(item.id)}`
  if (action === 'cancel') return apiRequest<ReminderItem>(path, { method: 'DELETE', body: { version: item.version } })
  if (action === 'retry-email') return apiRequest<{ accepted: boolean }>(`${path}/retry-email`, { method: 'POST', body: { version: item.version } })
  return apiRequest<ReminderItem>(`${path}/${action}`, { method: 'POST', body: { version: item.version, ...body } })
}
