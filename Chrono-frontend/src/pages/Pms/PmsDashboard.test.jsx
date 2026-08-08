/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../context/AuthContext.jsx';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(() => Promise.resolve({
        data: {
            properties: [],
            totalProperties: 0,
            totalRoomTypes: 0,
            totalRooms: 0,
            foundationComplete: false,
        },
    })),
}));

vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav>Chrono navigation</nav> }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsDashboard from './PmsDashboard.jsx';

const renderDashboard = () => render(
    <AuthContext.Provider
        value={{
            currentUser: {
                username: 'Christopher',
                firstName: 'Raja',
                lastName: 'Siefert',
                pagePermissions: { pms: 'MANAGE' },
            },
        }}
    >
        <PmsDashboard />
    </AuthContext.Provider>
);

describe('PmsDashboard', () => {
    beforeEach(() => {
        apiMock.get.mockImplementation(() => Promise.resolve({
            data: {
                properties: [],
                totalProperties: 0,
                totalRoomTypes: 0,
                totalRooms: 0,
                foundationComplete: false,
            },
        }));
    });

    it('renders an honest operational dashboard without invented hotel data', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        expect(screen.getByRole('heading', { name: /Guten Tag, Raja Siefert/i })).toBeInTheDocument();
        expect(screen.getByText('Keine Anreisen vorhanden')).toBeInTheDocument();
        expect(screen.getByText('Status- und Schnittstellenprüfung aktiv')).toBeInTheDocument();
        expect(screen.getByText('Hotel und Betriebsdaten anlegen')).toBeInTheDocument();
    });

    it('switches to pro mode and displays keyboard shortcuts', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        await userEvent.click(screen.getByRole('button', { name: 'Profi' }));

        expect(screen.getByText('Ctrl N')).toBeInTheDocument();
        expect(screen.getByText('Ctrl I')).toBeInTheDocument();
        expect(screen.getByText('Ctrl O')).toBeInTheDocument();
    });

    it('opens the command palette with Ctrl+K and filters commands', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');
        fireEvent.keyDown(window, { key: 'k', ctrlKey: true });

        const search = screen.getByLabelText('PMS durchsuchen');
        expect(search).toHaveFocus();
        expect(document.body).toHaveStyle({ overflow: 'hidden' });

        await userEvent.type(search, 'Zimmerplan');

        expect(screen.getByRole('button', { name: /Zimmerplan öffnen/i })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Check-in starten/i })).not.toBeInTheDocument();
        fireEvent.keyDown(window, { key: 'Escape' });

        expect(screen.queryByRole('dialog', { name: 'Schnellaktionen' })).not.toBeInTheDocument();
        await waitFor(() => expect(document.body.style.overflow).toBe(''));
    });

    it('opens the real hotel setup workspace from the setup call to action', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        await userEvent.click(screen.getByRole('button', { name: 'Einrichtung beginnen' }));

        expect(screen.getByRole('dialog', { name: 'Hoteleinrichtung' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Hotel anlegen' })).toBeInTheDocument();
        expect(screen.getByLabelText('Hotelcode')).toBeInTheDocument();
    });

    it('closes the setup dialog with Escape and restores page scrolling', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        await userEvent.click(screen.getByRole('button', { name: 'Einrichtung beginnen' }));

        expect(screen.getByRole('dialog', { name: 'Hoteleinrichtung' })).toBeInTheDocument();
        expect(document.body).toHaveStyle({ overflow: 'hidden' });

        fireEvent.keyDown(window, { key: 'Escape' });

        expect(screen.queryByRole('dialog', { name: 'Hoteleinrichtung' })).not.toBeInTheDocument();
        await waitFor(() => expect(document.body.style.overflow).toBe(''));
    });

    it('closes the operations dialog with Escape', async () => {
        const property = {
            id: 5,
            name: 'Chrono Test Hotel',
            currencyCode: 'CHF',
            roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
            rooms: [],
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [property],
                    totalProperties: 1,
                    totalRoomTypes: 1,
                    totalRooms: 0,
                    foundationComplete: false,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
            }
            return Promise.resolve({ data: {
                metrics: {},
                guests: [],
                reservations: [],
                ratePlans: [],
                rateOverrides: [],
                rooms: [],
                housekeepingTasks: [],
                folios: [],
            } });
        });
        renderDashboard();
        await screen.findAllByText('Chrono Test Hotel');

        await userEvent.click(screen.getByRole('button', { name: 'Reservierungen', exact: true }));

        expect(screen.getByRole('dialog', { name: 'Hotelbetrieb' })).toBeInTheDocument();
        expect(document.body).toHaveStyle({ overflow: 'hidden' });

        fireEvent.keyDown(window, { key: 'Escape' });

        expect(screen.queryByRole('dialog', { name: 'Hotelbetrieb' })).not.toBeInTheDocument();
        await waitFor(() => expect(document.body.style.overflow).toBe(''));
    });

    it('shows database, outbox, audit and backup alarms from operational monitoring', async () => {
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [{ id: 5, name: 'Chrono Zürich', currencyCode: 'CHF', rooms: [] }],
                    totalProperties: 1,
                    totalRoomTypes: 1,
                    totalRooms: 0,
                    foundationComplete: false,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: {
                    status: 'CRITICAL',
                    components: [
                        { key: 'database', label: 'Datenbank', status: 'OK', summary: 'Datenbankabfrage erfolgreich.' },
                        { key: 'outbox', label: 'Externe Übertragungen', status: 'CRITICAL', summary: '0 offen, 0 fehlgeschlagen, 2 endgültig fehlgeschlagen.' },
                        { key: 'audit', label: 'Änderungsprotokoll', status: 'OK', summary: '10 letzte Ereignisse geprüft, 0 ungültig.' },
                        { key: 'backup', label: 'Datensicherung', status: 'WARNING', summary: 'Sicherung nicht aktiviert.' },
                    ],
                    alerts: [{
                        code: 'PMS_OUTBOX_DEAD_LETTER',
                        severity: 'CRITICAL',
                        title: 'Integrationsereignisse endgültig fehlgeschlagen',
                        details: '2 Übertragungen sind endgültig fehlgeschlagen.',
                        recommendedAction: 'Schnittstellen & Integrationen öffnen.',
                    }],
                } });
            }
            return Promise.resolve({ data: {
                ...{
                    metrics: {},
                    reservations: [],
                    arrivals: [],
                    departures: [],
                    guests: [],
                    ratePlans: [],
                    rateOverrides: [],
                    rooms: [],
                    housekeepingTasks: [],
                    folios: [],
                },
            } });
        });

        renderDashboard();

        expect(await screen.findByText('Kritischer Betriebsalarm')).toBeInTheDocument();
        expect(screen.getByText('Datenbankabfrage erfolgreich.')).toBeInTheDocument();
        expect(screen.getByText('Integrationsereignisse endgültig fehlgeschlagen')).toBeInTheDocument();
        expect(screen.getByText('Kritisch')).toBeInTheDocument();
        expect(screen.queryByText('CRITICAL')).not.toBeInTheDocument();
        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/health', { params: { propertyId: 5 } });
    });

    it('keeps cleaning, occupancy and sellability separate in the room status summary', async () => {
        const property = {
            id: 5,
            name: 'Chrono Zürich',
            currencyCode: 'CHF',
            roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
            rooms: [],
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [property],
                    totalProperties: 1,
                    totalRoomTypes: 1,
                    totalRooms: 5,
                    foundationComplete: true,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
            }
            return Promise.resolve({ data: {
                metrics: { totalRooms: 3, occupiedRooms: 1, dirtyRooms: 1, openBalance: 0 },
                reservations: [],
                arrivals: [],
                departures: [],
                guests: [],
                ratePlans: [],
                rateOverrides: [],
                housekeepingTasks: [],
                folios: [],
                rooms: [
                    { id: 1, operationalStatus: 'IN_SERVICE', housekeepingStatus: 'CLEAN', currentReservation: null },
                    { id: 2, operationalStatus: 'IN_SERVICE', housekeepingStatus: 'DIRTY', currentReservation: null },
                    { id: 3, operationalStatus: 'IN_SERVICE', housekeepingStatus: 'IN_PROGRESS', currentReservation: {} },
                    { id: 4, operationalStatus: 'OUT_OF_ORDER', housekeepingStatus: 'CLEAN', currentReservation: null },
                    { id: 5, operationalStatus: 'FUTURE_STATUS', housekeepingStatus: 'CLEAN', currentReservation: null },
                ],
                roomBlocks: [{
                    id: 8,
                    roomId: 1,
                    type: 'OUT_OF_SERVICE',
                    status: 'ACTIVE',
                    startDate: '2026-01-01',
                    endDate: '2027-01-01',
                }],
            } });
        });

        renderDashboard();
        const panel = (await screen.findByRole('heading', { name: 'Zimmerstatus' })).closest('section');
        const hasLegendValue = (expected) => (_, element) =>
            element.tagName === 'SPAN'
            && element.textContent.replace(/\s+/g, ' ').trim() === expected;

        await waitFor(() => {
            expect(within(panel).getByText(hasLegendValue('Sauber & frei 0'))).toBeInTheDocument();
            expect(within(panel).getByText(hasLegendValue('Zu reinigen 1'))).toBeInTheDocument();
            expect(within(panel).getByText(hasLegendValue('Reinigung läuft 1'))).toBeInTheDocument();
            expect(within(panel).getByText(hasLegendValue('Eingeschränkter Betrieb (OOS) 1')))
                .toBeInTheDocument();
            expect(within(panel).getByText(hasLegendValue('Nicht verkaufbar 2'))).toBeInTheDocument();
        });
    });

    it('offers check-in only for confirmed arrivals and renders other arrival states as statuses', async () => {
        const property = {
            id: 5,
            name: 'Chrono Zürich',
            currencyCode: 'CHF',
            checkInTime: '15:00:00',
            roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
            rooms: [],
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [property],
                    totalProperties: 1,
                    totalRoomTypes: 1,
                    totalRooms: 3,
                    foundationComplete: true,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
            }
            return Promise.resolve({ data: {
                metrics: {
                    totalRooms: 3,
                    occupiedRooms: 2,
                    availableRooms: 1,
                    occupancyPercent: 67,
                    inHouse: 1,
                    arrivals: 2,
                },
                reservations: [],
                arrivals: [
                    {
                        id: 1,
                        guestName: 'Gabriela Tschopp',
                        confirmationCode: 'CHR-1001',
                        roomNumber: '101',
                        roomTypeName: 'Doppelzimmer',
                        status: 'CONFIRMED',
                    },
                    {
                        id: 2,
                        guestName: 'Raja Siefert',
                        confirmationCode: 'CHR-1002',
                        roomNumber: '102',
                        roomTypeName: 'Doppelzimmer',
                        status: 'CHECKED_IN',
                    },
                    {
                        id: 3,
                        guestName: 'Christopher Test',
                        confirmationCode: 'CHR-1003',
                        roomNumber: '103',
                        roomTypeName: 'Doppelzimmer',
                        status: 'TENTATIVE',
                    },
                ],
                departures: [],
                guests: [],
                ratePlans: [],
                rateOverrides: [],
                rooms: [],
                housekeepingTasks: [],
                folios: [],
                roomBlocks: [],
                maintenanceWorkOrders: [],
            } });
        });

        renderDashboard();
        const arrivalsPanel = (await screen.findByRole('heading', { name: 'Anreisen heute' }))
            .closest('section');

        await waitFor(() => {
            expect(within(arrivalsPanel).getByRole('button', { name: 'Einchecken', exact: true }))
                .toBeInTheDocument();
            expect(within(arrivalsPanel).getByText('Eingecheckt')).toBeInTheDocument();
            expect(within(arrivalsPanel).getByText('Option')).toBeInTheDocument();
            expect(within(arrivalsPanel).queryByRole('button', { name: 'Eingecheckt' }))
                .not.toBeInTheDocument();
            expect(within(arrivalsPanel).queryByRole('button', { name: 'Option' }))
                .not.toBeInTheDocument();
        });
        expect(screen.getByText('2 von 3 Zimmern verkauft oder belegt')).toBeInTheDocument();
    });
});
