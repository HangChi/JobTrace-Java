import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import InterviewsPage from './InterviewsPage'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } })
const privateItem = { id: 'i1', applicationId: 'a1', companyName: '测试公司', positionName: '开发', stage: 'interview_1', interviewedOn: '2026-10-08', status: 'draft', roundResult: 'pending', questionCount: 0, visibility: 'private', authorMode: 'anonymous', publishedAt: null }
const detail = { ...privateItem, version: 1, format: null, durationMinutes: null, interviewerNotes: null, highlights: null, gaps: null, questions: [], actionItems: [] }
const publicItem = { id: 'p1', companyName: '公开公司', positionName: '工程师', city: null, stage: 'interview_1', interviewedOn: '2026-10-08', publishedAt: '2026-10-08T00:00:00Z', questionCount: 1, author: { username: '匿名', image: null }, engagement: { likeCount: 0, commentCount: 0, viewCount: 1, likedByViewer: false }, highlights: '经验总结', gaps: null, questions: [{ category: 'other', question: '如何准备？', originalAnswer: null, followUpNotes: null, improvedAnswer: null }], recentComments: [] }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('filters private review rows and restores them', async () => {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => path.startsWith('/api/applications')
    ? json({ items: [], total: 0 })
    : json({ items: [privateItem], total: 1, nextCursor: null, limit: 20 })))
  render(<InterviewsPage initialTab="private" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByRole('button', { name: '查看与编辑' })).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('搜索公司'), { target: { value: '其他公司' } })
  expect(screen.queryByRole('button', { name: '查看与编辑' })).not.toBeInTheDocument()
  expect(screen.getByText('没有符合条件的复盘记录。')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '清除' }))
  expect(screen.getByRole('button', { name: '查看与编辑' })).toBeInTheDocument()
})

it('creates a private interview draft and saves an answer', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path === '/api/interviews' && init?.method === 'POST') return json(detail)
    if (path === '/api/interviews/i1') return json({ ...detail, questions: [{ id: 'q1', category: 'other', question: '如何准备？', originalAnswer: '复盘', followUpNotes: null, improvedAnswer: null, selfRating: null }], version: 2 })
    return json({ items: [], total: 0, nextCursor: null, limit: 20 })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<InterviewsPage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('关联投递'), { target: { value: 'a1' } })
  fireEvent.submit(screen.getByRole('button', { name: '创建草稿' }).closest('form')!)
  expect(await screen.findByRole('heading', { name: '编辑面经 · 测试公司' })).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('面试问题'), { target: { value: '如何准备？' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存面经' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/interviews/i1', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('如何准备？') })))
})

it('reads sanitized public content and toggles a like with CSRF', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path.startsWith('/api/applications')) return json({ items: [], total: 0 })
    if (path.startsWith('/api/interviews/public?')) return json({ items: [publicItem], nextCursor: null, total: 1, limit: 20, facets: { cities: [], positions: [] } })
    if (path === '/api/interviews/public/p1/comments') return init?.method === 'POST'
      ? json({ comment: { id: 'c1', content: '很有帮助', createdAt: '2026-10-08T00:00:00Z', author: { username: '我', image: null } }, engagement: { ...publicItem.engagement, commentCount: 1 } })
      : json([])
    if (path === '/api/interviews/public/p1/like') return json({ ...publicItem.engagement, likeCount: 1, likedByViewer: true })
    if (path === '/api/interviews/public/p1') return json(publicItem)
    return json({ items: [], total: 0, nextCursor: null, limit: 20 })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<InterviewsPage onExpired={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: '公开面经' }))
  fireEvent.click(await screen.findByRole('button', { name: '查看完整面经' }))
  expect(await screen.findByText('如何准备？')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '点赞 · 0' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/interviews/public/p1/like', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) })))
  fireEvent.change(screen.getByLabelText('发表评论'), { target: { value: '很有帮助' } })
  fireEvent.submit(screen.getByRole('button', { name: '提交评论' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/interviews/public/p1/comments', expect.objectContaining({ method: 'POST', body: JSON.stringify({ content: '很有帮助' }) })))
})

it('keeps the public square and detail visible when a post has no author', async () => {
  const anonymous = { ...publicItem, author: null }
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    if (path.startsWith('/api/applications')) return json({ items: [], total: 0 })
    if (path.startsWith('/api/interviews/public?')) return json({ items: [anonymous], nextCursor: null, total: 1, limit: 20, facets: { cities: [], positions: [] } })
    if (path === '/api/interviews/public/p1') return json(anonymous)
    if (path === '/api/interviews/public/p1/comments') return json([])
    return json({ items: [], nextCursor: null, total: 0, limit: 20 })
  }))
  render(<InterviewsPage initialTab="public" onExpired={vi.fn()} />)
  expect(await screen.findByRole('heading', { name: '公开公司 · 工程师' })).toBeInTheDocument()
  expect(screen.getByText('匿名用户')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '查看完整面经' }))
  expect(await screen.findByText('如何准备？')).toBeInTheDocument()
  expect(screen.getByText('匿名 · 2026-10-08')).toBeInTheDocument()
})

