import { useEffect, useRef, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { cn } from '@/lib';
import { SkeletonText } from '@/components/ui';
import { useAuth, usePermissions } from '@/hooks';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { PRIMARY_NAV, filterNavByPermissions } from './navigation';

/**
 * Role-aware application shell.
 *
 * Layout: a header (banner), a primary navigation sidebar, and a main content
 * region. Navigation entries and header actions render based on the permissions
 * the server returned for the current session. Hiding a control is a UX
 * affordance ONLY — the server authorizes every protected operation regardless
 * (Req 17.1).
 *
 * Accessibility:
 *   - A skip link lets keyboard users jump straight to the main content.
 *   - Landmark regions: <header role=banner>, <nav>, <main>.
 *   - On route change, focus moves to the main region and the route is
 *     announced via a polite live region so screen-reader users are informed of
 *     the navigation (Req 19.6) without losing keyboard context.
 */
export function AppShell({ children }: { children: ReactNode }): JSX.Element {
  const { session, isLoading, isAuthenticated } = useAuth();
  const { hasAny, hasAll } = usePermissions();
  const location = useLocation();

  const mainRef = useRef<HTMLElement>(null);
  const announceRef = useRef<HTMLDivElement>(null);
  // Skip focus management on the very first render so we don't steal focus on load.
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    // Move focus to the main region so keyboard/AT users land on new content.
    mainRef.current?.focus();
    if (announceRef.current) {
      announceRef.current.textContent = `Navigated to ${document.title || location.pathname}`;
    }
  }, [location.pathname]);

  const navItems = filterNavByPermissions(PRIMARY_NAV, hasAny, hasAll);

  return (
    <div className="flex min-h-screen flex-col bg-[var(--color-surface-muted)]">
      {/* Skip link: visible on focus (style in global.css). */}
      <a href="#main-content" className="sr-only skip-link">
        Skip to main content
      </a>

      {/* Polite live region announcing route changes to assistive tech (Req 19.6). */}
      <div ref={announceRef} aria-live="polite" className="sr-only" />

      <header
        role="banner"
        className="sticky top-0 z-30 flex h-14 items-center justify-between gap-4 border-b border-[var(--color-navy-700)] bg-[var(--color-foundation)] px-4 text-[var(--color-on-foundation)]"
      >
        <div className="flex items-center gap-3">
          <span className="text-lg font-semibold tracking-tight">Societix</span>
          {session?.society && (
            <span className="hidden text-sm text-[var(--color-navy-200)] sm:inline">
              {session.society.name}
            </span>
          )}
        </div>
        <HeaderActions />
      </header>

      <div className="flex flex-1">
        {isAuthenticated && (
          <nav
            aria-label="Primary"
            className="hidden w-60 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] p-3 md:block"
          >
            <ul className="flex flex-col gap-1">
              {navItems.map((item) => (
                <li key={item.key}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/'}
                    className={({ isActive }) =>
                      cn(
                        'block rounded-md px-3 py-2 text-sm font-medium transition-colors',
                        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]',
                        isActive
                          ? 'bg-[var(--color-accent-subtle)] text-[var(--color-accent-active)]'
                          : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text-primary)]',
                      )
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        )}

        <main
          id="main-content"
          ref={mainRef}
          tabIndex={-1}
          // tabIndex=-1 lets us move focus here on route change without adding it
          // to the natural tab order; outline is suppressed since it's programmatic.
          className="flex-1 px-4 py-6 outline-none md:px-8"
        >
          {isLoading ? (
            <div aria-busy="true" className="mx-auto max-w-2xl">
              <SkeletonText lines={4} label="Loading your workspace…" />
            </div>
          ) : (
            <ErrorBoundary resetKeys={[location.pathname]}>{children}</ErrorBoundary>
          )}
        </main>
      </div>
    </div>
  );
}

/**
 * Header actions rendered from server-granted permissions. Only the controls
 * the actor is allowed to see are shown; the server still authorizes each
 * action when invoked (Req 17.1).
 */
function HeaderActions(): JSX.Element | null {
  const { isAuthenticated, session } = useAuth();

  if (!isAuthenticated) {
    return (
      <span className="text-sm text-[var(--color-navy-200)]">Not signed in</span>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <span className="text-sm text-[var(--color-navy-100)]">{session?.user.displayName}</span>
    </div>
  );
}
