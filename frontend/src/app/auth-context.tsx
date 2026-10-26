import { createContext, useMemo, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchSession } from '@/lib/auth-api';
import type { Permission, Session } from '@/lib/session';

/**
 * Authentication context.
 *
 * Loads the current session once via TanStack Query and exposes it to the tree.
 * The shell reads `permissions` to decide which navigation and actions to
 * render. This is a UX affordance ONLY — the server authorizes every protected
 * operation regardless of what the client chooses to show (Req 17.1).
 */

export interface AuthContextValue {
  /** The current session, or `null` when unauthenticated. */
  readonly session: Session | null;
  /** True while the initial session load is in flight. */
  readonly isLoading: boolean;
  /** True when the session load failed (network/5xx), distinct from signed out. */
  readonly isError: boolean;
  /** Convenience: true when there is an authenticated session. */
  readonly isAuthenticated: boolean;
  /** Re-fetch the session (e.g. after sign-in/out). */
  readonly refresh: () => void;
  /**
   * Whether the current session carries a given permission. Returns false when
   * unauthenticated or still loading. UX-only (see Req 17.1).
   */
  readonly hasPermission: (permission: Permission) => boolean;
  /** True if the session carries ALL of the given permissions. */
  readonly hasAllPermissions: (permissions: ReadonlyArray<Permission>) => boolean;
  /** True if the session carries AT LEAST ONE of the given permissions. */
  readonly hasAnyPermission: (permissions: ReadonlyArray<Permission>) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

/** Query key for the session; no tenant id — scope comes from the server session. */
export const SESSION_QUERY_KEY = ['auth', 'session'] as const;

export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const query = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: ({ signal }) => fetchSession(signal),
    // The session is foundational; keep it fresh-ish but avoid refetch storms.
    staleTime: 60_000,
  });

  const value = useMemo<AuthContextValue>(() => {
    const session = query.data ?? null;
    const granted = new Set<Permission>(session?.permissions ?? []);

    const hasPermission = (permission: Permission): boolean => granted.has(permission);

    return {
      session,
      isLoading: query.isLoading,
      isError: query.isError,
      isAuthenticated: session !== null,
      refresh: () => void query.refetch(),
      hasPermission,
      hasAllPermissions: (permissions) => permissions.every(hasPermission),
      hasAnyPermission: (permissions) => permissions.some(hasPermission),
    };
  }, [query]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
