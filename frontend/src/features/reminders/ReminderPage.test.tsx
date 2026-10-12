import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ReminderPage from './ReminderPage'

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } })
const reminder = { id: 'r1', applicationId: 'a1', companyName: '测试公司', positionName: '开发', title: '准备面试', eventAt: '2026-10-10T10:00:00Z', notifyAt: '2026-10-09T10:00:00Z', emailEnabled: false, status: 'pending', version: 1, emailStatus: null }
const preferences = { homeEnabled: true, homeView: 'scheduled', defaultLead: '1h', defaultSnoozeMinutes: 30, emailDefault: false }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows a persisted reminder and completes it with a CSRF token and version', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'safe-token' })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path === '/api/reminders/r1/complete') return json({ ...reminder, status: 'completed', version: 2 })
    return json({ overdue: [reminder], upcoming: [], history: [], email: { available: false, address: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ReminderPage onExpired={vi.fn()} />)
  expect(await screen.findByRole('heading', { name: '准备面试' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '完成' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1/complete', expect.objectContaining({ method: 'POST', body: JSON.stringify({ version: 1 }), headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'safe-token' }) })))
  expect(await screen.findByText('提醒已完成。')).toBeInTheDocument()
})

it('creates a reminder for an application and saves preferences', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'safe-token' })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path === '/api/reminders' && init?.method === 'POST') return json(reminder)
    return json({ overdue: [], upcoming: [], history: [], email: { available: false, address: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ReminderPage onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '新建提醒' }))
  fireEvent.change(screen.getByLabelText('待办内容'), { target: { value: '准备面试' } })
  fireEvent.click(screen.getByRole('button', { name: '保存提醒' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders', expect.objectContaining({ method: 'POST', body: expect.stringContaining('准备面试') })))
  fireEvent.click(screen.getByRole('button', { name: '保存偏好' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminder-settings', expect.objectContaining({ method: 'PATCH', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'safe-token' }) })))
})

it('reopens a historical reminder only after choosing future times', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'safe-token' })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path.endsWith('/reopen')) return json({ ...reminder, status: 'pending', version: 2 })
    return json({ overdue: [], upcoming: [], history: [{ ...reminder, status: 'completed' }], email: { available: false, address: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ReminderPage onExpired={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: '已完成' }))
  expect(await screen.findByRole('heading', { name: '准备面试' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '重开' }))
  expect(screen.getByRole('heading', { name: '重开提醒' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '确认重开' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1/reopen', expect.objectContaining({ method: 'POST', body: expect.stringContaining('eventAt') })))
})

it('edits a reminder only after its times are moved into the future', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path === '/api/reminders/r1' && init?.method === 'PATCH') return json({ ...reminder, title: '准备终面', version: 2 })
    return json({ overdue: [reminder], upcoming: [], history: [], email: { available: false, address: null } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ReminderPage onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }))
  fireEvent.change(screen.getByLabelText('待办内容'), { target: { value: '准备终面' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存提醒' }).closest('form')!)
  expect(screen.getByRole('alert')).toHaveTextContent('未来的提醒时间')
  expect(fetcher).not.toHaveBeenCalledWith('/api/reminders/r1', expect.objectContaining({ method: 'PATCH' }))
  fireEvent.change(screen.getByLabelText('事项时间'), { target: { value: '2027-02-01T10:00' } })
  fireEvent.change(screen.getByLabelText('提醒时间'), { target: { value: '2027-02-01T09:00' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存提醒' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('"version":1') })))
  expect(await screen.findByText('提醒已更新。')).toBeInTheDocument()
})

it('snoozes a due reminder and retries failed email delivery', async () => {
  const failedEmail = { ...reminder, emailStatus: 'failed' }
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path.endsWith('/snooze')) return json({ ...failedEmail, version: 2 })
    if (path.endsWith('/retry-email')) return json({ accepted: true })
    return json({ overdue: [failedEmail], upcoming: [], history: [], email: { available: true, address: 'me@example.test' } })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ReminderPage onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '推迟' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1/snooze', expect.objectContaining({ method: 'POST', body: expect.stringContaining('"notifyAt"') })))
  fireEvent.click(screen.getByRole('button', { name: '重试邮件' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminders/r1/retry-email', expect.objectContaining({ method: 'POST', body: JSON.stringify({ version: 1 }) })))
  expect(await screen.findByText('邮件已重新加入发送队列。')).toBeInTheDocument()
})

it('expires when loading reminders returns unauthorized', async () => {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/reminder-settings' ? json(preferences) : new Response(JSON.stringify({ code: 'unauthorized' }), { status: 401 })))
  const onExpired = vi.fn()
  render(<ReminderPage onExpired={onExpired} />)
  await waitFor(() => expect(onExpired).toHaveBeenCalled())
  expect(screen.queryByRole('heading', { name: '准备面试' })).not.toBeInTheDocument()
})
