/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, useLocation } from 'react-router-dom';

const apiMock = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }));
const currentUser = {
    id: 7,
    username: 'anna',
    firstName: 'Anna',
    roles: ['ROLE_ADMIN'],
    companyId: 23,
    companyFeatureKeys: ['pms'],
    pagePermissions: { dashboard: 'MANAGE', adminDashboard: 'MANAGE', pms: 'MANAGE' },
};

vi.mock('../../context/AuthContext.jsx', () => ({
    useAuth: () => ({ authToken: 'test-token', currentUser }),
}));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
vi.mock('../../hooks/useRefreshOnMutation.js', () => ({ useRefreshOnMutation: vi.fn() }));
vi.mock('../../components/Navbar.jsx', () => ({ default: () => null }));

import PmsDashboard from './PmsDashboard.jsx';
import { WorkspaceTabsProvider, useWorkspaceTabs } from '../../components/workspace/WorkspaceTabsContext.jsx';
import WorkspaceRouteViews from '../../components/workspace/WorkspaceRouteViews.jsx';

const property = {
    id: 5,
    name: 'Chrono Integration Hotel',
    currencyCode: 'CHF',
    timezone: 'Europe/Zurich',
    roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
};
const operations = {
    currencyCode: 'CHF', metrics: {}, reservations: [], arrivals: [], departures: [],
    guests: [], organizations: [], ratePlans: [], rateOverrides: [], rooms: [],
    housekeepingTasks: [], folios: [], roomBlocks: [], maintenanceWorkOrders: [],
};

function WorkspaceControls() {
    const workspace = useWorkspaceTabs();
    const location = useLocation();
    const guestTabs = workspace.allTabs.filter((tab) => tab.url.includes('section=guests'));
    return <div>
        <output data-testid="integration-workspace">{JSON.stringify({
            tabs: workspace.allTabs,
            activeTabId: workspace.activeTabId,
            url: `${location.pathname}${location.search}`,
            action: location.state?.pmsAction,
            requestId: location.state?.pmsActionRequestId,
        })}</output>
        <button onClick={() => workspace.openRoute('/pms')}>Test overview</button>
        <button onClick={() => workspace.openRoute('/pms?section=reservations')}>Test reservations</button>
        <button onClick={() => workspace.openRoute('/pms?section=guests')}>Test guests</button>
        <button onClick={() => workspace.openRoute('/pms?section=guests', { forceNew: true })}>Test guest copy</button>
        <button onClick={() => workspace.activateTab(guestTabs[0]?.id)}>Test first guest tab</button>
        <button onClick={() => workspace.activateTab(guestTabs[1]?.id)}>Test second guest tab</button>
    </div>;
}

function renderWorkspace(initialEntry = '/pms') {
    return render(<MemoryRouter initialEntries={[initialEntry]}>
        <WorkspaceTabsProvider>
            <WorkspaceControls />
            <WorkspaceRouteViews><Route path="/pms" element={<PmsDashboard />} /></WorkspaceRouteViews>
        </WorkspaceTabsProvider>
    </MemoryRouter>);
}

const workspaceState = () => JSON.parse(screen.getByTestId('integration-workspace').textContent);
const guestFirstName = () => screen.getByRole('textbox', { name: 'Vorname', exact: true });

beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    window.scrollTo = vi.fn();
    apiMock.get.mockReset().mockImplementation((url) => {
        if (url === '/api/pms/access/me') return Promise.resolve({ data: { master: true, properties: [] } });
        if (url === '/api/ui/preferences/APP_TABS') {
            return Promise.resolve({ data: { schemaVersion: 1, revision: 0, payload: { tabs: [] } } });
        }
        if (url === '/api/ui/preferences/PMS_DASHBOARD') {
            return Promise.resolve({ data: { schemaVersion: 1, revision: 0, payload: {} } });
        }
        if (url === '/api/pms/setup') {
            return Promise.resolve({ data: {
                properties: [property], totalProperties: 1, totalRoomTypes: 1, totalRooms: 1, foundationComplete: true,
            } });
        }
        if (url === '/api/pms/health') {
            return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
        }
        if (url.endsWith('/availability')) return Promise.resolve({ data: { roomTypes: [] } });
        return Promise.resolve({ data: operations });
    });
    apiMock.put.mockReset().mockImplementation((_url, body) => Promise.resolve({
        data: { ...body, revision: Number(body.revision ?? 0) + 1 },
    }));
});

