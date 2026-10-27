import { expect, test } from '@playwright/test'

const LCP_BUDGET_MS = 2_500
const INP_BUDGET_MS = 200
const CLS_BUDGET = 0.1

test('market details stay compact and status controls remain keyboard operable', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }) }))
  await page.route('**/api/job-market/campaigns**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 'c1', listingKind: 'synced_jobs', company: { name: '星河科技', type: '企业', industry: '软件服务' }, recruitmentType: null, positions: Array.from({ length: 60 }, (_, index) => `软件研发工程师 · 业务方向 ${index + 1}`), positionCount: 60, locations: Array.from({ length: 30 }, (_, index) => ({ name: `招聘城市 ${index + 1}` })), status: 'open', primaryApplyUrl: 'https://example.com/jobs', source: { name: '招聘官网', url: 'https://example.com/jobs' }, publishedAt: '2026-10-27', lastConfirmedAt: null, isFavorite: false }], page: 1, limit: 20, total: 1 }) }))
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/')
    const row = page.getByRole('row').filter({ hasText: '星河科技' })
    await expect(row).toBeVisible()
    const rowHeight = (await row.boundingBox())!.height
    await page.getByRole('button', { name: '查看全部岗位' }).click()
    const positions = page.getByRole('dialog', { name: '星河科技的岗位列表' })
    await expect(positions).toBeVisible()
    expect((await row.boundingBox())!.height).toBe(rowHeight)
    const bounds = (await positions.boundingBox())!
    expect(bounds.height).toBeLessThanOrEqual(360)
    expect(bounds.x).toBeGreaterThanOrEqual(16)
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(width - 16)
    const scroll = positions.getByRole('region')
    await scroll.focus()
    await page.keyboard.press('End')
    await expect(positions.getByText('软件研发工程师 · 业务方向 60')).toBeInViewport()
    await page.screenshot({ path: test.info().outputPath(`market-details-${width}.png`) })
    await page.keyboard.press('Escape')
    await expect(positions).not.toBeVisible()
    await page.getByRole('button', { name: '查看全部地点' }).click()
    const locations = page.getByRole('dialog', { name: '星河科技的地点列表' })
    await expect(locations).toBeVisible()
    expect((await row.boundingBox())!.height).toBe(rowHeight)
    await page.screenshot({ path: test.info().outputPath(`market-locations-${width}.png`) })
    await locations.getByRole('button', { name: '关闭地点列表' }).click()
    await expect(locations).not.toBeVisible()
    const status = page.getByRole('combobox', { name: '状态', exact: true })
    await status.focus()
    await page.keyboard.press('ArrowDown')
    await expect(status.locator('option[value=closed]')).toBeVisible()
    await page.screenshot({ path: test.info().outputPath(`market-status-${width}.png`) })
    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await expect(status).toHaveValue('closed')
    await page.getByRole('button', { name: '筛选', exact: true }).click()
    await expect(page).toHaveURL(/status=closed/)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
  }
})

type BrowserMetrics = {
  lcp: number
  inp: number
  cls: number
  shifts: { value: number; sources: string[] }[]
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/analytics/summary', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ total: 0, submitted: 0, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [], progressReminders: [] }),
  }))
})

