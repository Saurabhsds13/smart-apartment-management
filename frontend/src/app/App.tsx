import { Route, Routes } from 'react-router-dom';
import { AppShell } from './AppShell';

/**
 * Application routes, rendered inside the role-aware shell.
 *
 * Route definitions are placeholders here; feature screens and role-guarded
 * routes are registered in later milestones. The shell provides the header,
 * permission-filtered navigation, landmark regions, skip link, focus management
 * on route change, and a per-route error boundary.
 */
export function App(): JSX.Element {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Overview />} />
      </Routes>
    </AppShell>
  );
}

function Overview(): JSX.Element {
  return (
    <section aria-labelledby="overview-heading" className="mx-auto max-w-4xl">
      <h1 id="overview-heading" className="text-2xl font-semibold text-[var(--color-text-primary)]">
        Overview
      </h1>
      <p className="mt-2 text-[var(--color-text-secondary)]">
        Welcome to Societix. Your available areas appear in the navigation based on your role.
      </p>
    </section>
  );
}
