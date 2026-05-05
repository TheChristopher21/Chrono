import { describe, expect, it } from 'vitest';
import { selectTrackableUsers } from '../adminDashboardUtils';

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
