import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

/**
 * Vitest configuration for component and unit tests.
 *
 * Kept separate from `vite.config.ts` so the production build config stays lean
 * and the test environment (jsdom + jest-dom matchers) only loads under test.
 * We intentionally do NOT load the Tailwind Vite plugin here: tests assert
 * behaviour and accessibility semantics, not computed styles, and skipping it
 * keeps the test runner fast and free of CSS-pipeline concerns.
 *
 * Playwright E2E specs live under `e2e/` and are excluded here — they run with
 * their own runner via `npm run test:e2e` (see `playwright.config.ts`).
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['node_modules', 'dist', 'e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/**/index.ts',
        'src/vite-env.d.ts',
        'src/main.tsx',
      ],
    },
  },
});
