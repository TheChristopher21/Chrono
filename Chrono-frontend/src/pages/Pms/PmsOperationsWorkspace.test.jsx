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
        apiMock.post.mockResolvedValue({ data: operations });
        apiMock.put.mockResolvedValue({ data: operations });
    });

    it('creates a complete reservation with guest, rate and optional room assignment', async () => {
        const { onOperationsChange } = renderWorkspace();

        await userEvent.selectOptions(screen.getByLabelText('Gast'), '7');
        await waitFor(() => expect(screen.getByLabelText('Ratenplan')).toHaveValue('20'));
        await userEvent.selectOptions(screen.getByLabelText('Zimmer (optional)'), '30');
        await userEvent.click(screen.getByRole('button', { name: 'Reservierung anlegen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/reservations?businessDate=2026-07-28',
            expect.objectContaining({
                propertyId: 5,
                guestId: 7,
                roomTypeId: 10,
                roomId: 30,
                ratePlanId: 20,
                status: 'CONFIRMED',
            }),
        );
        expect(onOperationsChange).toHaveBeenCalledWith(operations);
        expect(await screen.findByText('Reservierung angelegt.')).toBeInTheDocument();
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

        await userEvent.click(screen.getByRole('button', { name: 'Verfügbarkeit prüfen' }));

        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/properties/5/availability', {
            params: expect.objectContaining({
                arrival: '2026-07-28',
            }),
        });
        expect(await screen.findByText('1 von 1 verfügbar')).toBeInTheDocument();
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

    it('separates housekeeping from the dated operational and sales status in the room plan', () => {
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

        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });

        const availableRoom = screen.getByText('101').closest('article');
        const outOfOrderRoom = screen.getByText('102').closest('article');
        const ownerUseRoom = screen.getByText('103').closest('article');
        const outOfServiceRoom = screen.getByText('104').closest('article');
        const permanentlyUnavailableRoom = screen.getByText('105').closest('article');
        const unknownStatusRoom = screen.getByText('106').closest('article');

        expect(within(availableRoom).getByText('Housekeeping')).toBeInTheDocument();
        expect(within(availableRoom).getByText('Betriebs-/Verkaufsstatus')).toBeInTheDocument();
        expect(within(availableRoom).getByText('Frei und zuweisbar')).toBeInTheDocument();

        expect(within(outOfOrderRoom).getByText('Ausser Betrieb – nicht verkaufbar (OOO)')).toBeInTheDocument();
        expect(within(outOfOrderRoom).getByText('Nicht belegbar und nicht zuweisbar')).toBeInTheDocument();
        expect(within(outOfOrderRoom).queryByText(/^Frei/)).not.toBeInTheDocument();

        expect(within(ownerUseRoom).getByText('Eigennutzung – nicht verkaufbar')).toBeInTheDocument();
        expect(within(ownerUseRoom).getByText('Eigennutzung – nicht zuweisbar')).toBeInTheDocument();
        expect(within(ownerUseRoom).queryByText(/^Frei/)).not.toBeInTheDocument();

        expect(within(outOfServiceRoom).getByText('Eingeschränkter Betrieb – weiterhin zuweisbar (OOS)')).toBeInTheDocument();
        expect(within(outOfServiceRoom).getByText('Frei · eingeschränkt, aber zuweisbar')).toBeInTheDocument();

        expect(within(permanentlyUnavailableRoom).getByText('Technisch ausser Betrieb – nicht verkaufbar')).toBeInTheDocument();
        expect(within(permanentlyUnavailableRoom).queryByText(/^Frei/)).not.toBeInTheDocument();

        expect(within(unknownStatusRoom).getByText('Unbekannt')).toBeInTheDocument();
        expect(within(unknownStatusRoom).getByText('Betriebsstatus unbekannt – nicht zuweisbar')).toBeInTheDocument();
        expect(within(unknownStatusRoom).queryByText(/^Frei/)).not.toBeInTheDocument();
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

    it('prevents all operational writes for a view-only user', () => {
        renderWorkspace({ canManage: false });

        expect(screen.getByText(/Du hast Lesezugriff/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Reservierung anlegen' })).toBeDisabled();
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
            })],
        }));
        expect(await screen.findByText(/Gastprofil wurde anonymisiert/)).toBeInTheDocument();
    });

    it('moves an open reservation between compatible rooms with drag and drop', async () => {
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
            arrivalDate: '2026-07-28',
            departureDate: '2026-07-29',
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
        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });
        expect(screen.getByRole('heading', { name: 'Zimmerplan für 28.07.2026' }))
            .toBeInTheDocument();
        const data = {};
        const dataTransfer = {
            setData: (type, value) => { data[type] = value; },
            getData: (type) => data[type],
        };
        const guestButton = screen.getByRole('button', { name: /Gabriela Tschopp/ });
        const targetRoom = screen.getByText('102').closest('article');

        fireEvent.dragStart(guestButton, { dataTransfer });
        fireEvent.dragOver(targetRoom, { dataTransfer });
        fireEvent.drop(targetRoom, { dataTransfer });

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/reservations/60/move-room?businessDate=2026-07-28',
            { roomId: 31, reason: 'Verschoben im Zimmerplan' },
        ));
    });

    it('prevents moving a reservation onto an OOO room', async () => {
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
            arrivalDate: '2026-07-28',
            departureDate: '2026-07-29',
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
                startDate: '2026-07-28',
                endDate: '2026-07-30',
            }],
        };
        renderWorkspace({ section: 'room-plan', operations: roomPlanOperations });
        const data = {};
        const dataTransfer = {
            setData: (type, value) => { data[type] = value; },
            getData: (type) => data[type],
        };

        fireEvent.dragStart(screen.getByRole('button', { name: /Gabriela Tschopp/ }), { dataTransfer });
        fireEvent.drop(screen.getByText('102').closest('article'), { dataTransfer });

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
