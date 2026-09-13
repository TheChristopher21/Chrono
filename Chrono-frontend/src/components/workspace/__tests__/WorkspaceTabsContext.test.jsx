/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    put: vi.fn(),
}));
const translate = vi.hoisted(() => (_key, fallback) => fallback);

const currentUser = {
    id: 7,
    username: 'anna',
    roles: ['ROLE_ADMIN'],
    companyId: 23,
    companyFeatureKeys: ['pms'],
    pagePermissions: {
        dashboard: 'MANAGE',
        adminDashboard: 'MANAGE',
        pms: 'MANAGE',
    },
};

vi.mock('../../../context/AuthContext.jsx', () => ({
    useAuth: () => ({ authToken: 'test-token', currentUser }),
}));
vi.mock('../../../context/LanguageContext.jsx', () => ({
    useTranslation: () => ({ t: translate }),
}));
vi.mock('../../../utils/api.js', () => ({ default: apiMock }));

import { WorkspaceTabsProvider, useWorkspaceTabs } from '../WorkspaceTabsContext.jsx';

const Harness = () => {
    const workspace = useWorkspaceTabs();
    const navigate = useNavigate();
    const location = useLocation();
    const requests = workspace.tabs.find((tab) => tab.instanceKey === 'adminDashboard:requests');
    return (
        <>
            <output data-testid="workspace-state">{JSON.stringify({
                activeTabId: workspace.activeTabId,
                scope: workspace.scope,
                location: { url: `${location.pathname}${location.search}`, id: location.state?.workspaceTabId, state: location.state },
                launchItems: workspace.launchItems.map((item) => item.pageKey),
                recentlyClosed: workspace.recentlyClosed.map((tab) => tab.id),
                tabs: workspace.tabs.map(({ id, instanceKey, url, pinned }) => ({ id, instanceKey, url, pinned })),
            })}</output>
            <button type="button" onClick={() => workspace.openRoute('/admin/dashboard?tab=time')}>Open time</button>
            <button type="button" onClick={() => requests && workspace.activateTab(requests.id)}>Activate requests</button>
            <button type="button" onClick={() => navigate('/admin/dashboard?tab=time&week=2026-W36&focusUser=bob')}>Focus time</button>
            <button type="button" onClick={() => workspace.openRoute('/pms')}>Open PMS</button>
            <button type="button" onClick={() => workspace.openRoute('/pms', { state: { pmsAction: 'walk-in', pmsActionRequestId: 'first-request' } })}>PMS walk-in</button>
            <button type="button" onClick={() => workspace.openRoute('/pms', { state: { pmsAction: 'walk-in', pmsActionRequestId: 'second-request' } })}>Another PMS walk-in</button>
            <button type="button" onClick={() => workspace.openRoute('/dashboard')}>Open Chrono</button>
            <button type="button" onClick={() => workspace.openRoute('/pms', { forceNew: true })}>Duplicate PMS</button>
            <button type="button" onClick={() => workspace.openRoute('/admin/dashboard?tab=time', { forceNew: true })}>Duplicate time</button>
            <button type="button" onClick={() => navigate(-1)}>Back</button>
            <button type="button" onClick={() => navigate(1)}>Forward</button>
            <button type="button" onClick={() => workspace.activateTab(workspace.tabs[0]?.id)}>Activate first</button>
            <button type="button" onClick={() => workspace.closeTab(workspace.activeTabId)}>Close active</button>
            <button type="button" onClick={() => workspace.closeOtherTabs(workspace.activeTabId)}>Close others</button>
            <button type="button" onClick={() => workspace.closeTabsToRight(workspace.tabs[0]?.id)}>Close right</button>
            <button type="button" onClick={() => workspace.restoreLastClosed()}>Restore closed</button>
            <button type="button" onClick={() => workspace.reorderTab(workspace.activeTabId, 0)}>Move first</button>
        </>
    );
};

const readState = () => JSON.parse(screen.getByTestId('workspace-state').textContent);

const renderProvider = (entry) => render(
    <MemoryRouter initialEntries={[entry]}>
        <WorkspaceTabsProvider>
            <Harness />
        </WorkspaceTabsProvider>
    </MemoryRouter>
);

