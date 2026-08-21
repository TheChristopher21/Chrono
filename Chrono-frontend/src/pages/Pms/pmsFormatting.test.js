import { describe, expect, it } from 'vitest';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';

describe('PMS date formatting', () => {
    it('shows hotel dates in the de-CH reading order', () => {
        expect(formatPmsDate('2026-07-28')).toBe('28.07.2026');
        expect(formatPmsDateTime('2026-07-28T09:05:00')).toBe('28.07.2026, 09:05 Uhr');
    });

    it('does not expose invalid raw date values', () => {
        expect(formatPmsDate(null)).toBe('Nicht angegeben');
        expect(formatPmsDateTime('internal-date-value')).toBe('Nicht angegeben');
    });
});
