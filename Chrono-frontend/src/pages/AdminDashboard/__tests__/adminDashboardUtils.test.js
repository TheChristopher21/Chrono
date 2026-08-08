import { describe, expect, it } from 'vitest';
import {
    calculateWeeklyExpectedMinutes,
    getDatesUpToReferenceDate,
    getExpectedHoursForDay,
    isHourlyEmploymentModel,
    selectTrackableUsers,
} from '../adminDashboardUtils';

describe('selectTrackableUsers', () => {
    it('never returns superadmin accounts for dashboard overviews', () => {
        const result = selectTrackableUsers([
            { username: 'superadmin', roles: ['ROLE_SUPERADMIN'], includeInTimeTracking: true },
            { username: 'admin', roles: ['ROLE_ADMIN'], includeInTimeTracking: true },
            { username: 'worker', roles: ['ROLE_USER'], includeInTimeTracking: true },
        ]);

        expect(result.trackableUsers.map((user) => user.username)).toEqual(['admin', 'worker']);
    });
});

describe('isHourlyEmploymentModel', () => {
    it('detects hourly users from flags and role metadata', () => {
        expect(isHourlyEmploymentModel({ isHourly: true })).toBe(true);
        expect(isHourlyEmploymentModel({ isHourly: 'ja' })).toBe(true);
        expect(isHourlyEmploymentModel({ roles: ['ROLE_HOURLY'] })).toBe(true);
        expect(isHourlyEmploymentModel({ role: 'Stundenlohn' })).toBe(true);
    });

    it('does not mark regular users as hourly', () => {
        expect(isHourlyEmploymentModel({ isHourly: false, roles: ['ROLE_USER'] })).toBe(false);
        expect(isHourlyEmploymentModel(null)).toBe(false);
    });
});

describe('dashboard expected-time calculations', () => {
    it('calculates percentage expected minutes only for the evaluated date range', () => {
        const dates = [
            new Date('2026-05-04T00:00:00'),
            new Date('2026-05-05T00:00:00'),
            new Date('2026-05-06T00:00:00'),
        ];

        const expected = calculateWeeklyExpectedMinutes(
            { isPercentage: true, workPercentage: 60, expectedWorkDays: 5 },
            dates,
            8.5,
            [],
            [],
            {},
            [],
        );

        expect(expected).toBe(918);
    });

    it('keeps Sunday free for six-day percentage models', () => {
        const user = { isPercentage: true, workPercentage: 60, expectedWorkDays: 6 };

        expect(getExpectedHoursForDay(new Date('2026-05-09T00:00:00'), user, 8.5)).toBe(4.25);
        expect(getExpectedHoursForDay(new Date('2026-05-10T00:00:00'), user, 8.5)).toBe(0);
    });

    it('does not deduct an approved vacation day when tracked work exists', () => {
        const dates = [new Date('2026-05-04T00:00:00')];
        const expected = calculateWeeklyExpectedMinutes(
            {
                isPercentage: false,
                weeklySchedule: [{ monday: 8.5 }],
                scheduleCycle: 1,
            },
            dates,
            8.5,
            [{ startDate: '2026-05-04', endDate: '2026-05-04', approved: true }],
            [],
            {},
            [],
            new Set(['2026-05-04']),
        );

        expect(expected).toBe(510);
    });

    it('returns past dates only up to the reference date', () => {
        const dates = Array.from({ length: 7 }, (_, index) => new Date(2026, 4, 4 + index));

        expect(getDatesUpToReferenceDate(dates, new Date(2026, 4, 6)).map(date => date.getDate())).toEqual([4, 5, 6]);
    });
});