test('authenticated workspace stays within Core Web Vitals budgets', async ({ page }) => {
  await page.addInitScript(() => {
    const metrics: BrowserMetrics = { lcp: 0, inp: 0, cls: 0, shifts: [] }
    const metricWindow = window as Window & { __jobtraceMetrics: BrowserMetrics }
    metricWindow.__jobtraceMetrics = metrics

    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const largestPaint = entry as PerformanceEntry & {
          loadTime: number
          renderTime: number
        }
        metrics.lcp = Math.max(
          metrics.lcp,
          largestPaint.renderTime || largestPaint.loadTime || largestPaint.startTime,
        )
      }
    }).observe({ type: 'largest-contentful-paint', buffered: true })

    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const layoutShift = entry as PerformanceEntry & {
          hadRecentInput: boolean
          value: number
          sources?: { node?: Element }[]
        }
        if (!layoutShift.hadRecentInput) {
          metrics.cls += layoutShift.value
          metrics.shifts.push({ value: layoutShift.value, sources: layoutShift.sources?.map(source => source.node?.className || source.node?.tagName || '') ?? [] })
        }
      }
    }).observe({ type: 'layout-shift', buffered: true })

    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const interaction = entry as PerformanceEntry & { interactionId: number }
        if (interaction.interactionId > 0) {
          metrics.inp = Math.max(metrics.inp, interaction.duration)
        }
      }
    }).observe({ type: 'event', buffered: true, durationThreshold: 16 })
  })

  await page.route('**/api/auth/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }),
    })
  })
  await page.route('**/api/applications**', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }) })
  })

  await page.goto('/applications')
  await expect(page.getByRole('heading', { name: '投递记录', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '还没有投递记录' })).toBeVisible()
  await page.getByRole('button', { name: '新建投递' }).click()
  await expect(page.getByRole('heading', { name: '新建投递' })).toBeVisible()
  await page.evaluate(
    () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
  )

  const metrics = await page.evaluate(
    () => (window as Window & { __jobtraceMetrics: BrowserMetrics }).__jobtraceMetrics,
  )
  await test.info().attach('core-web-vitals.json', {
    body: JSON.stringify(metrics, null, 2),
    contentType: 'application/json',
  })
  console.info(`Core Web Vitals: ${JSON.stringify(metrics)}`)

  expect(metrics.lcp, 'LCP in milliseconds').toBeGreaterThan(0)
  expect(metrics.lcp, 'LCP in milliseconds').toBeLessThanOrEqual(LCP_BUDGET_MS)
  expect(metrics.inp, 'INP in milliseconds').toBeLessThanOrEqual(INP_BUDGET_MS)
  expect(metrics.cls, 'CLS score').toBeLessThanOrEqual(CLS_BUDGET)
})

test('workspace keeps navigation and primary actions operable at supported widths', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }) }))
  await page.route('**/api/applications**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }) }))
  for (const width of [375, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/applications')
    await expect(page.getByRole('button', { name: '新建投递' })).toBeVisible()
    const overflowing = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
    expect(overflowing, `horizontal overflow at ${width}px`).toBe(false)
  }
})

test('workspace primary navigation is reachable by keyboard', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }) }))
  await page.route('**/api/applications**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, total: 0, page: 1, limit: 50 }) }))
  await page.goto('/applications')
  await expect(page.getByRole('heading', { name: '还没有投递记录' })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'JobTrace 首页' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '招聘广场' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '我的投递' })).toBeFocused()
})

test('guest routes and protected deep links resolve without a dead end', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({
    status: 401,
    contentType: 'application/problem+json',
    body: JSON.stringify({ status: 401, title: 'Unauthorized' }),
  }))

  for (const [path, heading] of [
    ['/login', '登录 JobTrace'],
    ['/register', '创建账号'],
    ['/forgot-password', '找回密码'],
  ] as const) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
    await expect(page.getByRole('heading', { name: '页面未找到' })).toHaveCount(0)
  }

  await page.goto('/applications/record-1')
  await expect(page).toHaveURL(/\/login\?returnTo=%2Fapplications%2Frecord-1$/)
  await expect(page.getByRole('heading', { name: '登录 JobTrace' })).toBeVisible()
})

test('ordinary users cannot open administrator deep links', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }),
  }))

  await page.goto('/admin/users/other-user')
  await expect(page.getByRole('heading', { name: '无权访问' })).toBeVisible()
  await expect(page.getByRole('link', { name: '管理后台' })).toHaveCount(0)
})


