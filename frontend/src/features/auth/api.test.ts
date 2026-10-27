import { afterEach, expect, it, vi } from 'vitest'
import { getSession } from './api'

afterEach(() => vi.unstubAllGlobals())

it.each([
  ['admin', 'ADMIN'], ['ADMIN', 'ADMIN'], ['user', 'USER'], ['USER', 'USER'],
  ['administrator', 'USER'], ['', 'USER'], [null, 'USER'],
])('maps server role %s to UI role %s without inferring privileges from a name', async (role, expected) => {
  const current = { session: { id: 's1', expiresAt: '2027-01-01' }, user: { id: 'u1', displayName: 'admin', image: null, role } }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(current), { headers: { 'content-type': 'application/json' } })))
  expect(await getSession()).toEqual({ ...current, user: { ...current.user, role: expected } })
})
