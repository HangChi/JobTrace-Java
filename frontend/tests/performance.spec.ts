import { expect, test } from '@playwright/test'

const LCP_BUDGET_MS = 2_500
const INP_BUDGET_MS = 200
const CLS_BUDGET = 0.1

type BrowserMetrics = {
  lcp: number
  inp: number
  cls: number
}

test('authenticated workspace stays within Core Web Vitals budgets', async ({ page }) => {
  await page.addInitScript(() => {
    const metrics: BrowserMetrics = { lcp: 0, inp: 0, cls: 0 }
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
        }
        if (!layoutShift.hadRecentInput) metrics.cls += layoutShift.value
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
  await expect(page.getByRole('link', { name: '职迹 JobTrace' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '投递记录' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '提醒' })).toBeFocused()
})
