import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { listApplications } from '../applications/api'
import type { ApplicationSummary } from '../applications/api'
import { ApiError } from '../../shared/api/client'
import { createReminder, getReminderPreferences, listReminders, transitionReminder, updateReminder, updateReminderPreferences } from './api'
import type { ReminderDraft, ReminderItem, ReminderPreferences, ReminderSummary } from './api'

type Tab = 'active' | 'completed' | 'cancelled'
type Draft = Omit<ReminderDraft, 'eventAt' | 'notifyAt'> & { eventAt: string; notifyAt: string }

const pad = (value: number) => String(value).padStart(2, '0')
const localTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
const initialDraft = (): Draft => ({ applicationId: '', title: '', eventAt: localTime(new Date(Date.now() + 86_400_000).toISOString()), notifyAt: localTime(new Date(Date.now() + 3_600_000).toISOString()), emailEnabled: false })
const snoozedAt = (minutes: number) => new Date(Date.now() + minutes * 60_000).toISOString()
const message = (error: unknown) => error instanceof ApiError ? error.message : '操作未完成，请稍后重试。'

export default function ReminderPage({ onExpired }: { onExpired: () => void }) {
  const [tab, setTab] = useState<Tab>('active')
  const [summary, setSummary] = useState<ReminderSummary | null>(null)
  const [preferences, setPreferences] = useState<ReminderPreferences | null>(null)
  const [applications, setApplications] = useState<ApplicationSummary[]>([])
  const [form, setForm] = useState<Draft | null>(null)
  const [editing, setEditing] = useState<ReminderItem | null>(null)
  const [reopening, setReopening] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    listReminders(tab).then(value => { if (active) { setSummary(value); setError('') } })
      .catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    return () => { active = false }
  }, [tab, reload, onExpired])

  useEffect(() => {
    let active = true
    getReminderPreferences().then(value => { if (active) setPreferences(value) })
      .catch(caught => { if (active && caught instanceof ApiError && caught.status === 401) onExpired() })
    listApplications(new URLSearchParams('limit=100')).then(value => { if (active) setApplications(value.items) })
      .catch(caught => { if (active && caught instanceof ApiError && caught.status === 401) onExpired() })
    return () => { active = false }
  }, [onExpired])

  function openForm(item?: ReminderItem, reopen = false) {
    setEditing(item ?? null)
    setReopening(reopen)
    setForm(item ? { applicationId: item.applicationId, title: item.title, eventAt: reopen ? initialDraft().eventAt : localTime(item.eventAt), notifyAt: reopen ? initialDraft().notifyAt : localTime(item.notifyAt), emailEnabled: item.emailEnabled } : { ...initialDraft(), applicationId: applications[0]?.id ?? '', emailEnabled: preferences?.emailDefault ?? false })
    setError(''); setNotice('')
  }

  function selectTab(value: Tab) {
    setSummary(null)
    setTab(value)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form || busy) return
    const eventAt = new Date(form.eventAt)
    const notifyAt = new Date(form.notifyAt)
    if (!form.applicationId || !form.title.trim() || Number.isNaN(eventAt.getTime()) || Number.isNaN(notifyAt.getTime()) || notifyAt <= new Date() || notifyAt > eventAt) {
      setError('请填写投递、待办内容和未来的提醒时间；提醒时间不能晚于事项时间。'); return
    }
    const body = { ...form, title: form.title.trim(), eventAt: eventAt.toISOString(), notifyAt: notifyAt.toISOString() }
    setBusy(true); setError('')
    try {
      if (editing && reopening) await transitionReminder(editing, 'reopen', { eventAt: body.eventAt, notifyAt: body.notifyAt })
      else if (editing) await updateReminder(editing.id, { ...body, version: editing.version })
      else await createReminder(body)
      setForm(null); setEditing(null); setReopening(false); setNotice(reopening ? '提醒已重开。' : editing ? '提醒已更新。' : '提醒已创建。'); setTab('active'); setReload(value => value + 1)
    } catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  async function act(item: ReminderItem, action: 'complete' | 'snooze' | 'cancel' | 'retry-email') {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try {
      const body = action === 'snooze' ? { notifyAt: snoozedAt(preferences?.defaultSnoozeMinutes ?? 30) } : {}
      await transitionReminder(item, action, body)
      setNotice(action === 'retry-email' ? '邮件已重新加入发送队列。' : action === 'complete' ? '提醒已完成。' : action === 'snooze' ? '提醒已推迟。' : '提醒已取消。')
      setReload(value => value + 1)
    } catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  async function savePreferences(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!preferences || busy) return
    setBusy(true); setError('')
    try { setPreferences(await updateReminderPreferences(preferences)); setNotice('提醒设置已保存。') }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  const groups = tab === 'active' ? [{ label: '已到期', items: summary?.overdue ?? [] }, { label: '接下来', items: summary?.upcoming ?? [] }] : [{ label: tab === 'completed' ? '已完成' : '已取消', items: summary?.history ?? [] }]
  return <section className="reminders-content">
    <div className="feature-toolbar"><p>把待跟进的事项放在时间线上，完成后仍可回看。</p><button disabled={applications.length === 0} onClick={() => openForm()}>新建提醒</button></div>
    {applications.length === 0 && <p className="muted-note">先创建一条投递记录，才能为它设置提醒。</p>}
    <div className="segmented-tabs" role="group" aria-label="提醒状态">{([['active', '待处理'], ['completed', '已完成'], ['cancelled', '已取消']] as const).map(([value, label]) => <button key={value} type="button" aria-pressed={tab === value} onClick={() => selectTab(value)}>{label}</button>)}</div>
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {!summary && !error ? <p role="status">正在加载提醒…</p> : summary && groups.map(group => <div key={group.label} className="reminder-group"><h2>{group.label} <span>{group.items.length}</span></h2>{group.items.length === 0 ? <p className="muted-note">这里还没有提醒。</p> : <ul className="reminder-list">{group.items.map(item => <li key={item.id} className="reminder-card"><div><p className="reminder-context">{item.companyName} · {item.positionName}</p><h3>{item.title}</h3><p>事项时间 <time dateTime={item.eventAt}>{new Date(item.eventAt).toLocaleString('zh-CN')}</time> · 提醒时间 <time dateTime={item.notifyAt}>{new Date(item.notifyAt).toLocaleString('zh-CN')}</time></p>{item.emailStatus === 'failed' && <p className="reminder-warning">邮件发送失败，可重试。</p>}</div><div className="reminder-actions">{tab === 'active' ? <><button disabled={busy} onClick={() => void act(item, 'complete')}>完成</button><button disabled={busy} onClick={() => void act(item, 'snooze')}>推迟</button><button disabled={busy} onClick={() => openForm(item)}>编辑</button><button disabled={busy} onClick={() => void act(item, 'cancel')}>取消</button></> : <button disabled={busy} onClick={() => openForm(item, true)}>重开</button>}{item.emailStatus === 'failed' && <button disabled={busy} onClick={() => void act(item, 'retry-email')}>重试邮件</button>}</div></li>)}</ul>}</div>)}
    {form && <form className="content-card reminder-form" onSubmit={event => void save(event)}><h2>{reopening ? '重开提醒' : editing ? '编辑提醒' : '新建提醒'}</h2><label>关联投递<select value={form.applicationId} disabled={reopening} onChange={event => setForm({ ...form, applicationId: event.target.value })} required>{applications.map(item => <option key={item.id} value={item.id}>{item.companyName} · {item.positionName}</option>)}</select></label><label>待办内容<input value={form.title} maxLength={120} disabled={reopening} onChange={event => setForm({ ...form, title: event.target.value })} required /></label><div className="form-pair"><label>事项时间<input type="datetime-local" value={form.eventAt} onChange={event => setForm({ ...form, eventAt: event.target.value })} required /></label><label>提醒时间<input type="datetime-local" value={form.notifyAt} onChange={event => setForm({ ...form, notifyAt: event.target.value })} required /></label></div>{!reopening && <label className="checkbox-line"><input type="checkbox" checked={form.emailEnabled} disabled={!summary?.email.available} onChange={event => setForm({ ...form, emailEnabled: event.target.checked })} />同时发送邮件{summary?.email.available ? `至 ${summary.email.address}` : '（请先绑定邮箱）'}</label>}<div className="form-actions"><button type="submit" disabled={busy}>{busy ? '正在保存…' : reopening ? '确认重开' : '保存提醒'}</button><button type="button" className="quiet-button" onClick={() => setForm(null)}>取消</button></div></form>}
    {preferences && <form className="content-card reminder-settings" onSubmit={event => void savePreferences(event)}><h2>提醒偏好</h2><label className="checkbox-line"><input type="checkbox" checked={preferences.homeEnabled} onChange={event => setPreferences({ ...preferences, homeEnabled: event.target.checked })} />在首页显示提醒</label><div className="form-pair"><label>首页视图<select value={preferences.homeView} onChange={event => setPreferences({ ...preferences, homeView: event.target.value as ReminderPreferences['homeView'] })}><option value="scheduled">按时间</option><option value="suggestions">跟进建议</option></select></label><label>默认提前量<select value={preferences.defaultLead} onChange={event => setPreferences({ ...preferences, defaultLead: event.target.value as ReminderPreferences['defaultLead'] })}><option value="on_time">准时</option><option value="30m">提前 30 分钟</option><option value="1h">提前 1 小时</option><option value="1d">提前 1 天</option></select></label><label>推迟时长<select value={preferences.defaultSnoozeMinutes} onChange={event => setPreferences({ ...preferences, defaultSnoozeMinutes: Number(event.target.value) as ReminderPreferences['defaultSnoozeMinutes'] })}><option value={30}>30 分钟</option><option value={60}>1 小时</option><option value={1440}>1 天</option></select></label></div><label className="checkbox-line"><input type="checkbox" checked={preferences.emailDefault} disabled={!summary?.email.available} onChange={event => setPreferences({ ...preferences, emailDefault: event.target.checked })} />新提醒默认发送邮件</label><button type="submit" disabled={busy}>{busy ? '正在保存…' : '保存偏好'}</button></form>}
  </section>
}
