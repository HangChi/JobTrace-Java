import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { bindEmail, changePassword, getProfile, listSessions, requestEmailCode, revokeSession, unbindEmail, updateProfile, uploadAvatar } from './api'
import type { AccountSession, Profile } from './api'
import { getReminderPreferences, updateReminderPreferences } from '../reminders/api'
import type { ReminderPreferences } from '../reminders/api'

const message = (error: unknown) => error instanceof ApiError ? error.message : '操作未完成，请稍后重试。'

export default function ProfilePage({ onExpired, role }: { onExpired: () => void; role?: string }) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [sessions, setSessions] = useState<AccountSession[]>([])
  const [preferences, setPreferences] = useState<ReminderPreferences | null>(null)
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
    getReminderPreferences().then(value => { if (active) setPreferences(value) }).catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
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

  function savePreferences(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!preferences) return
    void execute(async () => { setPreferences(await updateReminderPreferences(preferences)); setNotice('提醒设置已保存。') })
  }

  return <section className="profile-page">
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {!profile && !error ? <p role="status">正在加载个人资料…</p> : profile && <>
      <header className="profile-hero content-card"><div className="profile-hero-identity">{profile.image ? <img src={profile.image} alt={`${profile.displayName}头像`} /> : <span aria-hidden="true">{profile.displayName.slice(0, 1)}</span>}<div><h1>个人中心</h1><p>{profile.displayName}</p></div></div><div className="profile-role-card"><small>账号状态</small><strong>{role === 'ADMIN' ? '管理员账号' : '个人账号'} · 正常</strong></div></header>
      <div className="profile-layout"><aside className="profile-index content-card"><p>设置</p><nav aria-label="个人中心分区"><a href="#profile-details">个人资料</a><a href="#profile-reminders">提醒设置</a><a href="#profile-security">账号安全</a><a href="#profile-data">数据管理</a></nav><p className="profile-index-note">管理个人资料、账号安全和数据。</p></aside><div className="profile-content">
      <section className="content-card" id="profile-details"><h2>个人资料</h2><form className="profile-form" onSubmit={saveProfile}><div className="profile-avatar">{profile.image ? <img src={profile.image} alt="当前头像" /> : <span aria-hidden="true">{profile.displayName.slice(0, 1)}</span>}<label>上传头像<input type="file" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={event => { const file = event.target.files?.[0]; if (file) void execute(async () => { const uploaded = await uploadAvatar(file); const value = await updateProfile(displayName.trim(), uploaded.url); setProfile(value); setNotice('头像已更新。') }) }} /></label></div><label>显示名称<input value={displayName} maxLength={80} onChange={event => setDisplayName(event.target.value)} required /></label><button disabled={busy} type="submit">保存资料</button></form></section>
      <section className="content-card" id="profile-reminders"><h2>提醒设置</h2><p>决定首页展示和新提醒的默认行为。</p>{preferences ? <form className="profile-form" onSubmit={savePreferences}><label className="checkbox-line"><input type="checkbox" checked={preferences.homeEnabled} onChange={event => setPreferences({ ...preferences, homeEnabled: event.target.checked })} />在首页显示提醒</label><div className="form-pair"><label>首页视图<select value={preferences.homeView} onChange={event => setPreferences({ ...preferences, homeView: event.target.value as ReminderPreferences['homeView'] })}><option value="scheduled">按时间</option><option value="suggestions">跟进建议</option></select></label><label>默认提前量<select value={preferences.defaultLead} onChange={event => setPreferences({ ...preferences, defaultLead: event.target.value as ReminderPreferences['defaultLead'] })}><option value="on_time">准时</option><option value="30m">提前 30 分钟</option><option value="1h">提前 1 小时</option><option value="1d">提前 1 天</option></select></label><label>推迟时长<select value={preferences.defaultSnoozeMinutes} onChange={event => setPreferences({ ...preferences, defaultSnoozeMinutes: Number(event.target.value) as ReminderPreferences['defaultSnoozeMinutes'] })}><option value={30}>30 分钟</option><option value={60}>1 小时</option><option value={1440}>1 天</option></select></label></div><label className="checkbox-line"><input type="checkbox" checked={preferences.emailDefault} disabled={!profile.emailVerified} onChange={event => setPreferences({ ...preferences, emailDefault: event.target.checked })} />新提醒默认发送邮件</label><button type="submit" disabled={busy}>{busy ? '正在保存…' : '保存提醒设置'}</button></form> : <p className="muted-note">正在加载提醒设置…</p>}</section>
      <section className="content-card" id="profile-security"><h2>账号安全</h2><h3>安全邮箱</h3><p>{profile.recoveryEmail ? `当前邮箱：${profile.recoveryEmail}${profile.emailVerified ? '（已验证）' : '（待验证）'}` : '尚未绑定安全邮箱。'}</p><form className="profile-form" onSubmit={saveEmail}><label>新邮箱<input type="email" value={email} onChange={event => setEmail(event.target.value)} required /></label><div className="form-pair"><label>验证码<input value={code} onChange={event => setCode(event.target.value)} required /></label><button type="button" className="quiet-button" disabled={busy || !email.trim()} onClick={() => void execute(async () => { const result = await requestEmailCode(email.trim()); setNotice(result.message) })}>发送验证码</button></div><label>当前密码<input type="password" value={emailPassword} onChange={event => setEmailPassword(event.target.value)} required /></label><button disabled={busy} type="submit">绑定邮箱</button></form>{profile.recoveryEmail && <button className="danger-button" disabled={busy || !emailPassword} onClick={() => { if (window.confirm('确定解除安全邮箱绑定？')) void execute(async () => { await unbindEmail(emailPassword); setProfile(await getProfile()); setEmail(''); setEmailPassword(''); setNotice('安全邮箱已解绑。') }) }}>解除绑定（需在上方输入当前密码）</button>}<h3>修改密码</h3><form className="profile-form" onSubmit={savePassword}><label>当前密码<input type="password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} required /></label><label>新密码<input type="password" value={newPassword} minLength={8} onChange={event => setNewPassword(event.target.value)} required /></label><button disabled={busy} type="submit">更新密码</button></form><h3>登录设备</h3>{sessions.length === 0 ? <p className="muted-note">没有其他登录记录。</p> : <ul className="session-list">{sessions.map(item => <li key={item.id}><div><strong>{item.current ? '当前设备' : item.userAgent || '其他设备'}</strong><small>登录于 {new Date(item.createdAt).toLocaleString('zh-CN')} · 到期 {new Date(item.expiresAt).toLocaleString('zh-CN')}</small></div>{!item.current && <button className="quiet-button" disabled={busy} onClick={() => void execute(async () => { await revokeSession(item.id); setSessions(await listSessions()); setNotice('设备已退出。') })}>退出此设备</button>}</li>)}</ul>}</section>
      <section className="content-card" id="profile-data"><h2>数据管理</h2><p>导入或导出当前账号的投递记录。</p><div className="form-actions"><a className="quiet-button profile-action-link" href="/import">导入投递记录</a><a className="quiet-button profile-action-link" href="/api/exports/applications?scope=all&format=xlsx">导出投递记录</a></div></section>
      </div></div>
    </>}
  </section>
}
