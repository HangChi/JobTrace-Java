import { cleanup, render, screen } from '@testing-library/react'
import axe from 'axe-core'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

it('has no automated accessibility violations on the login page', async () => {
  window.history.replaceState(null, '', '/login')
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 401 })))
  const { container } = render(<App />)
  await screen.findByRole('heading', { name: '登录 JobTrace' })
  const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(result.violations).toEqual([])
})

it('has no automated accessibility violations on the empty applications workspace', async () => {
  window.history.replaceState(null, '', '/applications')
  vi.stubGlobal('fetch', vi.fn(async (path: string) => new Response(JSON.stringify(path === '/api/auth/session'
    ? { session: { id: 's1', expiresAt: '2027-01-01T00:00:00Z' }, user: { id: 'u1', displayName: '小李', image: null, role: 'USER' } }
    : { items: [], nextCursor: null, total: 0, page: 1, limit: 50 }), { headers: { 'content-type': 'application/json' } })))
  const { container } = render(<App />)
  await screen.findByRole('heading', { name: '还没有投递记录' })
  const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(result.violations).toEqual([])
})
