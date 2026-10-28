import QueryFilterPanel from '../../shared/ui/QueryFilterPanel'
import '../../shared/ui/QueryToolbar.css'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { listApplications } from '../applications/api'
import type { ApplicationSummary } from '../applications/api'
import { ApiError } from '../../shared/api/client'
import { addPublicComment, createPrivateInterview, deletePrivateInterview, getPrivateInterview, getPublicInterview, listPrivateInterviews, listPublicComments, listPublicInterviews, togglePublicLike, updatePrivateInterview } from './api'
import type { Comment, InterviewDetail, InterviewPage, InterviewQuestion, PublicInterview, PublicInterviewPage } from './api'
import InterviewDocumentEditor from './InterviewDocumentEditor'
import { consolidateInterview } from './interviewDocument'
import { MarkdownPreview } from './MarkdownPreview'
import PublicInterviewCard from './PublicInterviewCard'

const stages = [['assessment', '测评'], ['interview_1', '一面'], ['interview_2', '二面'], ['interview_3', '三面'], ['hr_interview', 'HR 面'], ['final_interview', '终面']]
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const emptyQuestion = (): InterviewQuestion => ({ category: 'other', question: '', originalAnswer: '', followUpNotes: '', improvedAnswer: '', selfRating: null })
const message = (error: unknown) => error instanceof ApiError ? error.message : '面经操作未完成，请稍后重试。'

