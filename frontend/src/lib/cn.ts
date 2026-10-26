import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind class names, resolving conflicts (later wins) and dropping
 * falsy values. Used by every component so callers can override styling via a
 * `className` prop without specificity surprises.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
