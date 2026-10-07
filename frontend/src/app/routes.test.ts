import { afterEach, expect, it } from 'vitest'
import { currentPath, safePath } from './routes'

afterEach(() => window.history.replaceState(null, '', '/'))

it('accepts only internal return paths and preserves filters', () => {
  window.history.replaceState(null, '', '/applications?q=java#latest')
  expect(currentPath()).toBe('/applications?q=java#latest')
  expect(safePath('/applications?q=java')).toBe('/applications?q=java')
  expect(safePath('//evil.example')).toBe('/applications')
  expect(safePath('https://evil.example')).toBe('/applications')
  expect(safePath('/\\evil.example')).toBe('/applications')
  expect(safePath(null)).toBe('/applications')
})
