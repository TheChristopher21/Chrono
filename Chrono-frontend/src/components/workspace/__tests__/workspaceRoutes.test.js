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
    const superadmin = { ...user, roles: ['ROLE_SUPERADMIN'] };

    it('keeps new and classic dashboard areas in separate workspace tabs', () => {
        const classic = resolveWorkspaceRoute('/admin/dashboard?tab=time', superadmin);
        const workspace = resolveWorkspaceRoute('/admin/dashboard-neu?tab=time', superadmin);
        expect(classic.instanceKey).toBe('adminDashboard:time');
        expect(workspace).toMatchObject({ pageKey: 'adminDashboardWorkspace', instanceKey: 'adminDashboardWorkspace:time', title: 'Neues Dashboard · Zeitprüfung' });
        const merged = mergeWorkspaceStates(
            { tabs: [{ ...classic, id: 'classic' }], activeTabId: 'classic' },
            { tabs: [{ ...workspace, id: 'new' }], activeTabId: 'new' },
        );
        expect(merged.tabs).toHaveLength(2);
        expect(merged.activeTabId).toBe('new');
    });

    it('keeps employee identities separate and excludes both profiles from persistence', () => {
        const classic = resolveWorkspaceRoute('/admin/dashboard/mitarbeiter/mirjam', superadmin);
        const workspace = resolveWorkspaceRoute('/admin/dashboard-neu/mitarbeiter/mirjam', superadmin);
        expect(classic).toMatchObject({ instanceKey: 'adminDashboard:employee:mirjam', persist: false });
        expect(workspace).toMatchObject({ instanceKey: 'adminDashboardWorkspace:employee:mirjam', persist: false, title: 'Neues Dashboard · Mitarbeiter · mirjam' });
    });

    it('restores the new employee directory as its own labeled area without adding it to classic tabs', () => {
        const route = resolveWorkspaceRoute('/admin/dashboard-neu?tab=employees', superadmin);
        expect(route).toMatchObject({ instanceKey: 'adminDashboardWorkspace:employees', title: 'Neues Dashboard · Mitarbeitende', persist: true });
        const payload = serializeWorkspacePreference({ activeTabId: 'people', tabs: [{ ...route, id: 'people', pinned: true }] });
        expect(payload.tabs[0].params).toEqual({ tab: 'employees', experience: 'workspace' });
        expect(deserializeWorkspacePreference(payload, superadmin).tabs[0]).toMatchObject({
            url: '/admin/dashboard-neu?tab=employees', instanceKey: route.instanceKey, pinned: true,
        });
        expect(deserializeWorkspacePreference({ tabs: [{ id: 'classic', viewKey: 'adminDashboard', params: { tab: 'employees' } }] }, superadmin).tabs[0].url).toBe('/admin/dashboard');
    });

    it('exposes the new launcher only to superadmins and rejects forged grants in direct links', () => {
        expect(getWorkspaceLaunchItems(superadmin)).toEqual(expect.arrayContaining([
            expect.objectContaining({ pageKey: 'adminDashboardWorkspace', label: 'Neues Dashboard', url: '/admin/dashboard-neu' }),
        ]));
        const restricted = { ...user, pagePermissions: { ...user.pagePermissions, adminDashboardWorkspace: 'MANAGE' } };
        expect(getWorkspaceLaunchItems(restricted).some(item => item.pageKey === 'adminDashboardWorkspace')).toBe(false);
        expect(resolveWorkspaceRoute('/admin/dashboard-neu', restricted)).toBeNull();
        expect(resolveWorkspaceRoute('/admin/dashboard-neu/mitarbeiter/mirjam', restricted)).toBeNull();
    });

    it('roundtrips the new experience through the existing server view key without changing classic tabs', () => {
        const source = { activeTabId: 'new', tabs: [
            { ...resolveWorkspaceRoute('/admin/dashboard?tab=calendar', superadmin), id: 'classic', pinned: false },
            { ...resolveWorkspaceRoute('/admin/dashboard-neu?tab=time', superadmin), id: 'new', pinned: true },
        ] };
        const payload = serializeWorkspacePreference(source);
        expect(payload).toEqual({ activeTabId: 'new', tabs: [
            { id: 'classic', viewKey: 'adminDashboard', params: { tab: 'calendar' }, pinned: false },
            { id: 'new', viewKey: 'adminDashboard', params: { tab: 'time', experience: 'workspace' }, pinned: true },
        ] });
        const restored = deserializeWorkspacePreference(payload, superadmin);
        expect(restored.tabs.map(tab => tab.url)).toEqual(['/admin/dashboard?tab=calendar', '/admin/dashboard-neu?tab=time']);
        expect(restored.activeTabId).toBe('new');
        expect(deserializeWorkspacePreference(serializeWorkspacePreference({ tabs: [
            { ...resolveWorkspaceRoute('/admin/dashboard-neu', superadmin), id: 'home', pinned: false },
        ] }), superadmin).tabs[0].url).toBe('/admin/dashboard-neu');
    });

    it('removes new dashboard tabs from local and remote restoration when the superadmin role is lost', () => {
        const source = { activeTabId: 'new', tabs: [
            { ...resolveWorkspaceRoute('/admin/dashboard', superadmin), id: 'classic', pinned: false },
            { ...resolveWorkspaceRoute('/admin/dashboard-neu', superadmin), id: 'new', pinned: true },
        ] };
        const restricted = { ...user, pagePermissions: { ...user.pagePermissions, adminDashboardWorkspace: 'MANAGE' } };
        const local = sanitizeStoredWorkspace(serializeWorkspace(source), restricted);
        const remote = deserializeWorkspacePreference(serializeWorkspacePreference(source), restricted);
        for (const restored of [local, remote]) {
            expect(restored.tabs.map(tab => tab.id)).toEqual(['classic']);
            expect(restored.activeTabId).toBe('classic');
        }
    });

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
