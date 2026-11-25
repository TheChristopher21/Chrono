import { describe, expect, it } from 'vitest';
import {
    deriveWorkspaceIssueRows, groupWorkspaceCorrections, workspaceAbsences, workspaceBalances, workspaceDate,
} from '../adminWorkspaceData';

describe('workspace projections of real dashboard data', () => {
    it('preserves real issue dates and does not infer an issue from a negative balance', () => {
        const rows = deriveWorkspaceIssueRows([
            { username: 'healthy', cumulativeBalanceMinutes: -5000, problemIndicators: {} },
            { username: 'Mirjam', displayName: 'Mirjam Burkart', problemIndicators: {
                incompleteDaysCount: 1, autoCompletedUncorrectedCount: 1,
                problematicDays: [
                    { dateIso: '2026-09-11', type: 'auto_completed_incomplete_uncorrected' },
                    { dateIso: '2026-09-11', type: 'auto_completed_incomplete_uncorrected' },
                ],
            } },
        ], '2026-09-07', '2026-09-13');
        expect(rows).toHaveLength(1);
        expect(rows[0]).toMatchObject({ username: 'Mirjam', dateIso: '2026-09-11', periodStart: '2026-09-07', periodEnd: '2026-09-13' });
        expect(rows[0].issues).toEqual([{ dateIso: '2026-09-11', type: 'auto_completed_incomplete_uncorrected' }]);
    });

    it('keeps a count-only issue undated and removes acknowledged weekly deviations', () => {
        expect(deriveWorkspaceIssueRows([{ username: 'a', problemIndicators: { missingEntriesCount: 2 } }])[0].issues)
            .toEqual([{ type: 'missing', dateIso: null, count: 2 }]);
        expect(deriveWorkspaceIssueRows([{ username: 'a', problemIndicators: { unusualWeeklyDeltaCount: 0, problematicDays: [] } }])).toEqual([]);
        expect(workspaceDate('2026-02-30')).toBeNull();
    });

    it('groups by the server approval scope: person and desired calendar day, irrespective of reason or requestDate', () => {
        const base = { username: 'Mirjam', requestDate: '2026-09-12', reason: 'Vergessen' };
        const rows = groupWorkspaceCorrections([
            { ...base, id: 2, desiredTimestamp: '2026-09-11T17:00:00' },
            { ...base, id: 1, desiredTimestamp: '2026-09-11T08:00:00', approved: true },
            { ...base, id: 3, desiredTimestamp: '2026-09-11T12:00:00' },
            { ...base, id: 3, desiredTimestamp: '2026-09-11T12:00:00' },
            { ...base, id: 4, requestDate: '2026-08-20', reason: 'Anderer Grund', desiredTimestamp: '2026-09-11T09:00:00' },
            { ...base, id: 5, username: 'Luca', desiredTimestamp: '2026-09-11T09:00:00' },
            { ...base, id: 6, desiredTimestamp: '2026-09-12T00:01:00' },
        ]);
        expect(rows).toHaveLength(3);
        expect(rows[0].entries.map(entry => entry.id)).toEqual([1, 4, 3, 2]);
        expect(rows[0].pendingEntries.map(entry => entry.id)).toEqual([4, 3, 2]);
        expect(rows[0].dateIso).toBe('2026-09-11');
        expect(rows[0].decisionDate).toBe('2026-09-11');
        expect(rows[0].reasons).toEqual(['Vergessen', 'Anderer Grund']);
        expect(rows[1].username).toBe('Luca');
        expect(rows[2].decisionDate).toBe('2026-09-12');
        expect(groupWorkspaceCorrections([{ ...base, id: 5, denied: true }])).toEqual([]);
    });

    it('never combines records when their desired calendar day is missing or invalid', () => {
        const base = { username: 'Mirjam', requestDate: '2026-09-11', reason: 'Gleicher Grund' };
        const rows = groupWorkspaceCorrections([
            { ...base, id: 0 }, { ...base, id: 1, desiredTimestamp: 'invalid' },
            { ...base, id: 2, desiredTimestamp: '2026-02-30T09:00:00' },
            { ...base, id: 3, desiredTimestamp: '2026-09-11T09:00:00' },
        ]);
        expect(rows).toHaveLength(4);
        expect(new Set(rows.map(row => row.key)).size).toBe(4);
        expect(rows.every(row => row.entries.length === 1)).toBe(true);
        expect(rows.slice(0, 3).map(row => row.decisionDate)).toEqual([null, null, null]);
        expect(rows[3].decisionDate).toBe('2026-09-11');
    });

    it('excludes pending, denied and invalid absences and retains half-day/overtime/source fields', () => {
        const vacation = { id: 1, username: 'a', approved: true, startDate: '2026-09-14', endDate: '2026-09-15', halfDay: true, usesOvertime: true };
        const rows = workspaceAbsences([
            vacation, { ...vacation, id: 2, approved: false }, { ...vacation, id: 3, denied: true },
            { ...vacation, id: 4, endDate: '2026-09-13' }, { ...vacation, id: 5, startDate: 'invalid' },
        ], [{ id: 8, username: 'b', startDate: '2026-09-14', endDate: '2026-09-16' }]);
        expect(rows).toHaveLength(2);
        expect(rows[0]).toMatchObject({ kind: 'vacation', halfDay: true, usesOvertime: true, source: vacation });
        expect(rows[1].kind).toBe('sick');
    });

    it('uses finite balances, preserves zero, and excludes hourly workers using existing employment rules', () => {
        const rows = workspaceBalances([
            { username: 'a', trackingBalance: 0 }, { username: 'b', trackingBalance: -240 },
            { username: 'hourly', trackingBalance: 999 }, { username: 'missing', trackingBalance: null },
            { username: 'a', trackingBalance: 10 },
        ], [{ username: 'hourly', roles: ['ROLE_HOURLY'] }]);
        expect(rows.map(row => [row.username, row.trackingBalance])).toEqual([['a', 0], ['b', -240]]);
    });
});
