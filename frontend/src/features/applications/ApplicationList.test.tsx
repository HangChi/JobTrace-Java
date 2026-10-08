import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ApplicationList from './ApplicationList'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows live Java results and applies search filters in the URL', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ items: [{ id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: '上海', appliedDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: true, followUpDays: 7, followUpReason: '待跟进', version: 1 }], nextCursor: null, total: 1, page: 1, limit: 50 }), { status: 200 })))
  const navigate = vi.fn()
  render(<ApplicationList search="" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('星河科技')).toBeInTheDocument()
  expect(screen.getByText('待跟进')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('搜索投递'), { target: { value: '工程师' } })
  fireEvent.click(screen.getByRole('button', { name: '筛选' }))
  expect(navigate).toHaveBeenCalledWith('/applications?q=%E5%B7%A5%E7%A8%8B%E5%B8%88')
})

it('shows an empty state and a retryable failure', async () => {
  const fetcher = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(new Response(JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }), { status: 200 }))
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
