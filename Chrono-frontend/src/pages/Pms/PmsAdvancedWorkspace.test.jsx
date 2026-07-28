/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
}));

vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsAdvancedWorkspace from './PmsAdvancedWorkspace.jsx';

const property = {
    id: 5,
    name: 'Chrono Zürich',
    currencyCode: 'CHF',
    roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
};

const operations = {
    currencyCode: 'CHF',
    guests: [
        { id: 7, firstName: 'Gabriela', lastName: 'Tschopp', email: 'gabriela@example.com' },
        { id: 8, firstName: 'Raja', lastName: 'Siefert', email: 'raja@example.com' },
    ],
    reservations: [{
        id: 60,
        guestId: 7,
        guestName: 'Gabriela Tschopp',
        confirmationCode: 'CHR-TEST',
    }],
    ratePlans: [{
        id: 20,
        roomTypeId: 10,
        name: 'Beste Rate',
        active: true,
    }],
    rooms: [
        { id: 30, number: '101', roomTypeId: 10, roomTypeName: 'Doppelzimmer' },
        { id: 31, number: '102', roomTypeId: 10, roomTypeName: 'Doppelzimmer' },
    ],
    folios: [{
        id: 50,
        reservationId: 60,
        confirmationCode: 'CHR-TEST',
        guestName: 'Gabriela Tschopp',
        label: 'Hauptfolio',
        currencyCode: 'CHF',
        charges: 216.2,
    }],
};

const advanced = {
    propertyId: 5,
    businessDate: '2026-07-28',
    organizations: [{ id: 70, type: 'COMPANY', name: 'Beispiel AG', active: true, paymentTermsDays: 10 }],
    groups: [],
    invoices: [],
    nightAudits: [],
    communicationTemplates: [],
    communications: [],
    hotelResources: [{
        id: 91,
        type: 'CONFERENCE_ROOM',
        code: 'CONF-1',
        name: 'Konferenzraum Zürich',
        capacity: 20,
        hourlyRate: 120,
        currencyCode: 'CHF',
        active: true,
    }],
    resourceBookings: [],
    auditEvents: [{
        id: 92,
        actor: 'Christopher',
        eventType: 'reservation.created',
        aggregateType: 'reservation',
        aggregateId: '60',
        integrityHash: '1234567890abcdef',
        createdAt: '2026-07-28T10:00:00',
    }],
    integrationOutbox: [{
        id: 90,
        eventType: 'reservation.created',
        aggregateType: 'reservation',
        aggregateId: '60',
        status: 'PENDING',
        attemptCount: 0,
        createdAt: '2026-07-28T10:00:00',
    }],
};

const performanceReport = {
    propertyId: 5,
    propertyName: 'Chrono Zürich',
    currencyCode: 'CHF',
    fromDate: '2026-07-01',
    toDateExclusive: '2026-07-29',
    availableRoomNights: 56,
    soldRoomNights: 28,
    occupancyPercent: 50,
    roomRevenue: 4200,
    adr: 150,
    revPar: 75,
    arrivals: 12,
    cancellations: 2,
    noShows: 1,
    methodology: 'Aufenthaltsdatum, Ende exklusiv.',
    daily: [{
        date: '2026-07-28',
        availableRooms: 2,
        soldRooms: 1,
        occupancyPercent: 50,
        roomRevenue: 150,
        adr: 150,
        revPar: 75,
    }],
    sources: [{
        source: 'DIRECT',
        soldRoomNights: 28,
        roomRevenue: 4200,
        sharePercent: 100,
    }],
};

const portfolioReport = {
    businessDate: '2026-07-28',
    properties: 2,
    operationalRooms: 120,
    availableRooms: 118,
    soldRooms: 90,
    occupancyPercent: 76.27,
    arrivals: 22,
    departures: 18,
    hotels: [{
        propertyId: 5,
        code: 'ZRH',
        name: 'Chrono Zürich',
        city: 'Zürich',
        timezone: 'Europe/Zurich',
        currencyCode: 'CHF',
        operationalRooms: 60,
        availableRooms: 59,
        soldRooms: 45,
        occupancyPercent: 76.27,
        arrivals: 11,
        departures: 9,
    }],
};

