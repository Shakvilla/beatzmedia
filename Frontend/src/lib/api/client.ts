import { getToken, clearToken } from './token'
import { ApiError } from './errors'

const BASE_URL = import.meta.env.VITE_API_URL ?? '/v1'

interface ErrorEnvelope {
  error: { code: string; message: string; field?: string }
}

type UnauthorizedHandler = () => void
let unauthorizedHandler: UnauthorizedHandler = () => {}

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

export interface ApiFetchOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  idempotencyKey?: string
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const isForm = options.body instanceof FormData
  const headers: Record<string, string> = {}
  if (!isForm) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body:
      options.body === undefined
        ? undefined
        : isForm
          ? (options.body as FormData)
          : JSON.stringify(options.body),
  })

  if (response.status === 401) {
    clearToken()
    unauthorizedHandler()
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let envelope: ErrorEnvelope | null = null
    try {
      envelope = (await response.json()) as ErrorEnvelope
    } catch {
      envelope = null
    }
    throw new ApiError(
      response.status,
      envelope?.error.code ?? 'UNKNOWN',
      envelope?.error.message ?? 'Request failed',
      envelope?.error.field,
    )
  }

  return (await response.json()) as T
}
