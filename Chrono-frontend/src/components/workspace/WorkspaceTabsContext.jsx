import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { useTranslation } from '../../context/LanguageContext.jsx';
import api from '../../utils/api.js';
import {
    canOpenWorkspaceTab,
    deserializeWorkspacePreference,
    getWorkspaceIdentity,
    getWorkspaceLaunchItems,
    getWorkspaceScope,
    getWorkspaceStorageKey,
    mergeWorkspaceStates,
    normalizeWorkspaceUrl,
    resolveWorkspaceRoute,
    sanitizeStoredWorkspace,
    serializeWorkspace,
    serializeWorkspacePreference,
    withWorkspaceTabLimit,
    WORKSPACE_TAB_STATE_KEY,
} from './workspaceRoutes.js';

const WorkspaceTabsContext = createContext(null);

const emptyState = { tabs: [], activeTabId: null, recentlyClosed: [] };

const createTabId = (route) => {
    const randomPart = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return `${route.instanceKey}:${randomPart}`;
};

const readStoredState = (user, t) => {
    const storageKey = getWorkspaceStorageKey(user);
    if (!storageKey || typeof window === 'undefined') return emptyState;
    try {
        const raw = window.sessionStorage.getItem(storageKey);
        return raw ? sanitizeStoredWorkspace(JSON.parse(raw), user, t) : emptyState;
    } catch {
        return emptyState;
    }
};

