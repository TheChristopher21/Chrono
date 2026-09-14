import { useState } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    publishDataRefresh,
    resetDataRefreshCoordinator,
} from '../../../utils/dataRefresh';

const mocks = vi.hoisted(() => ({
    api: { get: vi.fn() },
    notify: vi.fn(),
    t: (_key, fallback) => fallback ?? _key,
}));

vi.mock('../../../utils/api', () => ({ default: mocks.api }));
vi.mock('../../../context/LanguageContext', () => ({
    useTranslation: () => ({ t: mocks.t, language: 'de' }),
}));
vi.mock('../../../context/NotificationContext', () => ({
    useNotification: () => ({ notify: mocks.notify }),
}));
vi.mock('../../../components/Navbar', () => ({ default: () => null }));
vi.mock('../EditTimeModal', () => ({ default: () => null }));
vi.mock('../../../components/VacationCalendarAdmin', () => ({
    default: ({ vacationRequests, focusUsername, onReloadVacations }) => {
        const [month, setMonth] = useState('September');
        const [draft, setDraft] = useState('');
        const [refreshed, setRefreshed] = useState(false);
        return (
            <section data-testid="vacation-calendar" aria-label={`Kalender für ${focusUsername}`}>
                <button onClick={() => setMonth('Oktober')}>Nächster Kalendermonat</button>
                <span>{month}</span>
                <input aria-label="Offener Urlaubsentwurf" value={draft} onChange={(event) => setDraft(event.target.value)} />
                <button onClick={async () => { await onReloadVacations(); setRefreshed(true); }}>Nach Speicherung aktualisieren</button>
                {refreshed && <span>Aktualisierung abgeschlossen</span>}
                <output data-testid="calendar-entries">{vacationRequests.map((entry) => entry.id).join(',')}</output>
            </section>
        );
    },
}));

import AdminEmployeeOverviewPage from '../AdminEmployeeOverviewPage';

const employee = { username: 'Mirjam', firstName: 'Mirjam', lastName: 'Burkart', annualVacationDays: 25 };
const vacation = (id) => ({ id, username: 'Mirjam', startDate: '2026-10-05', endDate: '2026-10-09', approved: true });
const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
};
const requestsFor = (path) => mocks.api.get.mock.calls.filter(([url]) => url === path);
const renderOverview = () => render(
    <MemoryRouter initialEntries={['/admin/dashboard/mitarbeiter/Mirjam']}>
        <Routes>
            <Route path="/admin/dashboard/mitarbeiter/:username" element={<AdminEmployeeOverviewPage />} />
        </Routes>
    </MemoryRouter>,
);

beforeEach(() => {
    resetDataRefreshCoordinator();
    mocks.notify.mockReset();
    mocks.api.get.mockReset().mockImplementation((url) => {
        if (url === '/api/admin/users') return Promise.resolve({ data: [employee] });
        if (url === '/api/holidays/details' || url === '/api/timetracking/period-summary') return Promise.resolve({ data: {} });
        return Promise.resolve({ data: [] });
    });
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
});

afterEach(() => {
    cleanup();
    resetDataRefreshCoordinator();
    vi.restoreAllMocks();
});

