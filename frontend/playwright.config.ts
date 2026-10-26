import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration.
 *
 * - `webServer` boots the Vite dev server and waits for it before the suite
 *   runs, so CI needs no manual server step. `reuseExistingServer` avoids a
 *   double-boot during local iteration.
 * - `baseURL` lets specs navigate with relative paths (`page.goto('/')`).
 * - On CI we retry flaky specs twice and forbid `test.only` slipping in.
 *
 * NOTE: running this suite requires Playwright browsers to be installed
 * (`npx playwright install --with-deps`). That is wired in CI; in a sandbox
 * without browser binaries the config is still valid but the run will report
 * that the browser is missing rather than passing.
 */
const PORT = 5173;
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
