import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import JobMarketPage from './JobMarketPage'

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } })
const campaign = { id: 'c1', listingKind: 'synced_jobs', company: { name: '示例公司', type: '企业', industry: '软件' }, recruitmentType: null, positions: ['Java 工程师'], positionCount: 1, locations: [{ name: '上海' }], status: 'open', primaryApplyUrl: 'https://example.com/jobs', source: { name: '招聘官网', url: 'https://example.com/jobs' }, publishedAt: '2026-10-01', lastConfirmedAt: null, isFavorite: false }
const page = { items: [campaign], page: 1, limit: 20, total: 1 }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('lists live campaigns, filters and saves a favorite using the Java API', async () => {
  const navigate = vi.fn()
  const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : path.endsWith('/favorite') ? json({ campaignId: 'c1', isFavorite: true }) : json(page))
  vi.stubGlobal('fetch', fetcher)
  render(<JobMarketPage search="" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('示例公司')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /立即投递/ })).toHaveAttribute('href', 'https://example.com/jobs')
  fireEvent.change(screen.getByLabelText('关键词'), { target: { value: 'Java' } })
  fireEvent.change(screen.getByLabelText('发布时间'), { target: { value: '2026-10-01' } })
  fireEvent.click(screen.getByRole('button', { name: '筛选' }))
  expect(navigate).toHaveBeenCalledWith('/?q=Java&postedFrom=2026-10-01')
  fireEvent.click(screen.getByRole('button', { name: '记录投递' }))
  expect(navigate).toHaveBeenCalledWith('/applications/new?company=%E7%A4%BA%E4%BE%8B%E5%85%AC%E5%8F%B8&position=Java+%E5%B7%A5%E7%A8%8B%E5%B8%88&city=%E4%B8%8A%E6%B5%B7&jobUrl=https%3A%2F%2Fexample.com%2Fjobs')
  fireEvent.click(screen.getByRole('button', { name: '收藏 示例公司' }))
  expect(await screen.findByRole('button', { name: '取消收藏 示例公司' })).toHaveAttribute('aria-pressed', 'true')
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/job-market/campaigns/c1/favorite', expect.objectContaining({ method: 'PUT' })))
})

it('does not render a non-HTTPS destination as an application link', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...page, items: [{ ...campaign, primaryApplyUrl: 'javascript:alert(1)', source: { name: '未知来源', url: null } }] })))
  render(<JobMarketPage search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByText('示例公司')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: /立即投递/ })).not.toBeInTheDocument()
  expect(screen.getByText('暂无入口')).toBeInTheDocument()
})
