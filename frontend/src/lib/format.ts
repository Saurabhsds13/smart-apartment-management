/**
 * Display formatting helpers for currency and time.
 *
 * These are PRESENTATION helpers only. The server remains the source of truth
 * for monetary precision (Java BigDecimal, HALF_UP, two decimals — Req 22.1–22.5)
 * and for stored timestamps (UTC — Req 23.1). The client simply renders what the
 * server computed, in the conventions Indian users expect.
 *
 *   - Money is shown in INR with exactly two decimal places using the `en-IN`
 *     locale grouping (Req 22.6).
 *   - Timestamps are shown in IST (Asia/Kolkata) by default; callers may pass a
 *     configured time zone to honour a user's preference (Req 23.2, 23.3).
 */

/** ISO 4217 code and locale used for all monetary display. */
const INR_CURRENCY = 'INR';
const EN_IN_LOCALE = 'en-IN';

/** Default civil time zone for display (Req 23.2). India Standard Time. */
export const DEFAULT_TIME_ZONE = 'Asia/Kolkata';

/**
 * A monetary amount for display. We accept a `number` or a decimal `string`
 * (the API serialises BigDecimal amounts as strings to preserve precision).
 * Strings are parsed without going through binary float where possible by
 * handing the raw value to `Intl.NumberFormat`, which accepts numeric strings.
 */
export type MonetaryInput = number | string;

const inrFormatter = new Intl.NumberFormat(EN_IN_LOCALE, {
  style: 'currency',
  currency: INR_CURRENCY,
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Format a monetary amount as INR with two decimal places (Req 22.6).
 *
 * Example: `1234.5` -> "₹1,234.50", `"2500000"` -> "₹25,00,000.00".
 *
 * Returns an em dash for values that are not finite numbers so the UI never
 * renders "NaN"; callers that need to distinguish "no value" should check
 * before calling.
 */
export function formatInr(amount: MonetaryInput | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '—';
  }
  const value = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(value)) {
    return '—';
  }
  return inrFormatter.format(value);
}

/** Accepts the shapes the API/date pickers hand us for a timestamp. */
export type DateInput = Date | string | number;

function toDate(input: DateInput | null | undefined): Date | undefined {
  if (input === null || input === undefined || input === '') {
    return undefined;
  }
  const date = input instanceof Date ? input : new Date(input);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

export interface DateTimeFormatOptions {
  /** Override the display time zone (Req 23.3). Defaults to IST. */
  timeZone?: string;
  /** Additional Intl options merged over the preset. */
  intl?: Intl.DateTimeFormatOptions;
}

/**
 * Format a timestamp as a date + time in IST by default (Req 23.2).
 * The stored value is UTC; we convert to the display zone here.
 *
 * Example: "2024-01-15T09:30:00Z" -> "15 Jan 2024, 3:00 pm" (IST).
 */
export function formatDateTimeIst(
  input: DateInput | null | undefined,
  options: DateTimeFormatOptions = {},
): string {
  const date = toDate(input);
  if (!date) {
    return '—';
  }
  return new Intl.DateTimeFormat(EN_IN_LOCALE, {
    timeZone: options.timeZone ?? DEFAULT_TIME_ZONE,
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: 'numeric',
    minute: '2-digit',
    ...options.intl,
  }).format(date);
}

/**
 * Format a timestamp as a date only, in IST by default (Req 23.2).
 *
 * Example: "2024-01-15T20:30:00Z" -> "16 Jan 2024" (because 20:30 UTC is
 * past midnight IST), demonstrating that civil-date boundaries follow the
 * display zone, not UTC (Req 23.4).
 */
export function formatDateIst(
  input: DateInput | null | undefined,
  options: DateTimeFormatOptions = {},
): string {
  const date = toDate(input);
  if (!date) {
    return '—';
  }
  return new Intl.DateTimeFormat(EN_IN_LOCALE, {
    timeZone: options.timeZone ?? DEFAULT_TIME_ZONE,
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    ...options.intl,
  }).format(date);
}