export const WorkspaceTabsProvider = ({ children }) => {
    const { authToken, currentUser } = useAuth();
    const { t } = useTranslation();
    const location = useLocation();
    const navigate = useNavigate();
    const scope = getWorkspaceScope(location.pathname);
    const identity = getWorkspaceIdentity(currentUser);
    const [state, setState] = useState(emptyState);
    const [loadedIdentity, setLoadedIdentity] = useState(null);
    const [serverLoadedIdentity, setServerLoadedIdentity] = useState(null);
    const stateRef = useRef(state);
    const previousIdentityRef = useRef(null);
    const preferenceRevisionRef = useRef(0);
    const lastServerPayloadRef = useRef(null);
    const serverSyncAvailableRef = useRef(false);
    const serverIdentityRef = useRef(null);
    const serverWriteQueueRef = useRef(Promise.resolve());
    const serverWriteTimerRef = useRef(null);

    const commitState = useCallback((update) => {
        const next = typeof update === 'function' ? update(stateRef.current) : update;
        stateRef.current = next;
        setState(next);
        return next;
    }, []);

    useEffect(() => {
        stateRef.current = state;
    }, [state]);

    useEffect(() => {
        if (!authToken || !currentUser || !identity) {
            stateRef.current = emptyState;
            setState(emptyState);
            setLoadedIdentity(null);
            setServerLoadedIdentity(null);
            previousIdentityRef.current = null;
            preferenceRevisionRef.current = 0;
            lastServerPayloadRef.current = null;
            serverSyncAvailableRef.current = false;
            serverIdentityRef.current = null;
            return;
        }

        if (previousIdentityRef.current !== identity) {
            const restored = readStoredState(currentUser, t);
            stateRef.current = restored;
            setState(restored);
            setLoadedIdentity(identity);
            setServerLoadedIdentity(null);
            previousIdentityRef.current = identity;
            preferenceRevisionRef.current = 0;
            lastServerPayloadRef.current = null;
            serverSyncAvailableRef.current = false;
            serverIdentityRef.current = identity;
        }
    }, [authToken, currentUser, identity, t]);

    useEffect(() => {
        if (!authToken || !currentUser || !identity || loadedIdentity !== identity || serverLoadedIdentity === identity) return undefined;
        let cancelled = false;
        const capturedIdentity = identity;

        api.get('/api/ui/preferences/APP_TABS', { params: { context: 'workspace' } })
            .then((response) => {
                if (cancelled || serverIdentityRef.current !== capturedIdentity) return;
                const data = response?.data || {};
                const remoteState = deserializeWorkspacePreference(data.payload, currentUser, t);
                preferenceRevisionRef.current = Number.isFinite(Number(data.revision))
                    ? Number(data.revision)
                    : 0;
                lastServerPayloadRef.current = data.payload || { tabs: [] };
                serverSyncAvailableRef.current = true;
                setState((current) => {
                    const merged = mergeWorkspaceStates(remoteState, current);
                    stateRef.current = merged;
                    return merged;
                });
                setServerLoadedIdentity(capturedIdentity);
            })
            .catch(() => {
                if (cancelled || serverIdentityRef.current !== capturedIdentity) return;
                // Session storage remains the durable fallback for offline/older servers.
                serverSyncAvailableRef.current = false;
                setServerLoadedIdentity(capturedIdentity);
            });

        return () => {
            cancelled = true;
        };
    }, [authToken, currentUser, identity, loadedIdentity, serverLoadedIdentity, t]);

    useEffect(() => {
        if (!identity || loadedIdentity !== identity || typeof window === 'undefined') return;
        const storageKey = getWorkspaceStorageKey(currentUser);
        if (!storageKey) return;
        try {
            window.sessionStorage.setItem(storageKey, JSON.stringify(serializeWorkspace(state)));
        } catch {
            // The workspace remains functional when session storage is unavailable.
        }
    }, [currentUser, identity, loadedIdentity, state]);

    useEffect(() => {
        if (serverLoadedIdentity !== identity || !serverSyncAvailableRef.current) return undefined;
        const payload = serializeWorkspacePreference(state);
        const signature = JSON.stringify(payload);
        if (signature === JSON.stringify(lastServerPayloadRef.current)) return undefined;

        if (serverWriteTimerRef.current) window.clearTimeout(serverWriteTimerRef.current);
        const capturedIdentity = identity;
        serverWriteTimerRef.current = window.setTimeout(() => {
            const save = async () => {
                if (serverIdentityRef.current !== capturedIdentity) return;
                if (JSON.stringify(serializeWorkspacePreference(stateRef.current)) === JSON.stringify(lastServerPayloadRef.current)) return;
                // A queued write may outlive several newer edits or another browser's save.
                // Read the current state when the queue runs and refresh the server revision
                // before writing, rather than using a payload captured by an old timer.
                let payloadToSave;
                const refreshPreference = async () => {
                    const latest = await api.get('/api/ui/preferences/APP_TABS', {
                        params: { context: 'workspace' },
                    });
                    if (serverIdentityRef.current !== capturedIdentity) return false;
                    const latestRevision = Number(latest?.data?.revision || 0);
                    if (latestRevision !== preferenceRevisionRef.current) {
                        const latestState = deserializeWorkspacePreference(latest?.data?.payload, currentUser, t);
                        commitState((current) => mergeWorkspaceStates(latestState, current, {
                            preferLocalPinned: true,
                        }));
                    }
                    preferenceRevisionRef.current = latestRevision;
                    lastServerPayloadRef.current = latest?.data?.payload || { tabs: [] };
                    payloadToSave = serializeWorkspacePreference(stateRef.current);
                    return JSON.stringify(payloadToSave) !== JSON.stringify(lastServerPayloadRef.current);
                };
                const putPreference = () => api.put('/api/ui/preferences/APP_TABS', {
                    schemaVersion: 1,
                    revision: preferenceRevisionRef.current,
                    payload: payloadToSave,
                }, { params: { context: 'workspace' } });

                try {
                    if (!await refreshPreference()) return;
                    let response;
                    try {
                        response = await putPreference();
                    } catch (error) {
                        if (error?.response?.status !== 409) throw error;
                        if (!await refreshPreference()) return;
                        response = await putPreference();
                    }
                    if (serverIdentityRef.current !== capturedIdentity) return;
                    preferenceRevisionRef.current = Number.isFinite(Number(response?.data?.revision))
                        ? Number(response.data.revision)
                        : preferenceRevisionRef.current + 1;
                    lastServerPayloadRef.current = response?.data?.payload || payloadToSave;
                } catch {
                    // A local session copy has already been written; retry on the next change/reload.
                }
            };
            serverWriteQueueRef.current = serverWriteQueueRef.current
                .catch(() => undefined)
                .then(save);
        }, 300);

        return () => {
            if (serverWriteTimerRef.current) {
                window.clearTimeout(serverWriteTimerRef.current);
                serverWriteTimerRef.current = null;
            }
        };
    }, [commitState, currentUser, identity, serverLoadedIdentity, state, t]);

    const visitTab = useCallback((tab, options = {}) => {
        navigate(tab.url, {
            ...options,
            state: { ...options.state, [WORKSPACE_TAB_STATE_KEY]: tab.id },
        });
    }, [navigate]);

    useEffect(() => {
        if (!authToken || !currentUser || loadedIdentity !== identity) return;
        const currentUrl = `${location.pathname}${location.search}${location.hash}`;
        const route = resolveWorkspaceRoute(currentUrl, currentUser, t);
        if (!route) return;
        const current = stateRef.current;
        const historyId = location.state?.[WORKSPACE_TAB_STATE_KEY];
        const historyTab = typeof historyId === 'string'
            ? current.tabs.find((tab) => tab.id === historyId && getWorkspaceScope(tab) === route.scope)
            : null;
        const activeMatch = current.tabs.find((tab) => tab.id === current.activeTabId && tab.instanceKey === route.instanceKey);
        // A history entry identifies its exact tab, even when several tabs have the
        // same URL. Untagged content links reuse the active matching instance.
        let target = historyTab;
        if (!target && !historyId) {
            target = activeMatch
                || current.tabs.find((tab) => tab.url === route.url)
                || current.tabs.find((tab) => tab.instanceKey === route.instanceKey);
        }
        if (!target) {
            if (!canOpenWorkspaceTab(current.tabs, route.scope)) return;
            target = {
                ...route,
                id: typeof historyId === 'string' && !current.tabs.some((tab) => tab.id === historyId)
                    ? historyId : createTabId(route),
                pinned: false,
                scrollY: 0,
                lastVisitedAt: Date.now(),
            };
            commitState({ ...current, tabs: withWorkspaceTabLimit([...current.tabs, target], target.id), activeTabId: target.id });
        } else if (target.id !== current.activeTabId || target.url !== route.url || target.title !== route.title) {
            target = { ...target, ...route, lastVisitedAt: Date.now() };
            commitState({
                ...current,
                activeTabId: target.id,
                tabs: current.tabs.map((tab) => tab.id === target.id ? target : tab),
            });
        }
        if (historyId !== target.id) {
            visitTab(target, { replace: true, state: location.state });
        }
    }, [authToken, commitState, currentUser, identity, loadedIdentity, location.hash, location.key, location.pathname, location.search, location.state, t, visitTab]);

    useEffect(() => {
        if (!currentUser || !state.tabs.length) return;
        const allowedTabs = state.tabs.filter((tab) => resolveWorkspaceRoute(tab.url, currentUser, t));
        if (allowedTabs.length === state.tabs.length) return;
        const nextActive = allowedTabs.some((tab) => tab.id === state.activeTabId)
            ? state.activeTabId
            : allowedTabs.find((tab) => getWorkspaceScope(tab) === scope)?.id ?? null;
        commitState((current) => ({ ...current, tabs: allowedTabs, activeTabId: nextActive }));
        const target = allowedTabs.find((tab) => tab.id === nextActive);
        if (target && normalizeWorkspaceUrl(target.url) !== normalizeWorkspaceUrl(`${location.pathname}${location.search}${location.hash}`)) {
            visitTab(target, { replace: true });
        }
    }, [commitState, currentUser, location.hash, location.pathname, location.search, scope, state.activeTabId, state.tabs, t, visitTab]);

    const rememberScroll = useCallback((tabId) => {
        if (!tabId || typeof window === 'undefined') return;
        const scrollY = window.scrollY;
        commitState((current) => ({
            ...current,
            tabs: current.tabs.map((tab) => tab.id === tabId ? { ...tab, scrollY } : tab),
        }));
    }, [commitState]);

    const activateTab = useCallback((tabId, options = {}) => {
        const current = stateRef.current;
        const target = current.tabs.find((tab) => tab.id === tabId);
        if (!target) return false;
        if (!Object.hasOwn(options, 'state') && current.activeTabId === tabId && location.state?.[WORKSPACE_TAB_STATE_KEY] === tabId
            && `${location.pathname}${location.search}${location.hash}` === target.url) return true;
        rememberScroll(current.activeTabId);
        commitState((previous) => ({
            ...previous,
            activeTabId: tabId,
            tabs: previous.tabs.map((tab) => tab.id === tabId ? { ...tab, lastVisitedAt: Date.now() } : tab),
        }));
        visitTab(target, { state: options.state });
        if (typeof window !== 'undefined') {
            window.requestAnimationFrame(() => window.scrollTo({ top: target.scrollY || 0, behavior: 'auto' }));
        }
        return true;
    }, [commitState, location.hash, location.pathname, location.search, location.state, rememberScroll, visitTab]);

    const canHandleRoute = useCallback((url) => Boolean(currentUser && resolveWorkspaceRoute(url, currentUser, t)), [currentUser, t]);

    const openRoute = useCallback((url, options = {}) => {
        if (!currentUser) return false;
        const route = resolveWorkspaceRoute(url, currentUser, t);
        if (!route) return false;
        const current = stateRef.current;
        const matching = current.tabs.filter((tab) => tab.instanceKey === route.instanceKey);
        const existing = matching.find((tab) => tab.id === current.activeTabId) || matching[0];
        if (existing && options.forceNew !== true && options.duplicate !== true) {
            // Navigation selects the saved tab and preserves its filters/deep link.
            return activateTab(existing.id, options);
        }
        if (!canOpenWorkspaceTab(current.tabs, route.scope)) return false;
        rememberScroll(current.activeTabId);
        const nextTab = {
            ...route,
            id: createTabId(route),
            pinned: Boolean(options.pinned),
            scrollY: 0,
            lastVisitedAt: Date.now(),
        };
        commitState((previous) => ({
            ...previous,
            activeTabId: nextTab.id,
            tabs: withWorkspaceTabLimit([...previous.tabs, nextTab], nextTab.id),
        }));
        visitTab(nextTab, { state: options.state });
        return true;
    }, [activateTab, commitState, currentUser, rememberScroll, t, visitTab]);

    const closeTab = useCallback((tabId, options = {}) => {
        const current = stateRef.current;
        const scoped = current.tabs.filter((tab) => getWorkspaceScope(tab) === scope);
        const targetIndex = scoped.findIndex((tab) => tab.id === tabId);
        const target = scoped[targetIndex];
        if (!target || (target.pinned && options.force !== true)) return;
        const tabs = current.tabs.filter((tab) => tab.id !== tabId);
        const remaining = scoped.filter((tab) => tab.id !== tabId);
        const nextActiveTab = tabId === current.activeTabId
            ? (remaining[targetIndex] ?? remaining[targetIndex - 1] ?? null)
            : tabs.find((tab) => tab.id === current.activeTabId) ?? null;
        commitState({
            ...current,
            tabs,
            activeTabId: nextActiveTab?.id ?? null,
            recentlyClosed: [target, ...current.recentlyClosed].slice(0, 20),
        });
        if (tabId === current.activeTabId) {
            if (nextActiveTab) visitTab(nextActiveTab);
            else navigate(scope === 'pms' ? '/pms' : '/');
        }
    }, [commitState, navigate, scope, visitTab]);

    const togglePinned = useCallback((tabId) => {
        commitState((current) => ({
            ...current,
            tabs: current.tabs.map((tab) => tab.id === tabId && getWorkspaceScope(tab) === scope ? { ...tab, pinned: !tab.pinned } : tab),
        }));
    }, [commitState, scope]);

    const reorderTab = useCallback((tabId, targetIndex) => {
        commitState((current) => {
            const scoped = current.tabs.filter((tab) => getWorkspaceScope(tab) === scope);
            const sourceIndex = scoped.findIndex((tab) => tab.id === tabId);
            if (sourceIndex < 0) return current;
            const boundedIndex = Math.max(0, Math.min(targetIndex, scoped.length - 1));
            if (sourceIndex === boundedIndex) return current;
            const [moved] = scoped.splice(sourceIndex, 1);
            scoped.splice(boundedIndex, 0, moved);
            let index = 0;
            const tabs = current.tabs.map((tab) => getWorkspaceScope(tab) === scope ? scoped[index++] : tab);
            return { ...current, tabs };
        });
    }, [commitState, scope]);

    const closeOtherTabs = useCallback((tabId) => {
        const current = stateRef.current;
        const target = current.tabs.find((tab) => tab.id === tabId && getWorkspaceScope(tab) === scope);
        if (!target) return;
        const keep = current.tabs.filter((tab) => getWorkspaceScope(tab) !== scope || tab.id === tabId || tab.pinned);
        const removed = current.tabs.filter((tab) => !keep.some((candidate) => candidate.id === tab.id));
        commitState({
            ...current,
            tabs: keep,
            activeTabId: tabId,
            recentlyClosed: [...removed.reverse(), ...current.recentlyClosed].slice(0, 20),
        });
        if (current.activeTabId !== tabId) visitTab(target);
    }, [commitState, scope, visitTab]);

    const closeTabsToRight = useCallback((tabId) => {
        const current = stateRef.current;
        const scoped = current.tabs.filter((tab) => getWorkspaceScope(tab) === scope);
        const index = scoped.findIndex((tab) => tab.id === tabId);
        if (index < 0) return;
        const removed = scoped.slice(index + 1).filter((tab) => !tab.pinned);
        const removeIds = new Set(removed.map((tab) => tab.id));
        const tabs = current.tabs.filter((tab) => !removeIds.has(tab.id));
        const activeStillExists = tabs.some((tab) => tab.id === current.activeTabId);
        const nextActive = activeStillExists ? current.activeTabId : tabId;
        commitState({
            ...current,
            tabs,
            activeTabId: nextActive,
            recentlyClosed: [...removed.reverse(), ...current.recentlyClosed].slice(0, 20),
        });
        if (!activeStillExists) visitTab(tabs.find((tab) => tab.id === tabId));
    }, [commitState, scope, visitTab]);

    const restoreLastClosed = useCallback(() => {
        const current = stateRef.current;
        const closed = current.recentlyClosed.find((tab) => getWorkspaceScope(tab) === scope);
        if (!closed || !resolveWorkspaceRoute(closed.url, currentUser, t)) return false;
        if (!canOpenWorkspaceTab(current.tabs, scope)) return false;
        const restored = { ...closed, id: createTabId(closed), lastVisitedAt: Date.now() };
        commitState((previous) => ({
            ...previous,
            tabs: withWorkspaceTabLimit([...previous.tabs, restored], restored.id),
            activeTabId: restored.id,
            recentlyClosed: previous.recentlyClosed.filter((tab) => tab.id !== closed.id),
        }));
        visitTab(restored);
        return true;
    }, [commitState, currentUser, scope, t, visitTab]);

    const launchItems = useMemo(() => getWorkspaceLaunchItems(currentUser, t, scope), [currentUser, scope, t]);
    const tabs = useMemo(() => state.tabs.filter((tab) => getWorkspaceScope(tab) === scope), [scope, state.tabs]);
    const recentlyClosed = useMemo(() => state.recentlyClosed.filter((tab) => getWorkspaceScope(tab) === scope), [scope, state.recentlyClosed]);
    const activeTab = tabs.find((tab) => tab.id === state.activeTabId) ?? null;

    const value = useMemo(() => ({
        tabs,
        allTabs: state.tabs,
        recentlyClosed,
        activeTabId: activeTab?.id ?? null,
        scope,
        activeTab,
        launchItems,
        activateTab,
        canHandleRoute,
        openRoute,
        openWorkspaceTab: openRoute,
        closeTab,
        togglePinned,
        reorderTab,
        closeOtherTabs,
        closeTabsToRight,
        restoreLastClosed,
    }), [activateTab, activeTab, canHandleRoute, closeOtherTabs, closeTab, closeTabsToRight, launchItems, openRoute, recentlyClosed, reorderTab, restoreLastClosed, scope, state.tabs, tabs, togglePinned]);
    return <WorkspaceTabsContext.Provider value={value}>{children}</WorkspaceTabsContext.Provider>;
};

WorkspaceTabsProvider.propTypes = {
    children: PropTypes.node.isRequired,
};

export const useWorkspaceTabs = () => useContext(WorkspaceTabsContext);
