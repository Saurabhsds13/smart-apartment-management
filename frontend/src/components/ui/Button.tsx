import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /**
   * When true, shows a busy state and sets aria-busy. The button stays in the
   * DOM and focusable, but is disabled so it cannot be re-triggered.
   */
  isLoading?: boolean;
}

const base =
  'inline-flex items-center justify-center gap-2 font-medium whitespace-nowrap ' +
  'rounded-md border transition-colors select-none ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)] ' +
  'disabled:cursor-not-allowed disabled:opacity-60';

const variants: Record<ButtonVariant, string> = {
  primary:
    'border-transparent bg-[var(--color-accent)] text-[var(--color-on-accent)] ' +
    'hover:bg-[var(--color-accent-hover)] active:bg-[var(--color-accent-active)]',
  secondary:
    'border-[var(--color-border-strong)] bg-[var(--color-surface)] text-[var(--color-text-primary)] ' +
    'hover:bg-[var(--color-surface-sunken)]',
  ghost:
    'border-transparent bg-transparent text-[var(--color-text-primary)] ' +
    'hover:bg-[var(--color-surface-sunken)]',
  danger:
    'border-transparent bg-[var(--color-danger-500)] text-white ' +
    'hover:bg-[var(--color-danger-700)]',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-11 px-5 text-base',
};

/**
 * Accessible button. Renders a native <button> so it is keyboard operable
 * (Enter/Space) and reachable by default, with a visible focus ring (Req 19.2,
 * 19.3). Use the `type` prop explicitly in forms; it defaults to "button" to
 * avoid accidental submits.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'md', isLoading = false, disabled, type = 'button', children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(base, variants[variant], sizes[size], className)}
      disabled={disabled ?? isLoading}
      aria-busy={isLoading || undefined}
      {...props}
    >
      {isLoading && <Spinner />}
      {children}
    </button>
  );
});

function Spinner(): JSX.Element {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent',
        'motion-reduce:animate-none',
      )}
    />
  );
}