describe('WorkspaceTabsProvider', () => {
    beforeEach(() => {
        window.sessionStorage.clear();
        window.scrollTo = vi.fn();
        apiMock.get.mockReset().mockResolvedValue({
            data: { schemaVersion: 1, revision: 0, payload: { tabs: [] } },
        });
        apiMock.put.mockReset().mockImplementation((_url, body) => Promise.resolve({
            data: { ...body, revision: Number(body.revision) + 1 },
        }));
    });

    it('updates an existing admin instance instead of mutating the active sibling tab', async () => {
        const user = userEvent.setup();
        renderProvider('/admin/dashboard?tab=requests');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));

        await user.click(screen.getByRole('button', { name: 'Open time' }));
        await waitFor(() => expect(readState().tabs).toHaveLength(2));
        await user.click(screen.getByRole('button', { name: 'Activate requests' }));
        await user.click(screen.getByRole('button', { name: 'Focus time' }));

        await waitFor(() => {
            const state = readState();
            expect(state.tabs).toHaveLength(2);
            expect(state.tabs.map((tab) => tab.instanceKey).sort()).toEqual([
                'adminDashboard:requests',
                'adminDashboard:time',
            ]);
            expect(state.tabs.find((tab) => tab.instanceKey === 'adminDashboard:time').url)
                .toContain('focusUser=bob');
        });
    });

    it('merges the latest server tabs before retrying a 409 conflict', async () => {
        const initialPayload = {
            tabs: [{ id: 'dashboard', viewKey: 'dashboard', pinned: false }],
            activeTabId: 'dashboard',
        };
        const concurrentPayload = {
            tabs: [
                { id: 'dashboard', viewKey: 'dashboard', pinned: false },
                { id: 'requests', viewKey: 'adminDashboard', params: { tab: 'requests' }, pinned: true },
            ],
            activeTabId: 'requests',
        };
        apiMock.get
            .mockResolvedValueOnce({ data: { schemaVersion: 1, revision: 1, payload: initialPayload } })
            .mockResolvedValueOnce({ data: { schemaVersion: 1, revision: 2, payload: concurrentPayload } });
        apiMock.put
            .mockRejectedValueOnce({ response: { status: 409 } })
            .mockImplementationOnce((_url, body) => Promise.resolve({
                data: { ...body, revision: 3 },
            }));

        const user = userEvent.setup();
        renderProvider('/dashboard');
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(1));
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));

        await waitFor(() => expect(apiMock.put).toHaveBeenCalledTimes(2), { timeout: 2500 });
        const retriedPayload = apiMock.put.mock.calls[1][1].payload;
        expect(retriedPayload.tabs.map((tab) => tab.viewKey)).toEqual(
            expect.arrayContaining(['dashboard', 'adminDashboard', 'pms'])
        );
        expect(retriedPayload.tabs.find((tab) => tab.viewKey === 'adminDashboard')).toMatchObject({
            params: { tab: 'requests' },
            pinned: true,
        });
    });

    it('reuses a saved tab and its filters while explicit duplication creates another identity', async () => {
        const user = userEvent.setup();
        renderProvider('/admin/dashboard?tab=time&week=2026-W36');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));
        const firstId = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Open time' }));
        expect(readState().tabs).toHaveLength(1);
        expect(readState().location.url).toContain('week=2026-W36');
        await user.click(screen.getByRole('button', { name: 'Duplicate time' }));
        expect(readState().tabs).toHaveLength(2);
        expect(readState().activeTabId).not.toBe(firstId);
        expect(readState().location.id).toBe(readState().activeTabId);
    });

    it('restores exact duplicate tab identities with back and forward, even at the same URL', async () => {
        const user = userEvent.setup();
        renderProvider('/pms');
        await waitFor(() => expect(readState().location.id).toBe(readState().activeTabId));
        const firstId = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Duplicate PMS' }));
        const secondId = readState().activeTabId;
        expect(secondId).not.toBe(firstId);
        await user.click(screen.getByRole('button', { name: 'Back' }));
        expect(readState().activeTabId).toBe(firstId);
        expect(readState().tabs).toHaveLength(2);
        await user.click(screen.getByRole('button', { name: 'Forward' }));
        expect(readState().activeTabId).toBe(secondId);
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));
        expect(readState().tabs).toHaveLength(2);
        await user.click(screen.getByRole('button', { name: 'Back' }));
        expect(readState().activeTabId).toBe(firstId);
    });

    it('keeps independent visible tab sets and restores both areas through browser history', async () => {
        const user = userEvent.setup();
        renderProvider('/dashboard');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));
        const chronoId = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Open time' }));
        const timeId = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));
        const pmsId = readState().activeTabId;
        expect(readState().scope).toBe('pms');
        expect(readState().tabs.map((tab) => tab.id)).toEqual([pmsId]);
        expect(readState().launchItems.every((key) => key === 'pms')).toBe(true);
        await user.click(screen.getByRole('button', { name: 'Back' }));
        expect(readState().scope).toBe('chrono');
        expect(readState().activeTabId).toBe(timeId);
        expect(readState().tabs.map((tab) => tab.id)).toEqual([chronoId, timeId]);
        expect(readState().launchItems).not.toContain('pms');
        await user.click(screen.getByRole('button', { name: 'Forward' }));
        expect(readState().activeTabId).toBe(pmsId);
    });

    it('limits close, reorder and reopen operations to the visible workspace', async () => {
        const user = userEvent.setup();
        renderProvider('/dashboard');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));
        await user.click(screen.getByRole('button', { name: 'Open time' }));
        const chronoIds = readState().tabs.map((tab) => tab.id);
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));
        const firstPms = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Duplicate PMS' }));
        const secondPms = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'Move first' }));
        expect(readState().tabs.map((tab) => tab.id)).toEqual([secondPms, firstPms]);
        await user.click(screen.getByRole('button', { name: 'Close right' }));
        expect(readState().tabs.map((tab) => tab.id)).toEqual([secondPms]);
        await user.click(screen.getByRole('button', { name: 'Open Chrono' }));
        expect(readState().tabs.map((tab) => tab.id)).toEqual(chronoIds);
        expect(readState().recentlyClosed).toEqual([]);
        await user.click(screen.getByRole('button', { name: 'Restore closed' }));
        expect(readState().tabs.map((tab) => tab.id)).toEqual(chronoIds);
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));
        await user.click(screen.getByRole('button', { name: 'Restore closed' }));
        expect(readState().tabs).toHaveLength(2);
        await user.click(screen.getByRole('button', { name: 'Close others' }));
        expect(readState().tabs).toHaveLength(1);
        await user.click(screen.getByRole('button', { name: 'Open Chrono' }));
        expect(readState().tabs.map((tab) => tab.id)).toEqual(chronoIds);
    });

    it('restores all duplicate tabs and the active identity after a session reload', async () => {
        window.sessionStorage.setItem('chrono.workspaceTabs.v2.23:7', JSON.stringify({
            tabs: [
                { id: 'chrono', url: '/dashboard' },
                { id: 'pms-first', url: '/pms' },
                { id: 'pms-second', url: '/pms', pinned: true },
            ],
            activeTabId: 'pms-second',
        }));
        renderProvider('/pms');
        await waitFor(() => expect(readState().activeTabId).toBe('pms-second'));
        expect(readState().tabs.map((tab) => tab.id)).toEqual(['pms-first', 'pms-second']);
        expect(readState().location.id).toBe('pms-second');
        const persisted = JSON.parse(window.sessionStorage.getItem('chrono.workspaceTabs.v2.23:7'));
        expect(persisted.tabs.map((tab) => tab.id)).toEqual(['chrono', 'pms-first', 'pms-second']);
    });

    it('delivers action state to an existing active tab and another saved tab without creating duplicates', async () => {
        const user = userEvent.setup();
        renderProvider('/pms');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));
        const pmsId = readState().activeTabId;
        await user.click(screen.getByRole('button', { name: 'PMS walk-in' }));
        expect(readState().tabs).toHaveLength(1);
        expect(readState().location.state).toEqual({
            workspaceTabId: pmsId, pmsAction: 'walk-in', pmsActionRequestId: 'first-request',
        });
        await user.click(screen.getByRole('button', { name: 'Another PMS walk-in' }));
        expect(readState().tabs).toHaveLength(1);
        expect(readState().location.state.pmsActionRequestId).toBe('second-request');
        await user.click(screen.getByRole('button', { name: 'Open Chrono' }));
        await user.click(screen.getByRole('button', { name: 'PMS walk-in' }));
        expect(readState().activeTabId).toBe(pmsId);
        expect(readState().tabs).toHaveLength(1);
        expect(readState().location.state.pmsActionRequestId).toBe('first-request');
        await user.click(screen.getByRole('button', { name: 'Open Chrono' }));
        await user.click(screen.getByRole('button', { name: 'Open PMS' }));
        expect(readState().location.state).toEqual({ workspaceTabId: pmsId });
    });

    it('delivers action state when creating a previously unopened tab', async () => {
        const user = userEvent.setup();
        renderProvider('/dashboard');
        await waitFor(() => expect(readState().tabs).toHaveLength(1));
        await user.click(screen.getByRole('button', { name: 'PMS walk-in' }));
        expect(readState().tabs).toHaveLength(1);
        expect(readState().location.state).toEqual({
            workspaceTabId: readState().activeTabId, pmsAction: 'walk-in', pmsActionRequestId: 'first-request',
        });
    });
});
