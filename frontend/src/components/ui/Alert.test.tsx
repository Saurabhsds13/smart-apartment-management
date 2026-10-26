import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Alert } from './Alert';
import { runAxe, summarizeViolations } from '@/test/axe';

/**
 * Render + accessibility tests for the Alert primitive. Verifies the ARIA live
 * semantics (Req 19.6) and that the decorative icon carries no meaning, with an
 * automated axe scan (Req 19.1).
 */
describe('Alert', () => {
  it('renders danger tone assertively with role="alert"', () => {
    render(
      <Alert tone="danger" title="Payment failed">
        Try a different reference.
      </Alert>,
    );
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Payment failed');
    expect(alert).toHaveAttribute('aria-live', 'assertive');
  });

  it('renders info tone politely with role="status"', () => {
    render(<Alert tone="info">Saved as draft.</Alert>);
    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-live', 'polite');
  });

  it('respects an explicit live override', () => {
    render(
      <Alert tone="success" live="assertive">
        Done.
      </Alert>,
    );
    expect(screen.getByRole('alert')).toHaveAttribute('aria-live', 'assertive');
  });

  it('has no automated accessibility violations', async () => {
    const { container } = render(
      <Alert tone="warning" title="Heads up">
        Your session expires soon.
      </Alert>,
    );
    const results = await runAxe(container);
    expect(summarizeViolations(results)).toEqual([]);
  });
});
