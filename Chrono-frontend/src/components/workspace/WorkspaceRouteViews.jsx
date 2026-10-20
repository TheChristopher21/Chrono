import { Activity, useLayoutEffect, useRef, useState } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import AdminWorkspaceModuleShell from '../../pages/AdminDashboard/AdminWorkspaceModuleShell.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useWorkspaceTabs } from './WorkspaceTabsContext.jsx';
import { WorkspacePaneContext } from './WorkspacePaneContext.jsx';
import { getWorkspaceIdentity, getWorkspaceScope, isAdminWorkspaceTab, normalizeWorkspaceUrl, resolveWorkspaceRoute, WORKSPACE_TAB_STATE_KEY } from './workspaceRoutes.js';

const locationUrl = (location) => `${location.pathname}${location.search}${location.hash}`;

function WorkspaceRouteCache({ children, tabs, activeTab, location, currentUser }) {
    const [panes, setPanes] = useState([]);
    const openIds = new Set(tabs.map((tab) => tab.id));
    const url = normalizeWorkspaceUrl(locationUrl(location));
    const route = resolveWorkspaceRoute(url, currentUser);
    const historyTab = tabs.find((tab) => tab.id === location.state?.[WORKSPACE_TAB_STATE_KEY]
        && (normalizeWorkspaceUrl(tab.url) === url || (isAdminWorkspaceTab(tab) && route?.scope === getWorkspaceScope(tab))));
    const activeMatch = activeTab && normalizeWorkspaceUrl(activeTab.url) === url ? activeTab : null;
    const navigatingWorkspace = !location.state?.[WORKSPACE_TAB_STATE_KEY] && isAdminWorkspaceTab(activeTab)
        && route?.scope === getWorkspaceScope(activeTab) ? activeTab : null;
    const currentTab = historyTab || activeMatch || navigatingWorkspace;
    const currentId = currentTab?.id ?? null;
    const pageKey = isAdminWorkspaceTab(currentTab) ? location.pathname : '';
    const paneKey = currentId ? `${currentId}|${pageKey}` : null;
    const scrollPositions = useRef(new Map());
    const scrollKey = isAdminWorkspaceTab(currentTab)
        ? `${paneKey}|${new URLSearchParams(location.search).get('tab') || 'overview'}` : null;
    useLayoutEffect(() => {
        if (!scrollKey) return;
        window.scrollTo({ top: scrollPositions.current.get(scrollKey) || 0, behavior: 'instant' });
        return () => { scrollPositions.current.set(scrollKey, window.scrollY); };
    }, [scrollKey]);

    // Update the cached locations before rendering. A URL change in one pane must
    // never make another pane observe that route through useLocation/useSearchParams.
    let nextPanes = panes.filter((pane) => openIds.has(pane.id));
    if (currentId) {
        const existing = nextPanes.find((pane) => pane.key === paneKey);
        if (!existing) nextPanes = [...nextPanes, { id: currentId, key: paneKey, location }];
        else if (existing.location !== location) {
            nextPanes = nextPanes.map((pane) => pane.key === paneKey ? { ...pane, location } : pane);
        }
    }
    if (nextPanes.length !== panes.length || nextPanes.some((pane, index) => pane !== panes[index])) {
        setPanes(nextPanes);
    }

    return <>
        {nextPanes.map((pane) => {
            const active = pane.key === paneKey;
            return <WorkspacePaneContext.Provider key={pane.key} value={active}>
                <Activity mode={active ? 'visible' : 'hidden'}>
                    <div data-workspace-pane={pane.id} aria-hidden={active ? undefined : true} inert={!active}>
                        <Routes location={pane.location}><Route element={<AdminWorkspaceModuleShell />}>{children}</Route></Routes>
                    </div>
                </Activity>
            </WorkspacePaneContext.Provider>;
        })}
        {!currentId && <Routes location={location}><Route element={<AdminWorkspaceModuleShell />}>{children}</Route></Routes>}
    </>;
}

export default function WorkspaceRouteViews({ children }) {
    const { authToken, currentUser } = useAuth();
    const workspace = useWorkspaceTabs();
    const location = useLocation();
    const identity = authToken ? getWorkspaceIdentity(currentUser) : null;

    if (!identity || !workspace) return <Routes>{children}</Routes>;

    return <WorkspaceRouteCache
        key={identity}
        tabs={workspace.allTabs ?? workspace.tabs ?? []}
        activeTab={workspace.activeTab}
        location={location}
        currentUser={currentUser}
    >{children}</WorkspaceRouteCache>;
}
