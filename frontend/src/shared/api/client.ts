export type FieldError = { field: string; message: string; code?: string }

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: FieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  fetcher?: typeof fetch
}

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown> : null
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith('/api/') || path.startsWith('//')) {
    throw new ApiError(0, 'invalid_path', '请求地址无效。')
  }
  const { method = 'GET', body, signal, fetcher = fetch } = options
  let response: Response
  try {
    response = await fetcher(path, {
      method,
      credentials: 'same-origin',
      headers: {
        accept: 'application/json',
        ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal,
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    throw new ApiError(0, 'network', '网络连接失败，请稍后重试。')
  }
  if (response.status === 204) return undefined as T
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    throw new ApiError(response.status, 'invalid_response', '服务返回了无法识别的数据。')
  }
  if (!response.ok) {
    const problem = record(payload)
    const fieldErrors = Array.isArray(problem?.fieldErrors)
      ? problem.fieldErrors.filter((item): item is FieldError => {
        const value = record(item)
        return typeof value?.field === 'string' && typeof value.message === 'string'
      }) : []
    throw new ApiError(
      response.status,
      typeof problem?.code === 'string' ? problem.code : 'request_failed',
      typeof problem?.detail === 'string' && problem.detail ? problem.detail : '操作失败，请稍后重试。',
      fieldErrors,
    )
  }
  return payload as T
}
