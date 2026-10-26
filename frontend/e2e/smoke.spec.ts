import { expect, test } from '@playwright/test';

/**
 * Smoke test: the app boots and renders its landmark structure.
 *
 * Asserts the document title and the core landmarks the accessible shell
 * provides (banner header, main region) so a broken build or a crashing
 * provider tree fails CI fast (Req 19.1, shell from task 4.3).
 */
test('app loads with title and core landmarks', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle(/Societix/i);

  // Landmark regions rendered by the AppShell.
  await expect(page.getByRole('banner')).toBeVisible();
  await expect(page.getByRole('main')).toBeVisible();

  // The overview heading is the first content a signed-out visitor sees.
  await expect(page.getByRole('heading', { name: /overview/i })).toBeVisible();
});

test('provides a working skip-to-content link for keyboard users', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('main').waitFor();

  // The skip link is the first link in the DOM and targets the main region.
  const skipLink = page.getByRole('link', { name: /skip to main content/i });
  await expect(skipLink).toHaveAttribute('href', '#main-content');

  // It must be focusable and operable so keyboard users can bypass the header/nav.
  await skipLink.focus();
  await expect(skipLink).toBeFocused();
});
