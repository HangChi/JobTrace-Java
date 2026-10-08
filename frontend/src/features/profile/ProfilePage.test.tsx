import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } })
const profile = { id: 'u1', displayName: '小李', image: null, recoveryEmail: null, emailVerified: false }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('updates the current owner profile and changes password with CSRF', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/profile/sessions') return json([])
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
