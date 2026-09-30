import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function response(status: number, healthStatus: 'ok' | 'error') {
  return new Response(JSON.stringify({ status: healthStatus }), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('App', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows a loading state while health checks are pending', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)))

    render(<App />)

    expect(
      screen.getByRole('heading', { name: '职迹正在迁往新的 Java 后端' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('正在检查服务状态')
  })

  it('shows when both the backend and database are ready', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(200, 'ok')))

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('服务已就绪')
  })

  it('distinguishes database readiness from backend liveness', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(200, 'ok'))
        .mockResolvedValueOnce(response(503, 'error')),
    )

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent(
      '后端在线，数据库尚未就绪',
    )
  })

  it('shows a safe failure state when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new Error('offline'))))

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('无法连接后端')
  })

  it('treats a negative liveness response as backend unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(503, 'error')))

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('无法连接后端')
  })

  it('treats a failed readiness request as database unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(200, 'ok'))
        .mockRejectedValueOnce(new Error('database timeout')),
    )

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent(
      '后端在线，数据库尚未就绪',
    )
  })
})
