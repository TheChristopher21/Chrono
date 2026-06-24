import { describe, expect, it } from 'vitest';
import {
    calculateExpectedPercentageMinutesForDates,
    getDatesUpToReferenceDate,
    isModeledPercentageWorkDay,
} from '../percentageDashUtils';

describe('percentage dashboard expected-time calculations', () => {
    it('models six-day weeks as Monday through Saturday', () => {
        expect(isModeledPercentageWorkDay(new Date('2026-05-09T00:00:00'), 6)).toBe(true);
        expect(isModeledPercentageWorkDay(new Date('2026-05-10T00:00:00'), 6)).toBe(false);
    });

    it('calculates expected minutes from the evaluated dates instead of a fixed Monday-Friday week', () => {
        const dates = [
            new Date('2026-05-04T00:00:00'),
            new Date('2026-05-05T00:00:00'),
            new Date('2026-05-06T00:00:00'),
        ];

        expect(calculateExpectedPercentageMinutesForDates(
            { isPercentage: true, workPercentage: 60, expectedWorkDays: 5 },
            dates,
        )).toBe(918);
    });

    it('ignores an approved vacation day when the user has tracked work that day', () => {
        const dates = [new Date('2026-05-04T00:00:00')];

        expect(calculateExpectedPercentageMinutesForDates(
            { isPercentage: true, workPercentage: 100, expectedWorkDays: 5 },
            dates,
            {
                vacationRequests: [{ startDate: '2026-05-04', endDate: '2026-05-04', approved: true }],
                workedDateSet: new Set(['2026-05-04']),
            },
        )).toBe(510);
    });

    it('caps current/future date collections at the reference date', () => {
        const dates = Array.from({ length: 7 }, (_, index) => new Date(2026, 4, 4 + index));

        expect(getDatesUpToReferenceDate(dates, new Date(2026, 4, 6)).map(date => date.getDate())).toEqual([4, 5, 6]);
    });
});
