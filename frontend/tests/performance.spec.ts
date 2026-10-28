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
    await page.screenshot({ path: test.info().outputPath(`color-market-${width}.png`), fullPage: true })
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
    await page.getByRole('button', { name: '筛选条件', exact: true }).click()
    const status = page.getByRole('combobox', { name: '状态', exact: true })
    await status.focus()
    for (let index = 0; index < 4; index++) await page.keyboard.press('ArrowDown')
    await page.screenshot({ path: test.info().outputPath(`market-status-${width}.png`) })
    await page.keyboard.press('Enter')
    await expect(status).toHaveValue('closed')
    await page.getByRole('button', { name: '搜索', exact: true }).click()
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
    const question = card.locator('.public-feed-excerpt')
    expect(await question.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)
    await page.getByRole('button', { name: '筛选条件', exact: true }).click()
    const city = page.getByRole('combobox', { name: '城市', exact: true })
    await city.focus()
    await expect(city).toBeFocused()
    await city.selectOption('上海')
    await expect(city).toHaveValue('上海')
    await page.getByRole('button', { name: '完成', exact: true }).click()
    await page.getByLabel('面经排序').selectOption('hot')
    await expect(page.getByLabel('面经排序')).toHaveValue('hot')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`interviews-${width}.png`), fullPage: true })
    await page.goto('/analytics')
    await expect(page.getByRole('heading', { name: '投递趋势', exact: true })).toBeVisible()
    const period = page.getByRole('combobox', { name: '周期', exact: true })
    await period.focus()
    await expect(period).toBeFocused()
    await period.selectOption('30d')
    const request = page.waitForRequest(req => req.url().includes('/api/analytics/report?period=30d'))
    await page.getByRole('button', { name: '查看分析', exact: true }).click()
    await request
    await expect(page.getByRole('heading', { name: '投递趋势', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`analytics-${width}.png`), fullPage: true })
  }
})

