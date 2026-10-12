import { useEffect, useRef } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useTranslation } from '../../context/LanguageContext.jsx';
import { useWorkspaceTabs } from '../../components/workspace/WorkspaceTabsContext.jsx';
import { useWorkspacePaneActive } from '../../components/workspace/WorkspacePaneContext.jsx';
import { isAdminWorkspaceTab } from '../../components/workspace/workspaceRoutes.js';
import { PAGE_CATALOG, hasPageAccess, hasFeatureAccess } from '../../utils/pageAccess.js';
import { AdminWorkspaceSidebar } from './AdminWorkspaceChrome.jsx';
import { AdminWorkspaceShellContext } from './AdminWorkspaceShellContext.jsx';
import './AdminWorkspace.css';

// The existing routes and their permission guards remain the source of truth.
// An open new-dashboard workspace supplies navigation to its other pages.
export default function AdminWorkspaceModuleShell() {
    const { currentUser } = useAuth();
    const { t } = useTranslation();
    const workspace = useWorkspaceTabs();
    const active = useWorkspacePaneActive();
    const location = useLocation();
    const navigate = useNavigate();
    const rootRef = useRef(null);
    const tabs = workspace?.allTabs ?? workspace?.tabs ?? [];
    const newDashboardOpen = tabs.some(isAdminWorkspaceTab);
    const page = [...PAGE_CATALOG].sort((a, b) => b.path.length - a.path.length)
        .find(candidate => location.pathname === candidate.path || location.pathname.startsWith(`${candidate.path}/`));
    const employeeProfile = location.pathname.startsWith('/admin/dashboard-neu/mitarbeiter/');
    const enabled = Boolean(newDashboardOpen && currentUser && hasPageAccess(currentUser, 'adminDashboardWorkspace')
        && page && (employeeProfile || !['adminDashboard', 'adminDashboardWorkspace'].includes(page.key))
        && hasPageAccess(currentUser, page.key) && hasFeatureAccess(currentUser, page.featureKey));

    useEffect(() => {
        if (!enabled || !active) return;
        const root = rootRef.current;
        const chrome = root?.querySelector('.chrono-navbar-shell');
        if (!chrome) return;
        const measure = () => root.style.setProperty('--aw-chrome-height', `${chrome.getBoundingClientRect().height}px`);
        measure();
        const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(measure) : null;
        observer?.observe(chrome);
        window.addEventListener('resize', measure);
        return () => { observer?.disconnect(); window.removeEventListener('resize', measure); };
    }, [enabled, active]);

    const openTab = id => {
        const url = `/admin/dashboard-neu${id === 'overview' ? '' : `?tab=${id}`}`;
        if (!workspace?.openRoute(url, { restoreDashboardView: true })) navigate(url);
    };
    const dashboardTabs = [
        { id: 'overview', label: t('adminDashboard.tabs.overview', 'Übersicht') },
        { id: 'requests', label: t('adminDashboard.tabs.requests', 'Anträge') },
        { id: 'time', label: t('adminDashboard.tabs.timeReview', 'Zeitprüfung') },
        { id: 'calendar', label: t('adminDashboard.tabs.calendar', 'Kalender') },
        { id: 'employees', label: t('adminWorkspace.employees', 'Mitarbeitende') },
        { id: 'modules', label: t('adminDashboard.tabs.modules', 'Module') },
    ];
    // Keep the Outlet at the same position when the new dashboard is opened or
    // closed, so cached module forms do not lose their unsaved input.
    const passthroughStyle = enabled ? undefined : { display: 'contents' };
    return <div ref={rootRef} style={passthroughStyle}
        className={enabled ? 'admin-dashboard scoped-dashboard admin-workspace aw-module-workspace' : undefined}>
        {enabled && <Navbar />}
        <div style={passthroughStyle} className={enabled ? 'admin-dashboard-shell' : undefined}>
            {enabled && <AdminWorkspaceSidebar t={t} currentUser={currentUser} activeTab={employeeProfile ? 'employees' : ''} activePageKey={page.key}
                tabs={dashboardTabs} onOpenTab={openTab} />}
            <div role={enabled ? 'main' : undefined} style={passthroughStyle} className={enabled ? 'aw-module-content' : undefined}>
                <AdminWorkspaceShellContext.Provider value={enabled}><Outlet /></AdminWorkspaceShellContext.Provider>
            </div>
        </div>
    </div>;
}
