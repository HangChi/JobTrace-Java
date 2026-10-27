import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { addApplicationStage, createApplication, deleteApplication, getApplication, removeApplicationStage, updateApplication, updateApplicationStage, updateApplicationStatus } from './api'
import type { ApplicationDetail, ApplicationDraft, ApplicationSummary } from './api'
import ApplicationStageRow from './ApplicationStageRow'

const types = [['summer_internship', '暑期实习'], ['daily_internship', '日常实习'], ['spring_recruitment', '春招'], ['early_campus_recruitment', '提前批'], ['campus_recruitment', '校招'], ['social_recruitment', '社招']]
const stages = [['screening', '筛选'], ['assessment', '测评'], ['written_test', '笔试'], ['interview_1', '一面'], ['interview_2', '二面'], ['interview_3', '三面'], ['hr_interview', 'HR 面'], ['final_interview', '终面']]
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const blank = (): ApplicationDraft => ({ companyName: '', positionName: '', city: '', jobUrl: '', appliedDate: today(), type: 'campus_recruitment', status: 'submitted', notes: '', stages: [] })
const toDraft = (item: ApplicationDetail): ApplicationDraft => ({ companyName: item.companyName, positionName: item.positionName, city: item.city ?? '', jobUrl: item.jobUrl ?? '', appliedDate: item.appliedDate, type: item.type, status: item.status, notes: item.notes ?? '', stages: item.stageOccurrences.map(({ stage, occurredOn }) => ({ stage, occurredOn })) })
const errorText = (caught: unknown) => caught instanceof ApiError ? caught.message : '操作未完成，请稍后重试。'

export default function ApplicationEditor({ id, onClose, onChanged, onExpired, initialDraft }: { id: string | null; onClose: () => void; onChanged: () => void; onExpired: () => void; initialDraft?: Partial<ApplicationDraft> }) {
  const [detail, setDetail] = useState<ApplicationDetail | null>(null)
  const [draft, setDraft] = useState<ApplicationDraft>(() => ({ ...blank(), ...initialDraft }))
  const [stage, setStage] = useState('screening')
  const [stageDate, setStageDate] = useState(today)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(Boolean(id))

  useEffect(() => {
    if (!id) return
    let active = true
    getApplication(id).then(value => { if (active) { setDetail(value); setDraft(toDraft(value)); setLoading(false) } })
      .catch(caught => { if (active) { setLoading(false); if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) } })
    return () => { active = false }
  }, [id, onExpired])

  async function execute(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError('')
    try { await work() }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(errorText(caught)) }
    finally { setBusy(false) }
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void execute(async () => {
      if (detail) setDetail(await updateApplication(detail, draft, draft.appliedDate > today() ? draft.appliedDate : today()))
      else setDetail(await createApplication(draft))
      onChanged()
    })
  }

  function setStatus(status: ApplicationSummary['status']) {
    if (!detail) return
    void execute(async () => {
      const value = await updateApplicationStatus(detail, status)
      setDetail({ ...detail, status: value.status, version: value.version })
      setDraft({ ...draft, status: value.status })
      onChanged()
    })
  }

  function addStage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!detail) return
    void execute(async () => { const value = await addApplicationStage(detail, stage, stageDate); setDetail(value); setDraft(toDraft(value)); onChanged() })
  }

  return <section className="content-card application-editor" aria-label={id ? '投递详情' : '新建投递'}>
    <div className="editor-heading"><h2>{id ? '投递详情' : '新建投递'}</h2><button className="quiet-button" type="button" onClick={onClose}>关闭</button></div>
    {error && <p className="error" role="alert">{error}</p>}
    {loading ? <p role="status">正在加载投递详情…</p> : <>
      <form className="application-form" onSubmit={save}>
        <fieldset className="application-form-section"><legend>岗位信息</legend>
          <div className="application-form-grid"><label>公司名称<input value={draft.companyName} maxLength={200} required onChange={event => setDraft({ ...draft, companyName: event.target.value })} /></label><label>岗位名称<input value={draft.positionName} maxLength={200} required onChange={event => setDraft({ ...draft, positionName: event.target.value })} /></label></div>
          <div className="application-form-grid application-form-grid-link"><label>城市<input value={draft.city} maxLength={100} onChange={event => setDraft({ ...draft, city: event.target.value })} /></label><label>岗位链接<input type="url" value={draft.jobUrl} maxLength={2048} onChange={event => setDraft({ ...draft, jobUrl: event.target.value })} placeholder="https://" /></label></div>
        </fieldset>
        <fieldset className="application-form-section"><legend>投递进度</legend>
          <div className="application-form-grid application-form-grid-three"><label>投递日期<input type="date" value={draft.appliedDate} required onChange={event => setDraft({ ...draft, appliedDate: event.target.value })} /></label><label>招聘类型<select value={draft.type} onChange={event => setDraft({ ...draft, type: event.target.value })}>{types.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>状态<select className={`status-control status-${draft.status}`} value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value as ApplicationSummary['status'] })}><option value="submitted">已投递</option><option value="offer">已录用</option><option value="refused">未通过</option></select></label></div>
        </fieldset>
        <fieldset className="application-form-section"><legend>补充记录</legend><label>备注<textarea value={draft.notes} maxLength={10000} rows={4} onChange={event => setDraft({ ...draft, notes: event.target.value })} placeholder="记录沟通、面试安排或需要跟进的事项" /></label></fieldset>
        <div className="application-form-footer"><button type="submit" disabled={busy}>{busy ? '正在保存…' : detail ? '保存修改' : '创建投递'}</button></div>
      </form>
      {detail && <div className="application-operations"><h3>快捷操作</h3><div className="reminder-actions"><button disabled={busy || detail.status === 'submitted'} onClick={() => setStatus('submitted')}>标记已投递</button><button disabled={busy || detail.status === 'offer'} onClick={() => setStatus('offer')}>标记录用</button><button disabled={busy || detail.status === 'refused'} onClick={() => setStatus('refused')}>标记未通过</button></div><h3>招聘阶段</h3><ul className="stage-list">{detail.stageOccurrences.map(item => <ApplicationStageRow key={item.id} item={item} minimumDate={detail.appliedDate} busy={busy} onSave={(nextStage, occurredOn) => void execute(async () => { const value = await updateApplicationStage(detail, item.id, nextStage, occurredOn, today()); setDetail(value); setDraft(toDraft(value)); onChanged() })} onDelete={() => void execute(async () => { const value = await removeApplicationStage(detail, item.id, today()); setDetail(value); setDraft(toDraft(value)); onChanged() })} />)}</ul><form className="stage-add" onSubmit={addStage}><label>新增阶段<select value={stage} onChange={event => setStage(event.target.value)}>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>发生日期<input type="date" value={stageDate} min={detail.appliedDate} onChange={event => setStageDate(event.target.value)} required /></label><button disabled={busy} type="submit">添加阶段</button></form><button className="danger-button" disabled={busy} onClick={() => { if (window.confirm(`确定删除「${detail.companyName} · ${detail.positionName}」？此操作不可撤销。`)) void execute(async () => { await deleteApplication(detail.id); onChanged(); onClose() }) }}>删除投递</button></div>}
    </>}
  </section>
}
