/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useNavigate } from 'react-router-dom';

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
    const requests = workspace.tabs.find((tab) => tab.instanceKey === 'adminDashboard:requests');
    return (
        <>
            <output data-testid="workspace-state">{JSON.stringify({
                activeTabId: workspace.activeTabId,
                tabs: workspace.tabs.map(({ id, instanceKey, url, pinned }) => ({ id, instanceKey, url, pinned })),
            })}</output>
            <button type="button" onClick={() => workspace.openRoute('/admin/dashboard?tab=time')}>Open time</button>
            <button type="button" onClick={() => requests && workspace.activateTab(requests.id)}>Activate requests</button>
            <button type="button" onClick={() => navigate('/admin/dashboard?tab=time&week=2026-W36&focusUser=bob')}>Focus time</button>
            <button type="button" onClick={() => workspace.openRoute('/pms')}>Open PMS</button>
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
});
