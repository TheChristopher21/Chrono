import { describe, expect, it } from 'vitest';
import { isHourlyEmploymentModel, selectTrackableUsers } from '../adminDashboardUtils';

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
