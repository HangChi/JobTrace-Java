export type HealthStatus = 'ok' | 'error'

export type HealthResult = {
  status: HealthStatus
  httpStatus: number
}

export type HealthClientErrorKind = 'invalid-response' | 'network'

export class HealthClientError extends Error {
  readonly kind: HealthClientErrorKind

  constructor(kind: HealthClientErrorKind, message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = 'HealthClientError'
    this.kind = kind
  }
}

type Fetcher = typeof fetch

async function requestHealth(
  path: string,
  fetcher: Fetcher,
  signal?: AbortSignal,
): Promise<HealthResult> {
  let response: Response
  try {
    response = await fetcher(path, {
      headers: { accept: 'application/json' },
      signal,
    })
  } catch (error) {
    throw new HealthClientError('network', 'Unable to reach the backend.', {
      cause: error,
    })
  }

  let payload: unknown
  try {
    payload = await response.json()
  } catch (error) {
    throw new HealthClientError(
      'invalid-response',
      'The backend returned invalid health JSON.',
      { cause: error },
    )
  }

  if (!isHealthPayload(payload) || (!response.ok && payload.status === 'ok')) {
    throw new HealthClientError(
      'invalid-response',
      'The backend returned an invalid health response.',
    )
  }

  return { status: payload.status, httpStatus: response.status }
}

function isHealthPayload(value: unknown): value is { status: HealthStatus } {
  if (typeof value !== 'object' || value === null || !('status' in value)) {
    return false
  }
  return value.status === 'ok' || value.status === 'error'
}

export function getLiveness(fetcher: Fetcher = fetch, signal?: AbortSignal) {
  return requestHealth('/api/health/live', fetcher, signal)
}

export function getReadiness(fetcher: Fetcher = fetch, signal?: AbortSignal) {
  return requestHealth('/api/health/ready', fetcher, signal)
}