test('private reviews keep statuses visible and editing controls usable at supported widths', async ({ page }) => {
  const item = { id: 'i1', applicationId: 'a1', companyName: '星河科技（上海）', positionName: 'AI 应用与平台研发工程师', stage: 'interview_1', interviewedOn: '2026-10-08', status: 'completed', roundResult: 'passed', questionCount: 1, visibility: 'private', authorMode: 'anonymous', publishedAt: null }
  const draft = { ...item, id: 'i2', companyName: '远山科技', status: 'pending_review', roundResult: 'pending' }
  const detail = { ...item, version: 1, format: 'online', durationMinutes: 45, interviewerNotes: '围绕项目经历展开交流。', highlights: '能够清晰描述设计取舍。', gaps: '补充容量估算。', questions: [{ id: 'q1', category: 'system_design', question: '如何设计高可用的任务调度系统？', originalAnswer: '拆分调度与执行，确保幂等。', followUpNotes: '如何处理超时任务？', improvedAnswer: '补充失败重试、租约和监控告警。', selfRating: 4 }], actionItems: [{ id: 'action1', content: '补充系统设计笔记', completed: false }] }
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  await page.route('**/api/security/csrf', route => route.fulfill(fulfill({ token: 'test-csrf' })))
  await page.route('**/api/applications**', route => route.fulfill(fulfill({ items: [{ id: 'a1', companyName: item.companyName, positionName: item.positionName }], total: 1 })))
  await page.route('**/api/interviews**', route => route.fulfill(fulfill(route.request().url().includes('/i1') ? detail : { items: [item, draft], total: 2, nextCursor: null, limit: 20 })))
  for (const [width, height] of [[1440, 1000], [375, 812], [812, 375]]) {
    await page.setViewportSize({ width, height })
    await page.goto('/interviews/mine')
    const row = page.locator('.interview-record-list li').first()
    await expect(row.getByText('已完成', { exact: true })).toBeVisible()
    await expect(row.getByText('通过', { exact: true })).toBeVisible()
    await expect(row.getByText('私有', { exact: true })).toBeVisible()
    const checkbox = row.getByRole('checkbox')
    await checkbox.focus()
    await page.keyboard.press('Space')
    await expect(checkbox).toBeChecked()
    await expect(page.getByRole('link', { name: '导出 Markdown' })).toHaveAttribute('href', '/api/exports/interviews?id=i1')
    await page.getByRole('combobox', { name: '筛选状态', exact: true }).selectOption('pending_review')
    await expect(page.locator('.interview-record-list li')).toHaveCount(1)
    await expect(page.getByRole('button', { name: '远山科技 · AI 应用与平台研发工程师', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '清除', exact: true }).click()
    await expect(page.locator('.interview-record-list li')).toHaveCount(2)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`private-list-${width}.png`), fullPage: true })
    await row.getByRole('button', { name: '查看与编辑' }).click()
    await expect(page).toHaveURL(/\/interviews\/i1$/)
    await expect(page.getByRole('region', { name: '面试基本信息' })).toBeVisible()
    await expect(page.getByRole('region', { name: '发布设置' })).toBeVisible()
    const visibility = page.getByRole('radio', { name: /仅自己可见/ })
    expect((await visibility.boundingBox())!.width).toBeLessThanOrEqual(24)
    expect((await visibility.locator('..').boundingBox())!.height).toBeLessThan(150)
    await expect(page.getByText('补充复盘 · 备注、行动与总结', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('textbox', { name: '面经正文', exact: true })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '添加问题', exact: true })).toHaveCount(0)
    await page.getByRole('textbox', { name: '面经正文', exact: true }).fill('# 面试记录\n\n完整复盘内容\n\n' + Array.from({ length: 40 }, (_, i) => `### 讨论 ${i + 1}\n\n设计取舍、问题记录与下次改进。`).join('\n\n'))
    await page.getByRole('combobox', { name: '本轮结果', exact: true }).selectOption('failed')
    const frame = page.locator('.interview-markdown-frame')
    const sharing = page.getByRole('region', { name: '发布设置' })
    const beforeFrame = (await frame.boundingBox())!
    const beforeSharingTop = await sharing.evaluate(el => el.getBoundingClientRect().top + scrollY)
    const body = page.getByRole('textbox', { name: '面经正文', exact: true })
    await body.evaluate(el => { el.scrollTop = (el.scrollHeight - el.clientHeight) / 2 })
    await page.getByRole('button', { name: '预览 Markdown' }).click()
    await expect(page.locator('.interview-markdown-preview')).toContainText('完整复盘内容')
    expect((await frame.boundingBox())!.height).toBe(beforeFrame.height)
    expect(await sharing.evaluate(el => el.getBoundingClientRect().top + scrollY)).toBe(beforeSharingTop)
    expect(await page.getByRole('region', { name: '面经预览' }).evaluate(el => el.scrollTop / (el.scrollHeight - el.clientHeight))).toBeCloseTo(.5, 1)
    await page.screenshot({ path: test.info().outputPath(`private-preview-${width}.png`), fullPage: true })
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    const saveRequest = page.waitForRequest(req => req.method() === 'PATCH' && req.url().endsWith('/api/interviews/i1'))
    await page.getByRole('button', { name: '保存面经', exact: true }).click()
    expect((await saveRequest).postDataJSON()).toMatchObject({ roundResult: 'failed', questions: [{ selfRating: 4 }], interviewerNotes: detail.interviewerNotes, highlights: detail.highlights, gaps: detail.gaps, actionItems: detail.actionItems })
    await expect(page.getByRole('status')).toHaveText('面经已保存。')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`private-editor-${width}.png`), fullPage: true })
    await page.goto('/interviews/new')
    await expect(page.getByRole('heading', { name: '记录一次面试' })).toBeVisible()
    await page.getByRole('combobox', { name: '关联投递', exact: true }).selectOption('a1')
    await expect(page.getByRole('button', { name: '创建草稿' })).toBeEnabled()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`private-create-${width}.png`), fullPage: true })
  }
})

