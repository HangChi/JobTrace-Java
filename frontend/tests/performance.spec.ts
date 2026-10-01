import { expect, test } from '@playwright/test'

const LCP_BUDGET_MS = 2_500
const INP_BUDGET_MS = 200
const CLS_BUDGET = 0.1

type BrowserMetrics = {
  lcp: number
  inp: number
  cls: number
}

test('migration shell stays within Core Web Vitals budgets', async ({ page }) => {
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

  await page.route('**/api/health/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'ok' }),
    })
  })

  await page.goto('/')
  await expect(page.getByRole('status')).toHaveText('服务已就绪')
  await page.locator('main').click()
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