it('prevents publishing a draft until it has completed review content', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path.startsWith('/api/applications')) return json({ items: [{ id: 'a1', companyName: '测试公司', positionName: '开发' }], total: 1 })
    if (path === '/api/interviews' && init?.method === 'POST') return json(detail)
    if (path === '/api/interviews/i1') return json({ ...detail, status: 'completed', visibility: 'public', version: 2, questions: [{ id: 'q1', category: 'other', question: '如何准备？', originalAnswer: '', followUpNotes: '', improvedAnswer: '', selfRating: null }] })
    return json({ items: [], total: 0, nextCursor: null, limit: 20 })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<InterviewsPage onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('关联投递'), { target: { value: 'a1' } })
  fireEvent.submit(screen.getByRole('button', { name: '创建草稿' }).closest('form')!)
  await screen.findByRole('heading', { name: '编辑面经 · 测试公司' })
  fireEvent.change(screen.getByLabelText('可见范围'), { target: { value: 'public' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存面经' }).closest('form')!)
  expect(screen.getByRole('alert')).toHaveTextContent('请先完成复盘')
  expect(fetcher).not.toHaveBeenCalledWith('/api/interviews/i1', expect.objectContaining({ method: 'PATCH' }))
  fireEvent.change(screen.getByLabelText('面试问题'), { target: { value: '如何准备？' } })
  fireEvent.change(screen.getByLabelText('复盘状态'), { target: { value: 'completed' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存面经' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/interviews/i1', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('"visibility":"public"') })))
})

it('loads subsequent public interview pages without losing the first page', async () => {
  const secondItem = { ...publicItem, id: 'p2', companyName: '第二家公司' }
  const fetcher = vi.fn(async (path: string) => {
    if (path.startsWith('/api/applications')) return json({ items: [], total: 0 })
    if (path.startsWith('/api/interviews/public?')) return path.includes('cursor=next')
      ? json({ items: [secondItem], nextCursor: null, total: 2, limit: 20, facets: { cities: [], positions: [] } })
      : json({ items: [publicItem], nextCursor: 'next', total: 2, limit: 20, facets: { cities: [], positions: [] } })
    return json({ items: [], nextCursor: null, total: 0, limit: 20 })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<InterviewsPage initialTab="public" onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '加载更多公开面经' }))
  expect(await screen.findByRole('heading', { name: '第二家公司 · 工程师' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '公开公司 · 工程师' })).toBeInTheDocument()
  expect(fetcher).toHaveBeenCalledWith('/api/interviews/public?sort=latest&limit=20&cursor=next', expect.anything())
})

it('applies city and position filters in the public square', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path.startsWith('/api/applications')) return json({ items: [], total: 0 })
    if (path.startsWith('/api/interviews/public?')) return json({ items: [publicItem], nextCursor: null, total: 1, limit: 20, facets: { cities: ['上海'], positions: ['工程师'] } })
    return json({ items: [], nextCursor: null, total: 0, limit: 20 })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<InterviewsPage initialTab="public" onExpired={vi.fn()} />)
  await screen.findByRole('heading', { name: '公开公司 · 工程师' })
  fireEvent.change(screen.getByLabelText('城市'), { target: { value: '上海' } })
  fireEvent.change(screen.getByLabelText('岗位'), { target: { value: '工程师' } })
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/interviews/public?sort=latest&limit=20&city=%E4%B8%8A%E6%B5%B7&position=%E5%B7%A5%E7%A8%8B%E5%B8%88', expect.anything()))
})