const renderWorkspace = (section) => render(
    <PmsAdvancedWorkspace
        section={section}
        property={property}
        operations={operations}
        businessDate="2026-07-28"
        canManage
        onOperationsChange={vi.fn()}
    />,
);

describe('PmsAdvancedWorkspace', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMock.get.mockImplementation((url) => Promise.resolve({
            data: url === '/api/pms/advanced'
                ? advanced
                : url === '/api/pms/reports/performance'
                    ? performanceReport
                    : url === '/api/pms/reports/portfolio'
                        ? portfolioReport
                    : operations,
        }));
        apiMock.post.mockResolvedValue({ data: advanced });
    });

    it('creates an atomic multi-room group payload from the rooming list', async () => {
        renderWorkspace('groups');
        await screen.findByText('Gruppenbuchung');

        await userEvent.type(screen.getByLabelText('Gruppencode'), 'TEAM-26');
        await userEvent.type(screen.getByLabelText('Name'), 'Team Zürich');
        await userEvent.selectOptions(screen.getByLabelText('Kontakt'), '7');
        await userEvent.selectOptions(screen.getByLabelText('Gast Zimmer 1'), '7');
        await userEvent.selectOptions(screen.getByLabelText('Rate Zimmer 1'), '20');
        await userEvent.selectOptions(screen.getByLabelText('Zimmernummer 1'), '30');
        await userEvent.click(screen.getByRole('button', { name: 'Zimmer hinzufügen' }));
        await userEvent.selectOptions(screen.getByLabelText('Gast Zimmer 2'), '8');
        await userEvent.selectOptions(screen.getByLabelText('Rate Zimmer 2'), '20');
        await userEvent.selectOptions(screen.getByLabelText('Zimmernummer 2'), '31');
        await userEvent.click(screen.getByRole('button', { name: 'Gruppe verbindlich anlegen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/groups?businessDate=2026-07-28',
            expect.objectContaining({
                propertyId: 5,
                contactGuestId: 7,
                groupCode: 'TEAM-26',
                rooms: [
                    expect.objectContaining({ guestId: 7, roomId: 30, ratePlanId: 20 }),
                    expect.objectContaining({ guestId: 8, roomId: 31, ratePlanId: 20 }),
                ],
            }),
        );
    });

    it('creates a Swiss VAT invoice from a selected folio', async () => {
        renderWorkspace('invoices');
        await screen.findByText('Rechnung erstellen');

        await userEvent.selectOptions(screen.getByLabelText('Folio'), '50');
        await userEvent.type(screen.getByLabelText('IBAN'), 'CH9300762011623852957');
        await userEvent.click(screen.getByRole('button', { name: 'Rechnung ausstellen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/invoices?businessDate=2026-07-28',
            expect.objectContaining({
                folioId: 50,
                recipientName: 'Gabriela Tschopp',
                vatRate: 8.1,
                creditorIban: 'CH9300762011623852957',
            }),
        );
    });

    it('shows pending integration events and acknowledges them explicitly', async () => {
        renderWorkspace('integrations');

        expect(await screen.findAllByText('reservation.created')).toHaveLength(2);
        await userEvent.click(screen.getByRole('button', { name: 'Bestätigen' }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/integration-outbox/90/acknowledge?businessDate=2026-07-28',
            undefined,
        ));
    });

    it('creates a live channel using only an external secret reference', async () => {
        renderWorkspace('integrations');
        await screen.findByText('Integration Control Center');

        await userEvent.type(screen.getByLabelText('Provider-Code'), 'CHANNEL_GATEWAY');
        await userEvent.type(screen.getByLabelText('Name'), 'Live Channel');
        await userEvent.selectOptions(screen.getByLabelText('Umgebung'), 'LIVE');
        await userEvent.type(screen.getByLabelText('Secret-Referenz'), 'env:CHANNEL_PROVIDER_SECRET');
        await userEvent.selectOptions(screen.getByLabelText('Rate'), '20');
        await userEvent.type(screen.getByLabelText('Externer Zimmercode'), 'DBL');
        await userEvent.type(screen.getByLabelText('Externer Ratencode'), 'BAR');
        await userEvent.click(screen.getByRole('button', { name: 'Live-Verbindung anlegen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/channel-connections?businessDate=2026-07-28',
            {
                providerCode: 'CHANNEL_GATEWAY',
                displayName: 'Live Channel',
                environment: 'LIVE',
                secretReference: 'env:CHANNEL_PROVIDER_SECRET',
                mappings: [{
                    roomTypeId: 10,
                    ratePlanId: 20,
                    externalRoomCode: 'DBL',
                    externalRateCode: 'BAR',
                }],
            },
        );
    });

    it('records a provider-neutral inbound inbox message', async () => {
        renderWorkspace('communications');
        await screen.findByText('Unified Inbox');

        await userEvent.selectOptions(screen.getByLabelText('Posteingang Gast'), '7');
        await userEvent.selectOptions(screen.getByLabelText('Posteingang Reservierung'), '60');
        await userEvent.selectOptions(screen.getByLabelText('Posteingang Kanal'), 'OTA');
        await userEvent.type(screen.getByLabelText('Posteingang Absender'), 'Gabriela Tschopp');
        await userEvent.type(screen.getByLabelText('Posteingang Betreff'), 'Anreise');
        await userEvent.type(screen.getByLabelText('Posteingang Nachricht'), 'Können wir früher einchecken?');
        await userEvent.type(screen.getByLabelText('Posteingang Thread-ID'), 'booking-thread-4711');
        await userEvent.click(screen.getByRole('button', { name: 'Eingang erfassen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/inbox/messages?businessDate=2026-07-28',
            {
                guestId: 7,
                reservationId: 60,
                channel: 'OTA',
                sender: 'Gabriela Tschopp',
                subject: 'Anreise',
                body: 'Können wir früher einchecken?',
                externalThreadId: 'booking-thread-4711',
            },
        );
    });

    it('shows auditable occupancy, ADR and RevPAR reporting', async () => {
        renderWorkspace('reports');

        expect(await screen.findAllByText('50.00 %')).toHaveLength(2);
        expect(screen.getByText('Aufenthaltsdatum, Ende exklusiv.')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'RevPAR' })).toBeInTheDocument();
        expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/reports/performance',
            {
                params: {
                    propertyId: 5,
                    fromDate: '2026-07-01',
                    toDateExclusive: '2026-07-29',
                },
            },
        );
    });

    it('creates a conflict-checked MICE resource booking', async () => {
        renderWorkspace('events');
        await screen.findByText('Hotelressource');

        await userEvent.selectOptions(screen.getByLabelText('Buchungsressource'), '91');
        await userEvent.type(screen.getByLabelText('Eventtitel'), 'Strategiemeeting');
        await userEvent.type(screen.getByLabelText('Veranstalter'), 'Beispiel AG');
        await userEvent.click(screen.getByRole('button', { name: 'Konfliktfrei buchen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/resource-bookings?businessDate=2026-07-28',
            expect.objectContaining({
                resourceId: 91,
                title: 'Strategiemeeting',
                organizerName: 'Beispiel AG',
                startAt: '2026-07-28T09:00',
                endAt: '2026-07-28T12:00',
                attendees: 1,
                status: 'CONFIRMED',
                totalAmount: 0,
            }),
        );
    });

    it('shows a currency-safe multi-property portfolio', async () => {
        renderWorkspace('portfolio');

        expect(await screen.findByText('Multi-Property Control')).toBeInTheDocument();
        expect(screen.getByText('Chrono Zürich')).toBeInTheDocument();
        expect(screen.getByText(/Finanzwerte bleiben absichtlich/)).toBeInTheDocument();
        expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/reports/portfolio',
            { params: { businessDate: '2026-07-28' } },
        );
    });
});
