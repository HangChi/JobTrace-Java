import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'

const summary = { total: 0, submitted: 0, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [], progressReminders: [] }
const report = { availableCities: ['上海'], metrics: { applications: { value: 8 } }, trend: [], milestones: [], stageReach: [], interviews: { total: 0 }, typeBreakdown: [], cityBreakdown: [], summary: [], dataQuality: [], sampleSufficient: false }
async function checkToolbar(page: Page, toolbar: Locator, width: number, name: string) {
  await expect(toolbar).toBeVisible()
  const bounds = (await toolbar.boundingBox())!
  expect(bounds.height).toBeLessThanOrEqual(width > 900 ? 140 : 300)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const controls = await toolbar.locator('input,select,button').all()
  for (const control of controls) {
    if (!(await control.isVisible())) continue
    const box = (await control.boundingBox())!
    expect(box.x).toBeGreaterThanOrEqual(bounds.x)
    expect(box.x + box.width).toBeLessThanOrEqual(bounds.x + bounds.width + 1)
  }
  await toolbar.screenshot({ path: `/private/tmp/jobtrace-query-${name}-${width}.png` })
}

for (const width of [1440, 1024, 768, 375, 320]) {
  test(`Ant-style query bars work at ${width}px`, async ({ page }) => {
    await page.route('**/api/**', route => {
      const url = new URL(route.request().url())
      const path = url.pathname
      const body = path === '/api/auth/session' ? { session: { id: 's1', expiresAt: '2027-01-01' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'user' } }
        : path === '/api/analytics/summary' ? summary
          : path === '/api/analytics/report' ? { ...report, query: Object.fromEntries(url.searchParams) }
            : path === '/api/interviews/public' ? { items: [], total: 0, nextCursor: null, facets: { cities: ['上海'], positions: ['工程师'] } }
              : { items: [], total: 0, page: 1, limit: 20, nextCursor: null }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    })
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/applications')
    const applications = page.getByRole('form', { name: '筛选投递记录' })
    await checkToolbar(page, applications, width, 'applications')
    await applications.getByLabel('搜索投递').fill('Java')
    await applications.getByRole('combobox', { name: '排序', exact: true }).selectOption('latestDate')
    await applications.getByRole('button', { name: '筛选条件', exact: true }).click()
    const applicationPanel = applications.getByRole('region', { name: '筛选面板' })
    await applicationPanel.getByRole('combobox', { name: '状态', exact: true }).selectOption('offer')
    await applicationPanel.getByRole('combobox', { name: '类型', exact: true }).selectOption('campus_recruitment')
    await applicationPanel.screenshot({ path: `/private/tmp/jobtrace-application-panel-${width}.png` })
    await applicationPanel.getByRole('button', { name: '应用筛选' }).click()
    await expect(page).toHaveURL(/q=Java/)
    expect(new URL(page.url()).searchParams.get('status')).toBe('offer')
    await applications.getByRole('button', { name: '清空条件' }).click()
    await expect(applications.getByLabel('搜索投递')).toHaveValue('')

    await page.goto('/interviews')
    const interviews = page.getByRole('search', { name: '面经检索与筛选' })
    await checkToolbar(page, interviews, width, 'interviews')
    await interviews.getByLabel('搜索公司').fill('测试')
    await interviews.getByLabel('面经排序').selectOption('hot')
    await interviews.getByRole('button', { name: '筛选条件', exact: true }).click()
    await interviews.getByRole('combobox', { name: '城市', exact: true }).selectOption('上海')
    await interviews.getByRole('combobox', { name: '岗位', exact: true }).selectOption('工程师')
    await interviews.getByRole('button', { name: '完成' }).click()
    const resetRequest = page.waitForRequest(req => req.url().includes('/api/interviews/public?sort=latest&limit=20') && !req.url().includes('&q='))
    await interviews.getByRole('button', { name: '清除筛选' }).click()
    await resetRequest
    await expect(interviews.getByLabel('搜索公司')).toHaveValue('')
    await expect(interviews.getByLabel('面经排序')).toHaveValue('latest')

    await page.goto('/analytics')
    const analytics = page.getByRole('form', { name: '分析范围' })
    await checkToolbar(page, analytics, width, 'analytics')
    await expect(analytics.getByLabel('开始日期')).toHaveCount(0)
    await analytics.getByRole('button', { name: '筛选条件', exact: true }).click()
    await analytics.getByRole('combobox', { name: '城市', exact: true }).selectOption('上海')
    await analytics.getByRole('button', { name: '应用筛选' }).click()
    await analytics.getByRole('combobox', { name: '周期', exact: true }).selectOption('custom')
    await analytics.getByLabel('开始日期').fill('2026-10-01')
    await analytics.getByLabel('结束日期').fill('2026-10-27')
    const request = page.waitForRequest(req => req.url().includes('/api/analytics/report?period=custom&from=2026-10-01&to=2026-10-27&city='))
    await analytics.getByRole('button', { name: '查看分析' }).click()
    await request
    await analytics.screenshot({ path: `/private/tmp/jobtrace-query-custom-${width}.png` })
    await analytics.getByRole('button', { name: '恢复默认' }).click()
    await expect(analytics.getByLabel('开始日期')).toHaveCount(0)
    await expect(analytics.getByRole('combobox', { name: '周期', exact: true })).toHaveValue('90d')
  })
}
