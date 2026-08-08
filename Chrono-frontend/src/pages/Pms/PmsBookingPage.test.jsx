/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PmsBookingPage from './PmsBookingPage.jsx';

const apiMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));

const renderPage = (entry = '/book/ZRH') => render(<MemoryRouter initialEntries={[entry]}><Routes>
    <Route path="/book/:propertyCode" element={<PmsBookingPage />} />
</Routes></MemoryRouter>);

describe('PmsBookingPage', () => {
    beforeEach(() => {
        apiMock.get.mockReset();
        apiMock.post.mockReset();
        apiMock.get.mockImplementation((url) => {
            if (url.endsWith('/availability')) return Promise.resolve({ data: {
                roomTypes: [{ name: 'Doppelzimmer', rates: [{ ratePlanId: 9, name: 'Beste Rate', currencyCode: 'CHF', totalAmount: 220, available: true, restriction: null }] }],
            } });
            return Promise.resolve({ data: {
                hotelName: 'Chrono Zürich', city: 'Zürich', currencyCode: 'CHF',
                termsUrl: 'https://hotel.example/agb', privacyUrl: 'https://hotel.example/privacy', requireGuarantee: false,
            } });
        });
        apiMock.post.mockResolvedValue({ data: {
            confirmationCode: 'CHR-ABC123', roomTypeName: 'Doppelzimmer', rateName: 'Beste Rate',
            currencyCode: 'CHF', totalAmount: 220, confirmationMessage: 'Bis bald',
            status: 'TENTATIVE', verificationRequired: true, holdUntil: '2026-08-07T17:00:00',
        } });
    });

    it('searches availability and creates a direct reservation', async () => {
        renderPage();
        expect(await screen.findByText('Chrono Zürich')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Verfügbarkeit prüfen' }));
        await screen.findByText('Doppelzimmer');
        fireEvent.click(screen.getByRole('radio'));
        fireEvent.change(screen.getByLabelText('Vorname'), { target: { value: 'Raja' } });
        fireEvent.change(screen.getByLabelText('Nachname'), { target: { value: 'Siefert' } });
        fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: 'raja@example.com' } });
        const consentLabels = screen.getAllByText(/Ich akzeptiere/);
        fireEvent.click(consentLabels[0].closest('label').querySelector('input'));
        fireEvent.click(consentLabels[1].closest('label').querySelector('input'));
        fireEvent.click(screen.getByRole('button', { name: /Kostenpflichtig buchen/ }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/public/pms/booking/ZRH/reservations',
            expect.objectContaining({
                ratePlanId: 9, firstName: 'Raja', lastName: 'Siefert', email: 'raja@example.com',
                termsAccepted: true, privacyAccepted: true,
            }),
            expect.objectContaining({ headers: { 'Idempotency-Key': expect.any(String) } }),
        ));
        expect(await screen.findByText('CHR-ABC123')).toBeInTheDocument();
        expect(screen.getByText('E-Mail-Bestätigung erforderlich')).toBeInTheDocument();
    });

    it('verifies a booking from the email link', async () => {
        apiMock.post.mockResolvedValueOnce({ data: {
            confirmationCode: 'CHR-VERIFIED', roomTypeName: 'Doppelzimmer', rateName: 'Beste Rate',
            currencyCode: 'CHF', totalAmount: 220, status: 'CONFIRMED', verificationRequired: false,
        } });

        renderPage('/book/ZRH?verificationToken=abcdefghijklmnopqrstuvwxyz123456');

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/public/pms/booking/ZRH/verify',
            { token: 'abcdefghijklmnopqrstuvwxyz123456' },
        ));
        expect(await screen.findByText('Reservierung bestätigt')).toBeInTheDocument();
    });
});
