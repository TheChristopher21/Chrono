import {
    PAGE_CATALOG,
    ACCESS_VIEW,
    hasFeatureAccess,
    hasPageAccess,
    translatePageDefinition,
} from '../../utils/pageAccess.js';
import { PMS_SECTIONS } from '../../pages/Pms/pmsNavigation.js';

export const MAX_WORKSPACE_TABS = 12;
export const WORKSPACE_SCOPES = ['chrono', 'pms'];
export const WORKSPACE_TAB_STATE_KEY = 'workspaceTabId';

export const getWorkspaceScope = (tabOrUrl) => {
    if (typeof tabOrUrl === 'object' && tabOrUrl !== null) {
        return tabOrUrl.pageKey === 'pms' || tabOrUrl.scope === 'pms' ? 'pms' : 'chrono';
    }
    return /^\/pms(?:[/?#]|$)/.test(String(tabOrUrl || '')) ? 'pms' : 'chrono';
};

const EXCLUDED_PAGE_KEYS = new Set(['printReport']);
const EXCLUDED_PATH_PREFIXES = [
    '/login',
    '/register',
    '/guest-registration/',
    '/guest-check-in/',
    '/book/',
    '/print-report',
];

const ADMIN_TAB_LABELS = {
    overview: 'Übersicht',
    time: 'Zeitprüfung',
    requests: 'Anträge',
    calendar: 'Kalender',
    modules: 'Module',
};
const WORKSPACE_ADMIN_TAB_LABELS = { ...ADMIN_TAB_LABELS, employees: 'Mitarbeitende' };
const getAdminTabLabels = (pageKey) => pageKey === 'adminDashboardWorkspace'
    ? WORKSPACE_ADMIN_TAB_LABELS : ADMIN_TAB_LABELS;

const SERVER_TAB_ID_PATTERN = /[^A-Za-z0-9._:-]/g;
const WORKSPACE_PATH_ALIASES = {
    '/admin/supply-chain': '/workspace/supply-chain',
};

const safeDecode = (value) => {
    try {
        return decodeURIComponent(value);
    } catch {
        return value;
    }
};

export const normalizeWorkspaceUrl = (urlLike) => {
    const fallbackOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://chrono.local';
    const parsed = new URL(urlLike || '/', fallbackOrigin);
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
};

export const getWorkspaceIdentity = (user) => {
    if (!user) return null;
    const userId = user.id ?? user.username ?? user.email;
    const companyId = user.company?.id ?? user.companyId ?? user.company?.companyId ?? 'global';
    return userId ? `${companyId}:${userId}` : null;
};

export const getWorkspaceStorageKey = (user) => {
    const identity = getWorkspaceIdentity(user);
    return identity ? `chrono.workspaceTabs.v2.${identity}` : null;
};

const canUsePage = (user, page) => Boolean(
    user
    && page
    && !EXCLUDED_PAGE_KEYS.has(page.key)
    && hasFeatureAccess(user, page.featureKey)
    && hasPageAccess(user, page.key, ACCESS_VIEW)
);

const findPageForPath = (pathname) => {
    if (pathname === '/percentage-punch') {
        return PAGE_CATALOG.find((page) => page.key === 'dashboard') ?? null;
    }

    return [...PAGE_CATALOG]
        .sort((left, right) => right.path.length - left.path.length)
        .find((page) => pathname === page.path || pathname.startsWith(`${page.path}/`)) ?? null;
};

const pmsSectionDefinition = (sectionKey) => (
    PMS_SECTIONS.find((section) => section.key === sectionKey) ?? PMS_SECTIONS[0]
);

export const resolveWorkspaceRoute = (urlLike, user, t) => {
    const fallbackOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://chrono.local';
    const parsed = new URL(urlLike || '/', fallbackOrigin);
    if (parsed.origin !== new URL(fallbackOrigin).origin) return null;
    const requestedPathname = parsed.pathname;
    const pathname = WORKSPACE_PATH_ALIASES[requestedPathname] ?? requestedPathname;
    const { searchParams } = parsed;

    if (pathname === '/' || EXCLUDED_PATH_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(prefix))) {
        return null;
    }

    const page = findPageForPath(pathname);
    if (!canUsePage(user, page)) return null;

    const translatedPage = translatePageDefinition(page, t);
    let instanceKey = page.key;
    let title = translatedPage.label;
    let persist = true;

    if (page.key === 'dashboard') {
        instanceKey = 'dashboard';
        title = user?.isPercentage
            ? (typeof t === 'function' ? t('percentageDashboard.title', 'Prozent-Dashboard') : 'Prozent-Dashboard')
            : translatedPage.label;
    }

    const employeeMatch = pathname.match(/^\/admin\/dashboard(?:-neu)?\/mitarbeiter\/([^/]+)$/);
    if (employeeMatch) {
        const username = safeDecode(employeeMatch[1]);
        instanceKey = `${page.key}:employee:${username}`;
        title = `${typeof t === 'function' ? t('workspaceTabs.employee', 'Mitarbeiter') : 'Mitarbeiter'} · ${username}`;
        if (page.key === 'adminDashboardWorkspace') title = `${translatedPage.label} · ${title}`;
        // Usernames are deliberately not restored from browser storage.
        persist = false;
    } else if (page.key === 'adminDashboard' || page.key === 'adminDashboardWorkspace') {
        const tabKey = searchParams.get('tab') || 'overview';
        instanceKey = `${page.key}:${tabKey}`;
        title = tabKey === 'overview'
            ? translatedPage.label
            : `${translatedPage.label} · ${getAdminTabLabels(page.key)[tabKey] ?? tabKey}`;
    }

    if (page.key === 'pms') {
        const section = pmsSectionDefinition(searchParams.get('section') || 'overview');
        instanceKey = `pms:${section.key}`;
        title = section.key === 'overview' ? translatedPage.label : `PMS · ${section.label}`;
    }

    return {
        pageKey: page.key,
        scope: page.key === 'pms' ? 'pms' : 'chrono',
        instanceKey,
        pathname,
        search: parsed.search,
        hash: parsed.hash,
        url: `${pathname}${parsed.search}${parsed.hash}`,
        title,
        icon: translatedPage.icon || page.icon || title.slice(0, 2).toUpperCase(),
        persist,
        featureKey: page.featureKey ?? null,
    };
};

export const getWorkspaceLaunchItems = (user, t, scope) => {
    const baseItems = PAGE_CATALOG
        .filter((page) => canUsePage(user, page))
        .filter((page) => !EXCLUDED_PAGE_KEYS.has(page.key))
        .sort((left, right) => left.order - right.order)
        .map((page) => {
            const translated = translatePageDefinition(page, t);
            const path = page.key === 'dashboard' && user?.isPercentage ? '/percentage-punch' : page.path;
            return {
                key: page.key,
                pageKey: page.key,
                group: translated.group,
                label: translated.label,
                description: translated.description,
                icon: translated.icon,
                url: path,
            };
        });

    if (baseItems.some((item) => item.pageKey === 'adminDashboard')) {
        Object.entries(ADMIN_TAB_LABELS)
            .filter(([key]) => key !== 'overview')
            .forEach(([key, label]) => baseItems.push({
                key: `adminDashboard:${key}`,
                pageKey: 'adminDashboard',
                group: typeof t === 'function' ? t('pageCatalog.groups.Admin', 'Admin') : 'Admin',
                label: `Admin · ${label}`,
                description: typeof t === 'function' ? t('workspaceTabs.openAdminArea', 'Admin-Bereich in einem Arbeits-Tab öffnen.') : 'Admin-Bereich öffnen.',
                icon: 'AD',
                url: `/admin/dashboard?tab=${key}`,
            }));
    }

    if (baseItems.some((item) => item.pageKey === 'pms')) {
        PMS_SECTIONS
            .filter((section) => section.key !== 'overview')
            .forEach((section) => baseItems.push({
                key: `pms:${section.key}`,
                pageKey: 'pms',
                group: typeof t === 'function' ? t('workspaceTabs.pmsGroup', 'PMS-Arbeitsbereiche') : 'PMS-Arbeitsbereiche',
                label: section.label,
                description: typeof t === 'function' ? t('workspaceTabs.openPmsArea', 'PMS-Bereich in einem Arbeits-Tab öffnen.') : 'PMS-Bereich öffnen.',
                icon: section.code,
                url: `/pms?section=${encodeURIComponent(section.key)}`,
            }));
    }

    return scope ? baseItems.filter((item) => getWorkspaceScope(item) === scope) : baseItems;
};

export const sanitizeStoredWorkspace = (candidate, user, t) => {
    const rawTabs = Array.isArray(candidate?.tabs) ? candidate.tabs : [];
    const seen = new Set();
    const tabs = [];

    rawTabs.slice(0, MAX_WORKSPACE_TABS * WORKSPACE_SCOPES.length * 2).forEach((stored, index) => {
        const route = resolveWorkspaceRoute(stored?.url, user, t);
        const id = typeof stored?.id === 'string' && stored.id ? stored.id : `tab:${index + 1}`;
        if (!route || route.persist === false || seen.has(id)) return;
        seen.add(id);
        tabs.push({
            ...route,
            id,
            pinned: Boolean(stored.pinned),
            scrollY: Number.isFinite(stored.scrollY) ? Math.max(0, stored.scrollY) : 0,
            lastVisitedAt: Number.isFinite(stored.lastVisitedAt) ? stored.lastVisitedAt : Date.now(),
        });
    });

    const limitedTabs = withWorkspaceTabLimit(tabs, candidate?.activeTabId);
    return {
        tabs: limitedTabs,
        activeTabId: limitedTabs.some((tab) => tab.id === candidate?.activeTabId) ? candidate.activeTabId : limitedTabs[0]?.id ?? null,
        recentlyClosed: [],
    };
};

export const serializeWorkspace = (state) => ({
    version: 2,
    activeTabId: state.activeTabId,
    tabs: withWorkspaceTabLimit(state.tabs, state.activeTabId)
        .filter((tab) => tab.persist !== false)
        .map(({ id, url, pinned, scrollY, lastVisitedAt }) => ({ id, url, pinned, scrollY, lastVisitedAt })),
});

const getServerTabParams = (tab) => {
    const fallbackOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://chrono.local';
    const parsed = new URL(tab.url || '/', fallbackOrigin);
    if (tab.pageKey === 'adminDashboard' || tab.pageKey === 'adminDashboardWorkspace') {
        const requestedTab = parsed.searchParams.get('tab');
        const params = requestedTab && Object.hasOwn(getAdminTabLabels(tab.pageKey), requestedTab)
            ? { tab: requestedTab }
            : {};
        // Reuse the server's known view key while keeping the new route distinct.
        return tab.pageKey === 'adminDashboardWorkspace' ? { ...params, experience: 'workspace' } : params;
    }
    if (tab.pageKey === 'pms') {
        const section = pmsSectionDefinition(parsed.searchParams.get('section') || 'overview');
        return section.key === 'overview' ? {} : { section: section.key };
    }
    return {};
};

const createUniqueServerTabId = (rawId, index, usedIds) => {
    const fallback = `tab:${index + 1}`;
    const base = String(rawId || fallback)
        .replace(SERVER_TAB_ID_PATTERN, '_')
        .slice(0, 72) || fallback;
    let candidate = base;
    let suffix = 1;
    while (usedIds.has(candidate)) {
        candidate = `${base.slice(0, 70)}:${suffix}`;
        suffix += 1;
    }
    usedIds.add(candidate);
    return candidate;
};

/**
 * Converts local route tabs into the server's strict, permission-checkable shape.
 * URLs and translated titles never leave the browser.
 */
export const serializeWorkspacePreference = (state) => {
    const usedIds = new Set();
    const idMap = new Map();
    const tabs = withWorkspaceTabLimit(state?.tabs || [], state?.activeTabId)
        .filter((tab) => tab.persist !== false && tab.pageKey)
        .map((tab, index) => {
            const id = createUniqueServerTabId(tab.id, index, usedIds);
            idMap.set(tab.id, id);
            const params = getServerTabParams(tab);
            return {
                id,
                viewKey: tab.pageKey === 'adminDashboardWorkspace' ? 'adminDashboard' : tab.pageKey,
                ...(Object.keys(params).length ? { params } : {}),
                pinned: Boolean(tab.pinned),
            };
        });

    const activeTabId = idMap.get(state?.activeTabId);
    return {
        tabs,
        ...(activeTabId ? { activeTabId } : {}),
    };
};

const getServerTabUrl = (serverTab, user) => {
    const pageKey = serverTab?.viewKey === 'adminDashboard' && serverTab?.params?.experience === 'workspace'
        ? 'adminDashboardWorkspace'
        : serverTab?.viewKey;
    const page = PAGE_CATALOG.find((candidate) => candidate.key === pageKey);
    if (!canUsePage(user, page)) return null;
    if (page.key === 'dashboard') {
        return user?.isPercentage ? '/percentage-punch' : page.path;
    }
    if (page.key === 'adminDashboard' || page.key === 'adminDashboardWorkspace') {
        const requestedTab = serverTab?.params?.tab;
        const tabKey = requestedTab && Object.hasOwn(getAdminTabLabels(page.key), requestedTab)
            ? requestedTab
            : 'overview';
        return tabKey === 'overview' ? page.path : `${page.path}?tab=${encodeURIComponent(tabKey)}`;
    }
    if (page.key === 'pms') {
        const section = pmsSectionDefinition(serverTab?.params?.section || 'overview');
        return section.key === 'overview' ? page.path : `${page.path}?section=${encodeURIComponent(section.key)}`;
    }
    return page.path;
};

export const deserializeWorkspacePreference = (payload, user, t) => {
    const tabs = [];
    const seenIds = new Set();
    (Array.isArray(payload?.tabs) ? payload.tabs : [])
        .slice(0, MAX_WORKSPACE_TABS * WORKSPACE_SCOPES.length)
        .forEach((serverTab, index) => {
            const url = getServerTabUrl(serverTab, user);
            const route = url ? resolveWorkspaceRoute(url, user, t) : null;
            if (!route || route.persist === false) return;
            const id = createUniqueServerTabId(serverTab?.id, index, seenIds);
            tabs.push({
                ...route,
                id,
                pinned: Boolean(serverTab?.pinned),
                scrollY: 0,
                lastVisitedAt: Date.now() - tabs.length,
            });
        });

    const requestedActiveId = typeof payload?.activeTabId === 'string' ? payload.activeTabId : null;
    const limitedTabs = withWorkspaceTabLimit(tabs, requestedActiveId);
    return {
        tabs: limitedTabs,
        activeTabId: limitedTabs.some((tab) => tab.id === requestedActiveId)
            ? requestedActiveId
            : limitedTabs[0]?.id ?? null,
        recentlyClosed: [],
    };
};

export const mergeWorkspaceStates = (remoteState, localState, { preferLocalPinned = false } = {}) => {
    const remoteTabs = Array.isArray(remoteState?.tabs) ? remoteState.tabs : [];
    const localTabs = Array.isArray(localState?.tabs) ? localState.tabs : [];
    const tabs = [...remoteTabs];
    let activeTabId = remoteState?.activeTabId ?? null;
    const localIds = new Set(localTabs.map((tab) => tab.id));
    const consumedRemoteIds = new Set();

    localTabs.forEach((localTab) => {
        let matchingIndex = tabs.findIndex((tab) => tab.id === localTab.id);
        if (matchingIndex < 0) {
            // Match each remotely restored instance at most once. Deliberate duplicate
            // routes remain separate, while a just-opened deep link joins its saved tab.
            matchingIndex = tabs.findIndex((tab) => tab.instanceKey === localTab.instanceKey
                && !localIds.has(tab.id) && !consumedRemoteIds.has(tab.id));
        }
        if (matchingIndex >= 0) {
            const remoteTab = tabs[matchingIndex];
            consumedRemoteIds.add(remoteTab.id);
            tabs[matchingIndex] = {
                ...remoteTab,
                ...localTab,
                // Keep the browser-history identity of tabs already in this session.
                id: localTab.id,
                pinned: preferLocalPinned ? localTab.pinned : (remoteTab.pinned || localTab.pinned),
            };
            if (localState?.activeTabId === localTab.id) activeTabId = localTab.id;
            return;
        }
        if (!canOpenWorkspaceTab(tabs, getWorkspaceScope(localTab))) return;
        tabs.push(localTab);
        if (localState?.activeTabId === localTab.id) activeTabId = localTab.id;
    });

    const limitedTabs = withWorkspaceTabLimit(tabs, activeTabId);
    if (!limitedTabs.some((tab) => tab.id === activeTabId)) {
        activeTabId = limitedTabs[0]?.id ?? null;
    }
    return {
        tabs: limitedTabs,
        activeTabId,
        recentlyClosed: localState?.recentlyClosed || [],
    };
};

export const withWorkspaceTabLimit = (tabs, activeTabId) => {
    const removeIds = new Set();
    const byOldestVisit = (left, right) => (left.lastVisitedAt || 0) - (right.lastVisitedAt || 0);
    WORKSPACE_SCOPES.forEach((scope) => {
        const scoped = tabs.filter((tab) => getWorkspaceScope(tab) === scope);
        if (scoped.length <= MAX_WORKSPACE_TABS) return;
        const removable = [
            ...scoped.filter((tab) => !tab.pinned && tab.id !== activeTabId).sort(byOldestVisit),
            ...scoped.filter((tab) => !tab.pinned && tab.id === activeTabId).sort(byOldestVisit),
        ];
        removable.slice(0, scoped.length - MAX_WORKSPACE_TABS).forEach((tab) => removeIds.add(tab.id));
        // Malformed/restored payloads can exceed the cap with pinned tabs alone.
        const remaining = scoped.filter((tab) => !removeIds.has(tab.id));
        remaining.filter((tab) => tab.id !== activeTabId).slice(MAX_WORKSPACE_TABS - (remaining.some((tab) => tab.id === activeTabId) ? 1 : 0))
            .forEach((tab) => removeIds.add(tab.id));
    });
    return tabs.filter((tab) => !removeIds.has(tab.id));
};

export const canOpenWorkspaceTab = (tabs = [], scope) => {
    const scoped = scope ? tabs.filter((tab) => getWorkspaceScope(tab) === scope) : tabs;
    return scoped.length < MAX_WORKSPACE_TABS || scoped.some((tab) => !tab.pinned);
};