test('profile settings stay readable and operable across supported widths', async ({ page }) => {
  let profile = { id: 'user-0123456789-abcdefghij-0123456789', displayName: '求职记录用户', image: '/test-avatar.svg', recoveryEmail: 'candidate@example.test', emailVerified: true }
  let preferences = { homeEnabled: true, homeView: 'scheduled', defaultLead: '1h', defaultSnoozeMinutes: 30, emailDefault: false }
  const sessions = [{ id: 's1', createdAt: '2026-10-27T08:00:00Z', expiresAt: '2026-11-27T08:00:00Z', userAgent: 'Chrome / macOS', current: true }, { id: 's2', createdAt: '2026-10-20T08:00:00Z', expiresAt: '2026-11-20T08:00:00Z', userAgent: 'Mozilla/5.0 Chrome 浏览器 · Windows 11 · 已登录的其他设备', current: false }]
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  await page.route('**/api/security/csrf', route => route.fulfill(fulfill({ token: 'test-csrf' })))
  await page.route('**/api/profile', route => {
    if (route.request().method() === 'PATCH') profile = { ...profile, ...route.request().postDataJSON() }
    return route.fulfill(fulfill(profile))
  })
  await page.route('**/api/profile/sessions', route => route.fulfill(fulfill(sessions)))
  await page.route('**/api/reminder-settings', route => {
    if (route.request().method() === 'PATCH') preferences = route.request().postDataJSON()
    return route.fulfill(fulfill(preferences))
  })
  await page.route('**/test-avatar*.svg', route => route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" rx="32" fill="#087f73"/><circle cx="32" cy="24" r="10" fill="white"/><path d="M14 54a18 18 0 0136 0" fill="white"/></svg>' }))
  await page.route('**/api/profile/avatar', route => route.fulfill(fulfill({ url: '/test-avatar-new.svg' })))
  for (const [width, height] of [[1440, 1000], [375, 812], [812, 375]]) {
    await page.setViewportSize({ width, height })
    profile = { ...profile, image: '/test-avatar.svg' }
    await page.goto('/profile')
    await expect(page.getByRole('heading', { name: '个人中心', exact: true })).toBeVisible()
    const avatar = page.locator('.account-menu .avatar img')
    await expect(avatar).toBeVisible()
    await expect(avatar).toHaveAttribute('src', '/test-avatar.svg')
    await page.getByLabel('上传头像', { exact: true }).setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64') })
    await expect(page.getByRole('status')).toHaveText('头像已更新。')
    await expect(avatar).toHaveAttribute('src', '/test-avatar-new.svg')
    await expect(avatar).toHaveJSProperty('naturalWidth', 64)
    await page.locator('.account-menu').screenshot({ path: test.info().outputPath(`account-avatar-${width}.png`) })
    await page.getByRole('button', { name: '移除头像', exact: true }).click()
    await expect(avatar).toHaveCount(0)
    const menu = page.locator('.account-menu')
    await menu.locator('summary').focus()
    await page.keyboard.press('Enter')
    await expect(menu.locator('.account-popover')).toBeVisible()
    expect(await menu.locator('.menu-chevron').evaluate(el => el.tagName.toLowerCase())).toBe('svg')
    await expect(menu.locator('.menu-chevron')).toHaveCSS('width', '16px')
    await expect(menu.locator('.menu-chevron')).toHaveCSS('height', '16px')
    await menu.getByRole('link', { name: '个人中心', exact: true }).click()
    await expect(menu).not.toHaveAttribute('open')
    const name = page.getByRole('textbox', { name: '显示名称', exact: true })
    await name.fill('我的求职记录')
    await page.getByRole('button', { name: '保存资料', exact: true }).click()
    await expect(page.getByRole('status')).toHaveText('个人资料已保存。')
    await expect(page.locator('.account-menu .user-summary strong')).toHaveText('我的求职记录')
    await page.getByRole('navigation', { name: '个人中心分区' }).getByRole('link', { name: '提醒设置', exact: true }).click()
    const home = page.getByRole('checkbox', { name: '在首页显示提醒' })
    await home.focus()
    const wasChecked = await home.isChecked()
    await page.keyboard.press('Space')
    await expect(home).toBeChecked({ checked: !wasChecked })
    expect((await home.boundingBox())!.width).toBeLessThanOrEqual(20)
    await page.getByRole('combobox', { name: '默认提前量', exact: true }).selectOption('30m')
    const settingsRequest = page.waitForRequest(req => req.url().endsWith('/api/reminder-settings') && req.method() === 'PATCH')
    await page.getByRole('button', { name: '保存提醒设置', exact: true }).click()
    expect((await settingsRequest).postDataJSON()).toMatchObject({ defaultLead: '30m', homeEnabled: !wasChecked })
    await expect(page.getByRole('status')).toHaveText('提醒设置已保存。')
    await page.getByRole('navigation', { name: '个人中心分区' }).getByRole('link', { name: '账号安全', exact: true }).click()
    await expect(page.getByRole('region', { name: '安全邮箱', exact: true })).toBeVisible()
    await expect(page.getByRole('region', { name: '修改密码', exact: true })).toBeVisible()
    await expect(page.getByRole('region', { name: '登录设备', exact: true })).toBeVisible()
    const nav = page.getByRole('navigation', { name: '个人中心分区' })
    await nav.getByRole('link', { name: '账号信息', exact: true }).click()
    await expect(page).toHaveURL(/#profile-account$/)
    await expect(page.getByRole('heading', { name: '账号信息', exact: true })).toBeInViewport()
    await nav.getByRole('link', { name: '数据管理', exact: true }).click()
    await expect(page.getByRole('link', { name: '导出投递 Excel', exact: true })).toHaveAttribute('href', '/api/exports/applications?scope=all&format=xlsx')
    await page.evaluate(() => scrollTo(0, 0))
    for (const section of ['个人资料', '提醒设置', '账号安全', '数据管理', '账号信息']) {
      await nav.getByRole('link', { name: section, exact: true }).click()
      await expect(nav.getByRole('link', { name: section, exact: true })).toHaveAttribute('aria-current', 'location')
      await expect(page.locator('.profile-content>.content-card:visible')).toHaveCount(1)
      expect(await page.evaluate(() => scrollY)).toBe(0)
      expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
      await page.screenshot({ path: test.info().outputPath(`profile-${section}-${width}.png`), fullPage: true })
    }
    await nav.getByRole('link', { name: '个人资料', exact: true }).click()
    await expect(name).toHaveValue('我的求职记录')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`profile-${width}.png`), fullPage: true })
  }
})

