/** @vitest-environment jsdom */
import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const refreshRegistration = vi.hoisted(() => ({ loader: null, scopes: null, options: null }));
const notifyMock = vi.hoisted(() => vi.fn());
const translateMock = vi.hoisted(() => (_key, fallback) => fallback);
const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
}));

vi.mock('../../hooks/useRefreshOnMutation.js', () => ({
    useRefreshOnMutation: (scopes, loader, options) => {
        refreshRegistration.scopes = scopes;
        refreshRegistration.loader = loader;
        refreshRegistration.options = options;
    },
}));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav>Chrono navigation</nav> }));
vi.mock('../../context/NotificationContext.jsx', () => ({
    useNotification: () => ({ notify: notifyMock }),
}));
vi.mock('../../context/LanguageContext.jsx', () => ({
    useTranslation: () => ({ t: translateMock }),
}));
vi.mock('../../components/crm/KpiSummary.jsx', () => ({
    default: ({ leads }) => <output aria-label="Lead-Anzahl">{leads}</output>,
}));
vi.mock('../../components/crm/PipelineOverview.jsx', () => ({
    default: ({ leadFilter, setLeadFilter }) => (
        <div>
            <span data-testid="lead-filter">{leadFilter}</span>
            <button type="button" onClick={() => setLeadFilter('NEW')}>Neue Leads filtern</button>
        </div>
    ),
}));
vi.mock('../../components/crm/SalesBoard.jsx', () => ({
    default: ({ leads, onLeadCreate, onLeadUpdate }) => (
        <div>
            <form aria-label="Lead anlegen" onSubmit={onLeadCreate}>
                <button type="submit">Lead erstellen</button>
            </form>
            <button type="button" onClick={() => onLeadUpdate(1, 'NEW')}>Lead aktualisieren</button>
            <span data-testid="lead-name">{leads[0]?.companyName ?? ''}</span>
        </div>
    ),
}));
vi.mock('../../components/crm/MarketingBoard.jsx', () => ({ default: () => <div /> }));
vi.mock('../../components/crm/TeamPerformance.jsx', () => ({ default: () => <div /> }));
vi.mock('../../components/crm/EntitySlideOver.jsx', () => ({ default: () => null }));

import CrmDashboard from './CrmDashboard.jsx';

describe('CrmDashboard mutation refresh', () => {
    let leadsResponse;

    beforeEach(() => {
        vi.clearAllMocks();
        refreshRegistration.loader = null;
        refreshRegistration.scopes = null;
        refreshRegistration.options = null;
        leadsResponse = [];
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/crm/leads') return Promise.resolve({ data: leadsResponse });
            return Promise.resolve({ data: [] });
        });
        apiMock.post.mockImplementation(async () => {
            leadsResponse = [{ id: 1, companyName: 'Neu GmbH', status: 'NEW' }];
            await refreshRegistration.loader?.();
            return { data: leadsResponse[0] };
        });
        apiMock.patch.mockImplementation(async () => {
            leadsResponse = [{ id: 1, companyName: 'Aktualisiert AG', status: 'NEW' }];
            await refreshRegistration.loader?.();
            return { data: leadsResponse[0] };
        });
    });

    it('reloads a newly created lead even when the active filter does not change', async () => {
        render(<CrmDashboard />);
        expect(await screen.findByLabelText('Lead-Anzahl')).toHaveTextContent('0');

        await userEvent.click(screen.getByRole('button', { name: 'Neue Leads filtern' }));
        await waitFor(() => expect(screen.getByTestId('lead-filter')).toHaveTextContent('NEW'));
        const requestsBeforeMutation = apiMock.get.mock.calls
            .filter(([url]) => url === '/api/crm/leads').length;

        await userEvent.click(screen.getByRole('tab', { name: 'Vertrieb' }));
        await userEvent.click(screen.getByRole('button', { name: 'Lead erstellen' }));

        expect(apiMock.post).toHaveBeenCalledWith('/api/crm/leads', expect.objectContaining({ status: 'NEW' }));
        expect(await screen.findByLabelText('Lead-Anzahl')).toHaveTextContent('1');
        expect(screen.getByTestId('lead-name')).toHaveTextContent('Neu GmbH');
        expect(apiMock.get.mock.calls.filter(([url]) => url === '/api/crm/leads')).toHaveLength(requestsBeforeMutation + 1);
        expect(refreshRegistration.scopes).toEqual(['crm', 'customers']);
        expect(refreshRegistration.options).toEqual({
            enabled: true,
            debounceMs: 120,
            refreshOnFocus: true,
            focusThrottleMs: 30_000,
        });
    });

    it('reloads an updated lead when it remains inside the same filter', async () => {
        leadsResponse = [{ id: 1, companyName: 'Alt AG', status: 'NEW' }];
        render(<CrmDashboard />);
        expect(await screen.findByLabelText('Lead-Anzahl')).toHaveTextContent('1');

        await userEvent.click(screen.getByRole('button', { name: 'Neue Leads filtern' }));
        await userEvent.click(screen.getByRole('tab', { name: 'Vertrieb' }));
        await userEvent.click(screen.getByRole('button', { name: 'Lead aktualisieren' }));

        expect(apiMock.patch).toHaveBeenCalledWith('/api/crm/leads/1', { status: 'NEW' });
        expect(await screen.findByTestId('lead-name')).toHaveTextContent('Aktualisiert AG');
    });

    it('does not let an older request overwrite data loaded for a newer filter', async () => {
        render(<CrmDashboard />);
        expect(await screen.findByLabelText('Lead-Anzahl')).toHaveTextContent('0');

        const pendingResponses = [];
        let holdNextBatch = true;
        apiMock.get.mockImplementation((url) => {
            if (holdNextBatch) {
                return new Promise((resolve) => pendingResponses.push({ resolve, url }));
            }
            if (url === '/api/crm/leads') {
                return Promise.resolve({ data: [{ id: 2, companyName: 'Aktuell GmbH', status: 'NEW' }] });
            }
            return Promise.resolve({ data: [] });
        });

        let staleRequest;
        act(() => {
            staleRequest = refreshRegistration.loader();
        });
        await waitFor(() => expect(pendingResponses).toHaveLength(4));
        holdNextBatch = false;

        await userEvent.click(screen.getByRole('button', { name: 'Neue Leads filtern' }));
        await waitFor(() => expect(screen.getByLabelText('Lead-Anzahl')).toHaveTextContent('1'));

        await act(async () => {
            pendingResponses.forEach(({ resolve, url }) => resolve({
                data: url === '/api/crm/leads'
                    ? [{ id: 1, companyName: 'Veraltet AG', status: 'NEW' }]
                    : [],
            }));
            await staleRequest;
        });

        expect(screen.getByLabelText('Lead-Anzahl')).toHaveTextContent('1');
        await userEvent.click(screen.getByRole('tab', { name: 'Vertrieb' }));
        expect(screen.getByTestId('lead-name')).toHaveTextContent('Aktuell GmbH');
    });
});
