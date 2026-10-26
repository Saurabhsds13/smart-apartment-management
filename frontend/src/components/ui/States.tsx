import { type ReactNode } from 'react';
import { cn } from '@/lib';
import { Button } from './Button';

/**
 * Empty, loading (skeleton), and error state primitives.
 *
 * - EmptyState: communicates "nothing here yet" with an optional action.
 * - Skeleton / SkeletonText: loading placeholders. They carry aria-hidden and a
 *   polite "Loading" status so sighted users see placeholders while AT is told
 *   content is loading, without announcing meaningless shapes. Pulse animation
 *   is disabled under prefers-reduced-motion.
 * - ErrorState: communicates a failure with an optional retry (role="alert").
 */

export interface EmptyStateProps {
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ title, description, icon, action, className }: EmptyStateProps): JSX.Element {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-[var(--color-border-strong)]',
        'bg-[var(--color-surface-muted)] px-6 py-12 text-center',
        className,
      )}
    >
      {icon && (
        <span aria-hidden="true" className="text-[var(--color-text-disabled)]">
          {icon}
        </span>
      )}
      <p className="text-base font-semibold text-[var(--color-text-primary)]">{title}</p>
      {description && <p className="max-w-sm text-sm text-[var(--color-text-secondary)]">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export interface SkeletonProps {
  className?: string;
  /** Rounded shape preset. */
  shape?: 'rect' | 'circle' | 'text';
}

export function Skeleton({ className, shape = 'rect' }: SkeletonProps): JSX.Element {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'block animate-pulse bg-[var(--color-surface-sunken)] motion-reduce:animate-none',
        shape === 'circle' && 'rounded-full',
        shape === 'rect' && 'rounded-md',
        shape === 'text' && 'h-4 rounded',
        className,
      )}
    />
  );
}

export interface SkeletonTextProps {
  /** Number of placeholder lines. */
  lines?: number;
  className?: string;
  /** Accessible loading label announced politely to assistive tech. */
  label?: string;
}

export function SkeletonText({ lines = 3, className, label = 'Loading…' }: SkeletonTextProps): JSX.Element {
  return (
    <div className={cn('flex flex-col gap-2', className)} role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}</span>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} shape="text" className={i === lines - 1 ? 'w-2/3' : 'w-full'} />
      ))}
    </div>
  );
}

export interface ErrorStateProps {
  title?: ReactNode;
  description?: ReactNode;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}

export function ErrorState({
  title = 'Something went wrong',
  description = 'We could not load this content. Please try again.',
  onRetry,
  retryLabel = 'Try again',
  className,
}: ErrorStateProps): JSX.Element {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-lg border border-[var(--color-danger-500)]',
        'bg-[var(--color-danger-50)] px-6 py-10 text-center',
        className,
      )}
    >
      <p className="text-base font-semibold text-[var(--color-danger-700)]">{title}</p>
      {description && <p className="max-w-sm text-sm text-[var(--color-danger-700)]">{description}</p>}
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
