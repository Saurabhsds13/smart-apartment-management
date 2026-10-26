import { type ReactNode } from 'react';
import { cn } from '@/lib';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

export interface AlertProps {
  tone?: AlertTone;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
  /**
   * How assistive tech should announce this alert. Use "assertive" (role=alert)
   * for errors needing immediate attention; "polite" (role=status) otherwise.
   * Defaults are tone-appropriate.
   */
  live?: 'polite' | 'assertive';
}

const tones: Record<AlertTone, { container: string; icon: string; glyph: string; defaultLive: 'polite' | 'assertive' }> = {
  info: {
    container: 'border-[var(--color-info-500)] bg-[var(--color-info-50)] text-[var(--color-info-700)]',
    icon: 'text-[var(--color-info-500)]',
    glyph: 'i',
    defaultLive: 'polite',
  },
  success: {
    container: 'border-[var(--color-success-500)] bg-[var(--color-success-50)] text-[var(--color-success-700)]',
    icon: 'text-[var(--color-success-500)]',
    glyph: '✓',
    defaultLive: 'polite',
  },
  warning: {
    container: 'border-[var(--color-warning-500)] bg-[var(--color-warning-50)] text-[var(--color-warning-700)]',
    icon: 'text-[var(--color-warning-500)]',
    glyph: '!',
    defaultLive: 'polite',
  },
  danger: {
    container: 'border-[var(--color-danger-500)] bg-[var(--color-danger-50)] text-[var(--color-danger-700)]',
    icon: 'text-[var(--color-danger-500)]',
    glyph: '!',
    defaultLive: 'assertive',
  },
};

/**
 * Inline alert for status conveyed to assistive technology (Req 19.6). Uses
 * role="alert" for assertive announcements and role="status" for polite ones.
 * The icon is decorative (aria-hidden); meaning is carried by the text so the
 * non-text content has an equivalent text alternative (Req 19.5).
 */
export function Alert({ tone = 'info', title, children, className, live }: AlertProps): JSX.Element {
  const config = tones[tone];
  const announce = live ?? config.defaultLive;

  return (
    <div
      role={announce === 'assertive' ? 'alert' : 'status'}
      aria-live={announce}
      className={cn('flex gap-3 rounded-lg border p-4 text-sm', config.container, className)}
    >
      <span
        aria-hidden="true"
        className={cn('flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-current text-xs font-bold', config.icon)}
      >
        {config.glyph}
      </span>
      <div className="flex flex-col gap-0.5">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="[&_a]:underline">{children}</div>}
      </div>
    </div>
  );
}
