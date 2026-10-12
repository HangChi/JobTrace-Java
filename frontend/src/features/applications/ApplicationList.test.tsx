import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ApplicationList from './ApplicationList'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows live Java results and applies search filters in the URL', async () => {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => new Response(JSON.stringify(path === '/api/analytics/summary'
    ? { total: 1, submitted: 1, refused: 0, offers: 0, addedThisWeek: 1, stageDistribution: {}, followUps: [] }
    : { items: [{ id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: '上海', jobUrl: 'https://example.com/job', appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: true, followUpDays: 7, followUpReason: '待跟进', version: 1 }], nextCursor: null, total: 1, page: 1, limit: 10 }), { status: 200 })))
  const navigate = vi.fn()
  render(<ApplicationList search="" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('星河科技 · 上海')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '求职概览' })).toBeInTheDocument()
  expect(screen.getByRole('table')).toBeInTheDocument()
  expect(screen.getByText('投递记录 7 天未更新')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('搜索投递'), { target: { value: '工程师' } })
  fireEvent.click(screen.getByRole('button', { name: '筛选' }))
  expect(navigate).toHaveBeenCalledWith('/applications?q=%E5%B7%A5%E7%A8%8B%E5%B8%88')
})

it('shows an empty state and a retryable failure', async () => {
  const applications = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(new Response(JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 10 }), { status: 200 }))
  const fetcher = vi.fn((path: string) => path === '/api/analytics/summary'
    ? Promise.resolve(new Response(JSON.stringify({ total: 0, submitted: 0, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [] }), { status: 200 }))
    : applications())
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationList search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('网络连接失败')
  fireEvent.click(screen.getByRole('button', { name: '重试加载' }))
  await waitFor(() => expect(screen.getByText('还没有投递记录')).toBeInTheDocument())
})

it('expires the workspace on a Java 401 without showing stale data', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 'unauthorized' }), { status: 401 })))
  const onExpired = vi.fn()
  render(<ApplicationList search="" navigate={vi.fn()} onExpired={onExpired} />)
  await waitFor(() => expect(onExpired).toHaveBeenCalled())
  expect(screen.queryByText('星河科技')).not.toBeInTheDocument()
})

it('updates a status from the table using the current version', async () => {
  const item = { id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: null, jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: false, followUpDays: 0, followUpReason: null, version: 3 }
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return new Response(JSON.stringify({ token: 'csrf' }))
    if (path === '/api/analytics/summary') return new Response(JSON.stringify({ total: 1, submitted: 1, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [] }))
    if (path === '/api/applications/a1/status' && init?.method === 'PATCH') return new Response(JSON.stringify({ id: 'a1', status: 'offer', latestDate: '2026-10-10', version: 4 }))
    return new Response(JSON.stringify({ items: [item], nextCursor: null, total: 1, page: 1, limit: 10 }))
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationList search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('更新 星河科技 前端工程师 的状态'), { target: { value: 'offer' } })
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'offer', version: 3 }) })))
})

it('keeps a row after a rejected status change and displays the conflict', async () => {
  const item = { id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: null, jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: false, followUpDays: 0, followUpReason: null, version: 3 }
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return new Response(JSON.stringify({ token: 'csrf' }))
    if (path === '/api/analytics/summary') return new Response(JSON.stringify({ total: 1, submitted: 1, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [] }))
    if (path === '/api/applications/a1/status') return new Response(JSON.stringify({ code: 'version_conflict', detail: '记录已被修改，请刷新后重试。' }), { status: 409 })
    return new Response(JSON.stringify({ items: [item], nextCursor: null, total: 1, page: 1, limit: 10 }))
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationList search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('更新 星河科技 前端工程师 的状态'), { target: { value: 'offer' } })
  expect(await screen.findByRole('alert')).toHaveTextContent('记录已被修改')
  expect(screen.getByLabelText('更新 星河科技 前端工程师 的状态')).toHaveValue('submitted')
})

it('deletes a row only after confirmation and reloads the list', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
  const item = { id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: null, jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: false, followUpDays: 0, followUpReason: null, version: 3 }
  let deleted = false
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return new Response(JSON.stringify({ token: 'csrf' }))
    if (path === '/api/analytics/summary') return new Response(JSON.stringify({ total: deleted ? 0 : 1, submitted: deleted ? 0 : 1, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [] }))
    if (path === '/api/applications/a1' && init?.method === 'DELETE') { deleted = true; return new Response(null, { status: 204 }) }
    return new Response(JSON.stringify({ items: deleted ? [] : [item], nextCursor: null, total: deleted ? 0 : 1, page: 1, limit: 10 }))
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationList search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '删除' }))
  expect(confirm).toHaveBeenCalledTimes(1)
  expect(fetcher).not.toHaveBeenCalledWith('/api/applications/a1', expect.objectContaining({ method: 'DELETE' }))
  fireEvent.click(screen.getByRole('button', { name: '删除' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/applications/a1', expect.objectContaining({ method: 'DELETE' })))
  expect(await screen.findByText('还没有投递记录')).toBeInTheDocument()
})

it('moves between numbered pages while keeping sort and page size', async () => {
  const item = { id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: null, jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: false, followUpDays: 0, followUpReason: null, version: 3 }
  vi.stubGlobal('fetch', vi.fn(async (path: string) => new Response(JSON.stringify(path === '/api/analytics/summary'
    ? { total: 25, submitted: 25, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [] }
    : { items: [item], nextCursor: null, total: 25, page: 2, limit: 10 }))))
  const navigate = vi.fn()
  render(<ApplicationList search="page=2&sort=company" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('第 2 / 3 页')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '下一页' }))
  expect(navigate).toHaveBeenCalledWith('/applications?page=3&sort=company')
  fireEvent.change(screen.getByLabelText('每页显示'), { target: { value: '20' } })
  expect(navigate).toHaveBeenCalledWith('/applications?sort=company&limit=20')
})
