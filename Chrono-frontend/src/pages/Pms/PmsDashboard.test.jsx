/** @vitest-environment jsdom */
import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
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
    put: vi.fn((_url, body) => Promise.resolve({
        data: {
            schemaVersion: 1,
            revision: Number(body?.revision ?? 0) + 1,
            payload: body?.payload ?? {},
        },
    })),
}));
const refreshHookMock = vi.hoisted(() => vi.fn());

vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav>Chrono navigation</nav> }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
vi.mock('../../hooks/useRefreshOnMutation.js', () => ({ useRefreshOnMutation: refreshHookMock }));

import PmsDashboard from './PmsDashboard.jsx';

const emptyOperationsForTest = {
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
    roomBlocks: [],
    maintenanceWorkOrders: [],
};

const localDateKey = (value) => [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0'),
].join('-');

const HistoryProbe = () => {
    const location = useLocation();
    const navigate = useNavigate();
    return (
        <div hidden>
            <span data-testid="pms-location">{location.pathname}{location.search}</span>
            <button data-testid="pms-back" type="button" onClick={() => navigate(-1)}>Back</button>
        </div>
    );
};

const renderDashboard = (initialEntries = ['/pms']) => render(
    <MemoryRouter initialEntries={initialEntries}>
    <AuthContext.Provider
        value={{
            currentUser: {
                id: 7,
                companyId: 23,
                username: 'Christopher',
                firstName: 'Raja',
                lastName: 'Siefert',
                companyFeatureKeys: ['pms'],
                roles: ['ROLE_ADMIN'],
                pagePermissions: { pms: 'MANAGE', pmsSettings: 'MANAGE' },
            },
        }}
    >
        <PmsDashboard />
        <HistoryProbe />
    </AuthContext.Provider>
    </MemoryRouter>
);

