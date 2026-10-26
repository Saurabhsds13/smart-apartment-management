import * as RadixDialog from '@radix-ui/react-dialog';
import { forwardRef, type ReactNode } from 'react';
import { cn } from '@/lib';

/**
 * Accessible modal dialog.
 *
 * Built on Radix Dialog (chosen over a hand-rolled modal because correct focus
 * trapping, focus restoration, Escape-to-close, scroll locking, and the
 * role="dialog"/aria-modal wiring are easy to get subtly wrong). Radix provides
 * those behaviors out of the box:
 *   - focus is trapped within the dialog while open and restored to the trigger
 *     on close (Req 19.2 — focus is managed, never lost);
 *   - Escape and overlay click close it;
 *   - aria-labelledby / aria-describedby are wired from Title/Description.
 *
 * `DialogTitle` is required for an accessible name; use `DialogDescription` (or
 * aria-describedby) when there is body context.
 */
export const Dialog = RadixDialog.Root;
export const DialogTrigger = RadixDialog.Trigger;
export const DialogClose = RadixDialog.Close;

export interface DialogContentProps {
  children: ReactNode;
  className?: string;
  /** Accessible name. Rendered visually unless `hideTitle` is set. */
  title: ReactNode;
  /** Optional accessible description linked via aria-describedby. */
  description?: ReactNode;
  /** Hide the title visually while keeping it available to assistive tech. */
  hideTitle?: boolean;
}

export const DialogContent = forwardRef<HTMLDivElement, DialogContentProps>(
  function DialogContent({ children, className, title, description, hideTitle = false }, ref) {
    return (
      <RadixDialog.Portal>
        <RadixDialog.Overlay
          className={cn(
            'fixed inset-0 z-50 bg-[var(--color-navy-900)]/50',
            'data-[state=open]:animate-in data-[state=closed]:animate-out motion-reduce:animate-none',
          )}
        />
        <RadixDialog.Content
          ref={ref}
          className={cn(
            'fixed left-1/2 top-1/2 z-50 w-[min(32rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2',
            'rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-xl',
            'focus:outline-none',
            className,
          )}
        >
          <div className="mb-4 flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <RadixDialog.Title
                className={cn(
                  'text-lg font-semibold text-[var(--color-text-primary)]',
                  hideTitle && 'sr-only',
                )}
              >
                {title}
              </RadixDialog.Title>
              {description && (
                <RadixDialog.Description className="text-sm text-[var(--color-text-secondary)]">
                  {description}
                </RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close
              aria-label="Close dialog"
              className={cn(
                'inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md',
                'text-[var(--color-text-secondary)] transition-colors hover:bg-[var(--color-surface-sunken)]',
                'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]',
              )}
            >
              <svg
                aria-hidden="true"
                viewBox="0 0 20 20"
                fill="none"
                className="h-5 w-5"
                stroke="currentColor"
                strokeWidth="1.75"
                strokeLinecap="round"
              >
                <path d="M5 5l10 10M15 5L5 15" />
              </svg>
            </RadixDialog.Close>
          </div>
          {children}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    );
  },
);

/** Convenience footer for dialog actions (right-aligned). */
export function DialogFooter({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}): JSX.Element {
  return (
    <div className={cn('mt-6 flex items-center justify-end gap-3', className)}>{children}</div>
  );
}
