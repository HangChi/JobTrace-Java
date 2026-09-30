import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('shows the migration foundation status', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: '职迹正在迁往新的 Java 后端' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('工程骨架已就绪')
  })
})

