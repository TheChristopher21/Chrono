import { workspaceDate } from './adminWorkspaceData';

export const requestStatus = request => request.approved ? 'approved' : request.denied ? 'denied' : 'pending';
export const requestEntryKey = (type, id) => `${type}:${id}`;
// The approval endpoint replaces one person's entire desired calendar day.
// An absent or malformed desired timestamp must not infer that scope from another date.
export const correctionApprovalDay = entry => typeof entry.desiredTimestamp === 'string'
    && /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/.test(entry.desiredTimestamp)
    ? workspaceDate(entry.desiredTimestamp) : null;
const entryDate = entry => workspaceDate(entry.desiredTimestamp) || workspaceDate(entry.originalTimestamp);
const byId = (a, b) => String(a.id).localeCompare(String(b.id), 'en', { numeric: true });

/** Keep complete correction groups in history as well as in the pending inbox. */
export function buildWorkspaceRequestRows(vacations, corrections, decisions = {}, deletedIds = new Set()) {
    const applyDecision = (type, entry) => {
        const decision = decisions[requestEntryKey(type, entry.id)];
        return decision && !entry.approved && !entry.denied ? { ...entry, approved: decision.approved, denied: !decision.approved,
            [type === 'vacation' ? 'adminNote' : 'adminComment']: decision.comment } : entry;
    };
    const seen = new Set();
    const vacationRows = vacations.filter(item => {
        if (item?.id == null || deletedIds.has(String(item.id)) || seen.has(String(item.id))) return false;
        seen.add(String(item.id)); return true;
    }).map(item => {
        const vacation = applyDecision('vacation', item);
        return { key: requestEntryKey('vacation', item.id), type: 'vacation', id: item.id, username: item.username,
            entries: [vacation], vacation, startDate: workspaceDate(item.startDate), endDate: workspaceDate(item.endDate || item.startDate),
            // Vacation has no requestDate. Correction's requestDate is the user-selected
            // correction day, not a submission timestamp. Never infer arrival from an id.
            requestDate: workspaceDate(item.requestDate), reason: item.reason || item.comment || '' };
    });
    const groups = new Map();
    seen.clear();
    corrections.forEach(item => {
        if (item?.id == null || seen.has(String(item.id))) return;
        seen.add(String(item.id));
        const approvalDay = correctionApprovalDay(item);
        const key = JSON.stringify([item.username, approvalDay || `unknown:${item.id}`]);
        if (!groups.has(key)) groups.set(key, { key: `correction:${key}`, type: 'correction', username: item.username,
            approvalDay, entries: [] });
        groups.get(key).entries.push(applyDecision('correction', item));
    });
    const correctionRows = [...groups.values()].map(group => {
        const entries = [...group.entries].sort((a, b) => String(a.desiredTimestamp || a.originalTimestamp || '\uffff')
            .localeCompare(String(b.desiredTimestamp || b.originalTimestamp || '\uffff')) || byId(a, b));
        const dates = entries.map(entryDate).filter(Boolean).sort();
        const requestDates = [...new Set(entries.map(entry => workspaceDate(entry.requestDate)).filter(Boolean))].sort();
        const reasons = [...new Set(entries.map(entry => entry.reason?.trim()).filter(Boolean))];
        return { ...group, entries, id: entries[0].id, startDate: dates[0] || null, endDate: dates.at(-1) || null,
            requestDates, requestDate: requestDates[0] || null, reasons, reason: reasons.join('\n') };
    });
    return [...vacationRows, ...correctionRows].map(row => {
        const statuses = new Set(row.entries.map(requestStatus));
        return { ...row, pendingEntries: row.entries.filter(entry => requestStatus(entry) === 'pending'),
            status: statuses.has('pending') ? 'pending' : statuses.size === 1 ? [...statuses][0] : 'mixed' };
    });
}

export function sortWorkspaceRequestRows(rows, dateBasis = 'period', direction = 'asc', sortBy = 'date') {
    return [...rows].sort((a, b) => {
        const multiplier = direction === 'desc' ? -1 : 1;
        if (sortBy !== 'date') {
            const statusOrder = { pending: 0, approved: 1, denied: 2, mixed: 3 };
            const difference = sortBy === 'username' ? String(a.username || '').localeCompare(String(b.username || ''), 'de', { numeric: true })
                : statusOrder[a.status] - statusOrder[b.status];
            return difference * multiplier || a.key.localeCompare(b.key, 'en', { numeric: true });
        }
        const left = dateBasis === 'request' ? a.requestDate : a.startDate;
        const right = dateBasis === 'request' ? b.requestDate : b.startDate;
        if (!left && right) return 1;
        if (left && !right) return -1;
        return (left && right ? left.localeCompare(right) * multiplier : 0)
            || a.key.localeCompare(b.key, 'en', { numeric: true });
    });
}

export function matchesRequestDate(row, date, basis) {
    if (!date) return true;
    if (basis === 'request') return row.type === 'correction' ? row.requestDates.includes(date) : row.requestDate === date;
    if (row.type === 'correction') return row.entries.some(entry => entryDate(entry) === date);
    return row.startDate && row.endDate && row.startDate <= date && row.endDate >= date;
}