test('application statuses stay centered with and without follow-up reminders', async ({ page }) => {
  const base = { companyName: '状态对齐公司', positionName: '研发工程师', city: null, jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-10', type: 'campus_recruitment', stages: ['interview_1'], version: 1 }
  const items = [
    { ...base, id: 'a1', status: 'submitted', needsFollowUp: false, followUpDays: 0, followUpReason: null },
    { ...base, id: 'a2', status: 'offer', needsFollowUp: true, followUpDays: 17, followUpReason: 'timeline' },
    { ...base, id: 'a3', status: 'refused', needsFollowUp: true, followUpDays: 120, followUpReason: 'application' },
  ]
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  await page.route('**/api/security/csrf', route => route.fulfill(fulfill({ token: 'test-csrf' })))
  await page.route('**/api/applications**', route => {
    if (route.request().method() === 'PATCH') {
      const item = items[0]
      item.status = route.request().postDataJSON().status
      return route.fulfill(fulfill({ id: item.id, status: item.status, latestDate: item.latestDate, version: 2 }))
    }
    return route.fulfill(fulfill({ items, total: 3, page: 1, limit: 10, nextCursor: null }))
  })
  for (const [width, height] of [[1440, 1000], [375, 812], [812, 375]]) {
    await page.setViewportSize({ width, height })
    await page.goto('/applications')
    const cells = page.locator('.application-status-cell')
    await expect(cells).toHaveCount(3)
    const rowHeights: number[] = []
    const statusHeights: number[] = []
    for (const cell of await cells.all()) {
      const control = cell.getByRole('combobox')
      const box = (await control.boundingBox())!
      expect(box.width).toBeLessThanOrEqual(90)
      expect(box.height).toBeLessThanOrEqual(34)
      const stack = (await cell.locator('.application-status-stack').boundingBox())!
      expect(Math.abs(box.y + box.height / 2 - stack.y - stack.height / 2)).toBeLessThan(1)
      statusHeights.push(stack.height)
      const style = await control.evaluate(el => { const css = getComputedStyle(el); return { left: css.paddingLeft, right: css.paddingRight, align: css.textAlign } })
      expect(style.left).toBe(style.right)
      expect(style.align).toBe('center')
      await expect(control).toHaveCSS('border-radius', '999px')
      const colors: Record<string, string> = { submitted: 'rgb(237, 247, 241)', offer: 'rgb(251, 244, 230)', refused: 'rgb(252, 240, 242)' }
      await expect(control).toHaveCSS('background-color', colors[await control.inputValue()])
      const row = (await cell.locator('..').boundingBox())!
      rowHeights.push(row.height)
      const reminder = cell.locator('.follow-up')
      if (await reminder.count()) {
        const reminderBox = (await reminder.boundingBox())!
        expect(reminderBox.y).toBeGreaterThanOrEqual(box.y + box.height)
        expect(reminderBox.y + reminderBox.height).toBeLessThanOrEqual(row.y + row.height + 1)
      }
      if (width > 700) {
        const date = (await cell.locator('..').locator('time').first().boundingBox())!
        expect(Math.abs(box.y + box.height / 2 - date.y - date.height / 2)).toBeLessThan(2)
      }
    }
    expect(Math.max(...statusHeights) - Math.min(...statusHeights)).toBeLessThan(1)
    if (width > 700) expect(Math.max(...rowHeights) - Math.min(...rowHeights)).toBeLessThan(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`application-status-${width}.png`), fullPage: true })
    const control = cells.first().getByRole('combobox')
    const statusRequest = page.waitForRequest(req => req.url().endsWith('/api/applications/a1/status') && req.method() === 'PATCH')
    const nextStatus = items[0].status === 'offer' ? 'submitted' : 'offer'
    await control.selectOption(nextStatus)
    await statusRequest
    await expect(control).toHaveValue(nextStatus)
    await expect(control).toHaveCSS('background-color', nextStatus === 'offer' ? 'rgb(251, 244, 230)' : 'rgb(237, 247, 241)')
  }
})

