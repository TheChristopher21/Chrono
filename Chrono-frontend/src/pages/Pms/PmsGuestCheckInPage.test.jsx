/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
}));

vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsGuestCheckInPage from './PmsGuestCheckInPage.jsx';

const pendingRegistration = {
    registrationId: 9,
    status: 'PENDING',
    hotelName: 'Chrono Zürich',
    guestName: 'Gabriela Tschopp',
    confirmationCode: 'CHR-TEST',
    arrivalDate: '2026-09-28',
    departureDate: '2026-10-01',
    ruleCode: 'CH-MELDESCHEIN',
    ruleVersion: 1,
    requiredFields: [
        'addressLine',
        'postalCode',
        'city',
        'countryCode',
        'nationalityCode',
        'documentNumber',
        'signatureName',
        'privacyConsent',
    ],
    expiresAt: '2026-08-04T10:00:00',
};

const renderPage = () => render(
    <MemoryRouter initialEntries={['/guest-registration/single-use-token']}>
        <Routes>
            <Route path="/guest-registration/:token" element={<PmsGuestCheckInPage />} />
        </Routes>
    </MemoryRouter>,
);

describe('PmsGuestCheckInPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMock.get.mockResolvedValue({ data: pendingRegistration });
        apiMock.post.mockResolvedValue({
            data: { ...pendingRegistration, status: 'COMPLETED' },
        });
    });

    it('loads and completes the public single-use registration form', async () => {
        renderPage();

        await screen.findByRole('heading', { name: 'Digitaler Meldeschein' });
        expect(screen.getByText(/28\.09\.2026 bis 01\.10\.2026/)).toBeInTheDocument();
        expect(screen.getByText('Verfahren: Meldeschein Schweiz · Version 1'))
            .toBeInTheDocument();
        expect(screen.getByLabelText('Wohnsitzland (ISO-Ländercode, z. B. CH)'))
            .toHaveAttribute('pattern', '[A-Za-z]{2}');
        expect(screen.getByLabelText('Wohnsitzland (ISO-Ländercode, z. B. CH)'))
            .toBeRequired();
        expect(screen.getByLabelText('Adresse')).toBeRequired();
        expect(screen.getByLabelText('Ausweis- oder Passnummer')).toBeRequired();
        expect(screen.getByLabelText('Bestätigung durch Gast (vollständiger Name)'))
            .toHaveValue('Gabriela Tschopp');
        expect(screen.getByRole('link', { name: 'Datenschutzhinweise öffnen' }))
            .toHaveAttribute('href', '/datenschutz');
        expect(apiMock.get).toHaveBeenCalledWith(
            '/api/public/pms/guest-registration/single-use-token',
        );

        await userEvent.type(screen.getByLabelText('Adresse'), 'Seestrasse 2');
        await userEvent.type(screen.getByLabelText('PLZ'), '8002');
        await userEvent.type(screen.getByLabelText('Ort'), 'Zürich');
        await userEvent.type(
            screen.getByLabelText('Wohnsitzland (ISO-Ländercode, z. B. CH)'),
            'CH',
        );
        await userEvent.type(
            screen.getByLabelText('Nationalität (ISO-Ländercode, z. B. CH)'),
            'CH',
        );
        await userEvent.type(screen.getByLabelText('Ausweis- oder Passnummer'), 'X123456789');
        await userEvent.click(screen.getByRole('checkbox'));
        await userEvent.click(screen.getByRole('button', { name: 'Meldeschein übermitteln' }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/public/pms/guest-registration/single-use-token',
            expect.objectContaining({
                addressLine: 'Seestrasse 2',
                postalCode: '8002',
                city: 'Zürich',
                countryCode: 'CH',
                nationalityCode: 'CH',
                documentNumber: 'X123456789',
                signatureName: 'Gabriela Tschopp',
                privacyConsent: true,
                acknowledgedRuleCode: 'CH-MELDESCHEIN',
                acknowledgedRuleVersion: 1,
            }),
        ));
        expect(await screen.findByRole('heading', { name: 'Meldeschein übermittelt' })).toBeInTheDocument();
    });

    it('uses neutral wording for the global guest-registration rule', async () => {
        apiMock.get.mockResolvedValue({
            data: {
                ...pendingRegistration,
                ruleCode: 'GLOBAL-REGISTRATION',
                ruleVersion: 3,
                requiredFields: ['addressLine', 'privacyConsent'],
            },
        });
        apiMock.post.mockResolvedValue({
            data: {
                ...pendingRegistration,
                status: 'COMPLETED',
                ruleCode: 'GLOBAL-REGISTRATION',
                ruleVersion: 3,
            },
        });

        renderPage();

        await screen.findByRole('heading', { name: 'Digitale Gästeanmeldung' });
        expect(screen.getByText('Verfahren: Gästeanmeldung · Version 3'))
            .toBeInTheDocument();
        expect(screen.getByText(/für die Gästeanmeldung\./)).toBeInTheDocument();
        expect(screen.queryByText(/gesetzlich vorgeschrieben/)).not.toBeInTheDocument();
        expect(screen.getByLabelText('Adresse')).toBeRequired();
        expect(screen.getByLabelText('Ausweis- oder Passnummer (optional)'))
            .not.toBeRequired();

        await userEvent.type(screen.getByLabelText('Adresse'), 'Seestrasse 2');
        await userEvent.click(screen.getByRole('checkbox'));
        await userEvent.click(
            screen.getByRole('button', { name: 'Gästeanmeldung übermitteln' }),
        );

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/public/pms/guest-registration/single-use-token',
            expect.objectContaining({
                acknowledgedRuleCode: 'GLOBAL-REGISTRATION',
                acknowledgedRuleVersion: 3,
            }),
        ));
        expect(
            await screen.findByRole('heading', { name: 'Gästeanmeldung übermittelt' }),
        ).toBeInTheDocument();
    });

    it('uses the German rule label only for the German rule code', async () => {
        apiMock.get.mockResolvedValue({
            data: {
                ...pendingRegistration,
                ruleCode: 'DE-MELDESCHEIN',
            },
        });

        renderPage();

        await screen.findByText('Verfahren: Meldeschein Deutschland · Version 1');
        expect(screen.getByText(/für den Meldeschein Deutschland\./))
            .toBeInTheDocument();
        expect(screen.queryByText(/Meldeschein Schweiz/)).not.toBeInTheDocument();
    });
});