describe('employee overview background reconciliation', () => {
    it('keeps the calendar, selected month and draft mounted while loading saved vacations', async () => {
        const { container } = renderOverview();
        const calendar = await screen.findByTestId('vacation-calendar');
        fireEvent.click(screen.getByRole('button', { name: 'Nächster Kalendermonat' }));
        fireEvent.change(screen.getByRole('textbox', { name: 'Offener Urlaubsentwurf' }), { target: { value: 'Nächster Urlaub' } });
        const periodRequestsBeforeRefresh = requestsFor('/api/timetracking/period-summary').length;

        const refreshedVacations = deferred();
        const initialImplementation = mocks.api.get.getMockImplementation();
        mocks.api.get.mockImplementation((url, config) => url === '/api/vacation/all'
            ? refreshedVacations.promise
            : initialImplementation(url, config));

        act(() => publishDataRefresh(['absence']));
        await waitFor(() => expect(requestsFor('/api/vacation/all')).toHaveLength(2));
        expect(screen.getByTestId('vacation-calendar')).toBe(calendar);
        expect(container.querySelector('.employee-overview-skeleton-grid')).not.toBeInTheDocument();
        expect(screen.getByText('Oktober')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Offener Urlaubsentwurf' })).toHaveValue('Nächster Urlaub');

        await act(async () => refreshedVacations.resolve({ data: [vacation(17)] }));
        expect(screen.getByTestId('calendar-entries')).toHaveTextContent('17');
        expect(screen.getByTestId('vacation-calendar')).toBe(calendar);
        expect(screen.getByText('Oktober')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Offener Urlaubsentwurf' })).toHaveValue('Nächster Urlaub');
        expect(requestsFor('/api/timetracking/period-summary')).toHaveLength(periodRequestsBeforeRefresh + 1);
    });

    it('ignores an old initial response when a mutation refresh finishes first', async () => {
        const initialVacations = deferred();
        const initialImplementation = mocks.api.get.getMockImplementation();
        let vacationRequestCount = 0;
        mocks.api.get.mockImplementation((url, config) => {
            if (url !== '/api/vacation/all') return initialImplementation(url, config);
            vacationRequestCount += 1;
            return vacationRequestCount === 1 ? initialVacations.promise : Promise.resolve({ data: [vacation(22)] });
        });
        renderOverview();
        const initialSignal = requestsFor('/api/vacation/all')[0][1].signal;
        act(() => publishDataRefresh(['absence']));
        await screen.findByTestId('vacation-calendar');
        expect(initialSignal.aborted).toBe(true);
        expect(screen.getByTestId('calendar-entries')).toHaveTextContent('22');

        await act(async () => initialVacations.resolve({ data: [vacation(11)] }));
        expect(screen.getByTestId('calendar-entries')).toHaveTextContent('22');
        expect(mocks.notify).not.toHaveBeenCalled();
    });

    it('retains the calendar and unsaved draft when a background request fails', async () => {
        renderOverview();
        const calendar = await screen.findByTestId('vacation-calendar');
        fireEvent.change(screen.getByRole('textbox', { name: 'Offener Urlaubsentwurf' }), { target: { value: 'Bleibt offen' } });
        const initialImplementation = mocks.api.get.getMockImplementation();
        mocks.api.get.mockImplementation((url, config) => url === '/api/vacation/all'
            ? Promise.reject(new Error('Temporär nicht erreichbar'))
            : initialImplementation(url, config));
        vi.spyOn(console, 'error').mockImplementation(() => {});

        act(() => publishDataRefresh(['absence']));
        await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith('Mitarbeiter-Übersicht konnte nicht geladen werden.', 'error'));
        expect(screen.getByTestId('vacation-calendar')).toBe(calendar);
        expect(screen.getByRole('textbox', { name: 'Offener Urlaubsentwurf' })).toHaveValue('Bleibt offen');
    });

    it('awaits the replacement refresh when a mutation event overlaps the calendar callback', async () => {
        renderOverview();
        await screen.findByTestId('vacation-calendar');
        const calendarRefresh = deferred();
        const mutationRefresh = deferred();
        const pending = [calendarRefresh, mutationRefresh];
        const initialImplementation = mocks.api.get.getMockImplementation();
        mocks.api.get.mockImplementation((url, config) => url === '/api/vacation/all'
            ? pending.shift().promise
            : initialImplementation(url, config));

        fireEvent.click(screen.getByRole('button', { name: 'Nach Speicherung aktualisieren' }));
        act(() => publishDataRefresh(['absence']));
        await waitFor(() => expect(requestsFor('/api/vacation/all')).toHaveLength(3));
        await act(async () => calendarRefresh.resolve({ data: [vacation(10)] }));
        expect(screen.queryByText('Aktualisierung abgeschlossen')).not.toBeInTheDocument();

        await act(async () => mutationRefresh.resolve({ data: [vacation(20)] }));
        expect(screen.getByText('Aktualisierung abgeschlossen')).toBeInTheDocument();
        expect(screen.getByTestId('calendar-entries')).toHaveTextContent('20');
    });
});
