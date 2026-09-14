import { Activity, useState } from 'react';
import { Routes, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { useWorkspaceTabs } from './WorkspaceTabsContext.jsx';
import { WorkspacePaneContext } from './WorkspacePaneContext.jsx';
import { getWorkspaceIdentity, normalizeWorkspaceUrl, WORKSPACE_TAB_STATE_KEY } from './workspaceRoutes.js';

const locationUrl = (location) => `${location.pathname}${location.search}${location.hash}`;

function WorkspaceRouteCache({ children, tabs, activeTab, location }) {
    const [panes, setPanes] = useState([]);
    const openIds = new Set(tabs.map((tab) => tab.id));
    const url = normalizeWorkspaceUrl(locationUrl(location));
    const historyTab = tabs.find((tab) => tab.id === location.state?.[WORKSPACE_TAB_STATE_KEY]
        && normalizeWorkspaceUrl(tab.url) === url);
    const activeMatch = activeTab && normalizeWorkspaceUrl(activeTab.url) === url ? activeTab : null;
    const currentTab = historyTab || activeMatch;
    const currentId = currentTab?.id ?? null;

    // Update the cached locations before rendering. A URL change in one pane must
    // never make another pane observe that route through useLocation/useSearchParams.
    let nextPanes = panes.filter((pane) => openIds.has(pane.id));
    if (currentId) {
        const existing = nextPanes.find((pane) => pane.id === currentId);
        if (!existing) nextPanes = [...nextPanes, { id: currentId, location }];
        else if (existing.location !== location) {
            nextPanes = nextPanes.map((pane) => pane.id === currentId ? { ...pane, location } : pane);
        }
    }
    if (nextPanes.length !== panes.length || nextPanes.some((pane, index) => pane !== panes[index])) {
        setPanes(nextPanes);
    }

    return <>
        {nextPanes.map((pane) => {
            const active = pane.id === currentId;
            return <WorkspacePaneContext.Provider key={pane.id} value={active}>
                <Activity mode={active ? 'visible' : 'hidden'}>
                    <div data-workspace-pane={pane.id} aria-hidden={active ? undefined : true} inert={!active}>
                        <Routes location={pane.location}>{children}</Routes>
                    </div>
                </Activity>
            </WorkspacePaneContext.Provider>;
        })}
        {!currentId && <Routes location={location}>{children}</Routes>}
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
    >{children}</WorkspaceRouteCache>;
}
