import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import JobMarketPage from './JobMarketPage'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
const campaign = { id: 'c1', listingKind: 'synced_jobs', company: { name: '示例公司', type: '企业', industry: '软件' }, recruitmentType: null, positions: ['Java 工程师'], positionCount: 1, locations: [{ name: '上海' }], status: 'open', primaryApplyUrl: 'https://example.com/jobs', source: { name: '招聘官网', url: 'https://example.com/jobs' }, publishedAt: '2026-10-01', lastConfirmedAt: null, isFavorite: false }
const page = { items: [campaign], page: 1, limit: 20, total: 1 }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('lists live campaigns, filters and saves a favorite using the Java API', async () => {
  const navigate = vi.fn()
  const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : path.endsWith('/favorite') ? json({ campaignId: 'c1', isFavorite: true }) : json(page))
  vi.stubGlobal('fetch', fetcher)
  render(<JobMarketPage search="" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('示例公司')).toBeInTheDocument()
  expect(screen.getByRole('search', { name: '筛选招聘机会' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /立即投递/ })).toHaveAttribute('href', 'https://example.com/jobs')
  fireEvent.change(screen.getByLabelText('关键词'), { target: { value: 'Java' } })
  fireEvent.change(screen.getByLabelText('发布时间'), { target: { value: '2026-10-01' } })
  fireEvent.click(screen.getByRole('button', { name: '搜索' }))
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

it('blocks application actions for closed campaigns and expands every position and location', async () => {
  const closed = { ...campaign, status: 'closed', positions: ['甲岗位', '乙岗位', '丙岗位'], positionCount: 3, locations: [{ name: '北京' }, { name: '上海' }, { name: '深圳' }] }
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...page, items: [closed] })))
  render(<JobMarketPage search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByText('招聘记录已失效')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '记录投递' })).toBeDisabled()
  expect(screen.queryByRole('link', { name: /立即投递/ })).not.toBeInTheDocument()
  fireEvent.click(screen.getByText('查看全部岗位'))
  fireEvent.click(screen.getByText('查看全部地点'))
  expect(screen.getByText('丙岗位')).toBeInTheDocument()
  expect(screen.getByText('深圳')).toBeInTheDocument()
})

it('labels a truncated position projection as a summary', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...page, items: [{ ...campaign, positionCount: 80, positions: ['前端', '后端'] }] })))
  render(<JobMarketPage search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  fireEvent.click(await screen.findByText('查看岗位摘要（共 80 个）'))
  expect(screen.getByText('这里只展示前 2 个岗位，请前往招聘官网查看全部。')).toBeInTheDocument()
})

it('keeps filters while paging and toggling the favorites view', async () => {
  const navigate = vi.fn()
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...page, page: 2, total: 45, limit: 20 })))
  render(<JobMarketPage search="?q=Java&page=2&status=open" navigate={navigate} onExpired={vi.fn()} />)
  await screen.findByText('示例公司')
  fireEvent.click(screen.getByRole('button', { name: '上一页' }))
  expect(navigate).toHaveBeenCalledWith('/?q=Java&status=open')
  fireEvent.click(screen.getByRole('button', { name: '下一页' }))
  expect(navigate).toHaveBeenCalledWith('/?q=Java&page=3&status=open')
  fireEvent.click(screen.getByRole('button', { name: '第 1 页' }))
  expect(navigate).toHaveBeenLastCalledWith('/?q=Java&status=open')
  fireEvent.change(screen.getByLabelText('跳转页码'), { target: { value: '3' } })
  fireEvent.submit(screen.getByLabelText('跳转页码').closest('form')!)
  expect(navigate).toHaveBeenLastCalledWith('/?q=Java&page=3&status=open')
  fireEvent.click(screen.getByRole('button', { name: '仅看收藏' }))
  expect(navigate).toHaveBeenCalledWith('/?q=Java&status=open&favorite=true')
  fireEvent.click(screen.getByRole('button', { name: '清除筛选' }))
  expect(navigate).toHaveBeenCalledWith('/')
  expect(screen.getByLabelText('关键词')).toHaveValue('')
  expect(screen.getByLabelText('状态')).toHaveValue('')
})

