import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AuthPage from './AuthPage'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
const navigate = vi.fn()
const onLogin = vi.fn()
const onLink = vi.fn()

function page(path: string, search = '') {
  return render(<AuthPage path={path} query={new URLSearchParams(search)} navigate={navigate} onLogin={onLogin} onLink={onLink} />)
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); navigate.mockClear(); onLogin.mockClear(); onLink.mockClear() })

describe('auth forms', () => {
  it('requests an email code and creates an account', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(json({ message: '验证码已发送' }, 202)).mockResolvedValueOnce(json({ message: '账号已创建' }, 202))
    vi.stubGlobal('fetch', fetcher)
    page('/register')
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'user@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }))
    expect(await screen.findByRole('status')).toHaveTextContent('验证码已发送')
    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'user' } })
    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '123456' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建账号' }))
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/login?registered=1', true))
    expect(fetcher).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({ method: 'POST' }))
  })

  it('requires an email before requesting a code and preserves an error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ detail: '发送失败' }, 503)))
    page('/register')
    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }))
    expect(screen.getByRole('alert')).toHaveTextContent('请先填写邮箱')
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'user@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('发送失败')
  })

  it('sends recovery mail without disclosing whether the email exists', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: '如果该邮箱存在，我们已发送密码重置说明。' })))
    page('/forgot-password')
    fireEvent.change(screen.getByLabelText('注册邮箱'), { target: { value: 'user@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '发送重置邮件' }))
    expect(await screen.findByRole('status')).toHaveTextContent('如果该邮箱存在')
  })

  it('requires a reset token and submits a valid one', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ status: true })))
    const view = page('/reset-password')
    expect(screen.getByRole('button', { name: '保存新密码' })).toBeDisabled()
    view.rerender(<AuthPage path="/reset-password" query={new URLSearchParams('token=abc')} navigate={navigate} onLogin={onLogin} onLink={onLink} />)
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '保存新密码' }))
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/login?reset=1', true))
  })

  it('shows reset failures and avoids a success redirect', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ code: 'invalid_reset_token', detail: '链接已过期' }, 400)))
    page('/reset-password', 'token=abc&error=INVALID_TOKEN')
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '保存新密码' }))
    await waitFor(() => expect(screen.getAllByRole('alert').some(node => node.textContent?.includes('链接已过期'))).toBe(true))
    expect(navigate).not.toHaveBeenCalled()
  })
})
