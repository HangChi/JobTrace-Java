import { cleanup, fireEvent, render } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import AccountAvatar from './AccountAvatar'

afterEach(cleanup)

it('renders the image, falls back on failure, and retries when the image changes', () => {
  const { container, rerender } = render(<AccountAvatar name="小李" image="/avatar-a.png" />)
  expect(container.querySelector('img')).toHaveAttribute('src', '/avatar-a.png')
  fireEvent.error(container.querySelector('img')!)
  expect(container.querySelector('img')).toBeNull()
  expect(container.textContent).toBe('小')
  rerender(<AccountAvatar name="小李" image="/avatar-b.png" />)
  expect(container.querySelector('img')).toHaveAttribute('src', '/avatar-b.png')
  rerender(<AccountAvatar name=" " image={null} />)
  expect(container.textContent).toBe('用')
  rerender(<AccountAvatar name=" 王同学 " image={null} />)
  expect(container.textContent).toBe('王')
})
