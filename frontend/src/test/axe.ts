import axe, { type AxeResults, type RunOptions, type Spec } from 'axe-core';

/**
 * Component-level accessibility helper for jsdom tests (Req 19.1).
 *
 * Runs axe-core against a rendered DOM subtree and returns the violations so a
 * test can assert there are none. This is the component-scoped counterpart to
 * the Playwright + @axe-core/playwright scan that runs against the live app in
 * E2E. jsdom cannot evaluate colour-contrast (it has no layout/paint), so that
 * rule is disabled here; contrast is covered by the browser-based E2E scan.
 */
const DEFAULT_OPTIONS: RunOptions = {
  rules: {
    'color-contrast': { enabled: false },
  },
};

/** Ruleset tuned to WCAG 2.2 AA tags (Req 19.1). */
const WCAG_AA_SPEC: Spec = {};

export async function runAxe(
  container: Element,
  options: RunOptions = DEFAULT_OPTIONS,
): Promise<AxeResults> {
  axe.configure(WCAG_AA_SPEC);
  return axe.run(container, {
    runOnly: {
      type: 'tag',
      values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'],
    },
    ...options,
  });
}

/** Convenience: assert-friendly list of violation ids + messages. */
export function summarizeViolations(results: AxeResults): string[] {
  return results.violations.map(
    (v) => `${v.id}: ${v.help} (${v.nodes.length} node[s])`,
  );
}
