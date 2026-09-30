import { describe, expect, it, vi } from 'vitest'
import {
  HealthClientError,
  getLiveness,
  getReadiness,
  type HealthResult,
} from './health'

function response(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('health client', () => {
  it('returns a typed successful liveness result', async () => {
    const fetcher = vi.fn(async () => response(200, { status: 'ok' }))

    await expect(getLiveness(fetcher)).resolves.toEqual<HealthResult>({
      status: 'ok',
      httpStatus: 200,
    })
    expect(fetcher).toHaveBeenCalledWith('/api/health/live', {
      headers: { accept: 'application/json' },
      signal: undefined,
    })
  })

  it('keeps a valid readiness error available to the UI', async () => {
    const fetcher = vi.fn(async () => response(503, { status: 'error' }))

    await expect(getReadiness(fetcher)).resolves.toEqual<HealthResult>({
      status: 'error',
      httpStatus: 503,
    })
  })

  it('rejects malformed health payloads', async () => {
    const fetcher = vi.fn(async () => response(200, { ready: true }))

    await expect(getReadiness(fetcher)).rejects.toEqual(
      expect.objectContaining<Partial<HealthClientError>>({
        name: 'HealthClientError',
        kind: 'invalid-response',
      }),
    )
  })

  it('rejects non-JSON health responses', async () => {
    const fetcher = vi.fn(
      async () => new Response('not json', { status: 502 }),
    )

    await expect(getLiveness(fetcher)).rejects.toEqual(
      expect.objectContaining<Partial<HealthClientError>>({
        name: 'HealthClientError',
        kind: 'invalid-response',
      }),
    )
  })

  it('rejects an HTTP failure that claims to be healthy', async () => {
    const fetcher = vi.fn(async () => response(503, { status: 'ok' }))

    await expect(getReadiness(fetcher)).rejects.toEqual(
      expect.objectContaining<Partial<HealthClientError>>({
        kind: 'invalid-response',
      }),
    )
  })
})