test('color hierarchy preserves the brand and readable semantic text', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'unauthorized', detail: '请登录' }) }))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录 JobTrace', exact: true })).toBeVisible()
  const colors = await page.evaluate(() => {
    const css = getComputedStyle(document.documentElement)
    return Object.fromEntries(['brand', 'brand-dark', 'brand-soft', 'ink', 'muted', 'paper', 'surface', 'accent-info', 'accent-info-soft', 'accent-warm', 'accent-warm-soft', 'accent-review', 'accent-review-soft', 'accent-danger', 'accent-danger-soft'].map(name => [name, css.getPropertyValue(`--${name}`).trim()]))
  })
  expect({ brand: colors.brand, dark: colors['brand-dark'], soft: colors['brand-soft'] }).toEqual({ brand: '#357f78', dark: '#246b64', soft: '#e4f4ef' })
  function luminance(hex: string) {
    const full = hex.length === 4 ? '#' + hex.slice(1).split('').map(value => value + value).join('') : hex
    const channels = [1, 3, 5].map(index => parseInt(full.slice(index, index + 2), 16) / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
    return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
  }
  for (const [foreground, background] of [['ink', 'surface'], ['muted', 'surface'], ['muted', 'paper'], ['brand', 'surface'], ['accent-info', 'accent-info-soft'], ['accent-warm', 'accent-warm-soft'], ['accent-review', 'accent-review-soft'], ['accent-danger', 'accent-danger-soft']]) {
    const a = luminance(colors[foreground]); const b = luminance(colors[background])
    expect((Math.max(a, b) + .05) / (Math.min(a, b) + .05), `${foreground} on ${background}`).toBeGreaterThanOrEqual(4.5)
  }
  await expect(page.getByRole('button', { name: '登录', exact: false })).toHaveCSS('background-color', 'rgb(53, 127, 120)')
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 1000 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`color-login-${width}.png`), fullPage: true })
  }
})


