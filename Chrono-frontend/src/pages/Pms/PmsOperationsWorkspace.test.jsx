/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
}));

vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsOperationsWorkspace from './PmsOperationsWorkspace.jsx';
import { hotelToday, addPlanDays } from './pmsRoomPlan.js';

const property = {
    id: 5,
    name: 'Chrono Zürich',
    currencyCode: 'CHF',
    roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
};

const operations = {
    currencyCode: 'CHF',
    metrics: {},
    guests: [{ id: 7, firstName: 'Gabriela', lastName: 'Tschopp', email: 'gabriela@example.com', vip: false }],
    reservations: [],
    ratePlans: [{
        id: 20,
        roomTypeId: 10,
        roomTypeName: 'Doppelzimmer',
        code: 'BAR',
        name: 'Beste Rate',
        currencyCode: 'CHF',
        nightlyRate: 120,
        minStay: 1,
        breakfastIncluded: false,
        refundable: true,
        active: true,
    }],
    rateOverrides: [],
    rooms: [{
        id: 30,
        roomTypeId: 10,
        roomTypeName: 'Doppelzimmer',
        number: '101',
        floor: '1',
        operationalStatus: 'IN_SERVICE',
        housekeepingStatus: 'CLEAN',
        currentReservation: null,
    }],
    roomBlocks: [],
    housekeepingTasks: [{
        id: 40,
        roomId: 30,
        roomNumber: '101',
        serviceDate: '2026-07-28',
        type: 'DEPARTURE',
        status: 'DIRTY',
        priority: 90,
        estimatedMinutes: 35,
        notes: null,
    }],
    folios: [{
        id: 50,
        reservationId: 60,
        confirmationCode: 'CHR-TEST',
        guestName: 'Gabriela Tschopp',
        currencyCode: 'CHF',
        status: 'OPEN',
        charges: 120,
        payments: 0,
        balance: 120,
        items: [],
        paymentEntries: [{
            id: 51,
            amount: 40,
            method: 'CARD',
            status: 'POSTED',
            kind: 'PAYMENT',
        }],
    }],
};

const renderWorkspace = (overrides = {}) => {
    const onOperationsChange = vi.fn();
    const view = render(
        <PmsOperationsWorkspace
            section="reservations"
            setup={{ properties: [property] }}
            operations={operations}
            property={property}
            businessDate="2026-07-28"
            canManage
            onOperationsChange={onOperationsChange}
            onClose={vi.fn()}
            {...overrides}
        />
    );
    return { ...view, onOperationsChange };
};