export default function InterviewsPage({ onExpired, initialTab = 'private', navigate, initialPrivateId, initialPublicId, startCreating = false }: { onExpired: () => void; initialTab?: 'private' | 'public'; navigate?: (path: string) => void; initialPrivateId?: string; initialPublicId?: string; startCreating?: boolean }) {
  const [tab, setTab] = useState<'private' | 'public'>(initialTab)
  const [privatePage, setPrivatePage] = useState<InterviewPage | null>(null)
  const [publicPage, setPublicPage] = useState<PublicInterviewPage | null>(null)
  const [applications, setApplications] = useState<ApplicationSummary[]>([])
  const [detail, setDetail] = useState<InterviewDetail | null>(null)
  const [publicDetail, setPublicDetail] = useState<PublicInterview | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [comment, setComment] = useState('')
  const [search, setSearch] = useState('')
  const [city, setCity] = useState('')
  const [position, setPosition] = useState('')
  const [sort, setSort] = useState('latest')
  const [privateCompany, setPrivateCompany] = useState('')
  const [privateStage, setPrivateStage] = useState('')
  const [privateStatus, setPrivateStatus] = useState('')
  const [privateResult, setPrivateResult] = useState('')
  const [privateSort, setPrivateSort] = useState('latest')
  const [newApplication, setNewApplication] = useState('')
  const [newStage, setNewStage] = useState('interview_1')
  const [newDate, setNewDate] = useState(today)
  const [questions, setQuestions] = useState<InterviewQuestion[]>([emptyQuestion()])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [format, setFormat] = useState<string>('')
  const [durationMinutes, setDurationMinutes] = useState('')
  const [interviewerNotes, setInterviewerNotes] = useState('')
  const [actionItems, setActionItems] = useState<InterviewDetail['actionItems']>([])
  const [highlights, setHighlights] = useState('')
  const [gaps, setGaps] = useState('')
  const [status, setStatus] = useState<InterviewDetail['status']>('draft')
  const [visibility, setVisibility] = useState<InterviewDetail['visibility']>('private')
  const [authorMode, setAuthorMode] = useState<InterviewDetail['authorMode']>('anonymous')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const savedDraftRef = useRef<string | null>(null)
  const failedDraftRef = useRef<string | null>(null)
  const versionRef = useRef(0)

  useEffect(() => {
    let active = true
    listPrivateInterviews().then(value => { if (active) setPrivatePage(value) })
      .catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    listApplications(new URLSearchParams('limit=100')).then(value => { if (active) setApplications(value.items) })
      .catch(caught => { if (active && caught instanceof ApiError && caught.status === 401) onExpired() })
    return () => { active = false }
  }, [reload, onExpired])

  useEffect(() => {
    if (tab !== 'public') return
    let active = true
    const params = new URLSearchParams({ sort, limit: '20' })
    if (search.trim()) params.set('q', search.trim())
    if (city) params.set('city', city)
    if (position) params.set('position', position)
    listPublicInterviews(params).then(value => { if (active) setPublicPage(value) })
      .catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    return () => { active = false }
  }, [tab, sort, search, city, position, reload, onExpired])

  async function execute(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await work() }
    catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  function openDetail(value: InterviewDetail) {
    setDetail(value)
    versionRef.current = value.version
    savedDraftRef.current = null
    failedDraftRef.current = null
    const document = consolidateInterview(value.questions)
    setQuestions([document.question])
    setFormat(value.format ?? '')
    setDurationMinutes(value.durationMinutes == null ? '' : String(value.durationMinutes))
    setInterviewerNotes([value.interviewerNotes, document.privateMetadata].filter(Boolean).join('\n\n'))
    setActionItems(value.actionItems)
    setHighlights(value.highlights ?? '')
    setGaps(value.gaps ?? '')
    setStatus(value.status)
    setVisibility(value.visibility)
    setAuthorMode(value.authorMode)
  }

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!newApplication) { setError('请先选择关联投递。'); return }
    void execute(async () => { const value = await createPrivateInterview({ applicationId: newApplication, stage: newStage, interviewedOn: newDate, roundResult: 'pending' }); openDetail(value); setReload(index => index + 1); setNotice('面经草稿已创建，请继续填写内容。'); if (navigate) navigate(`/interviews/${encodeURIComponent(value.id)}`) })
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!detail) return
    if (status === 'completed' && !questions.some(item => item.question.trim())) { setError('完成复盘前请填写面经正文。'); return }
    if (visibility === 'public' && status !== 'completed') { setError('请先完成复盘，再公开面经。'); return }
    void execute(async () => {
      const validQuestions = questions.filter(item => item.question.trim()).map(item => ({ ...item, question: item.question.trim() }))
      const value = await updatePrivateInterview(detail.id, { version: versionRef.current, status, visibility, authorMode, roundResult: detail.roundResult, format: format || null, durationMinutes: durationMinutes ? Number(durationMinutes) : null, interviewerNotes, highlights, gaps, questions: validQuestions, actionItems: actionItems.filter(item => item.content.trim()) })
      openDetail(value); setReload(index => index + 1); setNotice('面经已保存。')
    })
  }

  const draftFingerprint = JSON.stringify({ questions, format, durationMinutes, interviewerNotes, highlights, gaps, actionItems, roundResult: detail?.roundResult })
  useEffect(() => {
    if (!detail || savedDraftRef.current === null) {
      if (detail) savedDraftRef.current = draftFingerprint
      return
    }
    if (status !== 'draft' || visibility !== 'private' || busy || draftFingerprint === savedDraftRef.current || draftFingerprint === failedDraftRef.current) return
    const timer = window.setTimeout(() => {
      setBusy(true)
      const validQuestions = questions.filter(item => item.question.trim()).map(item => ({ ...item, question: item.question.trim() }))
      updatePrivateInterview(detail.id, { version: versionRef.current, status: 'draft', visibility: 'private', authorMode, roundResult: detail.roundResult, format: format || null, durationMinutes: durationMinutes ? Number(durationMinutes) : null, interviewerNotes, highlights, gaps, questions: validQuestions, actionItems: actionItems.filter(item => item.content.trim()) })
        .then(value => { versionRef.current = value.version; setDetail(value); savedDraftRef.current = draftFingerprint; setNotice('草稿已自动保存。') })
        .catch(caught => { failedDraftRef.current = draftFingerprint; if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) })
        .finally(() => setBusy(false))
    }, 800)
    return () => window.clearTimeout(timer)
  }, [detail, draftFingerprint, questions, format, durationMinutes, interviewerNotes, highlights, gaps, actionItems, status, visibility, authorMode, busy, onExpired])

  function openPublic(id: string) {
    void execute(async () => { const [value, discussion] = await Promise.all([getPublicInterview(id), listPublicComments(id)]); setPublicDetail(value); setComments(discussion) })
  }

  useEffect(() => {
    if (!initialPrivateId) return
    let active = true
    getPrivateInterview(initialPrivateId).then(value => { if (active) openDetail(value) })
      .catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    return () => { active = false }
  }, [initialPrivateId, onExpired])

  useEffect(() => {
    if (!initialPublicId) return
    let active = true
    Promise.all([getPublicInterview(initialPublicId), listPublicComments(initialPublicId)]).then(([value, discussion]) => { if (active) { setPublicDetail(value); setComments(discussion) } })
      .catch(caught => { if (active) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) } })
    return () => { active = false }
  }, [initialPublicId, onExpired])

  function morePrivate() {
    if (!privatePage?.nextCursor) return
    void execute(async () => {
      const next = await listPrivateInterviews(privatePage.nextCursor ?? undefined)
      setPrivatePage({ ...next, items: [...privatePage.items, ...next.items] })
    })
  }

  function morePublic() {
    if (!publicPage?.nextCursor) return
    void execute(async () => {
      const params = new URLSearchParams({ sort, limit: '20', cursor: publicPage.nextCursor! })
      if (search.trim()) params.set('q', search.trim())
      if (city) params.set('city', city)
      if (position) params.set('position', position)
      const next = await listPublicInterviews(params)
      setPublicPage({ ...next, items: [...publicPage.items, ...next.items] })
    })
  }

  function toggleSelectedInterview(id: string) {
    if (!selectedIds.has(id) && selectedIds.size >= 100) { setError('一次最多选择 100 篇面经。'); return }
    setSelectedIds(current => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next })
  }

  const visiblePrivateItems = (privatePage?.items ?? []).filter(item =>
    item.companyName.toLocaleLowerCase().includes(privateCompany.trim().toLocaleLowerCase()) &&
    (!privateStage || item.stage === privateStage) &&
    (!privateStatus || item.status === privateStatus) &&
    (!privateResult || item.roundResult === privateResult)
  ).sort((left, right) => privateSort === 'oldest' ? left.interviewedOn.localeCompare(right.interviewedOn) : right.interviewedOn.localeCompare(left.interviewedOn))
  const PublicTitle = initialPublicId ? 'h1' : 'h2'
  const selectedExport = new URLSearchParams()
  selectedIds.forEach(id => selectedExport.append('id', id))

  return <section className={`interviews-content ${tab === 'public' ? 'interview-square-page' : 'interview-private-page'}${initialPublicId ? ' interview-reading-page' : ''}`}>
    {navigate ? tab === 'private' ? initialPrivateId || startCreating ? <div className="interview-route-actions"><button className="quiet-button interview-switch" onClick={() => navigate('/interviews/mine')}>← 返回个人面经</button></div> : null : initialPublicId ? <button className="quiet-button interview-switch" onClick={() => navigate('/interviews')}>← 返回面经广场</button> : null : <div className="segmented-tabs" role="group" aria-label="面经分类"><button aria-pressed={tab === 'private'} onClick={() => setTab('private')}>我的面经</button><button aria-pressed={tab === 'public'} onClick={() => setTab('public')}>公开面经</button></div>}
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {tab === 'private' ? <>
      {(!navigate || startCreating) && <form className="content-card interview-create" onSubmit={create}><h2>记录一次面试</h2><p className="muted-note interview-form-intro">先关联投递和面试轮次，创建草稿后继续记录问题与复盘。</p><div className="form-pair"><label>关联投递<select value={newApplication} onChange={event => setNewApplication(event.target.value)} required><option value="">请选择投递</option>{applications.map(item => <option key={item.id} value={item.id}>{item.companyName} · {item.positionName}</option>)}</select></label><label>轮次<select value={newStage} onChange={event => setNewStage(event.target.value)}>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>日期<input type="date" value={newDate} onChange={event => setNewDate(event.target.value)} required /></label></div><button disabled={busy || applications.length === 0} type="submit">创建草稿</button></form>}
      {(!navigate || (!startCreating && !initialPrivateId)) && <div className="private-interview-filters" role="group" aria-label="筛选复盘记录"><label>搜索公司<input value={privateCompany} onChange={event => setPrivateCompany(event.target.value)} placeholder="公司名称" /></label><label>面试轮次<select value={privateStage} onChange={event => setPrivateStage(event.target.value)}><option value="">全部轮次</option>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>筛选状态<select value={privateStatus} onChange={event => setPrivateStatus(event.target.value)}><option value="">全部状态</option><option value="draft">草稿</option><option value="pending_review">待复盘</option><option value="completed">已完成</option></select></label><label>面试结果<select value={privateResult} onChange={event => setPrivateResult(event.target.value)}><option value="">全部结果</option><option value="pending">待反馈</option><option value="passed">通过</option><option value="failed">未通过</option></select></label><label>排序<select value={privateSort} onChange={event => setPrivateSort(event.target.value)}><option value="latest">最新面试</option><option value="oldest">最早面试</option></select></label><button type="button" className="quiet-button" onClick={() => { setPrivateCompany(''); setPrivateStage(''); setPrivateStatus(''); setPrivateResult(''); setPrivateSort('latest') }}>清除</button></div>}
      {(!navigate || (!startCreating && !initialPrivateId)) && (!privatePage ? <p role="status">正在加载我的面经…</p> : <section className="interview-list-panel" aria-label="复盘记录"><header><h2>复盘记录</h2><span>共 {visiblePrivateItems.length} 篇</span></header>{selectedIds.size > 0 && <div className="form-actions interview-selection-actions"><span>已选择 {selectedIds.size} 篇</span><a className="quiet-button" href={`/api/exports/interviews?${selectedExport}`}>{selectedIds.size === 1 ? "导出 Markdown" : "导出 ZIP"}</a><button type="button" className="quiet-button" onClick={() => setSelectedIds(new Set())}>清除选择</button></div>}{visiblePrivateItems.length === 0 ? <p className="muted-note interview-empty">{privatePage.items.length === 0 ? '还没有面经，先记录一次面试。' : '没有符合条件的复盘记录。'}</p> : <ul className="interview-record-list">{visiblePrivateItems.map(item => <li key={item.id}><input type="checkbox" aria-label={`选择 ${item.companyName} ${item.positionName} 面经`} checked={selectedIds.has(item.id)} onChange={() => toggleSelectedInterview(item.id)} /><time dateTime={item.interviewedOn}>{item.interviewedOn}</time><div className="interview-record-main"><button type="button" className="interview-record-title" disabled={busy} onClick={() => navigate ? navigate(`/interviews/${encodeURIComponent(item.id)}`) : void execute(async () => openDetail(await getPrivateInterview(item.id)))}>{item.companyName} · {item.positionName}</button><small>{stages.find(([key]) => key === item.stage)?.[1] ?? item.stage}</small></div><div className="interview-record-status"><span className={`review-state review-state-${item.status}`}>{item.status === 'completed' ? '已完成' : item.status === 'pending_review' ? '待复盘' : '草稿'}</span><span className={`review-result review-result-${item.roundResult}`}>{item.roundResult === 'passed' ? '通过' : item.roundResult === 'failed' ? '未通过' : '待反馈'}</span><span>{item.visibility === 'public' ? item.authorMode === 'anonymous' ? '匿名公开' : '署名公开' : '私有'}</span></div><a href={`/api/exports/interviews?id=${encodeURIComponent(item.id)}`}>导出</a><button type="button" className="quiet-button interview-record-edit" disabled={busy} onClick={() => navigate ? navigate(`/interviews/${encodeURIComponent(item.id)}`) : void execute(async () => openDetail(await getPrivateInterview(item.id)))}>查看与编辑</button></li>)}</ul>}{privatePage.nextCursor && <button className="quiet-button" disabled={busy} onClick={morePrivate}>加载更多面经</button>}</section>)}
      {detail && <form className="interview-editor interview-document" onSubmit={save}>
        <header className="interview-document-heading">
          <span>已关联招聘阶段</span>
          <div className="editor-heading"><h2>{detail.companyName} · {detail.positionName}</h2><button className="quiet-button" type="button" onClick={() => navigate ? navigate('/interviews/mine') : setDetail(null)}>关闭</button></div>
          <p>{detail.interviewedOn} · {stages.find(([key]) => key === detail.stage)?.[1] ?? detail.stage}</p>
        </header>
        <section className="interview-document-card" aria-label="面试基本信息">
          <div className="editor-heading"><h3>面试背景</h3><span className="interview-document-badge">{status === 'completed' ? '已完成' : status === 'pending_review' ? '待复盘' : '草稿'}</span></div>
          <div className="interview-background-grid">
            <label>面试 / 测评日期<input type="date" value={detail.interviewedOn} readOnly /></label>
            <label>面试形式<select value={format} onChange={event => setFormat(event.target.value)}><option value="">未填写</option><option value="online">线上</option><option value="offline">线下</option><option value="phone">电话</option></select></label>
            <label>面试时长（分钟）<input type="number" min={1} max={600} value={durationMinutes} onChange={event => setDurationMinutes(event.target.value)} /></label>
            <label>本轮结果<select value={detail.roundResult} onChange={event => setDetail({ ...detail, roundResult: event.target.value as InterviewDetail['roundResult'] })}><option value="pending">待反馈</option><option value="passed">通过</option><option value="failed">未通过</option></select></label>
          </div>
        </section>
        <section className="interview-document-card" aria-label="面经内容">
          <InterviewDocumentEditor value={questions[0].question} onChange={value => setQuestions(current => [{ ...current[0], question: value }])} />

        </section>
        <section className="interview-document-card" aria-label="发布设置">
          <div className="editor-heading"><div><h3>分享设置</h3><p className="interview-document-note">决定这篇面经是否出现在面经广场。</p></div><span className="interview-document-badge">{visibility === 'private' ? '私有' : '公开'}</span></div>
          <fieldset className="interview-visibility"><legend>可见范围</legend>
            <label><input type="radio" name="interview-visibility" checked={visibility === 'private'} onChange={() => setVisibility('private')} /><span><strong>仅自己可见</strong><small>保留在个人面经中</small></span></label>
            <label><input type="radio" name="interview-visibility" checked={visibility === 'public'} onChange={() => setVisibility('public')} /><span><strong>公开到面经广场</strong><small>登录用户可阅读脱敏内容</small></span></label>
          </fieldset>
          <div className="form-pair interview-sharing-options"><label>复盘状态<select value={status} onChange={event => setStatus(event.target.value as InterviewDetail['status'])}><option value="draft">草稿</option><option value="pending_review">待复盘</option><option value="completed">已完成</option></select></label><label>公开身份<select value={authorMode} onChange={event => setAuthorMode(event.target.value as InterviewDetail['authorMode'])}><option value="anonymous">匿名</option><option value="attributed">展示用户名</option></select></label></div>
          <p className="interview-sharing-note">公开内容会经过隐私过滤，发布前请检查正文是否包含敏感信息。</p>
        </section>
        <div className="form-actions interview-document-actions"><div><button type="button" className="quiet-button" onClick={() => navigate ? navigate('/interviews/mine') : setDetail(null)}>返回个人面经</button><a className="quiet-button" href={`/applications/${encodeURIComponent(detail.applicationId)}`}>查看关联投递</a><button type="button" className="danger-button" disabled={busy} onClick={() => { if (window.confirm('确定删除这篇面经？')) void execute(async () => { await deletePrivateInterview(detail.id); setDetail(null); setReload(index => index + 1); setNotice('面经已删除。') }) }}>删除面经</button></div><button type="submit" disabled={busy}>保存面经</button></div>
      </form>}
    </> : <>
      {!initialPublicId && <div className="public-feed-toolbar list-query" role="search" aria-label="面经检索与筛选">
        <div className="query-search-box query-live-search"><label className="query-search"><span className="sr-only">搜索公司</span><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="搜索公司名称" /></label></div>
        <div className="query-toolbar-right"><label className="query-control"><span>排序</span><select aria-label="面经排序" value={sort} onChange={event => setSort(event.target.value)}><option value="latest">最新发布</option><option value="hot">按热度</option></select></label>
          <QueryFilterPanel count={Number(Boolean(city)) + Number(Boolean(position))} onReset={() => { setSearch(''); setCity(''); setPosition(''); setSort('latest') }} instant><label className="query-control"><span>城市</span><select value={city} onChange={event => setCity(event.target.value)}><option value="">全部</option>{(publicPage?.facets?.cities ?? []).map(value => <option key={value} value={value}>{value}</option>)}</select></label><label className="query-control"><span>岗位</span><select value={position} onChange={event => setPosition(event.target.value)}><option value="">全部</option>{(publicPage?.facets?.positions ?? []).map(value => <option key={value} value={value}>{value}</option>)}</select></label></QueryFilterPanel>
          <button type="button" className="query-reset" aria-label="清除筛选" title="清除筛选" onClick={() => { setSearch(''); setCity(''); setPosition(''); setSort('latest') }}><svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M4 7a6 6 0 1 1-.2 5M4 3v4h4" strokeLinecap="round" strokeLinejoin="round" /></svg><span>重置</span></button>
        </div>
      </div>}

      <div className="public-square-layout"><div className="public-main">{!initialPublicId && <header className="public-feed-heading"><div><h1>面经分享</h1><p>从真实面试记录中找到准备方向。</p></div><span>共 {publicPage?.total ?? 0} 篇</span></header>}
      {!initialPublicId && (!publicPage ? <p role="status">正在加载公开面经…</p> : <section className="public-feed" aria-label="公开面经列表">{publicPage.items.length === 0 ? <div className="content-card interview-empty-state"><h2>暂时没有公开面经</h2><p>换个筛选条件，或者先完成并分享你的第一篇面经。</p>{navigate && <button className="quiet-button" onClick={() => navigate('/interviews/mine')}>查看个人面经</button>}</div> : <ol className="public-feed-list">{publicPage.items.map(item => <li key={item.id}><PublicInterviewCard item={item} onOpen={() => navigate ? navigate(`/interviews/shared/${encodeURIComponent(item.id)}`) : openPublic(item.id)} /></li>)}</ol>}{publicPage.nextCursor && <button className="quiet-button public-feed-more" disabled={busy} onClick={morePublic}>加载更多公开面经</button>}</section>)}
      {initialPublicId && !publicDetail && !error && <p role="status">正在加载面经详情…</p>}
      {publicDetail && <article className="content-card public-interview">
        <header className="public-reading-header"><div className="editor-heading"><PublicTitle>{publicDetail.companyName} · {publicDetail.positionName}</PublicTitle><button className="quiet-button" onClick={() => navigate ? navigate('/interviews') : setPublicDetail(null)}>关闭</button></div>
          <p className="public-reading-byline">{publicDetail.author?.username ?? '匿名'} · {publicDetail.interviewedOn}</p>
          <div className="public-card-tags"><span>{stages.find(([key]) => key === publicDetail.stage)?.[1] ?? publicDetail.stage}</span>{publicDetail.city && <span>{publicDetail.city}</span>}<span>发布于 {publicDetail.publishedAt.slice(0, 10)}</span></div>
        </header>
        <section className="public-reading-body" aria-label="面经正文">{publicDetail.questions.map((item, index) => <div key={index} className="public-reading-entry"><MarkdownPreview value={item.question} headingOffset={1} />{item.originalAnswer && <p>{item.originalAnswer}</p>}{item.followUpNotes && <p>追问记录：{item.followUpNotes}</p>}{item.improvedAnswer && <p>改进回答：{item.improvedAnswer}</p>}</div>)}
          {(publicDetail.highlights || publicDetail.gaps) && <div className="public-reading-summary">{publicDetail.highlights && <p>亮点：{publicDetail.highlights}</p>}{publicDetail.gaps && <p>待改进：{publicDetail.gaps}</p>}</div>}
        </section>
        <div className="public-reading-actions"><button type="button" className="quiet-button public-like-button" aria-pressed={publicDetail.engagement.likedByViewer} disabled={busy} onClick={() => void execute(async () => { const engagement = await togglePublicLike(publicDetail.id); setPublicDetail({ ...publicDetail, engagement }) })}>{publicDetail.engagement.likedByViewer ? '取消点赞' : '点赞'} · {publicDetail.engagement.likeCount}</button><span>浏览 {publicDetail.engagement.viewCount} · 评论 {publicDetail.engagement.commentCount}</span></div>
        <section className="public-comments" aria-label="面经评论"><h2>评论 <span>{comments.length}</span></h2>{comments.length === 0 ? <p className="muted-note">还没有评论，分享你的想法。</p> : <ul className="public-comment-list">{comments.map(item => <li key={item.id}><div><strong>{item.author.username}</strong><time dateTime={item.createdAt}>{item.createdAt.slice(0, 10)}</time></div><p>{item.content}</p></li>)}</ul>}
          <form className="comment-form" onSubmit={event => { event.preventDefault(); if (!comment.trim()) return; void execute(async () => { const value = await addPublicComment(publicDetail.id, comment.trim()); setComments([...comments, value.comment]); setPublicDetail({ ...publicDetail, engagement: value.engagement }); setComment('') }) }}><label>发表评论<textarea rows={3} value={comment} maxLength={2000} placeholder="交流面试体验，分享准备建议…" onChange={event => setComment(event.target.value)} required /></label><button disabled={busy} type="submit">提交评论</button></form>
        </section>
      </article>}
      </div><aside className="public-feed-sidebar" aria-label="面经广场辅助信息">{navigate && !initialPublicId && <button className="public-mine-link" onClick={() => navigate('/interviews/mine')}><small>个人空间</small><strong>我的面经 →</strong></button>}<section className="content-card"><span>公开动态</span><p><strong>{publicPage?.total ?? '—'}</strong> 篇面经</p></section><section className="content-card"><h2>分享说明</h2><ul><li>完成复盘后才可公开</li><li>公开时默认匿名分享</li><li>可随时切回私有状态</li></ul></section></aside></div>
    </>}
  </section>
}
