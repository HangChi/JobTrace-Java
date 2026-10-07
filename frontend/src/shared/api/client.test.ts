import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest } from './client'

describe('apiRequest', () => {
  it('uses same-origin credentials and parses Java ProblemDetail', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ code: 'validation', detail: '请检查邮箱', fieldErrors: [{ field: 'email', message: '邮箱无效' }] }), { status: 400, headers: { 'content-type': 'application/problem+json' } }))
    await expect(apiRequest('/api/auth/register', { method: 'POST', body: { email: 'x' }, fetcher })).rejects.toMatchObject({ status: 400, code: 'validation', fieldErrors: [{ field: 'email', message: '邮箱无效' }] })
    expect(fetcher).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({ credentials: 'same-origin', method: 'POST' }))
  })
  it('rejects non-API paths and network failures', async () => {
    await expect(apiRequest('https://evil.example')).rejects.toBeInstanceOf(ApiError)
    await expect(apiRequest('/api/auth/session', { fetcher: vi.fn(async () => { throw new Error('offline') }) })).rejects.toMatchObject({ code: 'network' })
  })
})