// Real retained pages and keyboard navigation need headroom when the full suite runs in parallel.
describe('PMS real pages within retained workspace tabs', { timeout: 15_000 }, () => {
    it('delivers overview Alt+W to a newly created reservations pane in walk-in mode', async () => {
        renderWorkspace();
        await screen.findByRole('heading', { name: /Guten Tag, Anna/ });
        await waitFor(() => expect(workspaceState().tabs).toHaveLength(1));

        fireEvent.keyDown(window, { key: 'w', altKey: true });

        expect(await screen.findByRole('heading', { name: 'Walk-in vollständig aufnehmen' })).toBeInTheDocument();
        expect(workspaceState().url).toBe('/pms?section=reservations');
        expect(workspaceState().action).toBe('walk-in');
        expect(workspaceState().tabs).toHaveLength(2);
        expect(screen.getAllByRole('list', { name: 'Fortschritt der Rezeptionsbuchung' })).toHaveLength(1);
    });

    it('delivers a fresh quick action to the already open reservations pane without adding another tab', async () => {
        const user = userEvent.setup();
        renderWorkspace('/pms?section=reservations');
        expect(await screen.findByRole('heading', { name: 'Reservierung anlegen' })).toBeInTheDocument();
        await waitFor(() => expect(workspaceState().tabs).toHaveLength(1));
        const reservationsId = workspaceState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Test overview' }));
        await screen.findByRole('heading', { name: /Guten Tag, Anna/ });

        fireEvent.keyDown(window, { key: 'w', altKey: true });

        expect(await screen.findByRole('heading', { name: 'Walk-in vollständig aufnehmen' })).toBeInTheDocument();
        expect(workspaceState().activeTabId).toBe(reservationsId);
        expect(workspaceState().tabs).toHaveLength(2);
        const firstRequest = workspaceState().requestId;
        await user.click(screen.getByRole('button', { name: 'Test overview' }));
        await screen.findByRole('heading', { name: /Guten Tag, Anna/ });
        // A different action must reach this same retained component as well.
        fireEvent.keyDown(window, { key: 'n', ctrlKey: true });
        expect(await screen.findByRole('heading', { name: 'Reservierung anlegen' })).toBeInTheDocument();
        expect(workspaceState().activeTabId).toBe(reservationsId);
        expect(workspaceState().action).toBe('reservation');
        expect(workspaceState().requestId).not.toBe(firstRequest);
        expect(workspaceState().tabs).toHaveLength(2);
    });

    it('preserves separate unsaved guest first names across duplicate PMS pages and overview switches', async () => {
        const user = userEvent.setup();
        renderWorkspace('/pms?section=guests');
        await screen.findByRole('heading', { name: 'Gastprofil anlegen' });
        await waitFor(() => expect(workspaceState().tabs).toHaveLength(1));
        await user.type(guestFirstName(), 'Anna');
        await user.click(screen.getByRole('button', { name: 'Test guest copy' }));
        await waitFor(() => expect(workspaceState().tabs).toHaveLength(2));
        expect(guestFirstName()).toHaveValue('');
        await user.type(guestFirstName(), 'Beatrice');

        await user.click(screen.getByRole('button', { name: 'Test first guest tab' }));
        expect(guestFirstName()).toHaveValue('Anna');
        await user.click(screen.getByRole('button', { name: 'Test overview' }));
        await screen.findByRole('heading', { name: /Guten Tag, Anna/ });
        await user.click(screen.getByRole('button', { name: 'Test second guest tab' }));
        expect(guestFirstName()).toHaveValue('Beatrice');
        await user.click(screen.getByRole('button', { name: 'Test first guest tab' }));
        expect(guestFirstName()).toHaveValue('Anna');
        expect(screen.getAllByRole('heading', { name: 'Gastprofil anlegen' })).toHaveLength(1);
    });
});
