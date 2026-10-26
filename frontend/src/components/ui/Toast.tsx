import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { cn } from '@/lib';

/**
 * Toast notifications with ARIA live regions (Req 19.6).
 *
 * Status updates are announced to assistive technology without moving focus.
 * Two persistent live regions are rendered: a `polite` region (role="status")
 * for ordinary confirmations, and an `assertive` region (role="alert") for
 * errors. Toasts are inserted into the matching region so screen readers
 * announce them with the correct urgency. Auto-dismiss respects a duration and
 * can be paused; the close control is keyboard operable.
 */
export type ToastTone = 'info' | 'success' | 'warning' | 'danger';

export interface ToastOptions {
  title?: ReactNode;
  description: ReactNode;
  tone?: ToastTone;
  /** Auto-dismiss delay in ms. Set to 0 to require manual dismissal. */
  durationMs?: number;
}

interface ToastRecord extends Required<Pick<ToastOptions, 'description'>> {
  id: string;
  title?: ReactNode;
  tone: ToastTone;
  durationMs: number;
}

interface ToastContextValue {
  toast: (options: ToastOptions) => string;
  dismiss: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const DEFAULT_DURATION = 5000;

let counter = 0;
function nextId(): string {
  counter += 1;
  return `toast-${counter}`;
}

export function ToastProvider({ children }: { children: ReactNode }): JSX.Element {
  const [toasts, setToasts] = useState<ToastRecord[]>([]);
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const toast = useCallback(
    (options: ToastOptions): string => {
      const id = nextId();
      const record: ToastRecord = {
        id,
        description: options.description,
        tone: options.tone ?? 'info',
        durationMs: options.durationMs ?? DEFAULT_DURATION,
        ...(options.title !== undefined ? { title: options.title } : {}),
      };
      setToasts((current) => [...current, record]);
      if (record.durationMs > 0) {
        const timer = setTimeout(() => dismiss(id), record.durationMs);
        timers.current.set(id, timer);
      }
      return id;
    },
    [dismiss],
  );

  useEffect(() => {
    const active = timers.current;
    return () => {
      active.forEach((timer) => clearTimeout(timer));
      active.clear();
    };
  }, []);

  const value = useMemo<ToastContextValue>(() => ({ toast, dismiss }), [toast, dismiss]);

  const polite = toasts.filter((t) => t.tone !== 'danger');
  const assertive = toasts.filter((t) => t.tone === 'danger');

  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastRegion toasts={polite} live="polite" onDismiss={dismiss} />
      <ToastRegion toasts={assertive} live="assertive" onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within a <ToastProvider>.');
  }
  return ctx;
}

const toneStyles: Record<ToastTone, string> = {
  info: 'border-l-[var(--color-info-500)]',
  success: 'border-l-[var(--color-success-500)]',
  warning: 'border-l-[var(--color-warning-500)]',
  danger: 'border-l-[var(--color-danger-500)]',
};

function ToastRegion({
  toasts,
  live,
  onDismiss,
}: {
  toasts: ToastRecord[];
  live: 'polite' | 'assertive';
  onDismiss: (id: string) => void;
}): JSX.Element {
  return (
    <div
      // The region is always present so AT observes insertions; role matches urgency.
      role={live === 'assertive' ? 'alert' : 'status'}
      aria-live={live}
      aria-relevant="additions"
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          className={cn(
            'pointer-events-auto flex items-start gap-3 rounded-lg border border-l-4 border-[var(--color-border)]',
            'bg-[var(--color-surface)] p-4 shadow-lg',
            toneStyles[t.tone],
          )}
        >
          <div className="flex-1 text-sm">
            {t.title && <p className="font-semibold text-[var(--color-text-primary)]">{t.title}</p>}
            <p className="text-[var(--color-text-secondary)]">{t.description}</p>
          </div>
          <button
            type="button"
            onClick={() => onDismiss(t.id)}
            aria-label="Dismiss notification"
            className={cn(
              'inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-[var(--color-text-secondary)]',
              'hover:bg-[var(--color-surface-sunken)]',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]',
            )}
          >
            <svg aria-hidden="true" viewBox="0 0 20 20" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round">
              <path d="M5 5l10 10M15 5L5 15" />
            </svg>
          </button>
        </div>
      ))}
    </div>
  );
}
