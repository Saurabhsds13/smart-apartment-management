/**
 * Global test setup for Vitest.
 *
 * - Registers jest-dom's custom matchers (e.g. toBeInTheDocument,
 *   toHaveAccessibleName) so component tests can assert on accessibility
 *   semantics (Req 19.1–19.6).
 * - Runs React Testing Library's cleanup after every test to unmount trees and
 *   keep tests isolated (Vitest does not auto-cleanup like Jest+RTL does).
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
