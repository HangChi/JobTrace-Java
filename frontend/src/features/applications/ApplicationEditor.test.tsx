import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ApplicationEditor from './ApplicationEditor'

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } })
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
