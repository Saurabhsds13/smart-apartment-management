import { type ReactNode, useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { createQueryClient } from '@/lib';
import { ToastProvider } from '@/components/ui';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { AuthProvider } from './auth-context';

/**
 * Root providers for the SPA.
 *
 * Provider order (outer -> inner):
 *   1. ErrorBoundary — a last-resort boundary so a crash in any provider or the
 *      shell shows a recoverable error UI instead of a blank page.
 *   2. QueryClientProvider — TanStack Query server-state cache (lib/query-client).
 *   3. BrowserRouter — client routing; the shell manages focus on route change.
 *   4. ToastProvider — ARIA-live toast notifications (Req 19.6).
 *   5. AuthProvider — loads the server session + permissions used by the shell.
 *
 * The CSRF-aware api client (lib/api-client) is used by every query/mutation
 * (incl. the session query), so it is wired here implicitly via the data layer.
 */
export function AppProviders({ children }: { children: ReactNode }): JSX.Element {
  const [queryClient] = useState(createQueryClient);

  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastProvider>
            <AuthProvider>{children}</AuthProvider>
          </ToastProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
