import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * Automated accessibility checks against the live application (Req 19.1).
 *
 * Runs axe-core in a real browser so colour-contrast and other paint-dependent
 * rules — which jsdom cannot evaluate — are exercised. Scoped to the WCAG 2.2
 * AA ruleset to match the platform's accessibility goal (G5 / Req 19.1).
 *
 * This is the browser-level counterpart to the component axe checks in the
 * Vitest suite; together they wire Req 19.1's "automated accessibility checks".
 */
const WCAG_AA_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

test('app shell has no WCAG 2.2 AA violations', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('main').waitFor();

  const results = await new AxeBuilder({ page }).withTags(WCAG_AA_TAGS).analyze();

  expect(results.violations).toEqual([]);
});
