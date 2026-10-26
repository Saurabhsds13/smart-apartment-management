import type { Permission } from '@/lib/session';

/**
 * Navigation model for the application shell.
 *
 * Each item declares the permission(s) the server must have granted for the
 * entry to be shown. Filtering here is a UX affordance ONLY — the server
 * authorizes the underlying routes/operations regardless of what the shell
 * renders (Req 17.1). Routes themselves are added incrementally in later
 * milestones; this is the stable shape the shell consumes.
 */
export interface NavItem {
  /** Stable key for React lists and active matching. */
  readonly key: string;
  /** Visible label. */
  readonly label: string;
  /** Target route path. */
  readonly to: string;
  /**
   * Permissions required to show this item. An item with an empty list is
   * always shown to an authenticated user (e.g. the home/overview).
   */
  readonly requires?: ReadonlyArray<Permission>;
  /** Match mode: 'any' (default) shows if the user has any required permission. */
  readonly match?: 'any' | 'all';
}

/**
 * The primary navigation. Permission keys mirror the server's permission
 * vocabulary (opaque strings). These are placeholders aligned with the domain;
 * feature milestones register the corresponding routes.
 */
export const PRIMARY_NAV: ReadonlyArray<NavItem> = [
  { key: 'overview', label: 'Overview', to: '/', requires: [] },
  { key: 'buildings', label: 'Buildings & Flats', to: '/buildings', requires: ['building:read'] },
  { key: 'residents', label: 'Residents', to: '/residents', requires: ['resident:read'] },
  { key: 'billing', label: 'Billing', to: '/billing', requires: ['billing:read'] },
  { key: 'visitors', label: 'Visitors', to: '/visitors', requires: ['visitor:read'] },
  { key: 'amenities', label: 'Amenities', to: '/amenities', requires: ['amenity:read'] },
  { key: 'reports', label: 'Reports', to: '/reports', requires: ['report:read'] },
  {
    key: 'admin',
    label: 'Administration',
    to: '/admin',
    requires: ['society:manage', 'platform:manage'],
    match: 'any',
  },
];

/**
 * Filter navigation items by the actor's permissions.
 *
 * @param items     the full navigation set
 * @param hasAny    predicate from usePermissions (any-of)
 * @param hasAll    predicate from usePermissions (all-of)
 */
export function filterNavByPermissions(
  items: ReadonlyArray<NavItem>,
  hasAny: (permissions: ReadonlyArray<Permission>) => boolean,
  hasAll: (permissions: ReadonlyArray<Permission>) => boolean,
): ReadonlyArray<NavItem> {
  return items.filter((item) => {
    if (!item.requires || item.requires.length === 0) {
      return true;
    }
    return item.match === 'all' ? hasAll(item.requires) : hasAny(item.requires);
  });
}
