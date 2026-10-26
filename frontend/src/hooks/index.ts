/**
 * Cross-cutting hooks (design.md frontend architecture).
 *
 * `useAuth` / `usePermissions` read the server-returned session so the shell can
 * render role-aware navigation and actions. These are UX affordances only — the
 * server enforces authorization on every protected operation (Req 17.1).
 */
export { useAuth } from './useAuth';
export { usePermissions } from './usePermissions';
export type { PermissionChecks } from './usePermissions';
