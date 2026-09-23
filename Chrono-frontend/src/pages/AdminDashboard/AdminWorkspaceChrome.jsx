import { useId, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import WorkspaceLink from '../../components/workspace/WorkspaceLink.jsx';
import { getDashboardPagesForContext } from '../../utils/pageAccess.js';

const ICON_PATHS = {
    grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    inbox: 'M4 4h16l2 11v5H2v-5L4 4z M2 15h6l2 3h4l2-3h6',
    clock: 'M12 8v5l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
    calendar: 'M8 2v4 M16 2v4 M3 9h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2 M7 13h2 M12 13h2 M17 13h1 M7 17h2 M12 17h2',
    users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M17 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
    modules: 'M3 3h18v18H3z M3 10h18 M10 3v18',
    schedule: 'M8 2v4 M16 2v4 M3 9h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2 M7 13h4 M14 13h3 M7 17h3 M13 17h4',
    payroll: 'M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v12H5a3 3 0 0 1-3-3V6 M20 12h-6v5h6 M17 14.5h.01',
    chart: 'M4 20V10 M10 20v-5 M16 20V9 M22 20V3 M2 7l7 5 7-8 6-3',
    search: 'M21 21l-6-6 M17 9a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    punch: 'M10 17l5-5-5-5 M3 12h12 M15 3h5a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-5',
    arrow: 'M5 12h14 M12 5l7 7-7 7',
    chevron: 'M9 5l7 7-7 7',
    plus: 'M12 5v14 M5 12h14',
    print: 'M6 9V3h12v6 M6 18H3V9h18v9h-3 M6 14h12v8H6z M17 11h.01',
    settings: 'M3 6h6 M13 6h8 M3 12h12 M19 12h2 M3 18h2 M9 18h12 M9 3v6 M15 9v6 M5 15v6',
    more: 'M5 12h.01 M12 12h.01 M19 12h.01',
};

/** Decorative icons follow the native Navbar's currentColor SVG convention. */
export function AdminWorkspaceIcon({ name, className = '' }) {
    return (
        <svg className={`aw-icon ${className}`} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.55" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
            {name === 'more' ? <g fill="currentColor" stroke="none"><circle cx="5" cy="12" r="1.6" /><circle cx="12" cy="12" r="1.6" /><circle cx="19" cy="12" r="1.6" /></g> : <path d={ICON_PATHS[name] || ICON_PATHS.grid} />}
        </svg>
    );
}

AdminWorkspaceIcon.propTypes = { name: PropTypes.string.isRequired, className: PropTypes.string };

const TAB_ICONS = { overview: 'grid', requests: 'inbox', time: 'clock', calendar: 'calendar', employees: 'users', modules: 'modules' };
const SHORTCUTS = [
    { key: 'adminSchedule', icon: 'schedule' },
    { key: 'adminPayslips', icon: 'payroll' },
    { key: 'adminAnalytics', icon: 'chart' },
];

export function AdminWorkspaceSidebar({
    t, currentUser, activeTab, tabs, onOpenTab, onOpenEmployees, onCommand,
    onPunch, canPunch = false, isPunching = false,
}) {
    const [mobileMoreOpen, setMobileMoreOpen] = useState(false);
    const secondaryNavId = useId();
    const shortcuts = useMemo(() => {
        const accessiblePages = getDashboardPagesForContext(currentUser, 'admin', t);
        return SHORTCUTS.flatMap(shortcut => {
            const page = accessiblePages.find(candidate => candidate.key === shortcut.key);
            return page ? [{ ...page, icon: shortcut.icon }] : [];
        });
    }, [currentUser, t]);
    const orderedTabs = ['overview', 'requests', 'time', 'calendar']
        .map(id => tabs.find(tab => tab.id === id)).filter(Boolean);
    const employeeTab = tabs.find(tab => tab.id === 'employees');
    const moduleTab = tabs.find(tab => tab.id === 'modules');
    const openTab = id => { onOpenTab(id); setMobileMoreOpen(false); };
    const renderTab = tab => (
        <button
            key={tab.id}
            type="button"
            className={`aw-nav-item${activeTab === tab.id ? ' is-active' : ''}`}
            onClick={() => openTab(tab.id)}
            aria-current={activeTab === tab.id ? 'page' : undefined}
        >
            <AdminWorkspaceIcon name={TAB_ICONS[tab.id]} />
            <span>{tab.id === 'modules' ? t('adminWorkspace.allModules', 'Alle Module') : tab.label}</span>
            {Number(tab.count) > 0 && <span className="aw-nav-count">{tab.count}</span>}
        </button>
    );

    return (
        <aside className="aw-sidebar" aria-label={t('adminWorkspace.navigation', 'Arbeitsplatz-Navigation')}>
            <div className="aw-sidebar-label">{t('adminWorkspace.workplace', 'Arbeitsplatz')}</div>
            <nav className="aw-primary-nav" aria-label={t('adminDashboard.tabs.label', 'Admin-Dashboard Bereiche')}>
                {orderedTabs.map(renderTab)}
                {(employeeTab || onOpenEmployees) && (
                    <button
                        type="button"
                        className={`aw-nav-item${activeTab === 'employees' ? ' is-active' : ''}`}
                        aria-current={activeTab === 'employees' ? 'page' : undefined}
                        onClick={() => { if (onOpenEmployees) onOpenEmployees(); else onOpenTab('employees'); setMobileMoreOpen(false); }}
                    >
                        <AdminWorkspaceIcon name="users" />
                        <span>{employeeTab?.label || t('adminWorkspace.employees', 'Mitarbeitende')}</span>
                    </button>
                )}
                {moduleTab && renderTab(moduleTab)}
            </nav>
            <button type="button" className="aw-mobile-more aw-nav-item" aria-expanded={mobileMoreOpen} aria-controls={secondaryNavId} onClick={() => setMobileMoreOpen(open => !open)}>
                <AdminWorkspaceIcon name="more" />
                <span>{t('adminWorkspace.quickAccessMore', 'Schnellzugriff & mehr')}</span>
            </button>
            <div id={secondaryNavId} className={`aw-sidebar-secondary${mobileMoreOpen ? ' is-open' : ''}`}>
                {shortcuts.length > 0 && (
                    <div className="aw-shortcuts">
                        <div className="aw-sidebar-label">{t('adminDashboard.quickAccess', 'Schnellzugriff')}</div>
                        <nav aria-label={t('adminDashboard.quickAccess', 'Schnellzugriff')}>
                            {shortcuts.map(page => (
                                <WorkspaceLink key={page.key} to={page.path} className="aw-nav-item" onClick={() => setMobileMoreOpen(false)}>
                                    <AdminWorkspaceIcon name={page.icon} />
                                    <span>{page.label}</span>
                                </WorkspaceLink>
                            ))}
                        </nav>
                    </div>
                )}
                <div className="aw-sidebar-bottom">
                    {canPunch && onPunch && (
                        <button type="button" className="aw-nav-item aw-punch-action" onClick={onPunch} disabled={isPunching} aria-busy={isPunching}>
                            <AdminWorkspaceIcon name="punch" />
                            <span>{isPunching ? t('adminDashboard.punching', 'Wird gestempelt …') : t('manualPunchButton', 'Einstempeln')}</span>
                        </button>
                    )}
                    {onCommand && <button type="button" className="aw-nav-item" onClick={onCommand}>
                        <AdminWorkspaceIcon name="search" />
                        <span>{t('adminWorkspace.commands', 'Befehle suchen')}</span>
                        <kbd>⌘/Ctrl K</kbd>
                    </button>}
                    <WorkspaceLink to="/admin/dashboard" className="aw-legacy-link">
                        {t('adminWorkspace.legacyDashboard', 'Zur bisherigen Übersicht')}
                        <AdminWorkspaceIcon name="arrow" />
                    </WorkspaceLink>
                </div>
            </div>
        </aside>
    );
}

AdminWorkspaceSidebar.propTypes = {
    t: PropTypes.func.isRequired,
    currentUser: PropTypes.object,
    activeTab: PropTypes.string.isRequired,
    tabs: PropTypes.arrayOf(PropTypes.shape({ id: PropTypes.string.isRequired, label: PropTypes.string.isRequired, count: PropTypes.number })).isRequired,
    onOpenTab: PropTypes.func.isRequired,
    onOpenEmployees: PropTypes.func,
    onCommand: PropTypes.func,
    onPunch: PropTypes.func,
    canPunch: PropTypes.bool,
    isPunching: PropTypes.bool,
};

export function AdminWorkspaceHeader({ t, activeTab, onCreateVacation, onPrint, onCommand, canManage = false, context }) {
    const titles = {
        overview: t('adminWorkspace.title', 'Arbeitsübersicht'),
        requests: t('adminDashboard.tabs.requests', 'Anträge'),
        time: t('adminDashboard.tabs.timeReview', 'Zeitprüfung'),
        calendar: t('adminDashboard.tabs.calendar', 'Kalender'),
        modules: t('adminDashboard.tabs.modules', 'Module'),
        employees: t('adminWorkspace.employees', 'Mitarbeitende'),
    };
    const closeMenu = event => event.currentTarget.closest('details')?.removeAttribute('open');
    return (
        <header className="aw-header">
            <div className="aw-header-copy">
                <h1>{titles[activeTab] || titles.overview}</h1>
            </div>
            {context && <div className="aw-header-context">{context}</div>}
            <div className="aw-header-actions">
                {onCreateVacation && <button type="button" className="aw-button aw-button-primary" onClick={onCreateVacation} disabled={!canManage}>
                    <AdminWorkspaceIcon name="plus" />
                    {t('adminWorkspace.createVacation', 'Urlaub eintragen')}
                </button>}
                {(onPrint || onCommand) && <details className="aw-header-more">
                    <summary aria-label={t('adminWorkspace.moreActions', 'Weitere Aktionen')} title={t('adminWorkspace.moreActions', 'Weitere Aktionen')}>
                        <AdminWorkspaceIcon name="more" />
                    </summary>
                    <div className="aw-header-menu">
                        {onPrint && <button type="button" onClick={event => { closeMenu(event); onPrint(); }}><AdminWorkspaceIcon name="print" />{t('adminDashboard.header.printTimes', 'Zeiten drucken')}</button>}
                        {onCommand && <button type="button" onClick={event => { closeMenu(event); onCommand(); }}><AdminWorkspaceIcon name="search" />{t('adminWorkspace.commands', 'Befehle suchen')}<kbd>Ctrl K</kbd></button>}
                    </div>
                </details>}
            </div>
        </header>
    );
}

AdminWorkspaceHeader.propTypes = {
    t: PropTypes.func.isRequired,
    activeTab: PropTypes.string.isRequired,
    onCreateVacation: PropTypes.func,
    onPrint: PropTypes.func,
    onCommand: PropTypes.func,
    canManage: PropTypes.bool,
    context: PropTypes.node,
};