test('interview and analytics layouts support long content and responsive filters', async ({ page }) => {
  const summary = { total: 8, submitted: 5, refused: 2, offers: 1, addedThisWeek: 2, stageDistribution: {}, followUps: [], progressReminders: [{ id: 'p1', companyName: '测试公司', positionName: '开发', stageOccurrenceId: 'stage1', stage: '一面', occurredOn: '2026-10-08', reviewStatus: null, completed: false }] }
  const report = { query: { period: '90d' }, availableCities: ['上海'], metrics: { applications: { value: 8, previous: 4, delta: 100, deltaKind: 'percent' } }, trend: [{ periodStart: '2026-10-01', label: '10/01', applications: 8, interviewed: 2, offers: 1 }], milestones: [{ key: 'applications', label: '投递', count: 8, conversionFromPrevious: null }], stageReach: [{ stage: 'interview_1', count: 2, rate: 25 }], interviews: { total: 2, completed: 1, completionRate: 50, resolved: 1, passed: 1, passRate: 100, byStage: [] }, typeBreakdown: [], cityBreakdown: [], summary: ['本周期投递量上升。'], dataQuality: [], sampleSufficient: true }
  const post = { id: 'p1', companyName: '公开公司', positionName: '工程师', city: null, stage: 'interview_1', interviewedOn: '2026-10-08', publishedAt: '2026-10-08T00:00:00Z', questionCount: 1, author: { username: '匿名', image: null }, engagement: { likeCount: 0, commentCount: 0, viewCount: 1, likedByViewer: false }, highlights: '经验总结', gaps: null, questions: [{ category: 'other', question: '如何准备？', originalAnswer: '', followUpNotes: null, improvedAnswer: null }], recentComments: [] }
  Object.assign(report.metrics, { interviewRate: { value: 25 }, offerRate: { value: 12.5 }, medianDaysToFirstInterview: { value: 7 }, reviewCompletionRate: { value: 50 } })
  report.milestones.push({ key: 'interviews', label: '面试', count: 2, conversionFromPrevious: null }, { key: 'offers', label: 'Offer', count: 1, conversionFromPrevious: null })
  post.questions[0].question = '请介绍你如何设计一个高可用的任务调度系统？'.repeat(15)
  post.questions[0].originalAnswer = '通过分片调度、重试与幂等控制保证任务执行。'.repeat(40)
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  await page.route('**/api/applications**', route => route.fulfill(fulfill({ items: [], total: 0 })))
  await page.route('**/api/interviews**', route => route.fulfill(fulfill({ items: [], total: 0, nextCursor: null, limit: 20 })))
  await page.route('**/api/interviews/public**', route => route.fulfill(fulfill({ items: [post], total: 1, nextCursor: null, limit: 20, facets: { cities: ['上海'], positions: ['工程师'] } })))
  await page.route('**/api/analytics/summary', route => route.fulfill(fulfill(summary)))
  await page.route('**/api/analytics/report**', route => route.fulfill(fulfill(report)))
  for (const [width, height] of [[1440, 1000], [375, 812], [812, 375]]) {
    await page.setViewportSize({ width, height })
    await page.goto('/interviews')
    const card = page.locator('.public-feed-card').first()
    await expect(card).toBeVisible()
    expect((await card.boundingBox())!.height).toBeLessThan(640)
    const question = card.locator('.public-feed-preview-question')
    expect(await question.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)
    const city = page.getByRole('combobox', { name: '城市', exact: true })
    await city.focus()
    await expect(city).toBeFocused()
    await city.selectOption('上海')
    await expect(city).toHaveValue('上海')
    await page.getByRole('button', { name: '按热度', exact: true }).click()
    await expect(page.getByRole('button', { name: '按热度', exact: true })).toHaveAttribute('aria-pressed', 'true')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`interviews-${width}.png`), fullPage: true })
    await page.goto('/analytics')
    await expect(page.getByRole('heading', { name: '投递趋势', exact: true })).toBeVisible()
    const period = page.getByRole('combobox', { name: '周期', exact: true })
    await period.focus()
    await expect(period).toBeFocused()
    await period.selectOption('30d')
    const request = page.waitForRequest(req => req.url().includes('/api/analytics/report?period=30d'))
    await page.getByRole('button', { name: '应用筛选', exact: true }).click()
    await request
    await expect(page.getByRole('heading', { name: '投递趋势', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`analytics-${width}.png`), fullPage: true })
  }
})
