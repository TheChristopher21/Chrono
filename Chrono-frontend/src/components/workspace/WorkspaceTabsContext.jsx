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
    getWorkspaceStorageKey,
    mergeWorkspaceStates,
    normalizeWorkspaceUrl,
    resolveWorkspaceRoute,
    sanitizeStoredWorkspace,
    serializeWorkspace,
    serializeWorkspacePreference,
    withWorkspaceTabLimit,
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
        if (!authToken || !currentUser || !identity || loadedIdentity !== identity) return undefined;
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
    }, [authToken, currentUser, identity, loadedIdentity, t]);

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
                let payloadToSave = payload;
                const putPreference = () => api.put('/api/ui/preferences/APP_TABS', {
                    schemaVersion: 1,
                    revision: preferenceRevisionRef.current,
                    payload: payloadToSave,
                }, { params: { context: 'workspace' } });

                try {
                    let response;
                    try {
                        response = await putPreference();
                    } catch (error) {
                        if (error?.response?.status !== 409) throw error;
                        const latest = await api.get('/api/ui/preferences/APP_TABS', {
                            params: { context: 'workspace' },
                        });
                        preferenceRevisionRef.current = Number(latest?.data?.revision || 0);
                        const latestState = deserializeWorkspacePreference(latest?.data?.payload, currentUser, t);
                        const mergedState = mergeWorkspaceStates(latestState, stateRef.current, {
                            preferLocalPinned: true,
                        });
                        payloadToSave = serializeWorkspacePreference(mergedState);
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
    }, [currentUser, identity, serverLoadedIdentity, state, t]);

    useEffect(() => {
        if (!authToken || !currentUser || loadedIdentity !== identity) return;
        const currentUrl = `${location.pathname}${location.search}${location.hash}`;
        const route = resolveWorkspaceRoute(currentUrl, currentUser, t);
        if (!route) return;

        setState((current) => {
            const exact = current.tabs.find((tab) => tab.url === route.url);
            if (exact) {
                if (exact.id === current.activeTabId && exact.title === route.title) return current;
                return {
                    ...current,
                    activeTabId: exact.id,
                    tabs: current.tabs.map((tab) => tab.id === exact.id
                        ? { ...tab, ...route, lastVisitedAt: Date.now() }
                        : tab),
                };
            }

            const sameInstance = current.tabs.find((tab) => tab.instanceKey === route.instanceKey);
            if (sameInstance) {
                return {
                    ...current,
                    activeTabId: sameInstance.id,
                    tabs: current.tabs.map((tab) => tab.id === sameInstance.id
                        ? { ...tab, ...route, lastVisitedAt: Date.now() }
                        : tab),
                };
            }

            if (!canOpenWorkspaceTab(current.tabs)) return current;

            const nextTab = {
                ...route,
                id: createTabId(route),
                pinned: false,
                scrollY: 0,
                lastVisitedAt: Date.now(),
            };
            const tabs = withWorkspaceTabLimit([...current.tabs, nextTab], nextTab.id);
            return { ...current, tabs, activeTabId: nextTab.id };
        });
    }, [authToken, currentUser, identity, loadedIdentity, location.hash, location.pathname, location.search, t]);

    useEffect(() => {
        if (!currentUser || !state.tabs.length) return;
        const allowedTabs = state.tabs.filter((tab) => resolveWorkspaceRoute(tab.url, currentUser, t));
        if (allowedTabs.length === state.tabs.length) return;
        const nextActive = allowedTabs.some((tab) => tab.id === state.activeTabId)
            ? state.activeTabId
            : allowedTabs[0]?.id ?? null;
        setState((current) => ({ ...current, tabs: allowedTabs, activeTabId: nextActive }));
        if (nextActive) {
            const target = allowedTabs.find((tab) => tab.id === nextActive);
            if (target && normalizeWorkspaceUrl(target.url) !== normalizeWorkspaceUrl(`${location.pathname}${location.search}${location.hash}`)) {
                navigate(target.url, { replace: true });
            }
        }
    }, [currentUser, location.hash, location.pathname, location.search, navigate, state.activeTabId, state.tabs, t]);

    const rememberScroll = useCallback((tabId) => {
        if (!tabId || typeof window === 'undefined') return;
        const scrollY = window.scrollY;
        setState((current) => ({
            ...current,
            tabs: current.tabs.map((tab) => tab.id === tabId ? { ...tab, scrollY } : tab),
        }));
    }, []);

    const activateTab = useCallback((tabId) => {
        const current = stateRef.current;
        const target = current.tabs.find((tab) => tab.id === tabId);
        if (!target) return;
        rememberScroll(current.activeTabId);
        setState((previous) => ({
            ...previous,
            activeTabId: tabId,
            tabs: previous.tabs.map((tab) => tab.id === tabId ? { ...tab, lastVisitedAt: Date.now() } : tab),
        }));
        navigate(target.url);
        if (typeof window !== 'undefined') {
            window.requestAnimationFrame(() => window.scrollTo({ top: target.scrollY || 0, behavior: 'auto' }));
        }
    }, [navigate, rememberScroll]);

    const openRoute = useCallback((url, options = {}) => {
        if (!currentUser) return false;
        const route = resolveWorkspaceRoute(url, currentUser, t);
        if (!route) return false;
        const current = stateRef.current;
        const existing = current.tabs.find((tab) => tab.instanceKey === route.instanceKey);
        if (existing && options.duplicate !== true) {
            setState((previous) => ({
                ...previous,
                activeTabId: existing.id,
                tabs: previous.tabs.map((tab) => tab.id === existing.id
                    ? { ...tab, ...route, lastVisitedAt: Date.now() }
                    : tab),
            }));
            navigate(route.url);
            return true;
        }

        if (!canOpenWorkspaceTab(current.tabs)) return false;

        rememberScroll(current.activeTabId);
        const nextTab = {
            ...route,
            id: createTabId(route),
            pinned: Boolean(options.pinned),
            scrollY: 0,
            lastVisitedAt: Date.now(),
        };
        setState((previous) => ({
            ...previous,
            activeTabId: nextTab.id,
            tabs: withWorkspaceTabLimit([...previous.tabs, nextTab], nextTab.id),
        }));
        navigate(route.url);
        return true;
    }, [currentUser, navigate, rememberScroll, t]);

    const closeTab = useCallback((tabId, options = {}) => {
        const current = stateRef.current;
        const targetIndex = current.tabs.findIndex((tab) => tab.id === tabId);
        const target = current.tabs[targetIndex];
        if (!target || (target.pinned && options.force !== true)) return;
        const tabs = current.tabs.filter((tab) => tab.id !== tabId);
        const nextActiveTab = tabId === current.activeTabId
            ? (tabs[targetIndex] ?? tabs[targetIndex - 1] ?? null)
            : tabs.find((tab) => tab.id === current.activeTabId) ?? null;
        const nextState = {
            tabs,
            activeTabId: nextActiveTab?.id ?? null,
            recentlyClosed: [target, ...current.recentlyClosed].slice(0, 10),
        };
        stateRef.current = nextState;
        setState(nextState);
        if (tabId === current.activeTabId) {
            navigate(nextActiveTab?.url ?? '/');
        }
    }, [navigate]);

    const togglePinned = useCallback((tabId) => {
        setState((current) => ({
            ...current,
            tabs: current.tabs.map((tab) => tab.id === tabId ? { ...tab, pinned: !tab.pinned } : tab),
        }));
    }, []);

    const reorderTab = useCallback((tabId, targetIndex) => {
        setState((current) => {
            const sourceIndex = current.tabs.findIndex((tab) => tab.id === tabId);
            if (sourceIndex < 0) return current;
            const boundedIndex = Math.max(0, Math.min(targetIndex, current.tabs.length - 1));
            if (sourceIndex === boundedIndex) return current;
            const tabs = [...current.tabs];
            const [moved] = tabs.splice(sourceIndex, 1);
            tabs.splice(boundedIndex, 0, moved);
            return { ...current, tabs };
        });
    }, []);

    const closeOtherTabs = useCallback((tabId) => {
        const current = stateRef.current;
        const keep = current.tabs.filter((tab) => tab.id === tabId || tab.pinned);
        const removed = current.tabs.filter((tab) => !keep.some((candidate) => candidate.id === tab.id));
        setState({
            ...current,
            tabs: keep,
            activeTabId: tabId,
            recentlyClosed: [...removed.reverse(), ...current.recentlyClosed].slice(0, 10),
        });
        const target = keep.find((tab) => tab.id === tabId);
        if (target) navigate(target.url);
    }, [navigate]);

    const closeTabsToRight = useCallback((tabId) => {
        const current = stateRef.current;
        const index = current.tabs.findIndex((tab) => tab.id === tabId);
        if (index < 0) return;
        const removed = current.tabs.slice(index + 1).filter((tab) => !tab.pinned);
        const removeIds = new Set(removed.map((tab) => tab.id));
        const tabs = current.tabs.filter((tab) => !removeIds.has(tab.id));
        const activeStillExists = tabs.some((tab) => tab.id === current.activeTabId);
        const nextActive = activeStillExists ? current.activeTabId : tabId;
        setState({
            ...current,
            tabs,
            activeTabId: nextActive,
            recentlyClosed: [...removed.reverse(), ...current.recentlyClosed].slice(0, 10),
        });
        if (!activeStillExists) {
            const target = tabs.find((tab) => tab.id === tabId);
            if (target) navigate(target.url);
        }
    }, [navigate]);

    const restoreLastClosed = useCallback(() => {
        const current = stateRef.current;
        const [closed, ...rest] = current.recentlyClosed;
        if (!closed || !resolveWorkspaceRoute(closed.url, currentUser, t)) return;
        if (!canOpenWorkspaceTab(current.tabs)) return false;
        const restored = { ...closed, id: createTabId(closed), lastVisitedAt: Date.now() };
        setState((previous) => ({
            ...previous,
            tabs: withWorkspaceTabLimit([...previous.tabs, restored], restored.id),
            activeTabId: restored.id,
            recentlyClosed: rest,
        }));
        navigate(restored.url);
        return true;
    }, [currentUser, navigate, t]);

    const launchItems = useMemo(() => getWorkspaceLaunchItems(currentUser, t), [currentUser, t]);
    const activeTab = state.tabs.find((tab) => tab.id === state.activeTabId) ?? null;

    const value = useMemo(() => ({
        ...state,
        activeTab,
        launchItems,
        activateTab,
        openRoute,
        closeTab,
        togglePinned,
        reorderTab,
        closeOtherTabs,
        closeTabsToRight,
        restoreLastClosed,
    }), [
        activateTab,
        activeTab,
        closeOtherTabs,
        closeTab,
        closeTabsToRight,
        launchItems,
        openRoute,
        reorderTab,
        restoreLastClosed,
        state,
        togglePinned,
    ]);

    return <WorkspaceTabsContext.Provider value={value}>{children}</WorkspaceTabsContext.Provider>;
};

WorkspaceTabsProvider.propTypes = {
    children: PropTypes.node.isRequired,
};

export const useWorkspaceTabs = () => useContext(WorkspaceTabsContext);
