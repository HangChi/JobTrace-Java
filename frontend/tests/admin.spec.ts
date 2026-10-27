import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const user = { id: 'u2', username: 'linxia', email: 'linxia@example.com', internalEmail: null, role: 'USER', disabled: false, accessVersion: 1, applicationCount: 28, interviewCount: 6, createdAt: '2026-10-01T00:00:00Z', lastSignInAt: '2026-10-27T08:00:00Z' }
const summary = { counts: { status: 'available', value: { users: 1286, activeUsers: 864, administrators: 3, disabledUsers: 12, applications: 8452, interviews: 2168 } }, activity: { status: 'available', windows: { registered7d: 86, active7d: 328, registered30d: 264, active30d: 864 } } }
const audit = { id: 'e1', actorIdentifier: 'admin', targetIdentifier: 'linxia', eventType: 'disable_user', outcome: 'success', reason: '根据账号审核结果暂停访问', createdAt: '2026-10-27T08:00:00Z' }

async function mockAdmin(page: Page) {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const body = path === '/api/auth/session' ? { session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '管理员', image: null, role: 'admin' } }
      : path === '/api/admin/summary' ? summary
      : path === '/api/admin/users/u2' ? { ...user, recentAuditEvents: [audit], applications: { items: [] }, interviews: { items: [] } }
      : path === '/api/admin/users' ? { items: [user, { ...user, id: 'u3', username: 'chenyu', email: 'chenyu@example.com', role: 'ADMIN', disabled: true }], total: 2 }
      : path === '/api/admin/audit-events' ? { items: [audit] }
      : { items: [], total: 0 }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

async function fitsPage(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
}

for (const width of [1440, 768, 375]) {
  test(`admin views remain usable at ${width}px`, async ({ page }) => {
    await mockAdmin(page)
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/admin')
    await expect(page.getByText('1,286')).toBeVisible()
    const metrics = page.locator('.admin-summary-strip article')
    await expect(metrics).toHaveCount(6)
    for (const metric of await metrics.all()) {
      const height = (await metric.boundingBox())!.height
      expect(height).toBeGreaterThanOrEqual(width === 375 ? 104 : 116)
      expect(height).toBeLessThanOrEqual(136)
    }
    expect(await page.locator('.admin-summary-strip').evaluate(node => getComputedStyle(node).gridTemplateColumns.split(' ').length)).toBe(width === 375 ? 2 : 3)
    await expect(page.getByRole('heading', { name: '常用操作' })).toBeVisible()
    await fitsPage(page)
    await page.screenshot({ path: `/private/tmp/jobtrace-admin-overview-${width}.png`, fullPage: true })
    await page.getByRole('button', { name: '前往用户管理' }).click()
    await expect(page.getByRole('heading', { name: '账号目录' })).toBeVisible()
    await expect(page.getByRole('table').getByText('linxia', { exact: true })).toBeVisible()
    await fitsPage(page)
    await page.screenshot({ path: `/private/tmp/jobtrace-admin-users-${width}.png`, fullPage: true })
    const table = page.getByRole('table')
    await table.evaluate(node => { node.parentElement!.scrollLeft = node.parentElement!.scrollWidth })
    await page.getByRole('button', { name: '管理访问' }).first().click()
    await expect(page.getByRole('form', { name: '管理访问权限' })).toBeVisible()
    await page.getByRole('textbox', { name: '原因（至少 10 字）' }).fill('根据账号审核结果调整访问权限')
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '操作审计', exact: true }).click()
    await expect(page.getByText('admin → linxia')).toBeVisible()
    await fitsPage(page)
    await page.getByRole('button', { name: '招聘同步', exact: true }).click()
    await expect(page.getByRole('heading', { name: '招聘来源管理' })).toBeVisible()
    await fitsPage(page)
    await page.goto('/admin/users/u2')
    await expect(page.getByRole('heading', { name: 'linxia' })).toBeVisible()
    await fitsPage(page)
  })
}

for (const width of [1440, 375]) {
  test(`large marketplace inventories scroll inside each card at ${width}px`, async ({ page }) => {
    await mockAdmin(page)
    await page.route('**/api/admin/job-market/**', route => {
      const path = new URL(route.request().url()).pathname
      const items = Array.from({ length: 40 }, (_, index) => {
        const number = index + 1
        if (path.endsWith('/sources')) return { id: `s${index}`, company: { id: `c${index}`, name: `招聘来源 ${number}` }, adapter: 'ats', status: 'active', consecutiveFailures: 0 }
        if (path.endsWith('/catalog')) return { identityKey: `c${index}`, companyName: `收录公司 ${number}`, channelLabel: '官网', adapter: 'ats' }
        if (path.endsWith('/sync-runs')) return { id: `r${index}`, status: `运行记录 ${number}`, startedAt: '2026-10-27T08:00:00Z', counts: { created: 1, updated: 0, rejected: 0 } }
        const kind = path.endsWith('/discovery') ? '待审来源' : '待审公司'
        return { id: `d${index}`, companyName: `${kind} ${number}`, entryUrl: 'https://example.test/jobs', articleUrl: 'https://example.test/post' }
      })
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items, total: 40 }) })
    })
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/admin/job-market')
    await expect(page.getByText('共 40 个来源')).toBeVisible()
    for (const [label, lastItem] of [['已收录公司列表', '收录公司 40'], ['来源状态列表', '招聘来源 40'], ['待审来源列表', '待审来源 40'], ['待审公司列表', '待审公司 40'], ['同步运行记录列表', '运行记录 40']]) {
      const inventory = page.getByRole('region', { name: label, exact: true })
      await expect(inventory.getByRole('listitem')).toHaveCount(40)
      const bounds = await inventory.evaluate(node => ({ height: node.clientHeight, content: node.scrollHeight }))
      expect(bounds.height).toBeLessThanOrEqual(width === 375 ? 360 : 420)
      expect(bounds.content).toBeGreaterThan(bounds.height)
      await inventory.scrollIntoViewIfNeeded()
      await inventory.focus()
      await page.keyboard.press('End')
      await expect.poll(() => inventory.evaluate(node => node.scrollTop)).toBeGreaterThan(0)
      await expect(inventory.getByText(lastItem, { exact: label !== '同步运行记录列表' })).toBeInViewport()
      if (label === '来源状态列表') await expect(inventory.getByRole('button', { name: '暂停' }).last()).toBeInViewport()
      if (label === '待审来源列表' || label === '待审公司列表') await expect(inventory.getByRole('button', { name: '批准' }).last()).toBeInViewport()
    }
    await fitsPage(page)
    await page.screenshot({ path: `/private/tmp/jobtrace-admin-inventories-${width}.png`, fullPage: true })
  })
}