it('keeps the visible market fields in sync when the URL changes', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json(page)))
  const navigate = vi.fn()
  const { rerender } = render(<JobMarketPage search="?q=Java&location=上海" navigate={navigate} onExpired={vi.fn()} />)
  await screen.findByText('示例公司')
  expect(screen.getByLabelText('关键词')).toHaveValue('Java')
  rerender(<JobMarketPage search="?q=产品&status=open" navigate={navigate} onExpired={vi.fn()} />)
  expect(screen.getByLabelText('关键词')).toHaveValue('产品')
  expect(screen.getByLabelText('地点')).toHaveValue('')
  expect(screen.getByLabelText('状态')).toHaveValue('open')
})

it('reloads the favorites view after removing a saved recruitment directory entry', async () => {
  const navigate = vi.fn()
  const directory = { ...campaign, listingKind: 'recruitment_directory', recruitmentType: '校园招聘', positions: [], locations: [], isFavorite: true, primaryApplyUrl: null, source: { name: '', url: 'https://example.com/recruit' }, publishedAt: null, lastConfirmedAt: '2026-10-02' }
  const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : path.endsWith('/favorite') ? json({ isFavorite: false }) : json({ ...page, items: [directory] }))
  vi.stubGlobal('fetch', fetcher)
  render(<JobMarketPage search="?favorite=true" navigate={navigate} onExpired={vi.fn()} />)
  expect(await screen.findByText('校园招聘')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /查看招聘原文/ })).toHaveAttribute('href', 'https://example.com/recruit')
  expect(screen.queryByRole('button', { name: '记录投递' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '取消收藏 示例公司' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/job-market/campaigns/c1/favorite', expect.objectContaining({ method: 'DELETE' })))
  await waitFor(() => expect(fetcher.mock.calls.filter(([path]) => path === '/api/job-market/campaigns?favorite=true')).toHaveLength(2))
})

it('offers retry when the listing response is invalid and recovers to an empty result', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(json({ unexpected: true })).mockResolvedValueOnce(json({ ...page, items: [], total: 0 }))
  vi.stubGlobal('fetch', fetcher)
  render(<JobMarketPage search="" navigate={vi.fn()} onExpired={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('招聘数据格式异常')
  fireEvent.click(screen.getByRole('button', { name: '重试加载' }))
  expect(await screen.findByText('没有找到符合条件的招聘信息')).toBeInTheDocument()
})

it('shows a failed favorite save and expires an unauthorized listing request', async () => {
  const onExpired = vi.fn()
  const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf' ? json({ token: 'csrf' }) : path.endsWith('/favorite') ? json({ code: 'unavailable', detail: '收藏服务暂不可用' }, 503) : json(page))
  vi.stubGlobal('fetch', fetcher)
  const view = render(<JobMarketPage search="" navigate={vi.fn()} onExpired={onExpired} />)
  fireEvent.click(await screen.findByRole('button', { name: '收藏 示例公司' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/job-market/campaigns/c1/favorite', expect.anything()))
  expect(await screen.findByRole('alert')).toHaveTextContent('收藏服务暂不可用')
  expect(screen.getByRole('button', { name: '收藏 示例公司' })).toHaveAttribute('aria-pressed', 'false')
  fetcher.mockImplementation(async () => json({ code: 'unauthorized', message: '请登录' }, 401))
  view.rerender(<JobMarketPage search="?page=2" navigate={vi.fn()} onExpired={onExpired} />)
  await waitFor(() => expect(onExpired).toHaveBeenCalledOnce())
})

it('preserves the favorites constraint while filtering by location and status', async () => {
  const navigate = vi.fn()
  vi.stubGlobal('fetch', vi.fn(async () => json({ ...page, items: [], total: 0 })))
  render(<JobMarketPage search="?favorite=true" navigate={navigate} onExpired={vi.fn()} />)
  await screen.findByText('没有找到符合条件的招聘信息')
  fireEvent.change(screen.getByLabelText('地点'), { target: { value: ' 上海 ' } })
  fireEvent.change(screen.getByLabelText('状态'), { target: { value: 'stale' } })
  fireEvent.click(screen.getByRole('button', { name: '搜索' }))
  expect(navigate).toHaveBeenCalledWith('/?location=%E4%B8%8A%E6%B5%B7&status=stale&favorite=true')
  fireEvent.click(screen.getByRole('button', { name: '仅看收藏' }))
  expect(navigate).toHaveBeenCalledWith('/')
})
