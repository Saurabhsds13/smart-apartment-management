import { describe, expect, it } from 'vitest';
import {
  DEFAULT_TIME_ZONE,
  formatDateIst,
  formatDateTimeIst,
  formatInr,
} from './format';

/**
 * Display-formatting tests. These assert the presentation conventions Indian
 * users expect (Req 22.6 for INR, Req 23.2–23.4 for IST civil time). They do
 * not test monetary precision — the server owns that (Req 22.1–22.5).
 *
 * Node 22 ships full-ICU, so en-IN grouping and the Asia/Kolkata zone are
 * available and deterministic here.
 */

describe('formatInr', () => {
  it('formats a number with two decimals and the rupee symbol', () => {
    expect(formatInr(1234.5)).toBe('₹1,234.50');
  });

  it('applies Indian (lakh/crore) digit grouping', () => {
    // 25 lakh -> grouped as 25,00,000 not 2,500,000.
    expect(formatInr('2500000')).toBe('₹25,00,000.00');
  });

  it('accepts a decimal string (BigDecimal serialised by the API)', () => {
    expect(formatInr('99.99')).toBe('₹99.99');
  });

  it('formats zero as ₹0.00', () => {
    expect(formatInr(0)).toBe('₹0.00');
  });

  it('renders an em dash for null, undefined, or empty input', () => {
    expect(formatInr(null)).toBe('—');
    expect(formatInr(undefined)).toBe('—');
    expect(formatInr('')).toBe('—');
  });

  it('renders an em dash rather than NaN for non-numeric input', () => {
    expect(formatInr('not-a-number')).toBe('—');
  });
});

describe('formatDateTimeIst', () => {
  it('converts a UTC instant to IST wall-clock (UTC +5:30)', () => {
    // 09:30 UTC + 5:30 = 15:00 IST -> "3:00 pm".
    const result = formatDateTimeIst('2024-01-15T09:30:00Z');
    expect(result).toContain('15 Jan 2024');
    expect(result.toLowerCase()).toContain('3:00');
    expect(result.toLowerCase()).toContain('pm');
  });

  it('defaults to the Asia/Kolkata time zone', () => {
    expect(DEFAULT_TIME_ZONE).toBe('Asia/Kolkata');
  });

  it('honours an explicit time zone override', () => {
    // Same instant rendered in UTC shows the un-shifted wall clock.
    const result = formatDateTimeIst('2024-01-15T09:30:00Z', { timeZone: 'UTC' });
    expect(result.toLowerCase()).toContain('9:30');
    expect(result.toLowerCase()).toContain('am');
  });

  it('renders an em dash for missing or invalid input', () => {
    expect(formatDateTimeIst(null)).toBe('—');
    expect(formatDateTimeIst('nonsense')).toBe('—');
  });
});

describe('formatDateIst', () => {
  it('rolls the civil date forward when IST crosses midnight', () => {
    // 20:30 UTC + 5:30 = 02:00 IST next day -> 16 Jan, not 15 Jan (Req 23.4).
    expect(formatDateIst('2024-01-15T20:30:00Z')).toBe('16 Jan 2024');
  });

  it('renders an em dash for missing input', () => {
    expect(formatDateIst(undefined)).toBe('—');
  });
});
