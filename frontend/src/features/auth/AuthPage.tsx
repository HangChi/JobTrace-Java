import { useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import { safePath } from '../../app/routes'
import { ApiError } from '../../shared/api/client'
import { getSession, login, register, requestEmailCode, requestPasswordReset, resetPassword } from './api'
import type { CurrentSession } from './api'

type Props = {
  path: string
  query: URLSearchParams
  navigate: (target: string, replace?: boolean) => void
  onLogin: (session: CurrentSession) => void
  onLink: (event: MouseEvent<HTMLAnchorElement>, target: string) => void
}

const titles: Record<string, string> = {
  '/login': '登录 JobTrace', '/register': '创建账号',
  '/forgot-password': '找回密码', '/reset-password': '设置新密码',
}
const descriptions: Record<string, string> = {
  '/login': '使用邮箱或用户名登录。', '/register': '创建账号后即可记录和管理投递。',
  '/forgot-password': '输入注册邮箱，我们会向你发送密码重置说明。', '/reset-password': '使用一个安全且容易记住的新密码。',
}

export default function AuthPage({ path, query, navigate, onLogin, onLink }: Props) {
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [email, setEmail] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const message = (caught: unknown) => caught instanceof ApiError ? caught.message : '暂时无法完成操作，请稍后重试。'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const value = (key: string) => String(data.get(key) ?? '').trim()
    setError(''); setNotice(''); setBusy(true)
    try {
      if (path === '/login') {
        const returnTo = safePath(query.get('returnTo'))
        const result = await login(value('identifier'), String(data.get('password') ?? ''), returnTo)
        const session = await getSession()
        onLogin(session)
        navigate(safePath(result.redirectTarget, returnTo), true)
      } else if (path === '/register') {
        const password = String(data.get('password') ?? '')
        if (password !== String(data.get('confirmPassword') ?? '')) {
          setError('两次输入的密码不一致。')
          return
        }
        await register({ username: value('username'), email: value('email'), verificationCode: value('verificationCode'), password, displayName: value('displayName') })
        navigate('/login?registered=1', true)
      } else if (path === '/forgot-password') {
        setNotice((await requestPasswordReset(value('email'))).message)
      } else if (path === '/reset-password') {
        await resetPassword(query.get('token') ?? '', String(data.get('newPassword') ?? ''))
        navigate('/login?reset=1', true)
      }
    } catch (caught) { setError(message(caught)) }
    finally { setBusy(false) }
  }

  async function sendCode() {
    if (!email.trim()) { setError('请先填写邮箱。'); return }
    setError(''); setNotice(''); setBusy(true)
    try { setNotice((await requestEmailCode(email.trim())).message) }
    catch (caught) { setError(message(caught)) }
    finally { setBusy(false) }
  }

  return <main className={`auth-layout${path === '/register' ? ' auth-layout-register' : ''}`}>
    <section className="auth-intro" aria-label="职迹简介">
      <a className="app-brand" href="/login" onClick={event => onLink(event, '/login')}><img className="app-brand-mark" src="/jobtrace-icon.svg" alt="" width="38" height="38" /><span><strong>JobTrace</strong><small>职迹</small></span></a>
    </section>
    <section className="auth-panel" aria-labelledby="auth-title">
      <div className="auth-card">
        <header className="auth-heading"><h1 id="auth-title">{titles[path] ?? '登录 JobTrace'}</h1><p>{descriptions[path]}</p></header>
        {query.has('registered') && <p className="notice" role="status">账号已创建，请登录。</p>}
        {query.has('reset') && <p className="notice" role="status">密码已重置，请登录。</p>}
        {query.has('error') && <p className="error" role="alert">重置链接无效或已过期，请重新申请。</p>}
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="notice" role="status">{notice}</p>}
        <form className={path === '/register' ? 'auth-register-form' : undefined} onSubmit={submit}>
          {path === '/login' && <>
            <label>邮箱或用户名<input name="identifier" autoComplete="username" required /></label>
            <label>密码<input name="password" type="password" autoComplete="current-password" required /></label>
            <a className="inline-link" href="/forgot-password" onClick={event => onLink(event, '/forgot-password')}>忘记密码？</a>
          </>}
          {path === '/register' && <>
            <label>用户名<input aria-label="用户名" name="username" autoComplete="username" minLength={3} maxLength={30} pattern="[A-Za-z0-9_]+" required /><small>3–30 位字母、数字或下划线</small></label>
            <label>显示名称 <small>选填</small><input name="displayName" autoComplete="name" /></label>
            <label>邮箱<input name="email" type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} /></label>
            <div className="code-row"><label>邮箱验证码<input name="verificationCode" inputMode="numeric" minLength={6} maxLength={6} pattern="[0-9]{6}" required /></label><button type="button" className="secondary" disabled={busy} onClick={() => void sendCode()}>发送验证码</button></div>
            <label>密码<span className="auth-password-field"><input aria-label="密码" name="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={16} required /><button type="button" className="auth-password-toggle" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? '隐藏密码' : '显示密码'}>{showPassword ? '隐藏' : '显示'}</button></span><small>8–16 位字符</small></label>
            <label>确认密码<input name="confirmPassword" type={showPassword ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={16} required /></label>
          </>}
          {path === '/forgot-password' && <label>注册邮箱<input name="email" type="email" autoComplete="email" required /></label>}
          {path === '/reset-password' && <label>新密码<input name="newPassword" type="password" autoComplete="new-password" minLength={8} required /></label>}
          <button type="submit" disabled={busy || (path === '/reset-password' && !query.get('token'))}>{busy ? '正在处理…' : path === '/login' ? '登录' : path === '/register' ? '创建账号' : path === '/forgot-password' ? '发送重置说明' : '更新密码'}</button>
        </form>
        <p className="auth-switch">{path === '/login' ? <>还没有账号？ <a href="/register" onClick={event => onLink(event, '/register')}>创建账号</a></> : <>已有账号？ <a href="/login" onClick={event => onLink(event, '/login')}>返回登录</a></>}</p>
      </div>
    </section>
  </main>
}
