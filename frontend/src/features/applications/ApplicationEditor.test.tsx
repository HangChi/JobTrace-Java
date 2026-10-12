import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ApplicationEditor from './ApplicationEditor'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
const detail = { id: 'a1', companyName: '测试公司', positionName: '开发', city: '上海', jobUrl: null, appliedDate: '2026-10-08', type: 'campus_recruitment', status: 'submitted', notes: '', stages: [], stageOccurrences: [], events: [], version: 1 }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('creates an application with the selected type and returns to its detail view', async () => {
  const changed = vi.fn()
  const fetcher = vi.fn(async (path: string) => json(path === '/api/security/csrf' ? { token: 'csrf' } : detail))
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationEditor id={null} onClose={vi.fn()} onChanged={changed} onExpired={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('公司名称'), { target: { value: '测试公司' } })
  fireEvent.change(screen.getByLabelText('岗位名称'), { target: { value: '开发' } })
  fireEvent.click(screen.getByRole('button', { name: '创建投递' }))
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1))
  expect(fetcher).toHaveBeenCalledWith('/api/applications', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) }))
  expect(screen.getByRole('heading', { name: '招聘阶段' })).toBeInTheDocument()
})

it('loads existing detail, changes status and adds a stage', async () => {
  const changed = vi.fn()
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path.endsWith('/status')) return json({ id: 'a1', status: 'offer', version: 2 })
    if (path.endsWith('/stages')) return json({ ...detail, status: 'offer', version: 3, stageOccurrences: [{ id: 's1', stage: 'interview_1', occurredOn: '2026-10-09' }] })
    return json(detail)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationEditor id="a1" onClose={vi.fn()} onChanged={changed} onExpired={vi.fn()} />)
  expect(await screen.findByDisplayValue('测试公司')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '标记录用' }))
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1))
  fireEvent.change(screen.getByLabelText('新增阶段'), { target: { value: 'interview_1' } })
  fireEvent.click(screen.getByRole('button', { name: '添加阶段' }))
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(2))
  expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/stages', expect.objectContaining({ method: 'POST', body: expect.stringContaining('interview_1') }))
  expect(screen.getByText('一面 · 2026-10-09')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '编辑' }))
  fireEvent.change(screen.getByLabelText('阶段'), { target: { value: 'interview_2' } })
  fireEvent.submit(screen.getByRole('button', { name: '保存阶段' }).closest('form')!)
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/stages/s1', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('interview_2') })))
})

it('updates an existing application and removes a stage after confirmation', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const withStage = { ...detail, stageOccurrences: [{ id: 's1', stage: 'interview_1', occurredOn: '2026-10-09' }] }
  const changed = vi.fn()
  const closed = vi.fn()
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/applications/a1/stages/s1' && init?.method === 'DELETE') return json({ ...detail, version: 3 })
    if (path === '/api/applications/a1' && init?.method === 'DELETE') return new Response(null, { status: 204 })
    if (path === '/api/applications/a1' && init?.method === 'PATCH') return json({ ...withStage, notes: '更新备注', version: 2 })
    return json(withStage)
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationEditor id="a1" onClose={closed} onChanged={changed} onExpired={vi.fn()} />)
  await screen.findByDisplayValue('测试公司')
  fireEvent.change(screen.getByLabelText('备注'), { target: { value: '更新备注' } })
  fireEvent.click(screen.getByRole('button', { name: '保存修改' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/applications/a1', expect.objectContaining({ method: 'PATCH', body: expect.stringContaining('更新备注') })))
  await waitFor(() => expect(screen.getByRole('button', { name: '删除' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: '删除' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/applications/a1/stages/s1', expect.objectContaining({ method: 'DELETE' })))
  fireEvent.click(screen.getByRole('button', { name: '删除投递' }))
  await waitFor(() => expect(closed).toHaveBeenCalledOnce())
  expect(fetcher).toHaveBeenCalledWith('/api/applications/a1', expect.objectContaining({ method: 'DELETE' }))
  vi.restoreAllMocks()
})

it('keeps an edit open after a version conflict and shows the server message', async () => {
  const fetcher = vi.fn(async (path: string, init?: RequestInit) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : init?.method === 'PATCH' ? json({ code: 'version_conflict', detail: '记录已变化，请重新加载' }, 409) : json(detail))
  vi.stubGlobal('fetch', fetcher)
  render(<ApplicationEditor id="a1" onClose={vi.fn()} onChanged={vi.fn()} onExpired={vi.fn()} />)
  await screen.findByDisplayValue('测试公司')
  fireEvent.change(screen.getByLabelText('岗位名称'), { target: { value: '高级开发' } })
  fireEvent.click(screen.getByRole('button', { name: '保存修改' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('记录已变化，请重新加载')
  expect(screen.getByDisplayValue('高级开发')).toBeInTheDocument()
})

it('calls session expiry on an unauthorized detail request', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ code: 'unauthorized', detail: '请登录' }, 401)))
  const expired = vi.fn()
  render(<ApplicationEditor id="a1" onClose={vi.fn()} onChanged={vi.fn()} onExpired={expired} />)
  await waitFor(() => expect(expired).toHaveBeenCalledOnce())
})
