/**
 * Session retrieval against the same-origin API.
 *
 * The real authentication endpoints (sign-in, MFA, session) are delivered in
 * later milestones (see tasks 13.x / auth tasks). This module models the
 * expected shape and request now so the shell can be built and typechecked
 * against a stable contract. When the endpoint lands, only this file changes.
 *
 * Contract:
 *   GET /auth/session
 *     200 -> Session (the current actor, roles, permissions, tenant)
 *     401 -> unauthenticated (no active session)
 *
 * An unauthenticated response is NOT an error for the caller — it resolves to
 * `null` so the shell can render a signed-out state gracefully.
 */

import { apiClient, ApiError } from './api-client';
import type { Session } from './session';

/** Endpoint path (relative to the versioned API base). */
export const SESSION_PATH = '/auth/session';

/**
 * Fetch the current session. Resolves to the `Session` when authenticated, or
 * `null` when the server reports no active session (401). Any other failure
 * (network, 5xx) rejects so TanStack Query can surface it to an error boundary
 * or an inline error state.
 */
export async function fetchSession(signal?: AbortSignal): Promise<Session | null> {
  try {
    return await apiClient.get<Session>(SESSION_PATH, signal ? { signal } : undefined);
  } catch (error) {
    // 401 = no active session. Treat as "signed out", not a failure.
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}
