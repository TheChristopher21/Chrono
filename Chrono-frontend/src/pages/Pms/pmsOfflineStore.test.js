/** @vitest-environment jsdom */
import { beforeEach, describe, expect, it } from 'vitest';
import { offlineScope, readOffline, writeOffline, clearPmsOffline, cachedHousekeeping, cacheHousekeeping, enqueueHousekeeping, updateOfflineCommand, reconcilePmsOfflineAccess } from './pmsOfflineStore.js';
const login = (name) => localStorage.setItem('token', `x.${btoa(JSON.stringify({ sub: name }))}.x`);
beforeEach(() => localStorage.clear());
describe('PMS device queue', () => {
    it('requires explicit opt-in and isolates users and hotels', () => {
        login('anna'); const scope = offlineScope(5);
        expect(() => enqueueHousekeeping(scope, { id: 1 }, {})).toThrow('nicht aktiviert');
        writeOffline(scope, { enabled: true, snapshots: {}, queue: [] });
        cacheHousekeeping(scope, '2026-09-12', [{ id: 1, notes: 'Arbeitsnotiz' }]);
        expect(cachedHousekeeping(scope, '2026-09-12').tasks[0].id).toBe(1);
        expect(cachedHousekeeping(offlineScope(6), '2026-09-12')).toBeNull();
        login('ben'); expect(cachedHousekeeping(offlineScope(5), '2026-09-12')).toBeNull();
        clearPmsOffline(); login('anna'); expect(readOffline(offlineScope(5)).enabled).toBe(false);
    });
    it('keeps one immutable intent per task until acknowledged and preserves conflicts', () => {
        login('anna'); const scope = offlineScope(5); writeOffline(scope, { enabled: true, snapshots: {}, queue: [] });
        const entry = enqueueHousekeeping(scope, { id: 1, roomNumber: '101' }, { version: 7, workStatus: 'DONE' });
        expect(() => enqueueHousekeeping(scope, { id: 1 }, { version: 8 })).toThrow('offene Geräteaktion');
        updateOfflineCommand(scope, entry.commandId, { state: 'CONFLICT', error: 'Geändert' });
        expect(readOffline(scope).queue[0]).toMatchObject({ commandId: entry.commandId, state: 'CONFLICT', update: { version: 7, workStatus: 'DONE' } });
        updateOfflineCommand(scope, entry.commandId, null); expect(readOffline(scope).queue).toHaveLength(0);
    });
    it('does not present an expired snapshot or silently discard a full queue', () => {
        login('anna'); const scope = offlineScope(5); writeOffline(scope, { enabled: true, snapshots: { day: { capturedAt: Date.now() - 86_400_001, tasks: [] } }, queue: [] });
        expect(cachedHousekeeping(scope, 'day')).toBeNull();
        expect(() => writeOffline(scope, { enabled: true, queue: [], oversized: 'x'.repeat(2_000_000) })).toThrow('voll');
        expect(readOffline(scope).enabled).toBe(true);
    });
    it('a stale snapshot write from another tab cannot erase or resurrect individual commands', () => {
        login('anna'); const scope = offlineScope(5); writeOffline(scope, { enabled: true, snapshots: {}, queue: [] });
        const staleTab = readOffline(scope);
        const first = enqueueHousekeeping(scope, { id: 1 }, { version: 0, workStatus: 'DONE' });
        writeOffline(scope, { ...staleTab, snapshots: { today: { capturedAt: Date.now(), tasks: [] } } });
        expect(readOffline(scope).queue.map((entry) => entry.commandId)).toEqual([first.commandId]);
        const otherStaleTab = readOffline(scope);
        const second = enqueueHousekeeping(scope, { id: 2 }, { version: 0, workStatus: 'DONE' });
        updateOfflineCommand(scope, first.commandId, null);
        writeOffline(scope, otherStaleTab);
        expect(readOffline(scope).queue.map((entry) => entry.commandId)).toEqual([second.commandId]);
    });
    it('revokes only the affected user and hotels and prevents stale tabs recreating the cache', () => {
        login('ben'); const ben = offlineScope(5); writeOffline(ben, { enabled: true, snapshots: {}, queue: [] });
        login('anna'); const revoked = offlineScope(5); const retained = offlineScope(6);
        [revoked, retained].forEach((scope) => { writeOffline(scope, { enabled: true, snapshots: {}, queue: [] }); enqueueHousekeeping(scope, { id: 1 }, { version: 0 }); });
        const stale = readOffline(revoked);
        reconcilePmsOfflineAccess({ master: false, properties: [{ propertyId: 6, permissions: { HOUSEKEEPING: 'MANAGE' } }] });
        expect(readOffline(revoked)).toEqual({ enabled: false, snapshots: {}, queue: [] });
        expect(Object.keys(localStorage).some((key) => key.startsWith(`${revoked}:command:`))).toBe(false);
        expect(() => writeOffline(revoked, stale)).toThrow('entzogen');
        expect(readOffline(retained).queue).toHaveLength(1);
        expect(readOffline(ben).enabled).toBe(true);
        reconcilePmsOfflineAccess({ master: false, properties: [{ propertyId: 5, permissions: { HOUSEKEEPING: 'MANAGE' } }, { propertyId: 6, permissions: { HOUSEKEEPING: 'MANAGE' } }] });
        expect(readOffline(revoked).enabled).toBe(false);
        writeOffline(revoked, { enabled: true, snapshots: {}, queue: [] }); expect(readOffline(revoked).queue).toHaveLength(0);
    });
    it('a downgrade to read-only retains permitted snapshots but removes commands and blocks new writes', () => {
        login('anna'); const scope = offlineScope(5); writeOffline(scope, { enabled: true, snapshots: {}, queue: [] });
        cacheHousekeeping(scope, 'today', [{ id: 1 }]); enqueueHousekeeping(scope, { id: 1 }, { version: 0 });
        reconcilePmsOfflineAccess({ master: false, properties: [{ propertyId: 5, permissions: { HOUSEKEEPING: 'VIEW' } }] });
        expect(cachedHousekeeping(scope, 'today').tasks).toEqual([{ id: 1 }]);
        expect(readOffline(scope).queue).toHaveLength(0);
        expect(() => enqueueHousekeeping(scope, { id: 2 }, {})).toThrow('Bearbeitungsberechtigung');
    });
    it('an invalid or missing access response never removes offline data', () => {
        login('anna'); const scope = offlineScope(5); writeOffline(scope, { enabled: true, snapshots: {}, queue: [] }); enqueueHousekeeping(scope, { id: 1 }, {});
        [null, {}, { properties: [] }, { master: false }].forEach((access) => reconcilePmsOfflineAccess(access));
        expect(readOffline(scope).queue).toHaveLength(1);
    });
});
