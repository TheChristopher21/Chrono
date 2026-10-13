import { pmsHasPermission } from './pmsAccess.js';

const PREFIX = 'chrono:pms-offline:v1:';
const MAX_BYTES = 2 * 1024 * 1024;

const userPrefix = (token) => {
    try {
        const claims = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        if (!claims.sub) return null;
        return `${PREFIX}${encodeURIComponent(claims.sub)}:`;
    } catch { return null; }
};
export function offlineScope(propertyId) {
    const prefix = userPrefix(localStorage.getItem('token'));
    return prefix ? `${prefix}${propertyId}` : null;
}
const accessFor = (scope) => { try { return JSON.parse(localStorage.getItem(`${scope}:access`)); } catch { return null; } };
const empty = () => ({ enabled: false, snapshots: {}, queue: [] });
const commandKey = (scope, id) => `${scope}:command:${id}`;
const changed = (scope) => window.dispatchEvent(new CustomEvent('chrono:pms-offline-changed', { detail: { scope } }));
const assertFits = (data) => { if (JSON.stringify(data).length * 2 > MAX_BYTES) throw new Error('Die Geräteablage ist voll. Bitte zuerst die offenen Aktionen synchronisieren.'); };
export function readOffline(scope) {
    if (!scope || accessFor(scope)?.view === false) return empty();
    try {
        const data = JSON.parse(localStorage.getItem(scope)); if (!data?.enabled) return empty();
        const queue = Object.keys(localStorage).filter((key) => key.startsWith(`${scope}:command:`))
            .map((key) => JSON.parse(localStorage.getItem(key))).filter(Boolean).sort((a, b) => a.createdAt - b.createdAt);
        return { ...data, queue };
    }
    catch { return empty(); }
}
export function writeOffline(scope, data) {
    if (!scope) throw new Error('Für die Geräteablage ist eine angemeldete PMS-Sitzung erforderlich.');
    if (accessFor(scope)?.view === false) throw new Error('Die Housekeeping-Berechtigung für diese Geräteablage wurde entzogen.');
    assertFits({ ...data, queue: readOffline(scope).queue });
    // Snapshot/settings writes never rewrite the command collection. Each command
    // has its own atomic key, so another browser tab cannot lose queued work.
    const { queue: ignoredQueue, ...settings } = data;
    localStorage.setItem(scope, JSON.stringify(settings)); changed(scope);
}
export function clearPmsOffline(scope) {
    const keys = Object.keys(localStorage).filter((key) => scope ? key === scope || key.startsWith(`${scope}:command:`) : key.startsWith(PREFIX));
    keys.forEach((key) => localStorage.removeItem(key));
    changed(scope);
}
// Only an authoritative access response may revoke offline data. A retained
// denial marker also prevents stale browser tabs from recreating a cleared cache.
export function reconcilePmsOfflineAccess(access, token = localStorage.getItem('token')) {
    const prefix = userPrefix(token);
    if (!prefix || typeof access?.master !== 'boolean' || !Array.isArray(access.properties)) return;
    const scopes = new Set(Object.keys(localStorage).filter((key) => key.startsWith(prefix))
        .map((key) => `${prefix}${key.slice(prefix.length).split(':')[0]}`));
    access.properties.forEach((property) => { if (property?.propertyId != null) scopes.add(`${prefix}${property.propertyId}`); });
    scopes.forEach((scope) => {
        const propertyId = scope.slice(prefix.length);
        const view = pmsHasPermission(access, propertyId, 'HOUSEKEEPING');
        const manage = pmsHasPermission(access, propertyId, 'HOUSEKEEPING', true);
        localStorage.setItem(`${scope}:access`, JSON.stringify({ view, manage }));
        if (!view) clearPmsOffline(scope);
        else if (!manage) {
            Object.keys(localStorage).filter((key) => key.startsWith(`${scope}:command:`)).forEach((key) => localStorage.removeItem(key));
            changed(scope);
        }
    });
}
export function cacheHousekeeping(scope, date, tasks) {
    const data = readOffline(scope); if (!data.enabled) return;
    // Keep only the last two days viewed, with an explicit capture time.
    const snapshots = Object.fromEntries(Object.entries(data.snapshots || {}).sort(([, a], [, b]) => b.capturedAt - a.capturedAt).slice(0, 1));
    snapshots[date] = { capturedAt: Date.now(), tasks };
    writeOffline(scope, { ...data, snapshots });
}
export function cachedHousekeeping(scope, date) {
    const snapshot = readOffline(scope).snapshots?.[date];
    return snapshot && Date.now() - snapshot.capturedAt < 24 * 60 * 60 * 1000 ? snapshot : null;
}
export function enqueueHousekeeping(scope, task, update) {
    const data = readOffline(scope);
    if (!data.enabled) throw new Error('Offline-Arbeit ist auf diesem Gerät nicht aktiviert.');
    if (accessFor(scope)?.manage === false) throw new Error('Die Bearbeitungsberechtigung für Housekeeping wurde entzogen.');
    if (data.queue.some((entry) => entry.taskId === task.id)) throw new Error('Diese Aufgabe hat bereits eine offene Geräteaktion. Bitte zuerst synchronisieren oder den Konflikt prüfen.');
    if (data.queue.length >= 500) throw new Error('Bitte zuerst die offenen Geräteaktionen synchronisieren.');
    const entry = { commandId: crypto.randomUUID(), taskId: task.id, roomNumber: task.roomNumber, serviceDate: task.serviceDate, update, state: 'PENDING', createdAt: Date.now() };
    assertFits({ ...data, queue: [...data.queue, entry] });
    localStorage.setItem(commandKey(scope, entry.commandId), JSON.stringify(entry)); changed(scope); return entry;
}
export function updateOfflineCommand(scope, commandId, patch) {
    const data = readOffline(scope);
    if (!data.enabled) return;
    const key = commandKey(scope, commandId); const current = JSON.parse(localStorage.getItem(key) || 'null');
    if (patch === null) localStorage.removeItem(key);
    else if (current) localStorage.setItem(key, JSON.stringify({ ...current, ...patch }));
    changed(scope);
}
