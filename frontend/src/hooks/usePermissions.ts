import { useAuth } from './useAuth';
import type { Permission } from '@/lib/session';

export interface PermissionChecks {
  readonly permissions: ReadonlyArray<Permission>;
  readonly has: (permission: Permission) => boolean;
  readonly hasAll: (permissions: ReadonlyArray<Permission>) => boolean;
  readonly hasAny: (permissions: ReadonlyArray<Permission>) => boolean;
}

/**
 * Permission helpers for conditionally rendering navigation and actions.
 *
 * REMINDER (Req 17.1): hiding a control based on these checks is a UX
 * affordance only. The server authorizes every protected operation; a user who
 * forges a request still gets denied server-side.
 */
export function usePermissions(): PermissionChecks {
  const { session, hasPermission, hasAllPermissions, hasAnyPermission } = useAuth();
  return {
    permissions: session?.permissions ?? [],
    has: hasPermission,
    hasAll: hasAllPermissions,
    hasAny: hasAnyPermission,
  };
}
