import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })
const profile = { id: 'u1', displayName: '小李', image: null, recoveryEmail: null, emailVerified: false }
const preferences = { homeEnabled: true, homeView: 'scheduled', defaultLead: '1h', defaultSnoozeMinutes: 30, emailDefault: false }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('removes an existing avatar while preserving the display name', async () => {
  const withAvatar = { ...profile, image: 'https://example.test/avatar.png' }
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions') return json([])
    if (path === '/api/reminder-settings') return json(preferences)
    if (path === '/api/profile' && init?.method === 'PATCH') return json(profile)
    return json(withAvatar)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '移除头像' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ displayName: '小李', image: null }) })))
  expect(screen.queryByRole('button', { name: '移除头像' })).not.toBeInTheDocument()
})

it('updates the current owner profile and changes password with CSRF', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions') return json([])
    if (path === '/api/reminder-settings') return json(preferences)
    if (path === '/api/profile/password') return json({ message: '密码已更新，其他设备已退出登录。' })
    if (path === '/api/profile' && init?.method === 'PATCH') return json({ ...profile, displayName: '新名字' })
    return json(profile)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('显示名称'), { target: { value: '新名字' } })
  fireEvent.click(screen.getByRole('button', { name: '保存资料' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ displayName: '新名字', image: null }) })))
  fireEvent.change(screen.getAllByLabelText('当前密码')[1], { target: { value: 'old-password' } })
  fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password-123' } })
  fireEvent.submit(screen.getByRole('button', { name: '更新密码' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile/password', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) })))
})

it('shows the admin role and saves reminder settings inline', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions') return json([])
    if (path === '/api/reminder-settings' && init?.method === 'PATCH') return json({ ...preferences, homeEnabled: false })
    if (path === '/api/reminder-settings') return json(preferences)
    return json(profile)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage role="ADMIN" onExpired={vi.fn()} />)
  expect(await screen.findByText('管理员账号 · 正常')).toBeInTheDocument()
  fireEvent.click(screen.getByLabelText('在首页显示提醒'))
  fireEvent.click(screen.getByRole('button', { name: '保存提醒设置' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminder-settings', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('"homeEnabled":false') })))
})

it('binds a recovery email and revokes another device with CSRF', async () => {
  const otherSession = { id: 's2', current: false, userAgent: 'Other browser', createdAt: '2026-10-01T00:00:00Z', expiresAt: '2026-11-01T00:00:00Z' }
  let bound = false
  let revoked = false
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions' && init?.method === 'DELETE') { revoked = true; return json({ revoked: true }) }
    if (path === '/api/profile/sessions') return json(revoked ? [] : [otherSession])
    if (path === '/api/reminder-settings') return json(preferences)
    if (path === '/api/profile/email/code') return json({ message: '验证码已发送。' })
    if (path === '/api/profile/email' && init?.method === 'PATCH') { bound = true; return json({ email: 'new@example.test', emailVerified: true }) }
    return json(bound ? { ...profile, recoveryEmail: 'new@example.test', emailVerified: true } : profile)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('新邮箱'), { target: { value: 'new@example.test' } })
  fireEvent.click(screen.getByRole('button', { name: '发送验证码' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile/email/code', expect.objectContaining({ method: 'POST', body: JSON.stringify({ email: 'new@example.test' }) })))
  fireEvent.change(screen.getByLabelText('验证码'), { target: { value: '123456' } })
  fireEvent.change(screen.getAllByLabelText('当前密码')[0], { target: { value: 'old-password' } })
  fireEvent.submit(screen.getByRole('button', { name: '绑定邮箱' }).closest('form')!)
  await waitFor(() => expect(screen.getByText('当前邮箱：new@example.test（已验证）')).toBeInTheDocument())
  expect(fetcher).toHaveBeenCalledWith('/api/profile/email', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ email: 'new@example.test', verificationCode: '123456', currentPassword: 'old-password' }) }))
  fireEvent.click(screen.getByRole('button', { name: '退出此设备' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile/sessions', expect.objectContaining({ method: 'DELETE', body: JSON.stringify({ id: 's2' }) })))
  expect(await screen.findByText('没有其他登录记录。')).toBeInTheDocument()
})

it('keeps a bound email when unbinding is cancelled, then clears it after confirmation', async () => {
  vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
  let unbound = false
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions') return json([])
    if (path === '/api/reminder-settings') return json(preferences)
    if (path === '/api/profile/email' && init?.method === 'DELETE') { unbound = true; return json({ email: null, emailVerified: false }) }
    return json(unbound ? profile : { ...profile, recoveryEmail: 'old@example.test', emailVerified: true })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findAllByLabelText('当前密码').then(inputs => inputs[0]), { target: { value: 'old-password' } })
  fireEvent.click(screen.getByRole('button', { name: /解除绑定/ }))
  expect(fetcher).not.toHaveBeenCalledWith('/api/profile/email', expect.objectContaining({ method: 'DELETE' }))
  fireEvent.click(screen.getByRole('button', { name: /解除绑定/ }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile/email', expect.objectContaining({ method: 'DELETE', body: JSON.stringify({ currentPassword: 'old-password' }) })))
  expect(await screen.findByText('尚未绑定安全邮箱。')).toBeInTheDocument()
})

it('expires the page when loading account data returns 401', async () => {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => path === '/api/reminder-settings' ? json(preferences) : new Response(JSON.stringify({ code: 'unauthorized' }), { status: 401 })))
  const onExpired = vi.fn()
  render(<ProfilePage onExpired={onExpired} />)
  await waitFor(() => expect(onExpired).toHaveBeenCalled())
  expect(screen.queryByLabelText('显示名称')).not.toBeInTheDocument()
})

it('uploads an avatar, updates reminder defaults, and shows an email request failure', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/avatar') return json({ url: 'https://example.test/avatar.png' })
    if (path === '/api/profile' && init?.method === 'PATCH') return json({ ...profile, image: 'https://example.test/avatar.png' })
    if (path === '/api/profile/sessions') return json([])
    if (path === '/api/reminder-settings' && init?.method === 'PATCH') return json({ ...preferences, homeView: 'suggestions', defaultLead: '1d', defaultSnoozeMinutes: 1440 })
    if (path === '/api/reminder-settings') return json(preferences)
    if (path === '/api/profile/email/code') return json({ code: 'rate_limited', detail: '请稍后再试' }, 429)
    return json(profile)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('上传头像'), { target: { files: [new File(['image'], 'avatar.png', { type: 'image/png' })] } })
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/profile/avatar', expect.objectContaining({ method: 'POST' })))
  expect(await screen.findByAltText('当前头像')).toHaveAttribute('src', 'https://example.test/avatar.png')
  fireEvent.change(screen.getByLabelText('首页视图'), { target: { value: 'suggestions' } })
  fireEvent.change(screen.getByLabelText('默认提前量'), { target: { value: '1d' } })
  fireEvent.change(screen.getByLabelText('推迟时长'), { target: { value: '1440' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存提醒设置' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/reminder-settings', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('"defaultSnoozeMinutes":1440') })))
  fireEvent.change(screen.getByLabelText('新邮箱'), { target: { value: 'new@example.test' } })
  fireEvent.click(screen.getByRole('button', { name: '发送验证码' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('请稍后再试')
})

it('shows a preference loading failure while leaving account security available', async () => {
  const fetcher = vi.fn(async (path: string) => path === '/api/profile/sessions' ? json([{ id: 's1', current: true, userAgent: 'Current browser', createdAt: '2026-10-01T00:00:00Z', expiresAt: '2026-11-01T00:00:00Z' }]) : path === '/api/reminder-settings' ? json({ code: 'unavailable', detail: '提醒设置暂不可用' }, 503) : json({ ...profile, recoveryEmail: 'old@example.test', emailVerified: false }))
  vi.stubGlobal('fetch', fetcher)
  render(<ProfilePage onExpired={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('提醒设置暂不可用')
  expect(screen.getByText('当前邮箱：old@example.test（待验证）')).toBeInTheDocument()
  expect(screen.getByText('当前设备')).toBeInTheDocument()
  expect(screen.getByText('正在加载提醒设置…')).toBeInTheDocument()
})
