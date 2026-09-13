import { describe, expect, it } from 'vitest';
import {
    canOpenWorkspaceTab,
    deserializeWorkspacePreference,
    getWorkspaceLaunchItems,
    getWorkspaceScope,
    mergeWorkspaceStates,
    resolveWorkspaceRoute,
    sanitizeStoredWorkspace,
    serializeWorkspace,
    serializeWorkspacePreference,
    withWorkspaceTabLimit,
} from '../workspaceRoutes.js';

const user = {
    id: 7,
    username: 'anna',
    roles: ['ROLE_ADMIN'],
    companyId: 12,
    companyFeatureKeys: ['projects', 'analytics', 'pms'],
    pagePermissions: {
        dashboard: 'MANAGE',
        adminDashboard: 'MANAGE',
        adminUsers: 'MANAGE',
        adminProjects: 'VIEW',
        pms: 'MANAGE',
    },
};

describe('workspaceRoutes', () => {
    it('resolves admin and PMS subareas to stable tab descriptors', () => {
        expect(resolveWorkspaceRoute('/admin/dashboard?tab=time', user)?.title).toContain('Zeitprüfung');
        expect(resolveWorkspaceRoute('/admin/dashboard?tab=time', user)?.instanceKey).toBe('adminDashboard:time');
        expect(resolveWorkspaceRoute('/admin/projects', user)?.icon).toBe('PR');
        expect(resolveWorkspaceRoute('/pms?section=housekeeping', user)?.instanceKey).toBe('pms:housekeeping');
    });

    it('canonicalizes the percentage dashboard and rejects forbidden routes', () => {
        const percentageUser = { ...user, isPercentage: true };
        expect(resolveWorkspaceRoute('/percentage-punch', percentageUser)?.instanceKey).toBe('dashboard');
        expect(resolveWorkspaceRoute('/admin/accounting', user)).toBeNull();
        expect(resolveWorkspaceRoute('/login', user)).toBeNull();
        expect(resolveWorkspaceRoute('https://external.example/dashboard', user)).toBeNull();
    });

    it('canonicalizes the legacy Supply Chain alias into the workspace route', () => {
        const supplyChainUser = {
            ...user,
            companyFeatureKeys: [...user.companyFeatureKeys, 'supplyChain'],
            pagePermissions: { ...user.pagePermissions, supplyChain: 'VIEW' },
        };
        const route = resolveWorkspaceRoute('/admin/supply-chain', supplyChainUser);
        expect(route?.pageKey).toBe('supplyChain');
        expect(route?.url).toBe('/workspace/supply-chain');
    });

    it('filters restored tabs against permissions and strips sensitive employee tabs', () => {
        const restored = sanitizeStoredWorkspace({
            tabs: [
                { id: 'dashboard', url: '/dashboard' },
                { id: 'accounting', url: '/admin/accounting' },
                { id: 'employee', url: '/admin/dashboard/mitarbeiter/bob' },
            ],
            activeTabId: 'dashboard',
        }, user);
        expect(restored.tabs.map((tab) => tab.pageKey)).toEqual(['dashboard']);
    });

    it('only exposes launch items that the user may view', () => {
        const items = getWorkspaceLaunchItems(user);
        expect(items.some((item) => item.pageKey === 'adminProjects')).toBe(true);
        expect(items.some((item) => item.pageKey === 'adminAccounting')).toBe(false);
    });

    it('serializes only restorable fields', () => {
        const payload = serializeWorkspace({
            activeTabId: 'one',
            tabs: [{ id: 'one', url: '/dashboard', pinned: true, persist: true, title: 'Secret', scrollY: 20, lastVisitedAt: 1 }],
        });
        expect(payload.tabs[0]).toEqual({ id: 'one', url: '/dashboard', pinned: true, scrollY: 20, lastVisitedAt: 1 });
        expect(payload.tabs[0].title).toBeUndefined();
    });

    it('syncs tabs through permission-checkable view keys without URLs or titles', () => {
        const dashboard = resolveWorkspaceRoute('/admin/dashboard?tab=time', user);
        const pms = resolveWorkspaceRoute('/pms?section=housekeeping', user);
        const payload = serializeWorkspacePreference({
            activeTabId: 'admin:one',
            tabs: [
                { ...dashboard, id: 'admin:one', pinned: true, title: 'Nicht übertragen' },
                { ...pms, id: 'pms:two', pinned: false },
            ],
        });

        expect(payload).toEqual({
            tabs: [
                { id: 'admin:one', viewKey: 'adminDashboard', params: { tab: 'time' }, pinned: true },
                { id: 'pms:two', viewKey: 'pms', params: { section: 'housekeeping' }, pinned: false },
            ],
            activeTabId: 'admin:one',
        });
        expect(JSON.stringify(payload)).not.toContain('/admin/dashboard');
        expect(JSON.stringify(payload)).not.toContain('Nicht übertragen');

        const restored = deserializeWorkspacePreference(payload, user);
        expect(restored.tabs.map((tab) => tab.url)).toEqual([
            '/admin/dashboard?tab=time',
            '/pms?section=housekeeping',
        ]);
        expect(restored.activeTabId).toBe('admin:one');
    });

    it('drops remotely restored tabs when feature access was revoked', () => {
        const withoutPms = { ...user, companyFeatureKeys: ['projects', 'analytics'] };
        const restored = deserializeWorkspacePreference({
            tabs: [{ id: 'pms:one', viewKey: 'pms', params: { section: 'folios' } }],
            activeTabId: 'pms:one',
        }, withoutPms);
        expect(restored.tabs).toEqual([]);
    });

    it('merges the current local route into remotely restored tabs without duplicates', () => {
        const remoteTab = { ...resolveWorkspaceRoute('/dashboard', user), id: 'remote', pinned: true };
        const localTab = { ...resolveWorkspaceRoute('/dashboard', user), id: 'local', pinned: false };
        const merged = mergeWorkspaceStates(
            { tabs: [remoteTab], activeTabId: 'remote', recentlyClosed: [] },
            { tabs: [localTab], activeTabId: 'local', recentlyClosed: [] },
        );
        expect(merged.tabs).toHaveLength(1);
        expect(merged.tabs[0].id).toBe('local');
        expect(merged.tabs[0].pinned).toBe(true);
        expect(merged.activeTabId).toBe('local');
    });

    it('restores deliberately duplicated URLs from session and server storage by ID', () => {
        const source = { activeTabId: 'second', tabs: [
            { ...resolveWorkspaceRoute('/pms?section=reservations', user), id: 'first', pinned: false },
            { ...resolveWorkspaceRoute('/pms?section=reservations', user), id: 'second', pinned: true },
        ] };
        const local = sanitizeStoredWorkspace(serializeWorkspace(source), user);
        const remote = deserializeWorkspacePreference(serializeWorkspacePreference(source), user);
        for (const restored of [local, remote]) {
            expect(restored.tabs.map((tab) => tab.id)).toEqual(['first', 'second']);
            expect(restored.activeTabId).toBe('second');
        }
        expect(mergeWorkspaceStates(remote, local).tabs).toHaveLength(2);
    });

    it('pairs remote instances once when merging independently restored duplicate tabs', () => {
        const route = resolveWorkspaceRoute('/pms', user);
        const merged = mergeWorkspaceStates(
            { tabs: [{ ...route, id: 'remote', pinned: true }] },
            { tabs: [{ ...route, id: 'first' }, { ...route, id: 'second' }], activeTabId: 'second' },
        );
        expect(merged.tabs.map((tab) => tab.id)).toEqual(['first', 'second']);
        expect(merged.activeTabId).toBe('second');
    });

    it('filters launch items and enforces the twelve-tab capacity independently per workspace', () => {
        expect(getWorkspaceLaunchItems(user, undefined, 'pms').every((tab) => tab.pageKey === 'pms')).toBe(true);
        expect(getWorkspaceLaunchItems(user, undefined, 'chrono').every((tab) => tab.pageKey !== 'pms')).toBe(true);
        expect(getWorkspaceScope('/pms?section=rooms')).toBe('pms');
        expect(getWorkspaceScope('/pms-other')).toBe('chrono');
        const pinned = Array.from({ length: 12 }, (_, index) => ({
            ...resolveWorkspaceRoute('/dashboard', user), id: `chrono-${index}`, pinned: true,
        }));
        expect(canOpenWorkspaceTab(pinned, 'chrono')).toBe(false);
        expect(canOpenWorkspaceTab(pinned, 'pms')).toBe(true);
        const combined = [...pinned, ...Array.from({ length: 12 }, (_, index) => ({
            ...resolveWorkspaceRoute('/pms', user), id: `pms-${index}`, pinned: true,
        }))];
        expect(withWorkspaceTabLimit(combined, 'pms-11')).toHaveLength(24);
        expect(serializeWorkspacePreference({ tabs: combined, activeTabId: 'pms-11' }).tabs).toHaveLength(24);
        expect(sanitizeStoredWorkspace(serializeWorkspace({ tabs: combined }), user).tabs).toHaveLength(24);
    });

    it('never evicts pinned tabs when the twelve-tab limit is reached', () => {
        const pinnedTabs = Array.from({ length: 12 }, (_, index) => ({
            id: `pinned-${index}`,
            pinned: true,
            lastVisitedAt: index,
        }));
        expect(canOpenWorkspaceTab(pinnedTabs)).toBe(false);

        const limited = withWorkspaceTabLimit([
            ...pinnedTabs,
            { id: 'new-tab', pinned: false, lastVisitedAt: 20 },
        ], 'new-tab');
        expect(limited).toEqual(pinnedTabs);
    });
});
