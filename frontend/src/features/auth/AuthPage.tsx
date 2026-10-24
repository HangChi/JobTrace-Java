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

function FieldIcon({ kind }: { kind: 'user' | 'mail' | 'lock' | 'star' }) {
  return <span className="auth-input-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{kind === 'user' ? <><circle cx="12" cy="8" r="3.25" /><path d="M5.75 19c.55-3.25 2.63-5 6.25-5s5.7 1.75 6.25 5" /></> : kind === 'mail' ? <><rect x="3.5" y="5.5" width="17" height="13" rx="2" /><path d="m4.5 7 7.5 6 7.5-6" /></> : kind === 'star' ? <path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z" /> : <><rect x="5" y="10" width="14" height="10" rx="2.25" /><path d="M8.25 10V7.5a3.75 3.75 0 0 1 7.5 0V10M12 14v2" /></>}</svg></span>
}

function VisibilityToggle({ shown, onToggle }: { shown: boolean; onToggle: () => void }) {
  return <button className="auth-visibility-toggle" type="button" aria-label={shown ? '隐藏密码' : '显示密码'} aria-pressed={shown} onClick={onToggle}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3.5 12s3-5 8.5-5 8.5 5 8.5 5-3 5-8.5 5-8.5-5-8.5-5Z" /><circle cx="12" cy="12" r="2.5" />{!shown && <path d="m4 4 16 16" />}</svg></button>
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
    <section className="auth-surface" aria-labelledby="auth-title">
      <div className="auth-surface-top"><a className="auth-brand" href="/login" onClick={event => onLink(event, '/login')} aria-label="JobTrace 职迹首页"><span className="auth-brand-mark" aria-hidden="true">J</span><span><strong>JobTrace</strong><small>职迹</small></span></a>{path === '/register' && <p className="auth-top-switch">已经有账号？ <a href="/login" onClick={event => onLink(event, '/login')}>返回登录</a></p>}</div>
      <header className="auth-heading"><h1 id="auth-title">{titles[path] ?? '登录 JobTrace'}</h1><p>{descriptions[path]}</p></header>
      {query.has('registered') && <p className="notice" role="status">账号已创建，请登录。</p>}
      {query.has('reset') && <p className="notice" role="status">密码已重置，请登录。</p>}
      {query.has('error') && <p className="error" role="alert">重置链接无效或已过期，请重新申请。</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="notice" role="status">{notice}</p>}
      <form className={path === '/register' ? 'auth-form auth-register-form' : 'auth-form'} onSubmit={submit}>
        {path === '/login' && <>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-identifier">邮箱或用户名</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="user" /><input id="auth-identifier" name="identifier" autoComplete="username" placeholder="邮箱或用户名" required /></div></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-password">密码</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="lock" /><input id="auth-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="输入你的密码" required /><VisibilityToggle shown={showPassword} onToggle={() => setShowPassword(value => !value)} /></div></div>
          <a className="auth-recovery-link" href="/forgot-password" onClick={event => onLink(event, '/forgot-password')}>忘记密码？</a>
        </>}
        {path === '/register' && <>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-username">用户名</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="user" /><input id="auth-username" name="username" autoComplete="username" minLength={3} maxLength={30} pattern="[A-Za-z0-9_]+" placeholder="例如：lin_2026" required /></div><small className="auth-field-hint">3–30 位字母、数字或下划线</small></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-display-name">昵称</label><small>选填</small></div><div className="auth-input-wrap"><FieldIcon kind="star" /><input id="auth-display-name" name="displayName" autoComplete="name" placeholder="希望我们怎么称呼你" /></div></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-email">邮箱</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="mail" /><input id="auth-email" name="email" type="email" autoComplete="email" placeholder="name@example.com" required value={email} onChange={event => setEmail(event.target.value)} /><button type="button" className="auth-send-code" disabled={busy} onClick={() => void sendCode()}>发送验证码</button></div></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-code">邮箱验证码</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="mail" /><input id="auth-code" name="verificationCode" inputMode="numeric" minLength={6} maxLength={6} pattern="[0-9]{6}" placeholder="6 位验证码" required /></div></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-register-password">密码</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="lock" /><input id="auth-register-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={16} placeholder="8–16 位密码" required /><VisibilityToggle shown={showPassword} onToggle={() => setShowPassword(value => !value)} /></div><small className="auth-field-hint">8–16 位字符</small></div>
          <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-confirm-password">确认密码</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="lock" /><input id="auth-confirm-password" name="confirmPassword" type={showPassword ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={16} placeholder="再次输入密码" required /></div></div>
        </>}
        {path === '/forgot-password' && <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-recovery-email">注册邮箱</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="mail" /><input id="auth-recovery-email" name="email" type="email" autoComplete="email" required /></div></div>}
        {path === '/reset-password' && <div className="auth-field"><div className="auth-field-label"><label htmlFor="auth-new-password">新密码</label><small>必填</small></div><div className="auth-input-wrap"><FieldIcon kind="lock" /><input id="auth-new-password" name="newPassword" type="password" autoComplete="new-password" minLength={8} required /></div></div>}
        <button className="auth-submit" type="submit" disabled={busy || (path === '/reset-password' && !query.get('token'))}>{busy ? '正在处理…' : path === '/login' ? '登录' : path === '/register' ? '创建账号' : path === '/forgot-password' ? '发送重置说明' : '更新密码'} {!busy && <span aria-hidden="true">→</span>}</button>
      </form>
      {path === '/register' && <p className="auth-terms">创建账号即表示你同意妥善保管自己的登录信息。</p>}
      {path !== '/register' && <p className="auth-switch">{path === '/login' ? <>还没有账号？ <a href="/register" onClick={event => onLink(event, '/register')}>创建账号</a></> : <>已有账号？ <a href="/login" onClick={event => onLink(event, '/login')}>返回登录</a></>}</p>}
    </section>
  </main>
}
