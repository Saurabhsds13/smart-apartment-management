import {
  createContext,
  forwardRef,
  useContext,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { cn } from '@/lib';

/**
 * Form field accessibility contract (Req 19.4).
 *
 * `FormField` owns the ids and wires them so every control has a
 * programmatically associated label (via <label htmlFor>) and programmatically
 * associated help/error text (via aria-describedby + aria-errormessage). The
 * `Input`/`Textarea`/`Select` controls read those ids from context, so callers
 * cannot forget to associate them.
 */
interface FieldContextValue {
  controlId: string;
  descriptionId: string;
  errorId: string;
  hasError: boolean;
  hasDescription: boolean;
  required: boolean;
}

const FieldContext = createContext<FieldContextValue | null>(null);

function useFieldContext(control: string): FieldContextValue {
  const ctx = useContext(FieldContext);
  if (!ctx) {
    throw new Error(`<${control}> must be rendered inside a <FormField>.`);
  }
  return ctx;
}

export interface FormFieldProps {
  label: ReactNode;
  /** Optional helper text shown below the control and linked via aria-describedby. */
  description?: ReactNode;
  /** Validation error. When present, the control is marked invalid and linked via aria-errormessage. */
  error?: ReactNode;
  required?: boolean;
  className?: string;
  children: ReactNode;
}

export function FormField({
  label,
  description,
  error,
  required = false,
  className,
  children,
}: FormFieldProps): JSX.Element {
  const base = useId();
  const value: FieldContextValue = {
    controlId: `${base}-control`,
    descriptionId: `${base}-description`,
    errorId: `${base}-error`,
    hasError: Boolean(error),
    hasDescription: Boolean(description),
    required,
  };

  return (
    <FieldContext.Provider value={value}>
      <div className={cn('flex flex-col gap-1.5', className)}>
        <label
          htmlFor={value.controlId}
          className="text-sm font-medium text-[var(--color-text-primary)]"
        >
          {label}
          {required && (
            <>
              {' '}
              <span className="text-[var(--color-danger-500)]" aria-hidden="true">
                *
              </span>
              <span className="sr-only">(required)</span>
            </>
          )}
        </label>

        {children}

        {description && !error && (
          <p id={value.descriptionId} className="text-sm text-[var(--color-text-secondary)]">
            {description}
          </p>
        )}

        {error && (
          <p
            id={value.errorId}
            className="text-sm font-medium text-[var(--color-danger-700)]"
          >
            {error}
          </p>
        )}
      </div>
    </FieldContext.Provider>
  );
}

/** Compute the aria wiring shared by all field controls. */
function ariaProps(ctx: FieldContextValue): {
  id: string;
  required: boolean;
  'aria-invalid': true | undefined;
  'aria-describedby': string | undefined;
  'aria-errormessage': string | undefined;
} {
  const describedBy = ctx.hasDescription && !ctx.hasError ? ctx.descriptionId : undefined;
  return {
    id: ctx.controlId,
    required: ctx.required,
    'aria-invalid': ctx.hasError ? true : undefined,
    'aria-describedby': describedBy,
    'aria-errormessage': ctx.hasError ? ctx.errorId : undefined,
  };
}

const controlBase =
  'w-full rounded-md border bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-text-primary)] ' +
  'placeholder:text-[var(--color-text-disabled)] ' +
  'transition-colors ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)] ' +
  'disabled:cursor-not-allowed disabled:bg-[var(--color-surface-sunken)] disabled:opacity-70';

const controlState = (hasError: boolean): string =>
  hasError
    ? 'border-[var(--color-danger-500)] focus-visible:outline-[var(--color-danger-500)]'
    : 'border-[var(--color-border-strong)]';

export type InputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'id' | 'aria-invalid' | 'aria-describedby' | 'aria-errormessage'
>;

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, ...props },
  ref,
) {
  const ctx = useFieldContext('Input');
  return (
    <input
      ref={ref}
      className={cn(controlBase, controlState(ctx.hasError), className)}
      {...ariaProps(ctx)}
      {...props}
    />
  );
});

export type TextareaProps = Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  'id' | 'aria-invalid' | 'aria-describedby' | 'aria-errormessage'
>;

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { className, rows = 4, ...props },
  ref,
) {
  const ctx = useFieldContext('Textarea');
  return (
    <textarea
      ref={ref}
      rows={rows}
      className={cn(controlBase, 'resize-y', controlState(ctx.hasError), className)}
      {...ariaProps(ctx)}
      {...props}
    />
  );
});

export type SelectProps = Omit<
  SelectHTMLAttributes<HTMLSelectElement>,
  'id' | 'aria-invalid' | 'aria-describedby' | 'aria-errormessage'
>;

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, children, ...props },
  ref,
) {
  const ctx = useFieldContext('Select');
  return (
    <select
      ref={ref}
      className={cn(controlBase, 'pr-8', controlState(ctx.hasError), className)}
      {...ariaProps(ctx)}
      {...props}
    >
      {children}
    </select>
  );
});
