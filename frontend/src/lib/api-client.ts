/**
 * Same-origin API client for the Societix SPA.
 *
 * Design contract (ADR-3, REST conventions):
 * - Requests are same-origin; the browser attaches the `HttpOnly` session cookie.
 * - `credentials: 'include'` ensures the session cookie travels with every call.
 * - On unsafe methods (POST/PUT/PATCH/DELETE) the client attaches the CSRF token
 *   read from the `XSRF-TOKEN` cookie as the `X-XSRF-TOKEN` header.
 * - The client NEVER sends a society id — tenant scope is derived from the server
 *   session (Req 1.2, BR1).
 * - Errors are surfaced as `ApiError` carrying the RFC 7807 problem+json body when
 *   available (design error contract).
 */

import { CSRF_HEADER_NAME, isUnsafeMethod, readCsrfToken } from './csrf';

/** Versioned base path. Same-origin in production; dev proxies `/api` to the backend. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

/** RFC 7807 problem+json error body returned by the backend. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly correlationId?: string;
  readonly errors?: ReadonlyArray<{ field?: string; message?: string }>;
}

/** Error thrown for non-2xx responses. Carries the parsed problem detail when present. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | undefined;
  readonly correlationId: string | undefined;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.correlationId = problem?.correlationId;
  }
}

type QueryValue = string | number | boolean;

export interface RequestOptions {
  /** Request body; plain objects are JSON-encoded. Omit for GET/DELETE without a body. */
  readonly body?: unknown;
  /** Query string parameters appended to the path. `undefined`/`null` values are dropped. */
  readonly query?: Readonly<Record<string, QueryValue | null | undefined>>;
  /** Additional headers merged over the defaults. */
  readonly headers?: Readonly<Record<string, string>>;
  /** Abort signal for cancellation (TanStack Query passes one in). */
  readonly signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const base = path.startsWith('/') ? `${API_BASE_URL}${path}` : `${API_BASE_URL}/${path}`;
  if (!query) {
    return base;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) {
      params.append(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

async function parseProblem(response: Response): Promise<ProblemDetail | undefined> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return undefined;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  const hasBody = options.body !== undefined;
  if (hasBody) {
    headers.set('Content-Type', 'application/json');
  }

  // Attach CSRF token on state-changing requests (design ADR-3).
  if (isUnsafeMethod(method)) {
    const token = readCsrfToken();
    if (token) {
      headers.set(CSRF_HEADER_NAME, token);
    }
  }

  const init: RequestInit = {
    method,
    headers,
    // Send the same-origin session cookie with every request.
    credentials: 'include',
  };
  if (hasBody) {
    init.body = JSON.stringify(options.body);
  }
  if (options.signal) {
    init.signal = options.signal;
  }

  const response = await fetch(buildUrl(path, options.query), init);

  if (!response.ok) {
    const problem = await parseProblem(response);
    const message = problem?.detail ?? problem?.title ?? `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message, problem);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions): Promise<T> => request<T>('GET', path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('POST', path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('PUT', path, { ...options, body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('PATCH', path, { ...options, body }),
  delete: <T>(path: string, options?: RequestOptions): Promise<T> => request<T>('DELETE', path, options),
} as const;
