import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { listApplications } from '../applications/api'
import type { ApplicationSummary } from '../applications/api'
import { ApiError } from '../../shared/api/client'
import { addPublicComment, createPrivateInterview, deletePrivateInterview, getPrivateInterview, getPublicInterview, listPrivateInterviews, listPublicComments, listPublicInterviews, togglePublicLike, updatePrivateInterview } from './api'
import type { Comment, InterviewDetail, InterviewPage, InterviewQuestion, PublicInterview, PublicInterviewPage } from './api'
import InterviewQuestionsEditor from './InterviewQuestionsEditor'
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
  const [highlights, setHighlights] = useState('')
  const [gaps, setGaps] = useState('')
  const [status, setStatus] = useState<InterviewDetail['status']>('draft')
  const [visibility, setVisibility] = useState<InterviewDetail['visibility']>('private')
  const [authorMode, setAuthorMode] = useState<InterviewDetail['authorMode']>('anonymous')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

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
    setQuestions(value.questions.length > 0 ? value.questions : [emptyQuestion()])
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
    if (status === 'completed' && !questions.some(item => item.question.trim())) { setError('完成复盘前请填写至少一个问题。'); return }
    if (visibility === 'public' && status !== 'completed') { setError('请先完成复盘，再公开面经。'); return }
    void execute(async () => {
      const validQuestions = questions.filter(item => item.question.trim()).map(item => ({ ...item, question: item.question.trim() }))
      const value = await updatePrivateInterview(detail.id, { version: detail.version, status, visibility, authorMode, roundResult: detail.roundResult, highlights, gaps, questions: validQuestions, actionItems: detail.actionItems })
      openDetail(value); setReload(index => index + 1); setNotice('面经已保存。')
    })
  }

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

  const visiblePrivateItems = (privatePage?.items ?? []).filter(item =>
    item.companyName.toLocaleLowerCase().includes(privateCompany.trim().toLocaleLowerCase()) &&
    (!privateStage || item.stage === privateStage) &&
    (!privateStatus || item.status === privateStatus) &&
    (!privateResult || item.roundResult === privateResult)
  ).sort((left, right) => privateSort === 'oldest' ? left.interviewedOn.localeCompare(right.interviewedOn) : right.interviewedOn.localeCompare(left.interviewedOn))

  return <section className={`interviews-content ${tab === 'public' ? 'interview-square-page' : 'interview-private-page'}`}>
    {navigate ? tab === 'private' ? initialPrivateId || startCreating ? <div className="interview-route-actions"><button className="quiet-button interview-switch" onClick={() => navigate('/interviews/mine')}>← 返回个人面经</button></div> : null : initialPublicId ? <button className="quiet-button interview-switch" onClick={() => navigate('/interviews')}>← 返回面经广场</button> : null : <div className="segmented-tabs" role="group" aria-label="面经分类"><button aria-pressed={tab === 'private'} onClick={() => setTab('private')}>我的面经</button><button aria-pressed={tab === 'public'} onClick={() => setTab('public')}>公开面经</button></div>}
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {tab === 'private' ? <>
      {(!navigate || startCreating) && <form className="content-card interview-create" onSubmit={create}><h2>记录一次面试</h2><div className="form-pair"><label>关联投递<select value={newApplication} onChange={event => setNewApplication(event.target.value)} required><option value="">请选择投递</option>{applications.map(item => <option key={item.id} value={item.id}>{item.companyName} · {item.positionName}</option>)}</select></label><label>轮次<select value={newStage} onChange={event => setNewStage(event.target.value)}>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>日期<input type="date" value={newDate} onChange={event => setNewDate(event.target.value)} required /></label></div><button disabled={busy || applications.length === 0} type="submit">创建草稿</button></form>}
      {(!navigate || (!startCreating && !initialPrivateId)) && <div className="private-interview-filters" role="group" aria-label="筛选复盘记录"><label>搜索公司<input value={privateCompany} onChange={event => setPrivateCompany(event.target.value)} placeholder="公司名称" /></label><label>面试轮次<select value={privateStage} onChange={event => setPrivateStage(event.target.value)}><option value="">全部轮次</option>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>筛选状态<select value={privateStatus} onChange={event => setPrivateStatus(event.target.value)}><option value="">全部状态</option><option value="draft">草稿</option><option value="pending_review">待复盘</option><option value="completed">已完成</option></select></label><label>面试结果<select value={privateResult} onChange={event => setPrivateResult(event.target.value)}><option value="">全部结果</option><option value="pending">待反馈</option><option value="passed">通过</option><option value="failed">未通过</option></select></label><label>排序<select value={privateSort} onChange={event => setPrivateSort(event.target.value)}><option value="latest">最新面试</option><option value="oldest">最早面试</option></select></label><button type="button" className="quiet-button" onClick={() => { setPrivateCompany(''); setPrivateStage(''); setPrivateStatus(''); setPrivateResult(''); setPrivateSort('latest') }}>清除</button></div>}
      {(!navigate || (!startCreating && !initialPrivateId)) && (!privatePage ? <p role="status">正在加载我的面经…</p> : <section className="interview-list-panel" aria-label="复盘记录"><header><h2>复盘记录</h2><span>共 {visiblePrivateItems.length} 篇</span></header>{visiblePrivateItems.length === 0 ? <p className="muted-note interview-empty">{privatePage.items.length === 0 ? '还没有面经，先记录一次面试。' : '没有符合条件的复盘记录。'}</p> : <ul className="interview-record-list">{visiblePrivateItems.map(item => <li key={item.id}><time dateTime={item.interviewedOn}>{item.interviewedOn}</time><div className="interview-record-main"><button type="button" className="interview-record-title" disabled={busy} onClick={() => navigate ? navigate(`/interviews/${encodeURIComponent(item.id)}`) : void execute(async () => openDetail(await getPrivateInterview(item.id)))}>{item.companyName} · {item.positionName}</button><small>{stages.find(([key]) => key === item.stage)?.[1] ?? item.stage} · {item.questionCount} 个问题</small></div><div className="interview-record-status"><span>{item.status === 'completed' ? '已完成' : item.status === 'pending_review' ? '待复盘' : '草稿'}</span><span>{item.roundResult === 'passed' ? '通过' : item.roundResult === 'failed' ? '未通过' : '待反馈'}</span><span>{item.visibility === 'public' ? item.authorMode === 'anonymous' ? '匿名公开' : '署名公开' : '私有'}</span></div><a href={`/api/exports/interviews?id=${encodeURIComponent(item.id)}`}>导出</a><button type="button" className="quiet-button interview-record-edit" disabled={busy} onClick={() => navigate ? navigate(`/interviews/${encodeURIComponent(item.id)}`) : void execute(async () => openDetail(await getPrivateInterview(item.id)))}>查看与编辑</button></li>)}</ul>}{privatePage.nextCursor && <button className="quiet-button" disabled={busy} onClick={morePrivate}>加载更多面经</button>}</section>)}
      {detail && <form className="content-card interview-editor" onSubmit={save}>
        <div className="editor-heading"><h2>编辑面经 · {detail.companyName}</h2><button className="quiet-button" type="button" onClick={() => navigate ? navigate('/interviews/mine') : setDetail(null)}>关闭</button></div>
        <p>{stages.find(([key]) => key === detail.stage)?.[1] ?? detail.stage} · {detail.interviewedOn}</p>
        <InterviewQuestionsEditor questions={questions} onChange={setQuestions} />
        <label>亮点<textarea rows={3} value={highlights} onChange={event => setHighlights(event.target.value)} /></label>
        <label>待改进<textarea rows={3} value={gaps} onChange={event => setGaps(event.target.value)} /></label>
        <div className="form-pair"><label>复盘状态<select value={status} onChange={event => setStatus(event.target.value as InterviewDetail['status'])}><option value="draft">草稿</option><option value="pending_review">待复盘</option><option value="completed">已完成</option></select></label><label>可见范围<select value={visibility} onChange={event => setVisibility(event.target.value as InterviewDetail['visibility'])}><option value="private">仅自己可见</option><option value="public">公开</option></select></label><label>公开身份<select value={authorMode} onChange={event => setAuthorMode(event.target.value as InterviewDetail['authorMode'])}><option value="anonymous">匿名</option><option value="attributed">展示用户名</option></select></label></div>
        <p className="muted-note">公开内容由服务端进行隐私过滤。发布前请再次检查文本中是否包含个人或公司敏感信息。</p>
        <div className="form-actions"><button type="submit" disabled={busy}>保存面经</button><button type="button" className="danger-button" disabled={busy} onClick={() => { if (window.confirm('确定删除这篇面经？')) void execute(async () => { await deletePrivateInterview(detail.id); setDetail(null); setReload(index => index + 1); setNotice('面经已删除。') }) }}>删除面经</button></div>
      </form>}
    </> : <>
      {!initialPublicId && <div className="public-feed-toolbar"><div className="public-company-search"><label className="public-company-search-field"><span className="sr-only">搜索公司</span><svg aria-hidden="true" viewBox="0 0 20 20"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m12.5 12.5 4 4" /></svg><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="搜索公司名称" /></label><label className="public-filter-select"><span className="sr-only">城市</span><select value={city} onChange={event => setCity(event.target.value)}><option value="">城市</option>{(publicPage?.facets?.cities ?? []).map(value => <option key={value} value={value}>{value}</option>)}</select></label><label className="public-filter-select"><span className="sr-only">岗位</span><select value={position} onChange={event => setPosition(event.target.value)}><option value="">岗位</option>{(publicPage?.facets?.positions ?? []).map(value => <option key={value} value={value}>{value}</option>)}</select></label><div className="public-sort-control" role="group" aria-label="排序方式"><button type="button" aria-pressed={sort === 'latest'} onClick={() => setSort('latest')}>最新发布</button><button type="button" aria-pressed={sort === 'hot'} onClick={() => setSort('hot')}>按热度</button></div></div></div>}
      <div className="public-square-layout"><div className="public-main">{!initialPublicId && <header className="public-feed-heading"><h1>最新面经</h1><span>共 {publicPage?.total ?? 0} 篇</span></header>}
      {!initialPublicId && (!publicPage ? <p role="status">正在加载公开面经…</p> : <section className="public-feed" aria-label="公开面经列表">{publicPage.items.length === 0 ? <div className="content-card interview-empty-state"><h2>暂时没有公开面经</h2><p>换个筛选条件，或者先完成并分享你的第一篇面经。</p>{navigate && <button className="quiet-button" onClick={() => navigate('/interviews/mine')}>查看个人面经</button>}</div> : <ol className="public-feed-list">{publicPage.items.map(item => <li key={item.id}><PublicInterviewCard item={item} onOpen={() => navigate ? navigate(`/interviews/shared/${encodeURIComponent(item.id)}`) : openPublic(item.id)} /></li>)}</ol>}{publicPage.nextCursor && <button className="quiet-button public-feed-more" disabled={busy} onClick={morePublic}>加载更多公开面经</button>}</section>)}
      {initialPublicId && !publicDetail && !error && <p role="status">正在加载面经详情…</p>}
      {publicDetail && <article className="content-card public-interview"><div className="editor-heading"><h2>{publicDetail.companyName} · {publicDetail.positionName}</h2><button className="quiet-button" onClick={() => navigate ? navigate('/interviews') : setPublicDetail(null)}>关闭</button></div><p>{publicDetail.author?.username ?? '匿名'} · {publicDetail.interviewedOn}</p>{publicDetail.highlights && <p>亮点：{publicDetail.highlights}</p>}{publicDetail.gaps && <p>待改进：{publicDetail.gaps}</p>}{publicDetail.questions.map((item, index) => <div key={index} className="public-question"><h3>{item.question}</h3>{item.originalAnswer && <p>{item.originalAnswer}</p>}{item.improvedAnswer && <p>改进回答：{item.improvedAnswer}</p>}</div>)}<button className="quiet-button" disabled={busy} onClick={() => void execute(async () => { const engagement = await togglePublicLike(publicDetail.id); setPublicDetail({ ...publicDetail, engagement }) })}>{publicDetail.engagement.likedByViewer ? '取消点赞' : '点赞'} · {publicDetail.engagement.likeCount}</button><h3>评论</h3><ul className="insight-list">{comments.map(item => <li key={item.id}><strong>{item.author.username}</strong><span>{item.content}</span></li>)}</ul><form className="comment-form" onSubmit={event => { event.preventDefault(); if (!comment.trim()) return; void execute(async () => { const value = await addPublicComment(publicDetail.id, comment.trim()); setComments([...comments, value.comment]); setPublicDetail({ ...publicDetail, engagement: value.engagement }); setComment('') }) }}><label>发表评论<textarea value={comment} maxLength={2000} onChange={event => setComment(event.target.value)} required /></label><button disabled={busy} type="submit">提交评论</button></form></article>}
      </div><aside className="public-feed-sidebar" aria-label="面经广场辅助信息">{navigate && !initialPublicId && <button className="public-mine-link" onClick={() => navigate('/interviews/mine')}><small>个人空间</small><strong>我的面经 →</strong></button>}<section className="content-card"><span>公开动态</span><p><strong>{publicPage?.total ?? '—'}</strong> 篇面经</p></section><section className="content-card"><h2>分享说明</h2><ul><li>完成复盘后才可公开</li><li>公开时默认匿名分享</li><li>可随时切回私有状态</li></ul></section></aside></div>
    </>}
  </section>
}
