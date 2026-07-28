/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
        expect(screen.getByText('Keine externen Systeme verbunden')).toBeInTheDocument();
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

        expect(screen.getByRole('dialog', { name: 'Hotelfundament' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Hotel anlegen' })).toBeInTheDocument();
        expect(screen.getByLabelText('Hotelcode')).toBeInTheDocument();
    });

    it('closes the setup dialog with Escape and restores page scrolling', async () => {
        renderDashboard();
        await screen.findByText('Hotel einrichten');

        await userEvent.click(screen.getByRole('button', { name: 'Einrichtung beginnen' }));

        expect(screen.getByRole('dialog', { name: 'Hotelfundament' })).toBeInTheDocument();
        expect(document.body).toHaveStyle({ overflow: 'hidden' });

        fireEvent.keyDown(window, { key: 'Escape' });

        expect(screen.queryByRole('dialog', { name: 'Hotelfundament' })).not.toBeInTheDocument();
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
                        { key: 'outbox', label: 'Integrationsqueue', status: 'CRITICAL', summary: '0 offen, 0 fehlgeschlagen, 2 Dead-Letter.' },
                        { key: 'audit', label: 'Audit-Integrität', status: 'OK', summary: '10 letzte Ereignisse geprüft, 0 ungültig.' },
                        { key: 'backup', label: 'Datensicherung', status: 'WARNING', summary: 'Sicherung nicht aktiviert.' },
                    ],
                    alerts: [{
                        code: 'PMS_OUTBOX_DEAD_LETTER',
                        severity: 'CRITICAL',
                        title: 'Integrationsereignisse endgültig fehlgeschlagen',
                        details: '2 Ereignisse liegen in der Dead-Letter-Queue.',
                        recommendedAction: 'Integration Control Center öffnen.',
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
        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/health', { params: { propertyId: 5 } });
    });
});