test('full public interviews keep a readable document and separate discussion on desktop and mobile', async ({ page }) => {
  const post = { id: 'p1', companyName: '星河科技', positionName: 'AI 应用与平台研发工程师', city: '上海', stage: 'interview_1', interviewedOn: '2026-10-08', publishedAt: '2026-10-09T00:00:00Z', questionCount: 1, author: { username: '分享者', image: null }, engagement: { likeCount: 0, commentCount: 0, viewCount: 1, likedByViewer: false }, highlights: null, gaps: null, questions: [{ category: 'other', question: '# 面试记录\n\n围绕项目经历和系统设计展开交流。\n\n## 项目交流\n\n- 介绍项目背景\n- 说明技术取舍\n\n> 面试前整理关键决策。\n\n```typescript\nconst result = "' + 'x'.repeat(180) + '";\n```', originalAnswer: null, followUpNotes: null, improvedAnswer: null }], recentComments: [] }
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  await page.route('**/api/security/csrf', route => route.fulfill(fulfill({ token: 'test-csrf' })))
  await page.route('**/api/applications**', route => route.fulfill(fulfill({ items: [], total: 0 })))
  await page.route('**/api/interviews**', route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/like')) return route.fulfill(fulfill({ ...post.engagement, likeCount: 1, likedByViewer: true }))
    if (url.pathname.endsWith('/comments')) {
      if (route.request().method() === 'POST') return route.fulfill(fulfill({ comment: { id: 'c1', content: '感谢分享，准备方向很清晰。', createdAt: '2026-10-10T00:00:00Z', author: { username: '测试用户', image: null } }, engagement: { ...post.engagement, commentCount: 1 } }))
      return route.fulfill(fulfill([]))
    }
    if (url.pathname.endsWith('/public/p1')) return route.fulfill(fulfill(post))
    return route.fulfill(fulfill({ items: [post], total: 1, nextCursor: null, limit: 20, facets: { cities: ['上海'], positions: [] } }))
  })
  for (const width of [1920, 1440, 1024, 768, 375, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/interviews')
    const squareBounds = (await page.locator('.interview-square-page').boundingBox())!
    await page.getByRole('button', { name: '查看完整面经' }).click()
    await expect(page).toHaveURL(/shared\/p1$/)
    await expect(page.getByRole('heading', { name: '星河科技 · AI 应用与平台研发工程师', level: 1 })).toBeVisible()
    const readingBounds = (await page.locator('.interview-reading-page').boundingBox())!
    const cardBounds = (await page.locator('.public-interview').boundingBox())!
    expect(Math.abs(readingBounds.x - squareBounds.x)).toBeLessThanOrEqual(1)
    expect(Math.abs(readingBounds.width - squareBounds.width)).toBeLessThanOrEqual(1)
    expect(Math.abs(cardBounds.x - squareBounds.x)).toBeLessThanOrEqual(1)
    const toolsBounds = (await page.locator('.public-reading-tools .public-feed-sidebar').boundingBox())!
    if (width > 900) {
      expect(Math.abs(cardBounds.width + 24 + toolsBounds.width - squareBounds.width)).toBeLessThanOrEqual(1)
      expect(toolsBounds.width).toBe(280)
      expect(cardBounds.width).toBeGreaterThan(0)
      expect(Math.abs((await page.locator('.public-reading-contents').boundingBox())!.y - cardBounds.y)).toBeLessThanOrEqual(1)
    } else {
      expect(Math.abs(cardBounds.width - squareBounds.width)).toBeLessThanOrEqual(1)
      const contents = (await page.locator('.public-reading-contents').boundingBox())!
      expect(contents.y + contents.height).toBeLessThanOrEqual(cardBounds.y)
      expect(toolsBounds.y).toBeGreaterThan(cardBounds.y + cardBounds.height)
    }
    const body = page.getByRole('region', { name: '面经正文' })
    expect((await body.boundingBox())!.width).toBeLessThanOrEqual(920)
    const headerBounds = (await page.locator('.public-reading-header').boundingBox())!
    const commentBounds = (await page.locator('.public-comments').boundingBox())!
    expect(Math.abs(headerBounds.x - commentBounds.x)).toBeLessThanOrEqual(1)
    await expect(body.getByRole('heading', { name: '面试记录', level: 2 })).toBeVisible()
    await expect(body.getByRole('list')).toBeVisible()
    await expect(page.locator('.public-feed-sidebar')).toBeVisible()
    const directory = page.getByRole('navigation', { name: '面经目录' })
    if (width <= 900) {
      await expect(directory).toBeHidden()
      await page.locator('.public-reading-contents summary').click()
    }
    await directory.getByRole('link', { name: '项目交流' }).click()
    await expect(page.locator('#reading-section-1')).toBeInViewport()
    await expect(page.locator('#reading-section-1')).toBeFocused()
    await directory.getByRole('link', { name: '评论', exact: true }).click()
    await expect(page.locator('#reading-comments')).toBeInViewport()
    if (width <= 900) await page.locator('.public-reading-contents summary').click()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    expect(await body.locator('pre').evaluate(el => el.scrollWidth > el.clientWidth)).toBe(true)
    await page.getByRole('button', { name: '点赞 · 0', exact: true }).click()
    await expect(page.getByRole('button', { name: '取消点赞 · 1' })).toHaveAttribute('aria-pressed', 'true')
    await page.getByLabel('发表评论').fill('感谢分享，准备方向很清晰。')
    await page.getByRole('button', { name: '提交评论' }).click()
    await expect(page.locator('.public-comment-list')).toContainText('感谢分享，准备方向很清晰。')
    await page.screenshot({ path: test.info().outputPath(`reading-width-${width}.png`), fullPage: true })
    await page.getByRole('button', { name: '返回面经广场' }).click()
    await expect(page).toHaveURL(/\/interviews$/)
  }
})


