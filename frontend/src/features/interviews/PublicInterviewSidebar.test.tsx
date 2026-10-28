import { cleanup, fireEvent, render, screen, act } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
import { listPublicInterviews } from './api'
import type { PublicInterview, PublicInterviewPage } from './api'
import PublicInterviewSidebar from './PublicInterviewSidebar'

vi.mock('./api', () => ({ listPublicInterviews: vi.fn() }))
const list = vi.mocked(listPublicInterviews)
const item = { id: 'p1', companyName: '星河科技', positionName: '前端工程师', city: '上海', stage: 'interview_2' } as PublicInterview
const page = (items: PublicInterview[]) => ({ items }) as PublicInterviewPage
const props = () => ({ onMine: vi.fn(), onOpen: vi.fn(), onExpired: vi.fn() })
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('loads the three hottest interviews independently and opens the selected record', async () => {
  list.mockResolvedValue(page([item, { ...item, id: 'p2', city: null, stage: 'custom' }, { ...item, id: 'p3' }, { ...item, id: 'p4' }]))
  const actions = props()
  render(<PublicInterviewSidebar {...actions} />)
  expect(await screen.findAllByRole('listitem')).toHaveLength(3)
  expect(list.mock.calls[0][0].toString()).toBe('sort=hot&limit=3')
  expect(screen.getAllByText('上海 · 二面')).toHaveLength(2)
  expect(screen.getByText('custom')).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: /阅读热门面经/ })[1])
  expect(actions.onOpen).toHaveBeenCalledWith('p2')
  fireEvent.click(screen.getByRole('button', { name: '个人空间 我的面经 →' }))
  expect(actions.onMine).toHaveBeenCalledOnce()
  expect(screen.getByText('完成复盘后可公开，默认匿名，可随时转为私有。')).toBeInTheDocument()
})

it('shows an empty state and supports rendering without a personal navigation action', async () => {
  list.mockResolvedValue(page([]))
  render(<PublicInterviewSidebar onOpen={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByText('还没有公开面经，期待你的分享。')).toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it.each([new Error('offline'), new ApiError(401, 'EXPIRED', 'expired')])('keeps sidebar failures local and handles an expired session: %s', async error => {
  list.mockRejectedValue(error)
  const actions = props()
  render(<PublicInterviewSidebar {...actions} />)
  expect(await screen.findByText('热门面经暂时无法加载')).toBeInTheDocument()
  expect(actions.onExpired).toHaveBeenCalledTimes(error instanceof ApiError ? 1 : 0)
  expect(screen.getByRole('button', { name: '个人空间 我的面经 →' })).toBeInTheDocument()
})

it.each(['resolve', 'reject'])('ignores an unmounted request that later completes with %s', async outcome => {
  let resolve!: (value: PublicInterviewPage) => void
  let reject!: (error: Error) => void
  list.mockReturnValue(new Promise((done, fail) => { resolve = done; reject = fail }))
  const actions = props()
  const { unmount } = render(<PublicInterviewSidebar {...actions} />)
  expect(screen.getByRole('status')).toHaveTextContent('正在加载热门面经…')
  unmount()
  await act(async () => { if (outcome === 'resolve') resolve(page([item])); else reject(new ApiError(401, 'EXPIRED', 'expired')) })
  expect(actions.onExpired).not.toHaveBeenCalled()
})
