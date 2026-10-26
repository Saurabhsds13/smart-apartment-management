/**
 * CSRF handling for the same-origin SPA.
 *
 * The backend (Spring Security, design ADR-3) issues a synchronizer CSRF token
 * to the SPA as a cookie (`XSRF-TOKEN`) and expects it echoed back in a request
 * header (`X-XSRF-TOKEN`) on unsafe methods. Because the session cookie is
 * `HttpOnly`, the CSRF token is the only piece the client reads directly; it is
 * deliberately NOT `HttpOnly` so the SPA can read it and echo it.
 *
 * We never send a society id from the client — tenant scope is derived entirely
 * from the server session (Req 1.2, BR1).
 */

/** Cookie name the backend uses to deliver the CSRF token to the SPA. */
export const CSRF_COOKIE_NAME = 'XSRF-TOKEN';

/** Request header the backend expects the echoed CSRF token in. */
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

/** HTTP methods that mutate state and therefore require a CSRF token. */
const UNSAFE_METHODS: ReadonlySet<string> = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Whether a given HTTP method requires CSRF protection. */
export function isUnsafeMethod(method: string): boolean {
  return UNSAFE_METHODS.has(method.toUpperCase());
}

/**
 * Read the CSRF token from the `XSRF-TOKEN` cookie.
 * Returns `undefined` when no token cookie is present (e.g. before first load).
 */
export function readCsrfToken(): string | undefined {
  if (typeof document === 'undefined') {
    return undefined;
  }
  const prefix = `${CSRF_COOKIE_NAME}=`;
  const segments = document.cookie ? document.cookie.split('; ') : [];
  for (const segment of segments) {
    if (segment.startsWith(prefix)) {
      return decodeURIComponent(segment.slice(prefix.length));
    }
  }
  return undefined;
}