test('export menu keeps its chevron aligned and opens by keyboard', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }) }))
  await page.route('**/api/applications**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], total: 0, page: 1, limit: 10 }) }))
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/applications')
    const menu = page.locator('.workspace-header-actions .application-export-menu')
    const trigger = menu.locator('summary')
    await expect(trigger).toHaveText('导出')
    const label = (await trigger.locator('span').boundingBox())!
    const icon = (await trigger.locator('svg').boundingBox())!
    expect(Math.abs(label.y + label.height / 2 - icon.y - icon.height / 2)).toBeLessThan(1)
    expect(icon.x - label.x - label.width).toBe(8)
    await trigger.focus()
    await page.keyboard.press('Enter')
    await expect(menu).toHaveAttribute('open', '')
    await expect(menu.getByRole('link', { name: 'Excel 工作簿 .xlsx' })).toHaveAttribute('href', /format=xlsx/)
    await page.screenshot({ path: test.info().outputPath(`export-menu-${width}.png`) })
    await page.keyboard.press('Enter')
    await expect(menu).not.toHaveAttribute('open')
  }
})

test('numbered pagination preserves filters and fits desktop and mobile', async ({ page }) => {
  const fulfill = (value: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route('**/api/auth/session', route => route.fulfill(fulfill({ session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } })))
  const item = { id: 'a1', companyName: '星河科技', positionName: '前端工程师', city: '上海', jobUrl: null, appliedDate: '2026-10-01', latestDate: '2026-10-01', status: 'submitted', type: 'campus_recruitment', stages: [], needsFollowUp: false, followUpDays: 0, followUpReason: null, version: 1 }
  await page.route('**/api/applications**', route => {
    const params = new URL(route.request().url()).searchParams
    return route.fulfill(fulfill({ items: [item], total: 240, page: Number(params.get('page') ?? 1), limit: Number(params.get('limit') ?? 10) }))
  })
  await page.route('**/api/job-market/campaigns**', route => route.fulfill(fulfill({ items: [{ id: 'c1', listingKind: 'synced_jobs', company: { name: '星河科技', type: '企业', industry: '软件' }, recruitmentType: null, positions: ['软件工程师'], positionCount: 1, locations: [], status: 'open', primaryApplyUrl: 'https://example.com/jobs', source: { name: '官网', url: 'https://example.com/jobs' }, publishedAt: '2026-10-27', isFavorite: false }], page: Number(new URL(route.request().url()).searchParams.get('page') ?? 1), total: 480, limit: 20 })))
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/applications?q=星河&sort=company&page=10')
    const nav = page.getByRole('navigation', { name: '投递记录分页' })
    await expect(nav.getByRole('button', { name: '第 10 页' })).toHaveAttribute('aria-current', 'page')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`pagination-middle-${width}.png`), fullPage: true })
    await nav.getByRole('button', { name: '向后跳 5 页' }).click()
    await expect(nav.getByRole('button', { name: '第 15 页' })).toHaveAttribute('aria-current', 'page')
    expect(new URL(page.url()).searchParams.get('q')).toBe('星河')
    await nav.getByLabel('跳转页码').fill('22')
    await nav.getByLabel('跳转页码').press('Enter')
    await expect(nav.getByRole('button', { name: '第 22 页' })).toHaveAttribute('aria-current', 'page')
    await page.getByLabel('每页显示', { exact: true }).selectOption('20')
    await expect(nav.getByRole('button', { name: '第 1 页' })).toHaveAttribute('aria-current', 'page')
    const size = (await page.getByLabel('每页显示', { exact: true }).boundingBox())!
    expect(size.width).toBeLessThan(90)
    expect(size.height).toBeLessThanOrEqual(36)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`pagination-applications-${width}.png`), fullPage: true })
    await page.goto('/?status=open&page=10')
    const market = page.getByRole('navigation', { name: '招聘记录分页' })
    await expect(market.getByRole('button', { name: '第 10 页' })).toHaveAttribute('aria-current', 'page')
    await market.getByRole('button', { name: '向前跳 5 页' }).click()
    await expect(market.getByRole('button', { name: '第 5 页' })).toHaveAttribute('aria-current', 'page')
    await market.getByLabel('跳转页码').fill('24')
    await market.getByRole('button', { name: '跳转', exact: true }).click()
    await expect(market.getByRole('button', { name: '下一页' })).toBeDisabled()
    expect(new URL(page.url()).searchParams.get('status')).toBe('open')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: test.info().outputPath(`pagination-market-${width}.png`), fullPage: true })
  }
})

