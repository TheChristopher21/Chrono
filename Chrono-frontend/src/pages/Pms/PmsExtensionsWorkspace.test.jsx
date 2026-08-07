/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PmsExtensionsWorkspace from './PmsExtensionsWorkspace.jsx';

const apiMock = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));

const extensionData = {
    bookingEngine: { publicSlug: 'chrono-zuerich', enabled: false, requireGuarantee: false, termsUrl: null, privacyUrl: null, confirmationMessage: null },
    tourismTax: { enabled: false, name: 'Kurtaxe', adultRate: 0, childRate: 0, childFreeUnder: 16, maximumNights: null },
    posTickets: [], accessCredentials: [], migrationBatches: [],
};

describe('PmsExtensionsWorkspace', () => {
    beforeEach(() => {
        apiMock.get.mockReset();
        apiMock.put.mockReset();
        apiMock.post.mockReset();
        apiMock.get.mockResolvedValue({ data: extensionData });
        apiMock.put.mockResolvedValue({ data: { ...extensionData, bookingEngine: { ...extensionData.bookingEngine, enabled: true } } });
    });

    it('activates the public booking engine with HTTPS policy links', async () => {
        render(<PmsExtensionsWorkspace property={{ id: 7, code: 'ZRH', currencyCode: 'CHF' }}
            operations={{ reservations: [], folios: [] }} businessDate="2026-08-07"
            canManage onOperationsChange={vi.fn()} />);

        await screen.findByText('Booking Engine');
        fireEvent.click(screen.getByLabelText('Onlinebuchung aktiv'));
        fireEvent.change(screen.getByLabelText('AGB-Adresse'), { target: { value: 'https://hotel.example/agb' } });
        fireEvent.change(screen.getByLabelText('Datenschutz-Adresse'), { target: { value: 'https://hotel.example/privacy' } });
        fireEvent.click(screen.getByRole('button', { name: 'Booking Engine speichern' }));

        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/7/booking-engine', expect.objectContaining({
            enabled: true,
            termsUrl: 'https://hotel.example/agb',
            privacyUrl: 'https://hotel.example/privacy',
        })));
        expect(await screen.findByText('Onlinebuchung ist aktiv: /book/chrono-zuerich')).toBeInTheDocument();
    });
});
