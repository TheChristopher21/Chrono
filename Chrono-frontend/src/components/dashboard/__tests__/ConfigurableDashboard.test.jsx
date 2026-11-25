/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ConfigurableDashboard from '../ConfigurableDashboard.jsx';
import api from '../../../utils/api.js';

vi.mock('../../../utils/api.js', () => ({
    default: {
        get: vi.fn(),
        put: vi.fn(),
    },
}));

const currentUser = {
    id: 17,
    username: 'alex',
    roles: ['ROLE_USER'],
    companyFeatureKeys: ['analytics'],
    pagePermissions: { dashboard: 'MANAGE' },
};

const registry = [
    {
        id: 'alpha',
        title: 'Alpha',
        requiredPagePermission: 'dashboard',
        defaultSize: 'full',
        component: <div data-testid="alpha-content">Alpha Inhalt</div>,
    },
    {
        id: 'beta',
        title: 'Beta',
        requiredPagePermission: 'dashboard',
        defaultSize: 'M',
        component: <div data-testid="beta-content">Beta Inhalt</div>,
    },
];

const renderDashboard = (props = {}) => render(
    <ConfigurableDashboard
        context="USER_STANDARD"
        registry={registry}
        permissionContext={currentUser}
        storageIdentity={currentUser.id}
        {...props}
    />
);

describe('ConfigurableDashboard', () => {
    beforeEach(() => {
        window.localStorage.clear();
        api.get.mockReset().mockResolvedValue({
            data: { schemaVersion: 1, revision: 0, payload: {} },
        });
        api.put.mockReset().mockImplementation((_url, body) => Promise.resolve({
            data: {
                schemaVersion: 1,
                revision: Number(body.revision || 0) + 1,
                payload: body.payload,
            },
        }));
    });

    it('filters widgets by context, page permission and feature', async () => {
        renderDashboard({
            registry: [
                ...registry,
                { id: 'wrong-context', title: 'Falscher Kontext', allowedContexts: ['ADMIN'], component: <div>Falsch</div> },
                { id: 'missing-page', title: 'Keine Seite', requiredPagePermission: 'adminDashboard', component: <div>Gesperrt</div> },
                { id: 'missing-feature', title: 'Kein Feature', featureKey: 'crm', component: <div>CRM</div> },
                { id: 'analytics', title: 'Analytics', featureKey: 'analytics', component: <div>Analytics Inhalt</div> },
            ],
        });

        expect(screen.getByText('Alpha Inhalt')).toBeInTheDocument();
        expect(screen.getByText('Analytics Inhalt')).toBeInTheDocument();
        expect(screen.queryByText('Falsch')).not.toBeInTheDocument();
        expect(screen.queryByText('Gesperrt')).not.toBeInTheDocument();
        expect(screen.queryByText('CRM')).not.toBeInTheDocument();
        await waitFor(() => expect(screen.getByText('Mit dem Benutzerkonto synchronisiert')).toBeInTheDocument());
    });

    it('supports visibility, size, keyboard order controls and reset', async () => {
        const user = userEvent.setup();
        renderDashboard();

        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        const editor = screen.getByRole('complementary', { name: 'Dashboard-Bereiche' });

        await user.selectOptions(
            within(editor).getByRole('combobox', { name: 'Größe: Alpha' }),
            'S'
        );
        expect(screen.getByTestId('alpha-content').closest('[data-dashboard-widget="alpha"]'))
            .toHaveClass('dashboard-widget--size-s');

        await user.click(within(editor).getByRole('button', { name: 'Beta: Nach oben' }));
        const storedAfterMove = JSON.parse(window.localStorage.getItem(
            'chrono.dashboard-layout.17.USER_STANDARD.default'
        ));
        expect(storedAfterMove.layout.map((item) => item.id)).toEqual(['beta', 'alpha']);

        const alphaWidget = screen.getByTestId('alpha-content').closest('[data-dashboard-widget="alpha"]');
        fireEvent.keyDown(alphaWidget, { key: 'ArrowUp', altKey: true });
        expect(JSON.parse(window.localStorage.getItem(
            'chrono.dashboard-layout.17.USER_STANDARD.default'
        )).layout[0].id).toBe('alpha');

        await user.click(within(editor).getByRole('checkbox', { name: /Beta/ }));
        expect(screen.queryByTestId('beta-content')).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Standard wiederherstellen' }));
        expect(screen.getByTestId('beta-content')).toBeInTheDocument();
        expect(screen.getByTestId('alpha-content').closest('[data-dashboard-widget="alpha"]'))
            .toHaveClass('dashboard-widget--size-full');

        await waitFor(() => expect(api.put).toHaveBeenCalled());
        expect(api.put.mock.calls.at(-1)[1]).toMatchObject({
            schemaVersion: 1,
            payload: {
                layouts: {
                    default: { widgets: expect.any(Array) },
                },
            },
        });
        expect(api.put.mock.calls.at(-1)[2]).toEqual({ params: { context: 'USER_STANDARD' } });
    });

    it('uses the server layout when present and falls back to local storage on API errors', async () => {
        api.get.mockResolvedValueOnce({
            data: {
                schemaVersion: 1,
                revision: 3,
                payload: {
                    layouts: {
                        default: {
                            widgets: [
                                { id: 'beta', visible: false, order: 0, size: 'M' },
                                { id: 'alpha', visible: true, order: 1, size: 'L' },
                            ],
                        },
                    },
                },
            },
        });
        const firstRender = renderDashboard();

        await waitFor(() => expect(screen.queryByTestId('beta-content')).not.toBeInTheDocument());
        expect(screen.getByTestId('alpha-content').closest('[data-dashboard-widget="alpha"]'))
            .toHaveClass('dashboard-widget--size-l');
        firstRender.unmount();

        api.get.mockRejectedValueOnce(new Error('offline'));
        renderDashboard();

        await waitFor(() => expect(screen.getByText('Lokal auf diesem Gerät gespeichert')).toBeInTheDocument());
        expect(screen.queryByTestId('beta-content')).not.toBeInTheDocument();
    });

    it('uses local persistence only while remote preferences are disabled', async () => {
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false });

        expect(screen.getByText('Lokal auf diesem Gerät gespeichert')).toBeInTheDocument();
        expect(api.get).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        const editor = screen.getByRole('complementary', { name: 'Dashboard-Bereiche' });
        await user.click(within(editor).getByRole('checkbox', { name: /Beta/ }));

        expect(api.put).not.toHaveBeenCalled();
        expect(window.localStorage.getItem('chrono.dashboard-layout.17.USER_STANDARD.default')).not.toBeNull();
    });
});
