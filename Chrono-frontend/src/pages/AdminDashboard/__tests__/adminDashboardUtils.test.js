import { describe, expect, it } from 'vitest';
import {
    calculateWeeklyExpectedMinutes,
    getDetailedGlobalProblemIndicators,
    getDatesUpToReferenceDate,
    getExpectedHoursForDay,
    isCurrentUserIncludedInTimeTracking,
    isHourlyEmploymentModel,
    selectTrackableUsers,
} from '../adminDashboardUtils';

describe('isCurrentUserIncludedInTimeTracking', () => {
    const currentAdmin = { username: 'admin' };

    it('allows punching only when the current admin is explicitly included', () => {
        expect(isCurrentUserIncludedInTimeTracking(currentAdmin, [
            { username: 'admin', includeInTimeTracking: true },
        ])).toBe(true);
    });

    it('hides punching for excluded, missing, or not-yet-loaded admins', () => {
        expect(isCurrentUserIncludedInTimeTracking(currentAdmin, [
            { username: 'admin', includeInTimeTracking: false },
        ])).toBe(false);
        expect(isCurrentUserIncludedInTimeTracking(currentAdmin, [
            { username: 'someone-else', includeInTimeTracking: true },
        ])).toBe(false);
        expect(isCurrentUserIncludedInTimeTracking(currentAdmin, [])).toBe(false);
    });
});

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
    it('does not flag the freed original day after a backend workday swap', () => {
        const result = getDetailedGlobalProblemIndicators(
            [
                { date: '2026-07-13', expectedMinutes: 0, entries: [], primaryTimes: { isOpen: false } },
                {
                    date: '2026-07-14',
                    expectedMinutes: 510,
                    entries: [{ punchType: 'START', entryTimestamp: '2026-07-14T08:00:00' }, { punchType: 'ENDE', entryTimestamp: '2026-07-14T17:00:00' }],
                    primaryTimes: { isOpen: false },
                },
                { date: '2026-07-15', expectedMinutes: 0, entries: [], primaryTimes: { isOpen: false } },
                {
                    date: '2026-07-16',
                    expectedMinutes: 0,
                    entries: [],
                    primaryTimes: { isOpen: false },
                },
                { date: '2026-07-17', expectedMinutes: 0, entries: [], primaryTimes: { isOpen: false } },
                { date: '2026-07-18', expectedMinutes: 0, entries: [], primaryTimes: { isOpen: false } },
                { date: '2026-07-19', expectedMinutes: 0, entries: [], primaryTimes: { isOpen: false } },
            ],
            [],
            {
                isPercentage: false,
                weeklySchedule: [{ tuesday: 0, thursday: 8.5 }],
                scheduleCycle: 1,
                scheduleEffectiveDate: '2026-01-01',
            },
            8.5,
            [],
            {},
            [],
            '2026-07-13',
            '2026-07-19',
        );

        expect(result.missingEntriesCount).toBe(0);
        expect(result.problematicDays).toEqual([]);
    });

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