describe('PmsOperationsWorkspace', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMock.get.mockResolvedValue({ data: { roomTypes: [] } });
        apiMock.post.mockResolvedValue({ data: operations });
        apiMock.put.mockResolvedValue({ data: operations });
    });

    it('integrates the guided reception flow into the reservation workspace', async () => {
        renderWorkspace();

        expect(screen.getByRole('heading', { name: 'Reservierung anlegen' })).toBeInTheDocument();
        expect(screen.getByRole('list', { name: 'Fortschritt der Rezeptionsbuchung' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' })).toBeEnabled();
        await waitFor(() => expect(apiMock.get).toHaveBeenCalled());
    });

    it('checks availability through the company-scoped hotel endpoint', async () => {
        apiMock.get.mockResolvedValue({
            data: {
                roomTypes: [{
                    roomTypeId: 10,
                    name: 'Doppelzimmer',
                    totalRooms: 1,
                    availableRooms: 1,
                    rates: [{ ratePlanId: 20, name: 'Beste Rate', currencyCode: 'CHF', totalAmount: 120, available: true }],
                }],
            },
        });
        renderWorkspace();

        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/properties/5/availability',
            expect.objectContaining({
                params: expect.objectContaining({ arrival: '2026-07-28' }),
                signal: expect.any(AbortSignal),
            }),
        ));
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' }));
        expect(await screen.findByText('1 Zimmer verfügbar')).toBeInTheDocument();
    });

    it('opens the completed booking folio already selected for payment', async () => {
        apiMock.get.mockResolvedValue({
            data: {
                roomTypes: [{
                    roomTypeId: 10,
                    name: 'Doppelzimmer',
                    totalRooms: 1,
                    availableRooms: 1,
                    rates: [{
                        ratePlanId: 20,
                        name: 'Beste Rate',
                        currencyCode: 'CHF',
                        totalAmount: 120,
                        available: true,
                    }],
                }],
            },
        });
        apiMock.post.mockResolvedValue({
            data: {
                guestId: 7,
                reservationId: 60,
                folioId: 50,
                confirmationCode: 'CHR-TEST',
                totalAmount: 120,
                balance: 120,
                registrationStatus: 'PENDING',
                reservationStatus: 'CONFIRMED',
                checkInReady: false,
                checkInBlockers: [],
                operations,
            },
        });
        const onSectionChange = vi.fn();
        renderWorkspace({ onSectionChange });

        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' }));
        await userEvent.click(await screen.findByRole('radio', { name: /Doppelzimmer · Beste Rate/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zum Gast' }));
        await userEvent.click(screen.getByRole('radio', { name: /Gabriela Tschopp/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));
        await userEvent.click(screen.getByRole('button', { name: 'Reservierung verbindlich anlegen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Gastkonto öffnen' }));

        expect(onSectionChange).toHaveBeenCalledWith('folios');
        expect(screen.getByRole('combobox', { name: 'Gastkonto (Folio)' })).toHaveValue('50');
        expect(screen.getByRole('spinbutton', { name: 'Betrag' })).toHaveValue(120);
        expect(screen.getByRole('status')).toHaveTextContent('CHR-TEST ist für die weitere Abrechnung ausgewählt.');
    });

    it('updates housekeeping state with the complete task payload', async () => {
        renderWorkspace({ section: 'housekeeping' });

        await userEvent.click(screen.getByRole('button', { name: 'Start' }));

        expect(apiMock.put).toHaveBeenCalledWith(
            '/api/pms/properties/5/housekeeping/40?businessDate=2026-07-28',
            {
                type: 'DEPARTURE',
                status: 'IN_PROGRESS',
                priority: 90,
                estimatedMinutes: 35,
                notes: null,
                assignedTo: undefined,
            },
        );
    });

    it('offers only backend-supported housekeeping task types and sends each exact enum value', async () => {
        renderWorkspace({ section: 'housekeeping' });
        const planningCard = screen.getByRole('heading', { name: 'Aufgabe einplanen' })
            .closest('section');

        const typeSelect = within(planningCard).getByLabelText('Auftragsart');
        expect(Array.from(typeSelect.options).map((option) => option.value)).toEqual([
            'ARRIVAL',
            'DEPARTURE',
            'STAYOVER',
            'INSPECTION',
            'MANUAL',
        ]);
        expect(screen.queryByRole('option', { name: 'Grundreinigung' })).not.toBeInTheDocument();
        expect(screen.queryByRole('option', { name: 'Technik' })).not.toBeInTheDocument();

        for (const type of ['ARRIVAL', 'DEPARTURE', 'STAYOVER', 'INSPECTION', 'MANUAL']) {
            await userEvent.selectOptions(within(planningCard).getByLabelText('Zimmer'), '30');
            await userEvent.selectOptions(within(planningCard).getByLabelText('Auftragsart'), type);
            await userEvent.click(within(planningCard).getByRole('button', { name: 'Aufgabe speichern' }));

            await waitFor(() => expect(apiMock.post).toHaveBeenLastCalledWith(
                '/api/pms/properties/5/housekeeping?businessDate=2026-07-28',
                expect.objectContaining({
                    roomId: 30,
                    type,
                    priority: 50,
                    estimatedMinutes: 30,
                }),
            ));
        }
    });

    it('shows hotel terms instead of raw housekeeping and payment enum values', () => {
        const { unmount } = renderWorkspace({ section: 'housekeeping' });

        expect(screen.getAllByText(/Abreisereinigung/).length).toBeGreaterThan(0);
        expect(screen.queryByText('DEPARTURE')).not.toBeInTheDocument();

        unmount();
        renderWorkspace({ section: 'folios' });
        expect(screen.getAllByText(/Kartenzahlung/).length).toBeGreaterThan(0);
        expect(screen.getByText(/Verbucht/)).toBeInTheDocument();
        expect(screen.queryByText('CARD')).not.toBeInTheDocument();
        expect(screen.queryByText('POSTED')).not.toBeInTheDocument();
    });

    it('separates housekeeping from date-specific blocks in the continuous room plan', async () => {
        const roomPlanOperations = {
            ...operations,
            rooms: [
                operations.rooms[0],
                { ...operations.rooms[0], id: 31, number: '102' },
                { ...operations.rooms[0], id: 32, number: '103' },
                { ...operations.rooms[0], id: 33, number: '104' },
                {
                    ...operations.rooms[0],
                    id: 34,
                    number: '105',
                    operationalStatus: 'OUT_OF_ORDER',
                },
                {
                    ...operations.rooms[0],
                    id: 35,
                    number: '106',
                    operationalStatus: 'FUTURE_STATUS',
                },
            ],
            roomBlocks: [
                {
                    id: 70,
                    roomId: 30,
                    type: 'OWNER_USE',
                    status: 'ACTIVE',
                    startDate: '2026-07-27',
                    endDate: '2026-07-28',
                },
                {
                    id: 71,
                    roomId: 31,
                    type: 'OUT_OF_SERVICE',
                    status: 'ACTIVE',
                    startDate: '2026-07-28',
                    endDate: '2026-07-30',
                },
                {
                    id: 74,
                    roomId: 31,
                    type: 'OUT_OF_ORDER',
                    status: 'ACTIVE',
                    startDate: '2026-07-28',
                    endDate: '2026-07-30',
                },
                {
                    id: 72,
                    roomId: 32,
                    type: 'OWNER_USE',
                    status: 'ACTIVE',
                    startDate: '2026-07-28',
                    endDate: '2026-07-30',
                },
                {
                    id: 73,
                    roomId: 33,
                    type: 'OUT_OF_SERVICE',
                    status: 'ACTIVE',
                    startDate: '2026-07-28',
                    endDate: '2026-07-30',
                },
            ],
        };

        const today = hotelToday();
        apiMock.get.mockResolvedValue({ data: {
            rooms: roomPlanOperations.rooms.map((room) => ({ ...room, active: true })),
            reservations: [],
            blocks: roomPlanOperations.roomBlocks.filter((block) => block.id !== 70).map((block) => ({ ...block, startDate: today, endDate: addPlanDays(today, 2) })),
            page: 0, size: 50, totalRooms: 6, filters: {},
        } });
        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });
        await screen.findByRole('table');
        const row = (number) => screen.getByRole('rowheader', { name: new RegExp(number) }).closest('[role=row]');
        expect(within(row('101')).getByText('Sauber')).toBeInTheDocument();
        expect(within(row('101')).queryByRole('button')).not.toBeInTheDocument();
        expect(within(row('102')).getByRole('button', { name: 'Ausser Betrieb – nicht verkaufbar (OOO)' })).toBeInTheDocument();
        expect(within(row('103')).getByRole('button', { name: 'Eigennutzung – nicht verkaufbar' })).toBeInTheDocument();
        expect(within(row('104')).getByRole('button', { name: 'Eingeschränkter Betrieb – weiterhin zuweisbar (OOS)' })).toBeInTheDocument();
        expect(row('105')).toHaveClass('is-unavailable');
        expect(within(row('105')).getByText(/Technisch ausser Betrieb – nicht verkaufbar/)).toBeInTheDocument();
        expect(row('106')).toHaveClass('is-unavailable');
        expect(within(row('106')).getByText(/Unbekannt/)).toBeInTheDocument();
    });

    it('shows unknown folio statuses as unknown instead of closed', () => {
        renderWorkspace({
            section: 'folios',
            operations: {
                ...operations,
                folios: [{ ...operations.folios[0], status: 'ARCHIVED' }],
            },
        });

        expect(screen.getByText('CHR-TEST · Unbekannt')).toBeInTheDocument();
        expect(screen.queryByText('CHR-TEST · Geschlossen')).not.toBeInTheDocument();
    });

    it('creates a dated maintenance block from the housekeeping workspace', async () => {
        renderWorkspace({ section: 'housekeeping' });
        const maintenanceCard = screen.getByRole('heading', { name: 'Wartung & Zimmerverfügbarkeit' })
            .closest('section');

        expect(within(maintenanceCard).getByText(/Bei OOS bleibt es im Bestand/)).toBeInTheDocument();
        await userEvent.selectOptions(within(maintenanceCard).getByLabelText('Zimmer'), '30');
        await userEvent.type(within(maintenanceCard).getByLabelText('Titel'), 'Wasserhahn ersetzen');
        await userEvent.click(within(maintenanceCard).getByRole('button', { name: 'Wartungsauftrag erstellen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/maintenance?businessDate=2026-07-28',
            expect.objectContaining({
                roomId: 30,
                title: 'Wasserhahn ersetzen',
                blockRoom: true,
                blockType: 'OUT_OF_ORDER',
            }),
        );
    });

    it('prevents all operational writes for a view-only user', async () => {
        renderWorkspace({ canManage: false });

        expect(screen.getByText(/Eine Rezeptionsbuchung kann nicht gespeichert werden/)).toBeInTheDocument();
        expect(apiMock.post).not.toHaveBeenCalled();
        await waitFor(() => expect(apiMock.get).toHaveBeenCalled());
    });

    it('exports guest privacy data only from the administrator workspace', async () => {
        apiMock.get.mockResolvedValue({
            data: {
                guest: { id: 7, firstName: 'Gabriela', lastName: 'Tschopp' },
                reservations: [],
                retainedInvoices: [],
            },
        });
        const createObjectUrl = vi.fn(() => 'blob:guest-export');
        const revokeObjectUrl = vi.fn();
        Object.defineProperty(window.URL, 'createObjectURL', {
            configurable: true,
            value: createObjectUrl,
        });
        Object.defineProperty(window.URL, 'revokeObjectURL', {
            configurable: true,
            value: revokeObjectUrl,
        });
        const downloadClick = vi.spyOn(HTMLAnchorElement.prototype, 'click')
            .mockImplementation(() => {});
        renderWorkspace({ section: 'guests', canManageGuestPrivacy: true });

        await userEvent.click(screen.getByRole('button', { name: 'Datenexport' }));

        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/privacy/guests/7/export');
        expect(createObjectUrl).toHaveBeenCalledOnce();
        expect(revokeObjectUrl).toHaveBeenCalledWith('blob:guest-export');
        expect(await screen.findByText(/Datenexport für Gabriela Tschopp erstellt/)).toBeInTheDocument();
        downloadClick.mockRestore();
    });

    it('requires a reason and styled confirmation before anonymizing a finished guest', async () => {
        const { onOperationsChange } = renderWorkspace({
            section: 'guests',
            canManageGuestPrivacy: true,
            operations: { ...operations, guests: operations.guests.map((guest) => ({ ...guest,
                privateEmail: 'private@example.test', businessEmail: 'business@example.test',
                additionalEmails: ['additional@example.test'], dietaryNotes: 'Laktosefrei',
                organizationContactId: 'contact-1', billingOverride: true, billingProfile: { costCenter: 'SALES' },
            })) },
        });

        await userEvent.click(screen.getByRole('button', { name: 'Anonymisieren' }));
        await userEvent.type(
            screen.getByLabelText('Begründung'),
            'Schriftlicher Antrag der betroffenen Person',
        );
        await userEvent.click(screen.getByRole('button', { name: 'Endgültig anonymisieren' }));

        expect(apiMock.post).not.toHaveBeenCalledWith(
            '/api/pms/privacy/guests/7/anonymize',
            expect.anything(),
        );
        const confirmationDialog = screen.getByRole('dialog', { name: 'Gastprofil anonymisieren?' });
        expect(confirmationDialog).toBeInTheDocument();

        fireEvent.keyDown(
            within(confirmationDialog).getByRole('button', { name: 'Anonymisierung bestätigen' }),
            { key: 'Escape' },
        );
        expect(screen.queryByRole('dialog', { name: 'Gastprofil anonymisieren?' })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Endgültig anonymisieren' }));

        await userEvent.click(screen.getByRole('button', { name: 'Anonymisierung bestätigen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/privacy/guests/7/anonymize',
            { reason: 'Schriftlicher Antrag der betroffenen Person' },
        );
        expect(onOperationsChange).toHaveBeenCalledWith(expect.objectContaining({
            guests: [expect.objectContaining({
                id: 7,
                firstName: 'Anonymisiert',
                lastName: 'GAST-7',
                email: null,
                privateEmail: '', businessEmail: '', additionalEmails: [], dietaryNotes: '',
                organizationContactId: '', billingOverride: false, billingProfile: {},
            })],
        }));
        expect(await screen.findByText(/Gastprofil wurde anonymisiert/)).toBeInTheDocument();
    });

    it('moves an open reservation between compatible rooms with drag and drop', async () => {
        const today = hotelToday();
        const reservation = {
            id: 60,
            guestId: 7,
            guestName: 'Gabriela Tschopp',
            confirmationCode: 'CHR-TEST',
            roomTypeId: 10,
            roomTypeName: 'Doppelzimmer',
            roomId: 30,
            roomNumber: '101',
            ratePlanId: 20,
            arrivalDate: today,
            departureDate: addPlanDays(today, 1),
            adults: 1,
            children: 0,
            status: 'CONFIRMED',
            source: 'DIRECT',
            notes: null,
        };
        const roomPlanOperations = {
            ...operations,
            reservations: [reservation],
            rooms: [
                { ...operations.rooms[0], currentReservation: reservation },
                { ...operations.rooms[0], id: 31, number: '102', currentReservation: null },
            ],
        };
        apiMock.get.mockResolvedValue({ data: { rooms: roomPlanOperations.rooms.map((room) => ({ ...room, active: true })), reservations: [reservation], blocks: [], page: 0, size: 50, totalRooms: 2, filters: {} } });
        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });
        await screen.findByRole('table');
        expect(screen.getByRole('heading', { name: 'Zimmerplan', level: 3 }))
            .toBeInTheDocument();
        const data = {};
        const dataTransfer = {
            setData: (type, value) => { data[type] = value; },
            getData: (type) => data[type],
        };
        const guestButton = screen.getByRole('button', { name: /Gabriela Tschopp/ });
        const targetRoom = screen.getByRole('rowheader', { name: /102/ }).closest('[role=row]');

        fireEvent.dragStart(guestButton, { dataTransfer });
        fireEvent.dragOver(targetRoom, { dataTransfer });
        fireEvent.drop(targetRoom, { dataTransfer });

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/reservations/60/move-room',
            { roomId: 31, reason: 'Verschoben im fortlaufenden Zimmerplan' },
            { params: { businessDate: '2026-07-28' } },
        ));
    });

    it('prevents moving a reservation onto an OOO room', async () => {
        const today = hotelToday();
        const reservation = {
            id: 60,
            guestId: 7,
            guestName: 'Gabriela Tschopp',
            confirmationCode: 'CHR-TEST',
            roomTypeId: 10,
            roomTypeName: 'Doppelzimmer',
            roomId: 30,
            roomNumber: '101',
            ratePlanId: 20,
            arrivalDate: today,
            departureDate: addPlanDays(today, 1),
            adults: 1,
            children: 0,
            status: 'CONFIRMED',
            source: 'DIRECT',
            notes: null,
        };
        const roomPlanOperations = {
            ...operations,
            reservations: [reservation],
            rooms: [
                { ...operations.rooms[0], currentReservation: reservation },
                { ...operations.rooms[0], id: 31, number: '102', currentReservation: null },
            ],
            roomBlocks: [{
                id: 70,
                roomId: 31,
                type: 'OUT_OF_ORDER',
                status: 'ACTIVE',
                startDate: today,
                endDate: addPlanDays(today, 2),
            }],
        };
        apiMock.get.mockResolvedValue({ data: { rooms: roomPlanOperations.rooms.map((room) => ({ ...room, active: true })), reservations: [reservation], blocks: roomPlanOperations.roomBlocks, page: 0, size: 50, totalRooms: 2, filters: {} } });
        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });
        await screen.findByRole('table');
        const data = {};
        const dataTransfer = {
            setData: (type, value) => { data[type] = value; },
            getData: (type) => data[type],
        };

        fireEvent.dragStart(screen.getByRole('button', { name: /Gabriela Tschopp/ }), { dataTransfer });
        fireEvent.drop(screen.getByRole('rowheader', { name: /102/ }).closest('[role=row]'), { dataTransfer });

        await waitFor(() => expect(apiMock.post).not.toHaveBeenCalled());
    });

    it('confirms a waitlisted reservation through the explicit lifecycle endpoint', async () => {
        const waitlisted = {
            id: 61,
            guestId: 7,
            guestName: 'Gabriela Tschopp',
            confirmationCode: 'CHR-WAIT',
            roomTypeId: 10,
            roomTypeName: 'Doppelzimmer',
            roomId: null,
            ratePlanId: 20,
            arrivalDate: '2026-07-30',
            departureDate: '2026-07-31',
            adults: 1,
            children: 0,
            status: 'WAITLISTED',
            source: 'DIRECT',
            guaranteeStatus: 'CREDIT_CARD',
            totalAmount: 120,
            currencyCode: 'CHF',
            history: [],
        };
        renderWorkspace({ operations: { ...operations, reservations: [waitlisted] } });

        await userEvent.click(screen.getByRole('button', { name: 'Bestätigen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/reservations/61/confirm?businessDate=2026-07-28',
            { guaranteeStatus: 'CREDIT_CARD' },
        );
    });
});
