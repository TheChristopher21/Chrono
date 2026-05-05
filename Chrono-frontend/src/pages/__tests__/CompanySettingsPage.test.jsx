import { describe, it, beforeEach, expect, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CompanySettingsPage from '../CompanySettingsPage';
import api from '../../utils/api';

vi.mock('../../utils/api', () => ({
    default: {
        get: vi.fn(),
        put: vi.fn(),
    },
}));

vi.mock('../../components/Navbar', () => ({
    default: () => <nav aria-label="Navbar" />,
}));

vi.mock('../../context/LanguageContext', () => ({
    useTranslation: () => ({ t: (_key, fallback) => fallback ?? _key }),
}));

const holidayCatalog = [
    { code: 'CH_NEUJAHR', name: 'Neujahr', country: 'CH', regionHint: 'Schweiz' },
    { code: 'CH_BERCHTOLDSTAG', name: 'Berchtoldstag', country: 'CH', regionHint: 'St. Gallen' },
    { code: 'CH_ALLERHEILIGEN', name: 'Allerheiligen', country: 'CH', regionHint: 'St. Gallen' },
    { code: 'DE_NEUJAHR', name: 'Neujahr', country: 'DE', regionHint: 'bundesweit' },
];

const mockSettings = (overrides = {}) => ({
    uvgBuRate: null,
    uvgNbuRate: null,
    ktgRateEmployee: null,
    ktgRateEmployer: null,
    fakRate: null,
    midijobFactor: null,
    customHolidaySelectionEnabled: false,
    holidayPreferences: [
        { holidayCode: 'CH_NEUJAHR', halfDay: false },
        { holidayCode: 'CH_BERCHTOLDSTAG', halfDay: false },
        { holidayCode: 'CH_ALLERHEILIGEN', halfDay: false },
    ],
    ...overrides,
});

describe('CompanySettingsPage holidays', () => {
    beforeEach(() => {
        api.get.mockReset();
        api.put.mockReset();
        api.put.mockResolvedValue({ data: {} });
    });

    it('shows seeded St. Gallen holidays for existing companies while custom mode is off', async () => {
        api.get.mockImplementation((url) => {
            if (url === '/api/admin/company/settings') {
                return Promise.resolve({ data: mockSettings() });
            }
            if (url === '/api/admin/company/holiday-catalog') {
                return Promise.resolve({ data: holidayCatalog });
            }
            return Promise.resolve({ data: {} });
        });

        render(<CompanySettingsPage />);

        const customToggle = await screen.findByLabelText(/Firmenauswahl verwenden/);
        const berchtoldstagCheckbox = screen.getByLabelText(/Berchtoldstag/);
        const allerheiligenCheckbox = screen.getByLabelText(/Allerheiligen/);

        expect(customToggle).not.toBeChecked();
        expect(berchtoldstagCheckbox).toBeChecked();
        expect(allerheiligenCheckbox).toBeChecked();
        expect(berchtoldstagCheckbox).toBeDisabled();
        expect(allerheiligenCheckbox).toBeDisabled();
    });

    it('saves selected holidays and half-day flags without dropping existing choices', async () => {
        api.get.mockImplementation((url) => {
            if (url === '/api/admin/company/settings') {
                return Promise.resolve({
                    data: mockSettings({
                        customHolidaySelectionEnabled: true,
                        holidayPreferences: [
                            { holidayCode: 'CH_NEUJAHR', halfDay: false },
                        ],
                    }),
                });
            }
            if (url === '/api/admin/company/holiday-catalog') {
                return Promise.resolve({ data: holidayCatalog });
            }
            return Promise.resolve({ data: {} });
        });

        render(<CompanySettingsPage />);

        const allerheiligenRow = (await screen.findByText('Allerheiligen')).closest('.holiday-row');
        await userEvent.click(within(allerheiligenRow).getByLabelText(/Allerheiligen/));
        await userEvent.click(within(allerheiligenRow).getByLabelText(/Halbtags/));
        await userEvent.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(api.put).toHaveBeenCalled());
        const payload = api.put.mock.calls[0][1];

        expect(payload.customHolidaySelectionEnabled).toBe(true);
        expect(payload.holidayPreferences).toEqual(expect.arrayContaining([
            { holidayCode: 'CH_NEUJAHR', halfDay: false },
            { holidayCode: 'CH_ALLERHEILIGEN', halfDay: true },
        ]));
    });
});
