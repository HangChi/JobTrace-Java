import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { bindEmail, changePassword, getProfile, listSessions, requestEmailCode, revokeSession, unbindEmail, updateProfile, uploadAvatar } from './api'
import type { AccountSession, Profile } from './api'

const message = (error: unknown) => error instanceof ApiError ? error.message : '操作未完成，请稍后重试。'

export default function ProfilePage({ onExpired }: { onExpired: () => void }) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [sessions, setSessions] = useState<AccountSession[]>([])
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [emailPassword, setEmailPassword] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([getProfile(), listSessions()]).then(([value, activeSessions]) => {
      if (active) { setProfile(value); setDisplayName(value.displayName); setEmail(value.recoveryEmail ?? ''); setSessions(activeSessions) }
    }).catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    return () => { active = false }
  }, [onExpired])

  async function execute(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await work() }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profile) return
    void execute(async () => { const value = await updateProfile(displayName.trim(), profile.image); setProfile(value); setDisplayName(value.displayName); setNotice('个人资料已保存。') })
  }

  function savePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void execute(async () => { const result = await changePassword(currentPassword, newPassword); setCurrentPassword(''); setNewPassword(''); setNotice(result.message) })
  }

  function saveEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void execute(async () => { await bindEmail(email.trim(), code.trim(), emailPassword); setProfile(await getProfile()); setCode(''); setEmailPassword(''); setNotice('安全邮箱已绑定。') })
  }

  return <section className="profile-content">
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {!profile && !error ? <p role="status">正在加载个人资料…</p> : profile && <>
      <section className="content-card"><h2>个人资料</h2><form className="profile-form" onSubmit={saveProfile}><div className="profile-avatar">{profile.image ? <img src={profile.image} alt="当前头像" /> : <span aria-hidden="true">{profile.displayName.slice(0, 1)}</span>}<label>上传头像<input type="file" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={event => { const file = event.target.files?.[0]; if (file) void execute(async () => { const uploaded = await uploadAvatar(file); const value = await updateProfile(displayName.trim(), uploaded.url); setProfile(value); setNotice('头像已更新。') }) }} /></label></div><label>显示名称<input value={displayName} maxLength={80} onChange={event => setDisplayName(event.target.value)} required /></label><button disabled={busy} type="submit">保存资料</button></form></section>
      <section className="content-card"><h2>安全邮箱</h2><p>{profile.recoveryEmail ? `当前邮箱：${profile.recoveryEmail}${profile.emailVerified ? '（已验证）' : '（待验证）'}` : '尚未绑定安全邮箱。'}</p><form className="profile-form" onSubmit={saveEmail}><label>新邮箱<input type="email" value={email} onChange={event => setEmail(event.target.value)} required /></label><div className="form-pair"><label>验证码<input value={code} onChange={event => setCode(event.target.value)} required /></label><button type="button" className="quiet-button" disabled={busy || !email.trim()} onClick={() => void execute(async () => { const result = await requestEmailCode(email.trim()); setNotice(result.message) })}>发送验证码</button></div><label>当前密码<input type="password" value={emailPassword} onChange={event => setEmailPassword(event.target.value)} required /></label><button disabled={busy} type="submit">绑定邮箱</button></form>{profile.recoveryEmail && <button className="danger-button" disabled={busy || !emailPassword} onClick={() => { if (window.confirm('确定解除安全邮箱绑定？')) void execute(async () => { await unbindEmail(emailPassword); setProfile(await getProfile()); setEmail(''); setEmailPassword(''); setNotice('安全邮箱已解绑。') }) }}>解除绑定（需在上方输入当前密码）</button>}</section>
      <section className="content-card"><h2>修改密码</h2><form className="profile-form" onSubmit={savePassword}><label>当前密码<input type="password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} required /></label><label>新密码<input type="password" value={newPassword} minLength={8} onChange={event => setNewPassword(event.target.value)} required /></label><button disabled={busy} type="submit">更新密码</button></form></section>
      <section className="content-card"><h2>登录设备</h2>{sessions.length === 0 ? <p className="muted-note">没有其他登录记录。</p> : <ul className="session-list">{sessions.map(item => <li key={item.id}><div><strong>{item.current ? '当前设备' : item.userAgent || '其他设备'}</strong><small>登录于 {new Date(item.createdAt).toLocaleString('zh-CN')} · 到期 {new Date(item.expiresAt).toLocaleString('zh-CN')}</small></div>{!item.current && <button className="quiet-button" disabled={busy} onClick={() => void execute(async () => { await revokeSession(item.id); setSessions(await listSessions()); setNotice('设备已退出。') })}>退出此设备</button>}</li>)}</ul>}</section>
    </>}
  </section>
}
