import { cleanup, render } from '@testing-library/react'
import axe from 'axe-core'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App accessibility', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('has no automated accessibility violations in the migration shell', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)))

    const { container } = render(<App />)
    const result = await axe.run(container, {
      rules: {
        'color-contrast': { enabled: false },
      },
    })

    expect(result.violations).toEqual([])
  })
})
