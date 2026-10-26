import { describe, expect, it } from 'vitest';
import type { Permission } from '@/lib/session';
import { PRIMARY_NAV, filterNavByPermissions, type NavItem } from './navigation';

/**
 * Tests for the pure navigation filter. This is a UX affordance only — the
 * server still authorizes every route/operation (Req 17.1) — but the filter
 * must be correct so the shell never surfaces entries the actor lacks.
 */

/** Build an `any`/`all` predicate pair from a concrete granted set. */
function predicates(granted: ReadonlyArray<Permission>) {
  const set = new Set(granted);
  return {
    hasAny: (perms: ReadonlyArray<Permission>) => perms.some((p) => set.has(p)),
    hasAll: (perms: ReadonlyArray<Permission>) => perms.every((p) => set.has(p)),
  };
}

describe('filterNavByPermissions', () => {
  it('always shows items with no required permissions', () => {
    const { hasAny, hasAll } = predicates([]);
    const result = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

    expect(result.map((i) => i.key)).toContain('overview');
  });

  it('hides items whose required permission is not granted', () => {
    const { hasAny, hasAll } = predicates([]);
    const result = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

    expect(result.map((i) => i.key)).toEqual(['overview']);
  });

  it('shows an item when the single required permission is granted', () => {
    const { hasAny, hasAll } = predicates(['billing:read']);
    const result = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

    const keys = result.map((i) => i.key);
    expect(keys).toContain('overview');
    expect(keys).toContain('billing');
    expect(keys).not.toContain('residents');
  });

  it("treats match 'any' as showing when at least one permission is held", () => {
    const admin: NavItem = {
      key: 'admin',
      label: 'Administration',
      to: '/admin',
      requires: ['society:manage', 'platform:manage'],
      match: 'any',
    };
    const { hasAny, hasAll } = predicates(['platform:manage']);

    const result = filterNavByPermissions([admin], hasAny, hasAll);
    expect(result).toHaveLength(1);
  });

  it("treats match 'all' as requiring every listed permission", () => {
    const needsBoth: NavItem = {
      key: 'needs-both',
      label: 'Needs Both',
      to: '/both',
      requires: ['a:read', 'b:read'],
      match: 'all',
    };
    const onlyOne = predicates(['a:read']);
    expect(filterNavByPermissions([needsBoth], onlyOne.hasAny, onlyOne.hasAll)).toHaveLength(0);

    const both = predicates(['a:read', 'b:read']);
    expect(filterNavByPermissions([needsBoth], both.hasAny, both.hasAll)).toHaveLength(1);
  });

  it('shows every primary entry when all permissions are granted', () => {
    const everything = PRIMARY_NAV.flatMap((i) => i.requires ?? []);
    const { hasAny, hasAll } = predicates(everything);
    const result = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

    expect(result).toHaveLength(PRIMARY_NAV.length);
  });

  it('preserves the declared order of items', () => {
    const everything = PRIMARY_NAV.flatMap((i) => i.requires ?? []);
    const { hasAny, hasAll } = predicates(everything);
    const result = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

    expect(result.map((i) => i.key)).toEqual(PRIMARY_NAV.map((i) => i.key));
  });
});
