import {
    PAGE_CATALOG,
    ACCESS_VIEW,
    hasFeatureAccess,
    hasPageAccess,
    translatePageDefinition,
} from '../../utils/pageAccess.js';
import { PMS_SECTIONS } from '../../pages/Pms/pmsNavigation.js';

export const MAX_WORKSPACE_TABS = 12;

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

    const employeeMatch = pathname.match(/^\/admin\/dashboard\/mitarbeiter\/([^/]+)$/);
    if (employeeMatch) {
        const username = safeDecode(employeeMatch[1]);
        instanceKey = `adminDashboard:employee:${username}`;
        title = `${typeof t === 'function' ? t('workspaceTabs.employee', 'Mitarbeiter') : 'Mitarbeiter'} · ${username}`;
        // Usernames are deliberately not restored from browser storage.
        persist = false;
    } else if (page.key === 'adminDashboard') {
        const tabKey = searchParams.get('tab') || 'overview';
        instanceKey = `adminDashboard:${tabKey}`;
        title = tabKey === 'overview'
            ? translatedPage.label
            : `${translatedPage.label} · ${ADMIN_TAB_LABELS[tabKey] ?? tabKey}`;
    }

    if (page.key === 'pms') {
        const section = pmsSectionDefinition(searchParams.get('section') || 'overview');
        instanceKey = `pms:${section.key}`;
        title = section.key === 'overview' ? translatedPage.label : `PMS · ${section.label}`;
    }

    return {
        pageKey: page.key,
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

export const getWorkspaceLaunchItems = (user, t) => {
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

    return baseItems;
};

export const sanitizeStoredWorkspace = (candidate, user, t) => {
    const rawTabs = Array.isArray(candidate?.tabs) ? candidate.tabs : [];
    const seen = new Set();
    const tabs = [];

    rawTabs.slice(0, MAX_WORKSPACE_TABS * 2).forEach((stored) => {
        const route = resolveWorkspaceRoute(stored?.url, user, t);
        if (!route || route.persist === false || seen.has(route.instanceKey)) return;
        seen.add(route.instanceKey);
        tabs.push({
            ...route,
            id: typeof stored.id === 'string' && stored.id ? stored.id : route.instanceKey,
            pinned: Boolean(stored.pinned),
            scrollY: Number.isFinite(stored.scrollY) ? Math.max(0, stored.scrollY) : 0,
            lastVisitedAt: Number.isFinite(stored.lastVisitedAt) ? stored.lastVisitedAt : Date.now(),
        });
    });

    return {
        tabs: tabs.slice(0, MAX_WORKSPACE_TABS),
        activeTabId: tabs.some((tab) => tab.id === candidate?.activeTabId) ? candidate.activeTabId : tabs[0]?.id ?? null,
        recentlyClosed: [],
    };
};

export const serializeWorkspace = (state) => ({
    version: 2,
    activeTabId: state.activeTabId,
    tabs: state.tabs
        .filter((tab) => tab.persist !== false)
        .slice(0, MAX_WORKSPACE_TABS)
        .map(({ id, url, pinned, scrollY, lastVisitedAt }) => ({ id, url, pinned, scrollY, lastVisitedAt })),
});

const getServerTabParams = (tab) => {
    const fallbackOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://chrono.local';
    const parsed = new URL(tab.url || '/', fallbackOrigin);
    if (tab.pageKey === 'adminDashboard') {
        const requestedTab = parsed.searchParams.get('tab');
        return requestedTab && Object.hasOwn(ADMIN_TAB_LABELS, requestedTab)
            ? { tab: requestedTab }
            : {};
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
    const tabs = (state?.tabs || [])
        .filter((tab) => tab.persist !== false && tab.pageKey)
        .slice(0, MAX_WORKSPACE_TABS)
        .map((tab, index) => {
            const id = createUniqueServerTabId(tab.id, index, usedIds);
            idMap.set(tab.id, id);
            const params = getServerTabParams(tab);
            return {
                id,
                viewKey: tab.pageKey,
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
    const page = PAGE_CATALOG.find((candidate) => candidate.key === serverTab?.viewKey);
    if (!canUsePage(user, page)) return null;
    if (page.key === 'dashboard') {
        return user?.isPercentage ? '/percentage-punch' : page.path;
    }
    if (page.key === 'adminDashboard') {
        const requestedTab = serverTab?.params?.tab;
        const tabKey = requestedTab && Object.hasOwn(ADMIN_TAB_LABELS, requestedTab)
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
    const seenInstances = new Set();
    const seenIds = new Set();
    (Array.isArray(payload?.tabs) ? payload.tabs : [])
        .slice(0, MAX_WORKSPACE_TABS)
        .forEach((serverTab, index) => {
            const url = getServerTabUrl(serverTab, user);
            const route = url ? resolveWorkspaceRoute(url, user, t) : null;
            if (!route || route.persist === false || seenInstances.has(route.instanceKey)) return;
            const id = createUniqueServerTabId(serverTab?.id, index, seenIds);
            seenInstances.add(route.instanceKey);
            tabs.push({
                ...route,
                id,
                pinned: Boolean(serverTab?.pinned),
                scrollY: 0,
                lastVisitedAt: Date.now() - tabs.length,
            });
        });

    const requestedActiveId = typeof payload?.activeTabId === 'string' ? payload.activeTabId : null;
    return {
        tabs,
        activeTabId: tabs.some((tab) => tab.id === requestedActiveId)
            ? requestedActiveId
            : tabs[0]?.id ?? null,
        recentlyClosed: [],
    };
};

export const mergeWorkspaceStates = (remoteState, localState, { preferLocalPinned = false } = {}) => {
    const remoteTabs = Array.isArray(remoteState?.tabs) ? remoteState.tabs : [];
    const localTabs = Array.isArray(localState?.tabs) ? localState.tabs : [];
    const tabs = [...remoteTabs];
    let activeTabId = remoteState?.activeTabId ?? null;

    localTabs.forEach((localTab) => {
        const matchingIndex = tabs.findIndex((tab) => tab.instanceKey === localTab.instanceKey);
        if (matchingIndex >= 0) {
            const remoteTab = tabs[matchingIndex];
            tabs[matchingIndex] = {
                ...remoteTab,
                ...localTab,
                id: remoteTab.id,
                pinned: preferLocalPinned ? localTab.pinned : (remoteTab.pinned || localTab.pinned),
            };
            if (localState?.activeTabId === localTab.id) activeTabId = remoteTab.id;
            return;
        }
        if (tabs.length >= MAX_WORKSPACE_TABS && tabs.every((tab) => tab.pinned)) return;
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
    if (tabs.length <= MAX_WORKSPACE_TABS) return tabs;
    const byOldestVisit = (left, right) => left.lastVisitedAt - right.lastVisitedAt;
    const removable = [
        ...tabs.filter((tab) => !tab.pinned && tab.id !== activeTabId).sort(byOldestVisit),
        ...tabs.filter((tab) => !tab.pinned && tab.id === activeTabId).sort(byOldestVisit),
    ];
    const removeIds = new Set(removable.slice(0, tabs.length - MAX_WORKSPACE_TABS).map((tab) => tab.id));
    return tabs.filter((tab) => !removeIds.has(tab.id));
};

export const canOpenWorkspaceTab = (tabs = []) => (
    tabs.length < MAX_WORKSPACE_TABS || tabs.some((tab) => !tab.pinned)
);
