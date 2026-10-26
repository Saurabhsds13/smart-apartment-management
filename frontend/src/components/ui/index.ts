/**
 * Societix accessible component library (task 4.2).
 *
 * Base primitives built on the design tokens in `src/styles`. Every interactive
 * control is keyboard operable with a visible focus indicator (Req 19.2, 19.3),
 * form controls carry programmatic labels + error association (Req 19.4),
 * dialogs trap focus and close on Escape, and toasts/alerts expose status via
 * ARIA live regions (Req 19.6). Motion respects prefers-reduced-motion.
 */
export { Button } from './Button';
export type { ButtonProps, ButtonVariant, ButtonSize } from './Button';

export { FormField, Input, Textarea, Select } from './FormField';
export type { FormFieldProps, InputProps, TextareaProps, SelectProps } from './FormField';

export {
  Dialog,
  DialogTrigger,
  DialogClose,
  DialogContent,
  DialogFooter,
} from './Dialog';
export type { DialogContentProps } from './Dialog';

export {
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableHeaderCell,
  TableCell,
} from './Table';
export type { TableProps, TableHeaderCellProps, SortDirection } from './Table';

export { Alert } from './Alert';
export type { AlertProps, AlertTone } from './Alert';

export { ToastProvider, useToast } from './Toast';
export type { ToastOptions, ToastTone } from './Toast';

export {
  EmptyState,
  Skeleton,
  SkeletonText,
  ErrorState,
} from './States';
export type {
  EmptyStateProps,
  SkeletonProps,
  SkeletonTextProps,
  ErrorStateProps,
} from './States';
