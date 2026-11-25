import { render, screen, fireEvent, within } from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminWorkspaceModuleShell from '../AdminWorkspaceModuleShell.jsx';
import { AdminWorkspaceSidebar } from '../AdminWorkspaceChrome.jsx';
import { useAdminWorkspaceShell } from '../AdminWorkspaceShellContext.jsx';

const mocks = vi.hoisted(() => ({ user: null, tabs: [], openRoute: vi.fn() }));
vi.mock('../../../context/AuthContext.jsx', () => ({ useAuth: () => ({ currentUser: mocks.user }) }));
vi.mock('../../../context/LanguageContext.jsx', () => ({ useTranslation: () => ({ t: (_key, fallback) => fallback }) }));
vi.mock('../../../components/workspace/WorkspaceTabsContext.jsx', () => ({ useWorkspaceTabs: () => ({
    allTabs: mocks.tabs, openRoute: mocks.openRoute, canHandleRoute: () => false,
}) }));
vi.mock('../../../components/Navbar.jsx', () => ({ default: () => useAdminWorkspaceShell() ? null : <nav aria-label="App navigation" /> }));

function Module() {
    const location = useLocation();
    const [draft, setDraft] = useState('');
    return <><MockNavbar /><h1>Module content</h1><output>{location.pathname}</output>
        <input aria-label="Module draft" value={draft} onChange={event => setDraft(event.target.value)} /></>;
}
function MockNavbar() {
    return useAdminWorkspaceShell() ? null : <nav aria-label="App navigation" />;
}
const moduleTree = () => <MemoryRouter initialEntries={['/admin/schedule']}>
    <Routes><Route element={<AdminWorkspaceModuleShell />}>
        <Route path="/admin/schedule" element={<Module />} />
    </Route></Routes>
</MemoryRouter>;
const renderModule = () => render(moduleTree());

beforeEach(() => {
    mocks.user = { roles: ['ROLE_SUPERADMIN'] };
    mocks.tabs = [{ id: 'new', url: '/admin/dashboard-neu?tab=calendar' }];
    mocks.openRoute.mockReset().mockReturnValue(true);
});

describe('Shared admin workspace navigation', () => {
    it('keeps navigation when the same workspace tab has moved to a module and filters the page list', () => {
        mocks.tabs = [{ id: 'new', url: '/admin/schedule', navigationRoot: '/admin/dashboard-neu' }];
        renderModule();
        const sidebar = screen.getByRole('complementary', { name: 'Arbeitsplatz-Navigation' });
        fireEvent.change(within(sidebar).getByRole('searchbox', { name: 'Bereich suchen' }), { target: { value: 'Meine Daten' } });
        expect(within(sidebar).getByRole('link', { name: 'Meine Daten' })).toBeInTheDocument();
        expect(within(sidebar).queryByRole('link', { name: 'Dienstplan' })).not.toBeInTheDocument();
        expect(within(sidebar).getByRole('button', { name: 'Kalender' })).toBeInTheDocument();
    });

    it('keeps canonical module routes and one app navbar while showing the shared sidebar', () => {
        renderModule();
        expect(screen.getAllByRole('navigation', { name: 'App navigation' })).toHaveLength(1);
        expect(screen.getByText('/admin/schedule')).toBeInTheDocument();
        const sidebar = screen.getByRole('complementary', { name: 'Arbeitsplatz-Navigation' });
        expect(within(sidebar).getByRole('link', { name: 'Dienstplan' })).toHaveAttribute('aria-current', 'page');
        expect(within(sidebar).getByRole('link', { name: 'Firmenverwaltung' })).toBeInTheDocument();
        expect(within(sidebar).getByRole('link', { name: 'Meine Daten' })).toBeInTheDocument();
        fireEvent.click(within(sidebar).getByRole('button', { name: 'Kalender' }));
        expect(mocks.openRoute).toHaveBeenCalledWith('/admin/dashboard-neu?tab=calendar', { restoreDashboardView: true });
    });

    it('retains the existing module presentation without an open new dashboard', () => {
        mocks.tabs = [{ id: 'old', url: '/admin/dashboard' }];
        renderModule();
        expect(screen.queryByRole('complementary', { name: 'Arbeitsplatz-Navigation' })).not.toBeInTheDocument();
        expect(screen.getByRole('navigation', { name: 'App navigation' })).toBeInTheDocument();
    });

    it('does not enable the new shell from a stale tab after losing its required role', () => {
        mocks.user = { roles: ['ROLE_ADMIN'], pagePermissions: { adminDashboardWorkspace: 'MANAGE', adminSchedule: 'MANAGE' }, companyFeatureKeys: ['roster'] };
        renderModule();
        expect(screen.queryByRole('complementary', { name: 'Arbeitsplatz-Navigation' })).not.toBeInTheDocument();
    });

    it('preserves unsaved module input when opening and closing the new dashboard', () => {
        mocks.tabs = [];
        const view = renderModule();
        fireEvent.change(screen.getByRole('textbox', { name: 'Module draft' }), { target: { value: 'Unsaved changes' } });
        mocks.tabs = [{ id: 'new', url: '/admin/dashboard-neu' }];
        view.rerender(moduleTree());
        expect(screen.getByRole('textbox', { name: 'Module draft' })).toHaveValue('Unsaved changes');
        mocks.tabs = [];
        view.rerender(moduleTree());
        expect(screen.getByRole('textbox', { name: 'Module draft' })).toHaveValue('Unsaved changes');
    });

    it('lists only permitted pages and enabled modules, including pages outside the admin dashboard catalog', () => {
        const user = { roles: ['ROLE_ADMIN'], companyFeatureKeys: ['roster'], pagePermissions: {
            personalData: 'VIEW', adminSchedule: 'VIEW', adminAnalytics: 'VIEW', adminUsers: 'NONE', pms: 'VIEW',
        } };
        render(<MemoryRouter><AdminWorkspaceSidebar t={(_key, fallback) => fallback} currentUser={user}
            activeTab="overview" tabs={[]} onOpenTab={() => {}} /></MemoryRouter>);
        expect(screen.getByRole('link', { name: 'Meine Daten' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Dienstplan' })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Analytics' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Benutzerverwaltung' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Hotelverwaltung (PMS)' })).not.toBeInTheDocument();
    });
});
