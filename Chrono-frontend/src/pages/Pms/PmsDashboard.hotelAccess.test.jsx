/** @vitest-environment jsdom */
import React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../../context/AuthContext.jsx';

const apiMock = vi.hoisted(() => ({ get: vi.fn((url) => Promise.resolve({ data: url === '/api/pms/access/me'
    ? { userId: 7, master: false, properties: [] }
    : { properties: [], totalProperties: 0, totalRoomTypes: 0, totalRooms: 0, foundationComplete: false } })) }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav>Chrono</nav> }));
vi.mock('../../components/dashboard/ConfigurableDashboard.jsx', () => ({ default: () => <div>Hotelkennzahlen</div> }));
vi.mock('../../hooks/useRefreshOnMutation.js', () => ({ useRefreshOnMutation: () => {} }));
vi.mock('./PmsOperationsWorkspace.jsx', () => ({ default: ({ property, operations }) => <div data-testid="scoped-operations">{property?.name}:{operations.guests.map((guest) => guest.firstName).join(',')}</div> }));
vi.mock('./PmsSetupWorkspace.jsx', () => ({ default: ({ setup, onPropertyChange, onClose }) => <div>{setup.properties.map((property) => <button key={property.id} onClick={() => { onPropertyChange(property.id); onClose(); }}>Wechsel zu {property.name}</button>)}</div> }));
import PmsDashboard from './PmsDashboard.jsx';

afterEach(cleanup);
describe('PMS employee without hotel grants', () => {
    it.each(['/pms', '/pms?section=reservations'])('explains the missing hotel assignment on %s without offering hotel setup', async (url) => {
        render(<MemoryRouter initialEntries={[url]}><AuthContext.Provider value={{ currentUser: {
            id: 7, companyId: 23, username: 'Reception', roles: ['ROLE_USER'], companyFeatureKeys: ['pms'], pagePermissions: { pms: 'MANAGE' },
        } }}><PmsDashboard /></AuthContext.Provider></MemoryRouter>);
        expect(await screen.findByRole('heading', { name: 'Hotelzuweisung erforderlich' })).toBeInTheDocument();
        expect(screen.getByText(/Ein PMS-Master muss dir das gewünschte Hotel/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Kein Hotel zugewiesen/ })).toBeDisabled();
        expect(screen.queryByText('Hotel einrichten')).not.toBeInTheDocument();
        expect(screen.queryByText('Einrichtung beginnen')).not.toBeInTheDocument();
        expect(screen.queryByText('Zuerst ein Hotel einrichten')).not.toBeInTheDocument();
        expect(screen.queryByText('Hotelkennzahlen')).not.toBeInTheDocument();
        expect(screen.queryByText('PMS betriebsbereit')).not.toBeInTheDocument();
    });
    it('never gives the new hotel workspace a cached guest from the previous hotel while its request is pending', async () => {
        let resolveBeta;
        const hotels = [{ id: 5, name: 'Alpha', currencyCode: 'CHF', roomTypes: [] }, { id: 6, name: 'Beta', currencyCode: 'CHF', roomTypes: [] }];
        const empty = { metrics: {}, guests: [], reservations: [], arrivals: [], departures: [], rooms: [], ratePlans: [], roomBlocks: [], folios: [], housekeepingTasks: [] };
        apiMock.get.mockImplementation((path, options) => {
            if (path === '/api/pms/setup') return Promise.resolve({ data: { properties: hotels, totalProperties: 2, totalRoomTypes: 0, totalRooms: 0 } });
            if (path === '/api/pms/access/me') return Promise.resolve({ data: { master: true, properties: [] } });
            if (path === '/api/pms/operations' && options.params.propertyId === 6) return new Promise((resolve) => { resolveBeta = resolve; });
            if (path === '/api/pms/operations') return Promise.resolve({ data: { ...empty, propertyId: 5, guests: [{ firstName: 'Anna' }] } });
            return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
        });
        render(<MemoryRouter initialEntries={['/pms?section=guests']}><AuthContext.Provider value={{ currentUser: { id: 7, username: 'Master', companyId: 23, roles: ['ROLE_ADMIN'], companyFeatureKeys: ['pms'], pagePermissions: { pms: 'MANAGE', pmsSettings: 'MANAGE' } } }}><PmsDashboard /></AuthContext.Provider></MemoryRouter>);
        await screen.findByText('Alpha:Anna');
        fireEvent.click(screen.getByRole('button', { name: /Aktives Hotel Alpha/ }));
        fireEvent.click(await screen.findByRole('button', { name: 'Wechsel zu Beta' }));
        expect(screen.getByTestId('scoped-operations').textContent).toBe('Beta:');
        await act(async () => resolveBeta({ data: { ...empty, propertyId: 6, guests: [{ firstName: 'Ben' }] } }));
        expect(screen.getByTestId('scoped-operations').textContent).toBe('Beta:Ben');
    });
});
