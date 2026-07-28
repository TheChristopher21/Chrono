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
    requiredFields: ['addressLine', 'documentNumber', 'privacyConsent'],
    expiresAt: '2026-08-04T10:00:00',
};

const renderPage = () => render(
    <MemoryRouter initialEntries={['/guest-check-in/single-use-token']}>
        <Routes>
            <Route path="/guest-check-in/:token" element={<PmsGuestCheckInPage />} />
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

    it('loads and completes the public single-use check-in form', async () => {
        renderPage();

        await screen.findByRole('heading', { name: 'Digitaler Check-in' });
        expect(apiMock.get).toHaveBeenCalledWith(
            '/api/public/pms/guest-registration/single-use-token',
        );

        await userEvent.type(screen.getByLabelText('Adresse'), 'Seestrasse 2');
        await userEvent.type(screen.getByLabelText('PLZ'), '8002');
        await userEvent.type(screen.getByLabelText('Ort'), 'Zürich');
        await userEvent.type(screen.getByLabelText('Ausweis- oder Passnummer'), 'X123456789');
        await userEvent.click(screen.getByRole('checkbox'));
        await userEvent.click(screen.getByRole('button', { name: 'Check-in abschließen' }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/public/pms/guest-registration/single-use-token',
            expect.objectContaining({
                addressLine: 'Seestrasse 2',
                postalCode: '8002',
                city: 'Zürich',
                documentNumber: 'X123456789',
                signatureName: 'Gabriela Tschopp',
                privacyConsent: true,
            }),
        ));
        expect(await screen.findByRole('heading', { name: 'Check-in vollständig' })).toBeInTheDocument();
    });
});