for (const width of [1440, 375]) {
  test(`all 125 users are reachable with complete actions at ${width}px`, async ({ page }) => {
    await mockAdmin(page)
    await page.route('**/api/admin/users**', route => {
      const url = new URL(route.request().url())
      const current = Number(url.searchParams.get('page'))
      const limit = Number(url.searchParams.get('limit'))
      const query = url.searchParams.get('q') || ''
      const all = Array.from({ length: 125 }, (_, index) => ({ ...user, id: `u${index}`, username: `user-${String(index + 1).padStart(3, '0')}`, email: `user${index + 1}@example.com` })).filter(item => item.username.includes(query))
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: all.slice((current - 1) * limit, current * limit), total: all.length, page: current, limit, totalPages: Math.ceil(all.length / limit) }) })
    })
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/admin/users')
    await expect(page.getByText('user-001', { exact: true })).toBeVisible()
    await expect(page.getByText('125', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '第 7 页', exact: true }).click()
    await expect(page.getByText('user-125', { exact: true })).toBeAttached()
    await expect(page.getByRole('button', { name: '下一页' })).toBeDisabled()
    await page.getByLabel('每页用户数').selectOption('50')
    await expect(page.getByText('user-001', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '第 3 页', exact: true }).click()
    await expect(page.getByText('user-125', { exact: true })).toBeAttached()
    await page.getByLabel('搜索用户').fill('user-125')
    const row = page.getByRole('row').filter({ hasText: 'user-125' })
    await expect(page.getByText('第 1 / 1 页')).toBeVisible()
    await row.scrollIntoViewIfNeeded()
    await expect(row.getByRole('button', { name: '查看档案' })).toBeInViewport()
    await expect(row.getByRole('button', { name: '管理访问' })).toBeInViewport()
    await expect(row.getByText('普通用户', { exact: true })).toBeVisible()
    await expect(row.getByText('正常', { exact: true })).toBeVisible()
    await fitsPage(page)
    await page.screenshot({ path: `/private/tmp/jobtrace-admin-paged-users-${width}.png`, fullPage: true })
  })
}


for (const width of [1920, 1440, 1024, 768, 375, 320]) {
  test(`pagination text and controls do not overlap at ${width}px`, async ({ page }) => {
    await mockAdmin(page)
    await page.route('**/api/admin/users**', route => {
      const query = new URL(route.request().url()).searchParams
      const limit = Number(query.get('limit'))
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [user], total: 125, page: Number(query.get('page')), limit, totalPages: Math.ceil(125 / limit) }) })
    })
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/admin/users')
    const footer = page.locator('.admin-user-pagination')
    const select = page.getByLabel('每页用户数')
    await expect(page.getByText('第 1 / 7 页')).toBeVisible()
    await select.selectOption('100')
    await expect(page.getByText('第 1 / 2 页')).toBeVisible()
    const textFits = await select.evaluate(node => {
      const picker = node as HTMLSelectElement
      const style = getComputedStyle(picker)
      const context = document.createElement('canvas').getContext('2d')!
      context.font = style.font
      return context.measureText(picker.selectedOptions[0].textContent!).width <= picker.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
    })
    expect(textFits).toBe(true)
    await select.selectOption('20')
    await expect(page.getByText('第 1 / 7 页')).toBeVisible()
    await footer.scrollIntoViewIfNeeded()
    const overlaps = await footer.evaluate(node => {
      const visible = (rect: DOMRect) => rect.width > 0 && rect.height > 0
      const boxes = Array.from(node.querySelectorAll('button,input,select')).map(element => ({ name: element.getAttribute('aria-label') || element.textContent, rect: element.getBoundingClientRect() })).filter(item => visible(item.rect))
      const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT)
      while (walker.nextNode()) {
        const text = walker.currentNode
        if (!text.textContent?.trim() || text.parentElement?.closest('button,select,option')) continue
        const range = document.createRange()
        range.selectNodeContents(text)
        const rect = range.getBoundingClientRect()
        if (visible(rect)) boxes.push({ name: text.textContent, rect })
      }
      return boxes.flatMap((a, i) => boxes.slice(i + 1).filter(b => Math.min(a.rect.right, b.rect.right) - Math.max(a.rect.left, b.rect.left) > 1 && Math.min(a.rect.bottom, b.rect.bottom) - Math.max(a.rect.top, b.rect.top) > 1).map(b => `${a.name} / ${b.name}`))
    })
    expect(overlaps).toEqual([])
    await fitsPage(page)
    await footer.screenshot({ path: `/private/tmp/jobtrace-admin-pagination-${width}.png` })
  })
}
