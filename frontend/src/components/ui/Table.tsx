import { type ReactNode, type ThHTMLAttributes } from 'react';
import { cn } from '@/lib';

/**
 * Accessible data table primitives (Req 19.4 — keyboard operable tables).
 *
 * These render native table semantics (<table>/<thead>/<tbody>/<th>/<td>) so
 * assistive technology exposes rows, columns, and headers correctly. Sorting
 * is driven by a real <button> inside the header cell (keyboard operable), and
 * the current sort direction is announced via `aria-sort` on the <th>. Callers
 * own sort state; this component only renders and reports intent.
 */
export type SortDirection = 'asc' | 'desc' | 'none';

export interface TableProps {
  /** Accessible caption describing the table's contents. Required for context. */
  caption: ReactNode;
  /** Visually hide the caption while keeping it for assistive tech. */
  hideCaption?: boolean;
  className?: string;
  children: ReactNode;
}

export function Table({ caption, hideCaption = true, className, children }: TableProps): JSX.Element {
  return (
    <div className="w-full overflow-x-auto rounded-lg border border-[var(--color-border)]">
      <table className={cn('w-full border-collapse text-left text-sm', className)}>
        <caption className={cn('px-4 py-3 text-left text-sm text-[var(--color-text-secondary)]', hideCaption && 'sr-only')}>
          {caption}
        </caption>
        {children}
      </table>
    </div>
  );
}

export function TableHead({ children }: { children: ReactNode }): JSX.Element {
  return <thead className="bg-[var(--color-surface-sunken)]">{children}</thead>;
}

export function TableBody({ children }: { children: ReactNode }): JSX.Element {
  return <tbody className="divide-y divide-[var(--color-border)]">{children}</tbody>;
}

export function TableRow({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}): JSX.Element {
  return <tr className={cn('bg-[var(--color-surface)]', className)}>{children}</tr>;
}

export interface TableHeaderCellProps extends Omit<ThHTMLAttributes<HTMLTableCellElement>, 'onClick'> {
  children: ReactNode;
  /** When provided, the header becomes a sortable button and reports direction via aria-sort. */
  sortDirection?: SortDirection;
  onSort?: () => void;
}

export function TableHeaderCell({
  children,
  sortDirection,
  onSort,
  className,
  ...props
}: TableHeaderCellProps): JSX.Element {
  const sortable = typeof onSort === 'function';
  const direction = sortDirection ?? 'none';
  const ariaSort: 'ascending' | 'descending' | 'none' | undefined = sortable
    ? direction === 'asc'
      ? 'ascending'
      : direction === 'desc'
        ? 'descending'
        : 'none'
    : undefined;

  return (
    <th
      scope="col"
      aria-sort={ariaSort}
      className={cn('px-4 py-3 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]', className)}
      {...props}
    >
      {sortable ? (
        <button
          type="button"
          onClick={onSort}
          className={cn(
            'inline-flex items-center gap-1.5 rounded-sm font-semibold uppercase tracking-wide',
            'hover:text-[var(--color-text-primary)]',
            'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]',
          )}
        >
          {children}
          <SortGlyph direction={sortDirection ?? 'none'} />
        </button>
      ) : (
        children
      )}
    </th>
  );
}

export function TableCell({
  children,
  className,
  ...props
}: ThHTMLAttributes<HTMLTableCellElement> & { children: ReactNode }): JSX.Element {
  return (
    <td className={cn('px-4 py-3 text-[var(--color-text-primary)]', className)} {...props}>
      {children}
    </td>
  );
}

function SortGlyph({ direction }: { direction: SortDirection }): JSX.Element {
  // Decorative; the sort state is conveyed to AT via aria-sort on the <th>.
  const label =
    direction === 'asc' ? '▲' : direction === 'desc' ? '▼' : '↕';
  return (
    <span aria-hidden="true" className="text-[0.625rem] leading-none opacity-70">
      {label}
    </span>
  );
}
