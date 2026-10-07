import { cleanup, render, screen } from '@testing-library/react'
import axe from 'axe-core'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

it('has no automated accessibility violations on the login page', async () => {
  window.history.replaceState(null, '', '/login')
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 401 })))
  const { container } = render(<App />)
  await screen.findByRole('heading', { name: '欢迎回到职迹' })
  const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(result.violations).toEqual([])
})
