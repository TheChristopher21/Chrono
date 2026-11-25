/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
const api = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav>Chrono</nav> }));
vi.mock('../../components/dashboard/ConfigurableDashboard.jsx', () => ({ default: () => <div>Hotelkennzahlen</div> }));
vi.mock('../../hooks/useRefreshOnMutation.js', () => ({ useRefreshOnMutation: () => {} }));
vi.mock('./PmsOperationsWorkspace.jsx', () => ({ default: ({ canManageHousekeeping }) => <div data-testid="housekeeping-workspace">{canManageHousekeeping ? 'Bearbeitbar' : 'Nur lesen'}</div> }));
vi.mock('./PmsSetupWorkspace.jsx', () => ({ default: () => null }));
import { AuthContext } from '../../context/AuthContext.jsx';
import PmsDashboard from './PmsDashboard.jsx';
import { offlineScope, readOffline, writeOffline, enqueueHousekeeping, cacheHousekeeping } from './pmsOfflineStore.js';

const user = { id: 7, companyId: 23, username: 'anna', roles: ['ROLE_USER'], companyFeatureKeys: ['pms'], pagePermissions: { pms: 'MANAGE' } };
const allowed = { userId: 7, master: false, properties: [5, 6].map((propertyId) => ({ propertyId, permissions: { HOUSEKEEPING: 'MANAGE' } })) };
const login = (name) => localStorage.setItem('token', `x.${btoa(JSON.stringify({ sub: name }))}.x`);
let accessReply;
const seed = (propertyId) => { const scope = offlineScope(propertyId); writeOffline(scope, { enabled: true, snapshots: {}, queue: [] }); cacheHousekeeping(scope, 'today', [{ id: 1 }]); enqueueHousekeeping(scope, { id: 1 }, { version: 0 }); return scope; };
const mount = async () => { let view; await act(async () => { view = render(<MemoryRouter initialEntries={['/pms?section=housekeeping']}><AuthContext.Provider value={{ currentUser: user }}><PmsDashboard /></AuthContext.Provider></MemoryRouter>); }); return view; };
beforeEach(() => {
    vi.useFakeTimers(); vi.clearAllMocks(); localStorage.clear(); login('anna');
    accessReply = () => Promise.resolve({ data: allowed });
    api.get.mockImplementation((path) => {
        if (path === '/api/pms/access/me') return accessReply();
        if (path === '/api/pms/setup') return Promise.resolve({ data: { properties: [{ id: 5, name: 'Alpha', currencyCode: 'CHF', roomTypes: [] }, { id: 6, name: 'Beta', currencyCode: 'CHF', roomTypes: [] }], totalProperties: 2 } });
        if (path === '/api/pms/health') return Promise.resolve({ data: { status: 'OK', components: [], alerts: [] } });
        return Promise.resolve({ data: { metrics: {}, guests: [], reservations: [], arrivals: [], departures: [], rooms: [], ratePlans: [], roomBlocks: [], folios: [], housekeepingTasks: [] } });
    });
});
afterEach(() => { cleanup(); vi.useRealTimers(); localStorage.clear(); });
describe('PMS access refresh and device data', () => {
    it('recovers an initially unavailable hotel directory instead of showing a false empty setup', async () => {
        const original = api.get.getMockImplementation(); let connected = false;
        api.get.mockImplementation((path, options) => path === '/api/pms/setup' && !connected
            ? Promise.reject(new TypeError('Network unavailable')) : original(path, options));
        await mount();
        expect(screen.getByRole('heading', { name: 'PMS-Stammdaten nicht erreichbar' })).toBeTruthy();
        expect(screen.queryByTestId('housekeeping-workspace')).toBeNull();
        connected = true;
        await act(async () => { window.dispatchEvent(new Event('online')); });
        expect(screen.getByTestId('housekeeping-workspace')).toHaveTextContent('Bearbeitbar');
        expect(screen.queryByRole('heading', { name: 'PMS-Stammdaten nicht erreichbar' })).toBeNull();
    });
    it('rechecks every minute and removes only the revoked hotel cache and queue', async () => {
        login('ben'); const otherUser = seed(5); login('anna'); const alpha = seed(5); const beta = seed(6);
        await mount(); expect(screen.getByTestId('housekeeping-workspace')).toHaveTextContent('Bearbeitbar');
        accessReply = () => Promise.resolve({ data: { ...allowed, properties: [allowed.properties[1]] } });
        await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
        expect(api.get.mock.calls.filter(([path]) => path === '/api/pms/access/me')).toHaveLength(2);
        expect(readOffline(alpha)).toEqual({ enabled: false, snapshots: {}, queue: [] });
        expect(readOffline(beta).queue).toHaveLength(1); expect(readOffline(otherUser).queue).toHaveLength(1);
        expect(screen.queryByTestId('housekeeping-workspace')).toBeNull();
        expect(screen.getByRole('heading', { name: 'Zugriff nicht freigegeben' })).toBeTruthy();
    });
    it('retains last confirmed permissions and queued work on temporary network and server errors', async () => {
        const scope = seed(5); await mount();
        for (const failure of [new Error('offline'), { response: { status: 503 } }]) {
            accessReply = () => Promise.reject(failure);
            await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
            expect(screen.getByTestId('housekeeping-workspace')).toHaveTextContent('Bearbeitbar'); expect(readOffline(scope).queue).toHaveLength(1);
        }
    });
    it.each([401, 403])('an authoritative HTTP %s clears this JWT user across hotels and denies the workspace', async (status) => {
        const alpha = seed(5); const beta = seed(6); await mount();
        accessReply = () => Promise.reject({ response: { status } });
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        expect(readOffline(alpha).enabled).toBe(false); expect(readOffline(beta).enabled).toBe(false);
        expect(screen.queryByTestId('housekeeping-workspace')).toBeNull();
    });
    it('keeps initial loading and temporary unavailability distinct from an explicit denial', async () => {
        const scope = seed(5); let rejectAccess;
        accessReply = () => new Promise((resolve, reject) => { rejectAccess = reject; });
        await mount(); expect(screen.getByText('PMS-Berechtigungen werden geprüft …')).toBeTruthy(); expect(screen.queryByTestId('housekeeping-workspace')).toBeNull();
        await act(async () => { rejectAccess(new Error('offline')); });
        expect(screen.getByText(/Berechtigungen konnten noch nicht geprüft werden/)).toBeTruthy(); expect(readOffline(scope).queue).toHaveLength(1);
        expect(screen.queryByRole('heading', { name: 'Zugriff nicht freigegeben' })).toBeNull();
    });
    it('aborts on unmount and ignores a delayed denial from a previous token session', async () => {
        const alpha = seed(5); await mount(); let rejectAccess;
        accessReply = () => new Promise((resolve, reject) => { rejectAccess = reject; });
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        login('ben'); const other = seed(5);
        await act(async () => { rejectAccess({ response: { status: 403 } }); });
        expect(readOffline(other).queue).toHaveLength(1); expect(readOffline(alpha).queue).toHaveLength(1);
        cleanup(); const count = api.get.mock.calls.filter(([path]) => path === '/api/pms/access/me').length;
        await act(async () => { await vi.advanceTimersByTimeAsync(60_000); window.dispatchEvent(new Event('focus')); });
        expect(api.get.mock.calls.filter(([path]) => path === '/api/pms/access/me')).toHaveLength(count);
    });
    it('retains confirmed access when an Activity tab is restored during a network interruption', async () => {
        const scope = seed(5);
        const page = (mode) => <MemoryRouter initialEntries={['/pms?section=housekeeping']}><AuthContext.Provider value={{ currentUser: user }}><Activity mode={mode}><PmsDashboard /></Activity></AuthContext.Provider></MemoryRouter>;
        let view; await act(async () => { view = render(page('visible')); });
        expect(screen.getByTestId('housekeeping-workspace')).toHaveTextContent('Bearbeitbar');
        await act(async () => { view.rerender(page('hidden')); });
        accessReply = () => Promise.reject(new Error('offline'));
        await act(async () => { view.rerender(page('visible')); });
        expect(screen.getByTestId('housekeeping-workspace')).toHaveTextContent('Bearbeitbar');
        expect(readOffline(scope).queue).toHaveLength(1);
    });
});
