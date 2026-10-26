/**
 * TanStack Query client.
 *
 * Query keys are prefixed with the resource and rely on the server session for
 * tenant scope — the client never sends a society id to scope data (Req 1.2, BR1).
 * Auth/authorization failures are not retried; the shell handles re-authentication.
 */

import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api-client';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          // Do not retry client errors (auth, forbidden, not-found, validation).
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
            return false;
          }
          return failureCount < 2;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}
