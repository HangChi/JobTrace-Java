import { expect, test } from '@playwright/test'

for (const width of [1440, 1024, 768, 375, 320]) {
  test(`market query bar and filter panel work at ${width}px`, async ({ page }) => {
    await page.route('**/api/auth/session', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ session: { id: 's1', expiresAt: '2027-01-01' }, user: { id: 'u1', displayName: '测试用户', image: null, role: 'user' } }) }))
    await page.route('**/api/job-market/campaigns**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [], page: 1, limit: 20, total: 0 }) }))
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/?favorite=true&page=2')
    const bar = page.getByRole('search', { name: '筛选招聘机会' })
    const trigger = bar.getByRole('button', { name: '筛选条件', exact: true })
    await expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect((await bar.boundingBox())!.height).toBeLessThanOrEqual(width > 800 ? 100 : 160)
    await bar.getByLabel('关键词').fill('Java')
    await bar.getByLabel('地点', { exact: true }).fill('上海')
    await trigger.click()
    const panel = bar.getByRole('region', { name: '筛选面板' })
    await expect(panel).toBeVisible()
    await panel.getByRole('combobox', { name: '状态', exact: true }).selectOption('open')
    await panel.getByLabel('发布时间').fill('2026-10-01')
    const bounds = (await panel.boundingBox())!
    expect(bounds.x).toBeGreaterThanOrEqual(12)
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(width - 12)
    expect(await panel.getByLabel('发布时间').evaluate(node => {
      const style = getComputedStyle(node)
      const context = document.createElement('canvas').getContext('2d')!
      context.font = style.font
      return node.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight) - 20 >= context.measureText('10/01/2026').width
    })).toBe(true)
    await panel.screenshot({ path: `/private/tmp/jobtrace-market-panel-${width}.png` })
    await panel.getByRole('button', { name: '应用筛选' }).click()
    await expect(page).toHaveURL(/q=Java/)
    expect(Object.fromEntries(new URL(page.url()).searchParams)).toEqual({ q: 'Java', location: '上海', status: 'open', postedFrom: '2026-10-01', favorite: 'true' })
    await expect(trigger).toHaveAttribute('aria-expanded', 'false')
    await expect(bar.getByLabel('已选 2 项')).toBeVisible()
    await bar.screenshot({ path: `/private/tmp/jobtrace-market-filters-${width}.png` })
    await trigger.click()
    await page.keyboard.press('Escape')
    await expect(trigger).toBeFocused()
    await expect(trigger).toHaveAttribute('aria-expanded', 'false')
    await trigger.click()
    await page.getByRole('heading', { name: '招聘广场', exact: true }).click()
    await expect(trigger).toHaveAttribute('aria-expanded', 'false')
    await bar.getByRole('button', { name: '清除筛选', exact: true }).click()
    await expect(page).toHaveURL(/\/$/)
    await expect(bar.getByLabel('关键词')).toHaveValue('')
    await trigger.click()
    await expect(panel.getByLabel('发布时间')).toHaveValue('')
    await expect(panel.getByRole('combobox', { name: '状态', exact: true })).toHaveValue('')
    await panel.getByRole('button', { name: '关闭筛选面板' }).click()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
}
