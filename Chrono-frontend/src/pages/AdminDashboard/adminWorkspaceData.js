import { isHourlyEmploymentModel } from './adminDashboardUtils';

export const isWorkspacePending = (request) => Boolean(request && !request.approved && !request.denied);

export function workspaceDate(value) {
    if (!value) return null;
    const candidate = typeof value === 'string' ? value.slice(0, 10) : '';
    if (!/^\d{4}-\d{2}-\d{2}$/.test(candidate)) return null;
    const date = new Date(`${candidate}T12:00:00Z`);
    return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === candidate ? candidate : null;
}

const ISSUE_COUNTS = [
    ['missingEntriesCount', 'missing'],
    ['incompleteDaysCount', 'incomplete'],
    ['autoCompletedUncorrectedCount', 'auto_completed'],
    ['holidayPendingCount', 'holiday_pending_decision'],
    ['unusualWeeklyDeltaCount', 'weekly_delta_unusual'],
];

// Project the existing time-review calculation; do not infer issues from balances,
// absent summaries, or local clocks in the workspace itself.
export function deriveWorkspaceIssueRows(analytics = [], periodStart, periodEnd) {
    return analytics.filter(row => row?.username && ISSUE_COUNTS.some(([key]) => row.problemIndicators?.[key] > 0)).map(row => {
        const indicators = row.problemIndicators;
        const seen = new Set();
        const issues = (Array.isArray(indicators.problematicDays) ? indicators.problematicDays : [])
            .filter(issue => issue?.type)
            .map(issue => ({ type: issue.type, dateIso: workspaceDate(issue.dateIso) }))
            .filter(issue => {
                const key = `${issue.type}:${issue.dateIso}`;
                if (seen.has(key)) return false;
                seen.add(key);
                return true;
            })
            .sort((a, b) => (a.dateIso || '9999').localeCompare(b.dateIso || '9999') || a.type.localeCompare(b.type));
        if (!issues.length) {
            ISSUE_COUNTS.forEach(([key, type]) => {
                if (indicators[key] > 0) issues.push({ type, dateIso: null, count: indicators[key] });
            });
        }
        return {
            id: row.username,
            username: row.username,
            displayName: row.displayName,
            dateIso: issues.find(issue => issue.dateIso)?.dateIso || null,
            periodStart: workspaceDate(periodStart),
            periodEnd: workspaceDate(periodEnd),
            problemIndicators: {
                ...Object.fromEntries(ISSUE_COUNTS.map(([key]) => [key, indicators[key] || 0])),
                problematicDays: issues,
            },
            issues,
        };
    }).sort((a, b) => a.username.localeCompare(b.username));
}

export function groupWorkspaceCorrections(corrections = []) {
    const groups = new Map();
    corrections.forEach(request => {
        if (!request?.username || request.id == null) return;
        // approveRequest replaces all pending corrections of this person on the
        // desired LocalDateTime's calendar day, regardless of reason/requestDate.
        // A missing day cannot establish that scope, so keep such records isolated.
        const decisionDate = workspaceDate(request.desiredTimestamp);
        const key = JSON.stringify([request.username, decisionDate || `id:${request.id}`]);
        if (!groups.has(key)) groups.set(key, {
            key: `correction:${key}`, kind: 'correction', username: request.username,
            decisionDate, entries: [],
        });
        groups.get(key).entries.push(request);
    });
    return [...groups.values()].map(group => {
        const seen = new Set();
        const entries = group.entries.filter(entry => {
            if (seen.has(entry.id)) return false;
            seen.add(entry.id);
            return true;
        }).sort((a, b) => String(a.desiredTimestamp || a.originalTimestamp || '').localeCompare(String(b.desiredTimestamp || b.originalTimestamp || '')));
        const pendingEntries = entries.filter(isWorkspacePending);
        return {
            ...group, entries, pendingEntries,
            reasons: [...new Set(entries.map(entry => typeof entry.reason === 'string' ? entry.reason.trim() : ''))],
            dateIso: group.decisionDate || workspaceDate(entries[0]?.requestDate) || workspaceDate(entries[0]?.originalTimestamp),
            sortDate: group.decisionDate || '',
        };
    }).filter(group => group.pendingEntries.length);
}

export function workspaceAbsences(vacations = [], sickLeaves = []) {
    return [
        ...vacations.filter(item => item?.approved && !item.denied).map(item => ({ ...item, kind: 'vacation', key: `vacation:${item.id}`, source: item })),
        ...sickLeaves.filter(Boolean).map(item => ({ ...item, kind: 'sick', key: `sick:${item.id ?? `${item.username}:${item.startDate}:${item.endDate}`}`, source: item })),
    ].map(item => ({ ...item, startIso: workspaceDate(item.startDate), endIso: workspaceDate(item.endDate || item.startDate) }))
        .filter(item => item.username && item.startIso && item.endIso && item.startIso <= item.endIso)
        .sort((a, b) => a.startIso.localeCompare(b.startIso) || a.username.localeCompare(b.username));
}

export function workspaceBalances(balances = [], users = []) {
    const usersByName = new Map(users.map(user => [user.username, user]));
    const seen = new Set();
    return balances.filter(item => {
        if (!item?.username || !Number.isFinite(item.trackingBalance) || seen.has(item.username)) return false;
        seen.add(item.username);
        return !isHourlyEmploymentModel(usersByName.get(item.username) || item);
    });
}
