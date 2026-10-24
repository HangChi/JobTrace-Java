import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AdminUserDetailPage from './AdminUserDetailPage'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })
const detail = {
  id: 'u2', username: 'tester', email: 'tester@example.test', internalEmail: null, role: 'USER', disabled: false,
  accessVersion: 1, createdAt: '2026-01-01T00:00:00Z', lastSignInAt: null, applicationCount: 1, interviewCount: 1,
  applications: { items: [{ id: 'a1', companyName: '示例公司', positionName: '开发', city: '上海', status: '已投递', appliedDate: '2026-10-01', notes: '关注远程工作', stages: [{ stage: '一面', occurredOn: '2026-10-05' }] }], total: 1, page: 1, limit: 20, totalPages: 1 },
  interviews: { items: [{ id: 'i1', companyName: '示例公司', positionName: '开发', stage: '一面', interviewedOn: '2026-10-05', status: '已完成', roundResult: 'passed', highlights: '沟通顺畅', questions: [{ question: '项目经历' }] }], total: 1, page: 1, limit: 20, totalPages: 1 },
  recentAuditEvents: [{ id: 'e1', actorIdentifier: 'admin', targetIdentifier: 'tester', eventType: 'enable_user', outcome: 'success', reason: null, createdAt: '2026-10-06T00:00:00Z' }],
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('opens a read-only user archive and returns to the directory', async () => {
  const navigate = vi.fn()
  const fetcher = vi.fn(async () => json(detail))
  vi.stubGlobal('fetch', fetcher)
  render(<AdminUserDetailPage id="u2" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByRole('heading', { name: 'tester' })).toBeInTheDocument()
  expect(screen.getByText('tester@example.test')).toBeInTheDocument()
  expect(screen.getByText('关注远程工作')).toBeInTheDocument()
  expect(screen.getByText('沟通顺畅')).toBeInTheDocument()
  expect(screen.getByText('enable_user')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: /返回用户目录/ }))
  expect(navigate).toHaveBeenCalledWith('/admin/users')
  expect(fetcher).toHaveBeenCalledWith('/api/admin/users/u2', expect.anything())
})

it('shows empty archive sections and account fallbacks', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...detail, role: 'ADMIN', disabled: true, email: null, internalEmail: null, lastSignInAt: '2026-10-01T00:00:00Z', applications: { ...detail.applications, items: [] }, interviews: { ...detail.interviews, items: [] }, recentAuditEvents: [] })))
  render(<AdminUserDetailPage id="u2" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByText('未绑定邮箱')).toBeInTheDocument()
  expect(screen.getByText('管理员')).toBeInTheDocument()
  expect(screen.getByText('已停用')).toBeInTheDocument()
  expect(screen.getByText('暂无投递记录。')).toBeInTheDocument()
  expect(screen.getByText('暂无面经记录。')).toBeInTheDocument()
  expect(screen.getByText('暂无相关审计记录。')).toBeInTheDocument()
})

it('retries a failed archive request and expires an unauthorized session', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(json({ code: 'unavailable', detail: '档案暂时不可用' }, 503)).mockResolvedValueOnce(json(detail)).mockResolvedValueOnce(json({ code: 'unauthorized', detail: '请重新登录' }, 401))
  const onExpired = vi.fn()
  vi.stubGlobal('fetch', fetcher)
  const view = render(<AdminUserDetailPage id="u2" navigate={vi.fn()} onExpired={onExpired} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('档案暂时不可用')
  fireEvent.click(screen.getByRole('button', { name: '重试' }))
  expect(await screen.findByRole('heading', { name: 'tester' })).toBeInTheDocument()
  view.rerender(<AdminUserDetailPage id="u3" navigate={vi.fn()} onExpired={onExpired} />)
  await waitFor(() => expect(onExpired).toHaveBeenCalledOnce())
})