describe('PmsDashboard', () => {
    beforeEach(() => {
        window.localStorage.clear();
        apiMock.get.mockImplementation(() => Promise.resolve({
            data: {
                properties: [],
                totalProperties: 0,
                totalRoomTypes: 0,
                totalRooms: 0,
                foundationComplete: false,
            },
        }));
        apiMock.put.mockClear();
        refreshHookMock.mockClear();
    });

    it('keeps no-property personalization local without sending an invalid PMS preference request', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        await userEvent.click(screen.getByRole('button', { name: 'Übersicht anpassen' }));
        const editor = screen.getByRole('complementary', { name: 'PMS-Übersicht' });

        await userEvent.click(within(editor).getByRole('button', { name: 'Anreisen: Entfernen', exact: true }));

        expect(screen.queryByText('Keine Anreisen vorhanden')).not.toBeInTheDocument();
        expect(screen.getByText('Betriebstag')).toBeInTheDocument();
        expect(screen.getByText('Lokal auf diesem Gerät gespeichert')).toBeInTheDocument();
        expect(apiMock.put).not.toHaveBeenCalled();
        expect(apiMock.get.mock.calls.some(([url]) => url === '/api/ui/preferences/PMS_DASHBOARD')).toBe(false);
    });

    it('allows an individual metric to replace the grouped metrics and restores that personal selection', async () => {
        const view = renderDashboard();
        await screen.findByText('Hotel einrichten');
        expect(document.querySelector('[data-dashboard-widget="metric-occupancy"]')).toBeNull();
        await userEvent.click(screen.getByRole('button', { name: 'Übersicht anpassen' }));
        const editor = screen.getByRole('complementary', { name: 'PMS-Übersicht' });
        await userEvent.click(within(editor).getByRole('button', { name: 'Kennzahlen: Entfernen', exact: true }));
        await userEvent.click(within(editor).getByRole('button', { name: 'Kennzahl: Auslastung: Hinzufügen', exact: true }));
        expect(document.querySelector('[data-dashboard-widget="metrics"]')).toBeNull();
        expect(within(document.querySelector('[data-dashboard-widget="metric-occupancy"]')).getByText('0 %')).toBeInTheDocument();

        view.unmount();
        renderDashboard();
        await screen.findByText('Hotel einrichten');
        expect(document.querySelector('[data-dashboard-widget="metrics"]')).toBeNull();
        expect(document.querySelector('[data-dashboard-widget="metric-occupancy"]')).toBeInTheDocument();
    });

    it('lets a separately placed quick action start its usual reception workflow', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');
        await userEvent.click(screen.getByRole('button', { name: 'Übersicht anpassen' }));
        const editor = screen.getByRole('complementary', { name: 'PMS-Übersicht' });
        await userEvent.click(within(editor).getByRole('button', { name: 'Schnellaktionen: Entfernen', exact: true }));
        await userEvent.click(within(editor).getByRole('button', { name: 'Aktion: Gast vor Ort aufnehmen: Hinzufügen', exact: true }));
        const action = document.querySelector('[data-dashboard-widget="action-walk-in"]');
        expect(action).toBeInTheDocument();
        await userEvent.click(within(action).getByRole('button', { name: /^Gast vor Ort aufnehmen/ }));
        await waitFor(() => expect(screen.getByTestId('pms-location')).toHaveTextContent('/pms?section=reservations'));
    });

    it('scopes dashboard preferences to the active property', async () => {
        const property = {
            id: 5,
            name: 'Chrono Test Hotel',
            currencyCode: 'CHF',
            roomTypes: [],
            rooms: [],
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [property],
                    totalProperties: 1,
                    totalRoomTypes: 0,
                    totalRooms: 0,
                    foundationComplete: false,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
            }
            if (url === '/api/ui/preferences/PMS_DASHBOARD') {
                return Promise.resolve({ data: { schemaVersion: 1, revision: 0, payload: {} } });
            }
            return Promise.resolve({ data: emptyOperationsForTest });
        });

        renderDashboard();
        await screen.findAllByText('Chrono Test Hotel');

        const dashboard = document.querySelector('[data-dashboard-context="PMS"]');
        expect(dashboard).toHaveAttribute('data-dashboard-scope', 'property:5');
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(
            '/api/ui/preferences/PMS_DASHBOARD',
            { params: { propertyId: 5 } },
        ));
    });

    it('renders an honest operational dashboard without invented hotel data', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        expect(screen.getByRole('heading', { name: /Guten Tag, Raja Siefert/i })).toBeInTheDocument();
        expect(screen.getByText('Keine Anreisen vorhanden')).toBeInTheDocument();
        expect(screen.getByText('Status- und Schnittstellenprüfung aktiv')).toBeInTheDocument();
        expect(screen.getByText('Hotel und Betriebsdaten anlegen')).toBeInTheDocument();
    });

    it('always displays all professional sections and shortcuts without a mode switch', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        expect(screen.queryByRole('button', { name: 'Einfach' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Profi' })).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Bedienmodus')).not.toBeInTheDocument();

        expect(screen.getByText('Ctrl N')).toBeInTheDocument();
        expect(screen.getByText('Ctrl I')).toBeInTheDocument();
        expect(screen.getByText('Ctrl O')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Hotelportfolio' })).toBeInTheDocument();
        expect(screen.getByText('Verkauf & Partner')).toBeInTheDocument();
    });

    it('offers a dedicated walk-in flow and opens it with the advertised shortcut', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        const quickActions = screen.getByRole('heading', { name: 'Schnellaktionen' }).closest('section');
        expect(within(quickActions).getByRole('button', { name: /Gast vor Ort aufnehmen/i })).toBeInTheDocument();
        expect(within(quickActions).getByRole('button', { name: /Reservierung anlegen/i })).toBeInTheDocument();

        fireEvent.keyDown(window, { key: 'w', altKey: true });

        await waitFor(() => expect(screen.getByTestId('pms-location'))
            .toHaveTextContent('/pms?section=reservations'));
        expect(screen.getByText('Zuerst ein Hotel einrichten')).toBeInTheDocument();
    });

    it('returns a walk-in from a planning date to the actual local hotel day', async () => {
        const property = {
            id: 5,
            name: 'Chrono Test Hotel',
            currencyCode: 'CHF',
            roomTypes: [],
            rooms: [],
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') {
                return Promise.resolve({ data: {
                    properties: [property],
                    totalProperties: 1,
                    totalRoomTypes: 0,
                    totalRooms: 0,
                    foundationComplete: false,
                } });
            }
            if (url === '/api/pms/health') {
                return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
            }
            if (url === '/api/ui/preferences/PMS_DASHBOARD') {
                return Promise.resolve({ data: { schemaVersion: 1, revision: 0, payload: {} } });
            }
            return Promise.resolve({ data: emptyOperationsForTest });
        });
        renderDashboard();
        await screen.findAllByText('Chrono Test Hotel');

        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        await userEvent.click(screen.getByRole('button', { name: 'Nächster Tag' }));
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/operations',
            expect.objectContaining({
                params: { propertyId: 5, businessDate: localDateKey(tomorrow) },
            }),
        ));

        apiMock.get.mockClear();
        fireEvent.keyDown(window, { key: 'w', altKey: true });

        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/operations',
            expect.objectContaining({
                params: { propertyId: 5, businessDate: localDateKey(new Date()) },
            }),
        ));
        expect(await screen.findByRole('heading', { name: 'Walk-in vollständig aufnehmen' })).toBeInTheDocument();
    });

    it('executes the displayed reception shortcuts instead of only showing labels', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        fireEvent.keyDown(window, { key: 'g', ctrlKey: true });

        await waitFor(() => expect(screen.getByTestId('pms-location'))
            .toHaveTextContent('/pms?section=guests'));
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

    it('shows operations directly on the page and returns through browser history', async () => {
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

        expect(screen.getByRole('heading', { name: 'Reservierungen' })).toBeInTheDocument();
        expect(screen.queryByRole('dialog', { name: 'Reservierungen' })).not.toBeInTheDocument();
        expect(screen.getByTestId('pms-location')).toHaveTextContent('/pms?section=reservations');
        expect(document.body.style.overflow).toBe('');

        fireEvent.click(screen.getByTestId('pms-back'));

        expect(await screen.findByRole('heading', { name: /Guten Tag/i })).toBeInTheDocument();
        expect(screen.getByTestId('pms-location')).toHaveTextContent('/pms');
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
        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/health', expect.objectContaining({
            params: { propertyId: 5 },
        }));
    });

    it('revalidates setup, operations and health after a PMS mutation', async () => {
        const property = {
            id: 5,
            name: 'Chrono Test Hotel',
            currencyCode: 'CHF',
            roomTypes: [{ id: 10, name: 'Doppelzimmer', active: true }],
            rooms: [],
        };
        let setupResponse = {
            properties: [property],
            totalProperties: 1,
            totalRoomTypes: 1,
            totalRooms: 0,
            foundationComplete: false,
        };
        let operationsResponse = emptyOperationsForTest;
        let healthResponse = { status: 'OK', components: [], alerts: [] };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/pms/setup') return Promise.resolve({ data: setupResponse });
            if (url === '/api/pms/operations') return Promise.resolve({ data: operationsResponse });
            if (url === '/api/pms/health') return Promise.resolve({ data: healthResponse });
            if (url === '/api/ui/preferences/PMS_DASHBOARD') {
                return Promise.resolve({ data: { schemaVersion: 1, revision: 0, payload: {} } });
            }
            return Promise.resolve({ data: {} });
        });

        renderDashboard();
        await screen.findAllByText('Chrono Test Hotel');
        await waitFor(() => expect(apiMock.get.mock.calls.some(([url]) => url === '/api/pms/operations')).toBe(true));

        const refreshedRoom = {
            id: 31,
            roomTypeId: 10,
            number: '101',
            operationalStatus: 'IN_SERVICE',
            housekeepingStatus: 'CLEAN',
            currentReservation: null,
        };
        setupResponse = {
            ...setupResponse,
            properties: [{ ...property, rooms: [refreshedRoom] }],
            totalRooms: 1,
            foundationComplete: true,
        };
        operationsResponse = {
            ...emptyOperationsForTest,
            rooms: [refreshedRoom],
            metrics: { totalRooms: 1 },
        };
        healthResponse = {
            status: 'WARNING',
            components: [],
            alerts: [{
                code: 'PMS_REFRESHED',
                severity: 'WARNING',
                title: 'Neu geprüft',
                details: 'Aktualisierte Betriebsdaten.',
                recommendedAction: 'Keine Aktion erforderlich.',
            }],
        };

        const refreshLoader = refreshHookMock.mock.calls.at(-1)?.[1];
        expect(refreshHookMock).toHaveBeenLastCalledWith(
            ['pms'],
            expect.any(Function),
            {
                debounceMs: 120,
                refreshOnFocus: true,
                focusThrottleMs: 30_000,
            },
        );
        await act(async () => {
            await refreshLoader();
        });

        expect(await screen.findByLabelText('1 Zimmer eingerichtet')).toBeInTheDocument();
        expect(screen.getByText('Neu geprüft')).toBeInTheDocument();
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
