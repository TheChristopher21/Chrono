import React, { useEffect, useState } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, useLocation, useNavigate } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WorkspaceRouteViews from '../WorkspaceRouteViews.jsx';
import { useWorkspacePaneActive } from '../WorkspacePaneContext.jsx';
import WorkspacePortal from '../WorkspacePortal.jsx';

const mocks = vi.hoisted(() => ({ auth: {}, workspace: {} }));
vi.mock('../../../context/AuthContext.jsx', () => ({ useAuth: () => mocks.auth }));
vi.mock('../WorkspaceTabsContext.jsx', () => ({ useWorkspaceTabs: () => mocks.workspace }));

let navigate;
const activeSubscriptions = new Set();

function DraftPage() {
    const [draft, setDraft] = useState('');
    const active = useWorkspacePaneActive();
    const location = useLocation();
    const tabId = location.state?.workspaceTabId ?? 'untracked';
    useEffect(() => {
        activeSubscriptions.add(tabId);
        return () => activeSubscriptions.delete(tabId);
    }, [tabId]);
    return <>
        {active && <nav aria-label="Page navigation">Navigation</nav>}
        <label>Draft<input value={draft} onChange={(event) => setDraft(event.target.value)} /></label>
        <span data-testid={`route-${tabId}`}>{location.pathname}{location.search}</span>
        <WorkspacePortal><aside role="dialog" aria-label={`Overlay ${tabId}`}>Overlay</aside></WorkspacePortal>
    </>;
}

function Harness() {
    navigate = useNavigate();
    return <WorkspaceRouteViews>
        <Route path="/pms" element={<DraftPage />} />
        <Route path="/dashboard" element={<DraftPage />} />
        <Route path="/login" element={<div>Login</div>} />
    </WorkspaceRouteViews>;
}

const pmsA = { id: 'pms-a', url: '/pms?section=guests' };
const pmsB = { id: 'pms-b', url: '/pms?section=guests' };
const chrono = { id: 'chrono-a', url: '/dashboard' };

function selectTab(tab) {
    mocks.workspace.activeTab = tab;
    mocks.workspace.tabs = mocks.workspace.allTabs.filter((item) => item.url.startsWith('/pms') === tab.url.startsWith('/pms'));
    return act(async () => navigate(tab.url, { state: { workspaceTabId: tab.id } }));
}

function renderWorkspace() {
    return render(<MemoryRouter initialEntries={[{ pathname: '/pms', search: '?section=guests', state: { workspaceTabId: pmsA.id } }]}>
        <Harness />
    </MemoryRouter>);
}

beforeEach(() => {
    activeSubscriptions.clear();
    mocks.auth = { authToken: 'token', currentUser: { id: 1, username: 'alice', company: { id: 10 } } };
    mocks.workspace = { allTabs: [pmsA, pmsB, chrono], tabs: [pmsA, pmsB], activeTab: pmsA };
});

describe('WorkspaceRouteViews', () => {
    it('keeps independent unsaved drafts for copies of the same URL', async () => {
        renderWorkspace();
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'First guest draft' } });
        await selectTab(pmsB);
        expect(screen.getByRole('textbox')).toHaveValue('');
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Second guest draft' } });
        await selectTab(pmsA);
        expect(screen.getByRole('textbox')).toHaveValue('First guest draft');
        await selectTab(pmsB);
        expect(screen.getByRole('textbox')).toHaveValue('Second guest draft');
    });

    it('stops inactive subscriptions and hides their navigation and portal overlays', async () => {
        renderWorkspace();
        expect([...activeSubscriptions]).toEqual(['pms-a']);
        await selectTab(pmsB);
        expect([...activeSubscriptions]).toEqual(['pms-b']);
        expect(screen.getAllByRole('navigation')).toHaveLength(1);
        expect(screen.getAllByRole('dialog')).toHaveLength(1);
        expect(screen.getByRole('dialog')).toHaveAccessibleName('Overlay pms-b');
    });

    it('preserves the other area and freezes each pane route while following browser history', async () => {
        renderWorkspace();
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Saved in PMS pane' } });
        await selectTab(chrono);
        expect(screen.getByTestId('route-pms-a')).toHaveTextContent('/pms?section=guests');
        expect(screen.getByTestId('route-chrono-a')).toHaveTextContent('/dashboard');
        // The provider may still expose the old active tab during this render;
        // browser history state already identifies the correct pane.
        await act(async () => navigate(-1));
        expect(screen.getByRole('textbox')).toHaveValue('Saved in PMS pane');
        expect([...activeSubscriptions]).toEqual(['pms-a']);
    });

    it('discards a closed pane and does not mount tabs that were never visited', async () => {
        const view = renderWorkspace();
        expect(screen.queryByTestId('route-pms-b')).not.toBeInTheDocument();
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Discard on close' } });
        await selectTab(chrono);
        mocks.workspace.allTabs = [pmsB, chrono];
        view.rerender(<MemoryRouter><Harness /></MemoryRouter>);
        expect(screen.queryByTestId('route-pms-a')).not.toBeInTheDocument();
        mocks.workspace.allTabs = [pmsA, pmsB, chrono];
        await selectTab(pmsA);
        expect(screen.getByRole('textbox')).toHaveValue('');
    });

    it('clears drafts on account change and logout', async () => {
        const view = renderWorkspace();
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Private draft' } });
        mocks.auth.currentUser = { id: 2, username: 'bob', company: { id: 10 } };
        view.rerender(<MemoryRouter><Harness /></MemoryRouter>);
        expect(screen.getByRole('textbox')).toHaveValue('');
        mocks.auth = { authToken: null, currentUser: null };
        await act(async () => navigate('/login'));
        expect(screen.getByText('Login')).toBeInTheDocument();
        await waitFor(() => expect(activeSubscriptions.size).toBe(0));
        expect(screen.queryByRole('textbox', { hidden: true })).not.toBeInTheDocument();
    });
});
