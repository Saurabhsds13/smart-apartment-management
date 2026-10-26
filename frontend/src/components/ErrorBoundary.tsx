import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from './ui';

/**
 * React error boundary with a recoverable fallback UI.
 *
 * Catches render/lifecycle errors in its subtree so a single broken screen does
 * not blank the whole SPA. The fallback uses the accessible ErrorState (role
 * "alert") and offers a retry that resets the boundary so the user can recover
 * without a full page reload. A `resetKeys` change (e.g. the route path) also
 * resets the boundary so navigating away clears a stale error.
 *
 * Diagnostic detail is logged to the console only; we never surface internal
 * error details to the user (aligns with not exposing sensitive system details,
 * Req 17.10). Wiring to the observability backend happens with the logging task.
 */

export interface ErrorBoundaryProps {
  children: ReactNode;
  /** Custom fallback renderer. Receives the error and a reset callback. */
  fallback?: (error: Error, reset: () => void) => ReactNode;
  /** When any value in this array changes, the boundary resets automatically. */
  resetKeys?: ReadonlyArray<unknown>;
  /** Optional title/description overrides for the default fallback. */
  title?: ReactNode;
  description?: ReactNode;
  /** Invoked when an error is caught (for logging/telemetry). */
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  error: Error | null;
}

function keysChanged(a: ReadonlyArray<unknown> = [], b: ReadonlyArray<unknown> = []): boolean {
  if (a.length !== b.length) {
    return true;
  }
  return a.some((value, index) => !Object.is(value, b[index]));
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  override componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    // Auto-recover when the caller signals a context change (e.g. route change).
    if (this.state.error && keysChanged(prevProps.resetKeys, this.props.resetKeys)) {
      this.reset();
    }
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Keep diagnostics out of the UI; log for developers/observability only.
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.error('ErrorBoundary caught an error:', error, info.componentStack);
    }
    this.props.onError?.(error, info);
  }

  reset = (): void => {
    this.setState({ error: null });
  };

  override render(): ReactNode {
    const { error } = this.state;
    if (error) {
      if (this.props.fallback) {
        return this.props.fallback(error, this.reset);
      }
      return (
        <div className="flex min-h-[40vh] items-center justify-center p-6">
          <ErrorState
            title={this.props.title ?? 'Something went wrong'}
            description={
              this.props.description ??
              'An unexpected error occurred while rendering this screen. You can try again.'
            }
            onRetry={this.reset}
          />
        </div>
      );
    }
    return this.props.children;
  }
}
