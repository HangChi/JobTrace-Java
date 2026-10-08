import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, apiUpload } from './client'

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
  it('keeps CSRF protection for authenticated workspace mutations', async () => {
    const fetcher = vi.fn(async (path: string) => path === '/api/security/csrf'
      ? new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200 })
      : new Response(JSON.stringify({ id: 'reminder-1' }), { status: 201 })) as typeof fetch
    await apiRequest('/api/reminders', { method: 'POST', body: { title: '跟进' }, fetcher })
    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/security/csrf', expect.objectContaining({ method: 'GET', credentials: 'same-origin' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/reminders', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }) }))
  })
  it('does not send a mutation when the CSRF token is unavailable', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ code: 'unavailable', detail: '会话已失效' }), { status: 401 })) as typeof fetch
    await expect(apiRequest('/api/applications', { method: 'POST', body: {}, fetcher })).rejects.toMatchObject({ status: 401 })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('uploads multipart data with CSRF protection and leaves the boundary to the browser', async () => {
    const form = new FormData()
    form.append('file', new File(['company,position'], 'applications.csv', { type: 'text/csv' }))
    const fetcher = vi.fn(async (path: string) => new Response(JSON.stringify(path === '/api/security/csrf' ? { token: 'csrf-token' } : { id: 'batch-1' }))) as typeof fetch
    await apiUpload('/api/imports/preview', form, fetcher)
    expect(fetcher).toHaveBeenCalledWith('/api/imports/preview', expect.objectContaining({ method: 'POST', body: form, headers: expect.not.objectContaining({ 'content-type': expect.anything() }) }))
  })
})
