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
  '/login': '欢迎回到职迹', '/register': '创建职迹账号',
  '/forgot-password': '找回密码', '/reset-password': '设置新密码',
}

export default function AuthPage({ path, query, navigate, onLogin, onLink }: Props) {
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [email, setEmail] = useState('')
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
        await register({ username: value('username'), email: value('email'), verificationCode: value('verificationCode'), password: String(data.get('password') ?? ''), displayName: value('displayName') })
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

  return <main className="auth-layout">
    <section className="auth-intro" aria-label="职迹简介">
      <a className="brand brand-light" href="/login" onClick={event => onLink(event, '/login')}>职迹 <span>JobTrace</span></a>
      <div><p className="eyebrow">把每一次尝试，变成看得见的进展</p><h1>求职路上，<br />每一步都有迹可循。</h1><p>整理投递、记录面试、掌握下一步。让信息归位，把精力留给真正重要的机会。</p></div>
      <p className="intro-footer">你的求职工作台 · 私密且有序</p>
    </section>
    <section className="auth-panel" aria-labelledby="auth-title">
      <div className="auth-card">
        <p className="eyebrow">JOBTRACE / ACCOUNT</p>
        <h2 id="auth-title">{titles[path] ?? '欢迎回到职迹'}</h2>
        {query.has('registered') && <p className="notice" role="status">账号已创建，请登录。</p>}
        {query.has('reset') && <p className="notice" role="status">密码已重置，请登录。</p>}
        {query.has('error') && <p className="error" role="alert">重置链接无效或已过期，请重新申请。</p>}
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="notice" role="status">{notice}</p>}
        <form onSubmit={submit}>
          {path === '/login' && <>
            <label>用户名或邮箱<input name="identifier" autoComplete="username" required /></label>
            <label>密码<input name="password" type="password" autoComplete="current-password" required /></label>
            <a className="inline-link" href="/forgot-password" onClick={event => onLink(event, '/forgot-password')}>忘记密码？</a>
          </>}
          {path === '/register' && <>
            <label>用户名<input name="username" autoComplete="username" required /></label>
            <label>显示名称<input name="displayName" autoComplete="name" /></label>
            <label>邮箱<input name="email" type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} /></label>
            <div className="code-row"><label>邮箱验证码<input name="verificationCode" inputMode="numeric" required /></label><button type="button" className="secondary" disabled={busy} onClick={() => void sendCode()}>发送验证码</button></div>
            <label>密码<input name="password" type="password" autoComplete="new-password" minLength={8} required /></label>
          </>}
          {path === '/forgot-password' && <label>注册邮箱<input name="email" type="email" autoComplete="email" required /></label>}
          {path === '/reset-password' && <label>新密码<input name="newPassword" type="password" autoComplete="new-password" minLength={8} required /></label>}
          <button type="submit" disabled={busy || (path === '/reset-password' && !query.get('token'))}>{busy ? '正在处理…' : path === '/login' ? '登录' : path === '/register' ? '创建账号' : path === '/forgot-password' ? '发送重置邮件' : '保存新密码'}</button>
        </form>
        <p className="auth-switch">{path === '/login' ? <>还没有账号？ <a href="/register" onClick={event => onLink(event, '/register')}>立即注册</a></> : <>已有账号？ <a href="/login" onClick={event => onLink(event, '/login')}>返回登录</a></>}</p>
      </div>
    </section>
  </main>
}