test('public interview sidebar aligns with the feed as one balanced panel', async ({ page }) => {
  const post = { id: 'p1', companyName: '星河科技', positionName: '前端开发工程师', city: '上海', stage: 'interview_2', interviewedOn: '2026-10-08', publishedAt: '2026-10-09T00:00:00Z', questionCount: 1, author: null, engagement: { likeCount: 0, commentCount: 0, viewCount: 4, likedByViewer: false }, highlights: null, questions: [{ question: '如何理解 React 从状态更新到页面提交的过程？'.repeat(30), originalAnswer: null, followUpNotes: null, improvedAnswer: null }] }
  await page.route('**/api/auth/session', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'USER' } }) }))
  await page.route('**/api/interviews/public**', route => {
    const hot = new URL(route.request().url()).searchParams.get('sort') === 'hot'
    const items = hot ? [post, { ...post, id: 'p2', companyName: '远山科技', positionName: '全栈开发工程师与平台研发方向'.repeat(5) }, { ...post, id: 'p3', companyName: '长河科技' }] : [post, { ...post, id: 'p2' }]
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items, total: 12345, nextCursor: null, facets: { cities: [], positions: [] } }) })
  })
  await page.route('**/api/interviews?**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [], total: 0, nextCursor: null }) }))
  for (const width of [1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/interviews')
    const sidebar = page.getByRole('complementary', { name: '面经广场辅助信息' })
    const panel = sidebar.locator('.public-feed-panel')
    const feed = page.getByRole('region', { name: '公开面经列表' })
    await expect(page.locator('.public-feed-total')).toHaveText('公开面经 12345 篇')
    await expect(sidebar.getByText('12345')).toHaveCount(0)
    await expect(sidebar.getByText('公开动态')).toHaveCount(0)
    await expect(sidebar.locator(':scope > div')).toHaveCount(1)
    await expect(sidebar.getByRole('listitem')).toHaveCount(3)
    const panelBounds = (await panel.boundingBox())!
    const feedBounds = (await feed.boundingBox())!
    if (width >= 1024) {
      expect(Math.abs(panelBounds.y - feedBounds.y)).toBeLessThanOrEqual(1)
      expect(panelBounds.height).toBeGreaterThanOrEqual(300)
      expect(panelBounds.height).toBeLessThanOrEqual(520)
      expect((await feed.locator('article').first().boundingBox())!.height).toBeLessThanOrEqual(340)
    } else {
      expect(panelBounds.y).toBeGreaterThanOrEqual(feedBounds.y + feedBounds.height)
    }
    expect(await panel.evaluate(element => element.scrollWidth <= element.clientWidth && element.scrollHeight <= element.clientHeight)).toBe(true)
    await expect(sidebar.getByText('完成复盘后可公开，默认匿名，可随时转为私有。')).toBeVisible()
    const excerpt = feed.locator('.public-feed-excerpt').first()
    expect(await excerpt.evaluate(element => element.getBoundingClientRect().height <= parseFloat(getComputedStyle(element).lineHeight) * 3 + 1)).toBe(true)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: test.info().outputPath(`balanced-feed-${width}.png`), fullPage: true })
    await sidebar.getByRole('button', { name: '阅读热门面经：星河科技 · 前端开发工程师' }).click()
    await expect(page).toHaveURL(/\/interviews\/shared\/p1$/)
    await page.goto('/interviews')
    await sidebar.getByRole('button', { name: '个人空间 我的面经 →' }).click()
    await expect(page).toHaveURL(/\/interviews\/mine$/)
  }
})
