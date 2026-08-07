import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CompanyManagementPage from '../CompanyManagementPage';
import api from '../../utils/api';

vi.mock('../../utils/api', () => ({
    default: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn(),
    },
}));

vi.mock('../../components/Navbar', () => ({
    default: () => <nav aria-label="Navbar" />,
}));

vi.mock('../../context/LanguageContext', () => ({
    useTranslation: () => ({ t: (_key, fallback) => fallback ?? _key }),
}));

vi.mock('../../context/AuthContext', () => ({
    useAuth: () => ({ currentUser: { roles: ['ROLE_SUPERADMIN'] } }),
}));

vi.mock('../../utils/analytics', () => ({
    isAnalyticsExcluded: vi.fn(() => true),
    setAnalyticsExcluded: vi.fn(),
}));

const analyticsSummary = {
    totalPageViews: 0,
    todayPageViews: 0,
    uniqueVisitors: 0,
    todayUniqueVisitors: 0,
    totalClicks: 0,
    todayClicks: 0,
    dailyStats: [],
    topPages: [],
    topClicks: [],
};

const company = {
    id: 1,
    name: 'Chrono Testhotel',
    active: true,
    paid: false,
    canceled: false,
    userCount: 1,
    enabledFeatures: [],
};

const resolvedGet = (url) => {
    if (url === '/api/superadmin/companies') {
        return Promise.resolve({ data: [company] });
    }
    if (url.startsWith('/api/superadmin/analytics/summary')) {
        return Promise.resolve({ data: analyticsSummary });
    }
    if (url === '/api/superadmin/analytics/excluded-ips') {
        return Promise.resolve({ data: [] });
    }
    return Promise.resolve({ data: {} });
};

describe('CompanyManagementPage', () => {
    beforeEach(() => {
        api.get.mockReset();
        api.post.mockReset();
        api.put.mockReset();
        api.delete.mockReset();
        api.get.mockImplementation(resolvedGet);
        api.post.mockResolvedValue({ data: {} });
        vi.spyOn(window, 'alert').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('keeps the rendered company list visible during the 30-second background refresh', async () => {
        vi.useFakeTimers();
        let companyRequestCount = 0;
        let resolveBackgroundRefresh;
        const backgroundRefresh = new Promise((resolve) => {
            resolveBackgroundRefresh = resolve;
        });

        api.get.mockImplementation((url) => {
            if (url === '/api/superadmin/companies') {
                companyRequestCount += 1;
                return companyRequestCount === 1
                    ? Promise.resolve({ data: [company] })
                    : backgroundRefresh;
            }
            return resolvedGet(url);
        });

        render(<CompanyManagementPage />);
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
        });

        expect(screen.getByText('Chrono Testhotel')).toBeInTheDocument();

        await act(async () => {
            vi.advanceTimersByTime(30000);
            await Promise.resolve();
        });

        expect(companyRequestCount).toBe(2);
        expect(screen.getByText('Chrono Testhotel')).toBeInTheDocument();

        await act(async () => {
            resolveBackgroundRefresh({ data: [company] });
            await Promise.resolve();
        });
    });

    it('submits a complete regular admin with optional PMS management access', async () => {
        render(<CompanyManagementPage />);

        const form = await screen.findByTestId('company-admin-create-form');
        const user = userEvent.setup();

        await user.type(within(form).getByLabelText('Firmenname'), 'Hotel Testbetrieb');
        await user.type(within(form).getByLabelText('Admin Benutzername'), 'hotel-test-admin');
        await user.type(within(form).getByLabelText('Admin Passwort'), 'SehrSicher!2026');
        await user.type(within(form).getByLabelText('Vorname'), 'Test');
        await user.type(within(form).getByLabelText('Nachname'), 'Hotel');
        await user.type(within(form).getByLabelText('Abteilung / Funktion'), 'Direktion');
        await user.type(within(form).getByLabelText('Personalnummer'), 'HOTEL-TEST-001');
        await user.click(within(form).getByLabelText('Hotelverwaltung (PMS) verwalten'));
        await user.click(within(form).getByRole('button', { name: 'Firma + Admin erstellen' }));

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith(
                '/api/superadmin/companies/create-with-admin',
                expect.objectContaining({
                    companyName: 'Hotel Testbetrieb',
                    adminUsername: 'hotel-test-admin',
                    adminPassword: 'SehrSicher!2026',
                    adminDepartment: 'Direktion',
                    adminCountry: 'CH',
                    adminTarifCode: 'A0',
                    adminCanton: 'SG',
                    adminPersonnelNumber: 'HOTEL-TEST-001',
                    adminIncludeInTimeTracking: false,
                    adminPmsAccess: true,
                })
            );
        });
    });
});
