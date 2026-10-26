import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from './Button';
import { runAxe, summarizeViolations } from '@/test/axe';

/**
 * Render + behaviour + accessibility tests for the Button primitive.
 *
 * Covers keyboard operability and focus semantics (Req 19.2, 19.3) and runs an
 * automated axe scan on the rendered control (Req 19.1).
 */
describe('Button', () => {
  it('renders a native button with an accessible name', () => {
    render(<Button>Save changes</Button>);
    const button = screen.getByRole('button', { name: 'Save changes' });
    expect(button).toBeInTheDocument();
    expect(button.tagName).toBe('BUTTON');
  });

  it('defaults to type="button" to avoid accidental form submits', () => {
    render(<Button>Click</Button>);
    expect(screen.getByRole('button', { name: 'Click' })).toHaveAttribute('type', 'button');
  });

  it('is operable by keyboard (Enter / Space)', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Go</Button>);

    const button = screen.getByRole('button', { name: 'Go' });
    button.focus();
    expect(button).toHaveFocus();

    await user.keyboard('{Enter}');
    await user.keyboard(' ');
    expect(onClick).toHaveBeenCalledTimes(2);
  });

  it('exposes a busy state and is disabled while loading', () => {
    render(<Button isLoading>Saving</Button>);
    const button = screen.getByRole('button', { name: 'Saving' });
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button).toBeDisabled();
  });

  it('has no automated accessibility violations', async () => {
    const { container } = render(<Button variant="primary">Accessible button</Button>);
    const results = await runAxe(container);
    expect(summarizeViolations(results)).toEqual([]);
  });
});
