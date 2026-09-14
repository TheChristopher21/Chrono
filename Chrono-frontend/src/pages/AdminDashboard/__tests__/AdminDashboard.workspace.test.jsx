import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    api: { get: vi.fn(), put: vi.fn(), post: vi.fn() },
    notify: vi.fn(), refresh: null, focus: vi.fn(), createVacation: vi.fn(), action: vi.fn(),
    t: (_key, fallback, vars) => (typeof fallback === 'object' ? fallback.defaultValue : fallback ?? _key).replace(/\{\{(\w+)\}\}/g, (_, key) => (vars || fallback)?.[key] ?? ''),
    user: { id: 1, username: 'admin', roles: ['ROLE_SUPERADMIN'], companyId: 7 },
}));
vi.mock('../../../utils/api', () => ({ default: mocks.api }));
vi.mock('../../../context/AuthContext', () => ({ useAuth: () => ({ currentUser: mocks.user, authToken: 'test-token' }) }));
vi.mock('../../../context/NotificationContext', () => ({ useNotification: () => ({ notify: mocks.notify }) }));
vi.mock('../../../context/LanguageContext', () => ({ useTranslation: () => ({ t: mocks.t }) }));
vi.mock('../../../hooks/useRefreshOnMutation', async () => {
    const { useEffect } = await import('react');
    const { useWorkspacePaneActive } = await import('../../../components/workspace/WorkspacePaneContext.jsx');
    return { useRefreshOnMutation: (_scopes, callback) => {
        const active = useWorkspacePaneActive();
        useEffect(() => { if (active) mocks.refresh = callback; }, [active, callback]);
    } };
});
vi.mock('../../../components/Navbar', () => ({ default: () => null }));
vi.mock('../../../components/AccessiblePagesPanel.jsx', () => ({ default: () => <div>Modules</div> }));
vi.mock('../../../components/dashboard/ConfigurableDashboard.jsx', () => ({ default: ({ registry, scope }) => <div data-testid={`scope-${scope}`}>{registry.map(item => <div key={item.id}>{item.component}</div>)}</div> }));
vi.mock('../AdminDashboardOverview', () => ({ default: () => null }));
vi.mock('../AdminVacationRequests', () => ({ default: ({ openSignal, canManage, onOpenInTimeReview }) => <div>
    <output data-testid="vacation-signal">{openSignal}</output><output data-testid="vacation-can-manage">{String(canManage)}</output>
    <button onClick={() => onOpenInTimeReview({ type: 'vacation', id: 12, username: 'Mirjam', startDate: '2026-09-21' })}>Antrag in Zeitprüfung</button>
</div> }));
vi.mock('../AdminCorrectionsList', () => ({ default: ({ openSignal, canManage }) => <div>
    <output data-testid="correction-signal">{openSignal}</output><output data-testid="correction-can-manage">{String(canManage)}</output>
</div> }));
vi.mock('../EditTimeModal', () => ({ default: () => null }));
vi.mock('../PrintUserTimesModal', () => ({ default: () => null }));
vi.mock('../AdminWeekSection', async () => {
    const { forwardRef, useImperativeHandle, useEffect, useState } = await import('react');
    const { useLocation } = await import('react-router-dom');
    return { default: forwardRef(({ onIssueRowsChange, users }, ref) => {
        const location = useLocation();
        const [filter, setFilter] = useState('');
        const apply = (type, ...args) => {
            mocks.action({ type, args, pane: location.state?.workspaceTabId, search: location.search });
            setFilter(type === 'focusUserDate' || type === 'focusUser' ? args[0] : type);
        };
        // Recreate the handle on every render, as the real calendar does.
        useImperativeHandle(ref, () => ({
            focusUserDate: (...args) => { mocks.focus(...args); apply('focusUserDate', ...args); },
            ...Object.fromEntries(['printOverview', 'focusNegativeBalances', 'focusPositiveBalances', 'focusIssueType', 'focusUser'].map(type => [type, (...args) => apply(type, ...args)])),
        }));
        useEffect(() => { onIssueRowsChange?.([]); }, [onIssueRowsChange]);
        return <div>Time review<input aria-label="Zeitsuche" value={filter} onChange={event => setFilter(event.target.value)} />
            <output data-testid="time-users">{users.filter(user => !filter || user.username === filter).map(user => user.username).join(',')}</output></div>;
    }) };
});
vi.mock('../../../components/VacationCalendarAdmin', async () => {
    const { forwardRef, useImperativeHandle, useState } = await import('react');
    const { useLocation } = await import('react-router-dom');
    return { default: forwardRef((_props, ref) => {
        const location = useLocation();
        const [dialog, setDialog] = useState('');
        useImperativeHandle(ref, () => ({
            createVacation: () => { mocks.createVacation(); mocks.action({ type: 'createVacation', pane: location.state?.workspaceTabId }); setDialog('create'); },
            openAbsence: value => { mocks.action({ type: 'openAbsence', value, pane: location.state?.workspaceTabId }); setDialog(value.startDate); },
        }));
        return <div>Calendar<output data-testid="calendar-dialog">{dialog}</output></div>;
    }) };
});
vi.mock('../AdminWorkspaceOverview', async () => {
    const { useState } = await import('react');
    return { default: props => {
        const [draft, setDraft] = useState('');
        return <div>
            <output data-testid="workspace-data">{JSON.stringify({
                users: props.users.map(user => user.username), vacations: props.allVacations.map(row => row.id),
                corrections: props.allCorrections.map(row => row.id), balances: props.weeklyBalances.map(row => row.username),
                team: props.selectedTeam, loading: props.loading,
            })}</output>
            <span role="status" data-testid="load-error">{props.loadError}</span>
            <input aria-label="Notiz" value={draft} onChange={event => setDraft(event.target.value)} />
            <button onClick={() => props.onTeamChange('Atelier')}>Team Atelier</button>
            <button onClick={() => props.onFocusEmployee('Mirjam', '2026-09-11')}>Prüftag öffnen</button>
            <button onClick={() => props.onOpenEmployee('Mirjam')}>Profil öffnen</button>
            <button onClick={() => props.onOpenAbsence({ startDate: '2026-09-21' })}>Abwesenheit öffnen</button>
            <button onClick={props.onFocusNegativeBalances}>Minussalden</button>
            <button onClick={props.onFocusOvertimeLeaders}>Überstundensalden</button>
            <button onClick={() => props.onApproveVacation(12, 'Passt').catch(() => {})}>Test genehmigen</button>
        </div>;
    } };
});

import AdminDashboard from '../AdminDashboard';
import { WorkspaceTabsProvider, useWorkspaceTabs } from '../../../components/workspace/WorkspaceTabsContext.jsx';
import WorkspaceRouteViews from '../../../components/workspace/WorkspaceRouteViews.jsx';

const people = [
    { username: 'Mirjam', firstName: 'Mirjam', lastName: 'Burkart', departmentName: 'Atelier', includeInTimeTracking: true },
    { username: 'Jonas', firstName: 'Jonas', lastName: 'Weber', departmentName: 'Beratung', includeInTimeTracking: true },
];
const vacations = [{ id: 12, username: 'Mirjam', startDate: '2026-09-21', endDate: '2026-09-25', approved: false }, { id: 13, username: 'Jonas', approved: true }];
const defaultGet = url => Promise.resolve({ data: url === '/api/ui/preferences/APP_TABS' ? { revision: 0, payload: { tabs: [] } } : url.endsWith('/users') ? people
    : url === '/api/vacation/all' ? vacations
        : url === '/api/correction/all' ? [{ id: 14, username: 'Jonas', requestDate: '2026-09-11' }]
            : url.endsWith('/tracking-balances') ? people.map(user => ({ username: user.username, trackingBalance: 20 }))
                : url.includes('/holidays/') ? {} : [] });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const Location = () => { const location = useLocation(); return <output data-testid="location">{location.pathname}{location.search}</output>; };
const mount = (workspace = true) => render(<MemoryRouter initialEntries={[workspace ? '/admin/dashboard-neu' : '/admin/dashboard']}>
    <AdminDashboard {...(workspace ? { experience: 'workspace' } : {})} /><Location />
</MemoryRouter>);
const data = () => JSON.parse(screen.getByTestId('workspace-data').textContent);
const loaded = () => waitFor(() => expect(data().loading).toBe(false));

const Tabs = () => {
    const workspace = useWorkspaceTabs();
    const location = useLocation();
    return <div><output data-testid="active-pane">{workspace.activeTabId}</output>
        <output data-testid="navigation-state">{JSON.stringify(location.state)}</output>
        {workspace.tabs.map(tab => <button key={tab.id} onClick={() => workspace.activateTab(tab.id)}>Tab {new URL(tab.url, 'http://test').searchParams.get('tab') || 'overview'}</button>)}
    </div>;
};
const mountPanes = () => render(<MemoryRouter initialEntries={['/admin/dashboard-neu']}>
    <WorkspaceTabsProvider><Tabs /><WorkspaceRouteViews>
        <Route path="/admin/dashboard-neu" element={<AdminDashboard experience="workspace" />} />
    </WorkspaceRouteViews><Location /></WorkspaceTabsProvider>
</MemoryRouter>);
const activePane = () => document.querySelector('[data-workspace-pane]:not([aria-hidden])');
const active = () => within(activePane());
const paneLoaded = async () => {
    await waitFor(() => expect(activePane()).not.toBeNull());
    await waitFor(() => expect(JSON.parse(active().getByTestId('workspace-data').textContent).loading).toBe(false));
};
const command = name => {
    fireEvent.click(active().getByRole('button', { name: 'Befehle suchen' }));
    fireEvent.click(active().getByRole('button', { name }));
};

beforeEach(() => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    HTMLElement.prototype.scrollIntoView = vi.fn();
    localStorage.clear(); sessionStorage.clear(); mocks.get = null; mocks.notify.mockClear(); mocks.focus.mockClear(); mocks.createVacation.mockClear(); mocks.action.mockClear();
    mocks.api.get.mockReset().mockImplementation(defaultGet);
    mocks.api.put.mockReset().mockResolvedValue({ data: {} }); mocks.api.post.mockReset().mockResolvedValue({ data: {} });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); delete HTMLElement.prototype.scrollIntoView; });

describe('separate admin workspace integration', () => {
    it('keeps the classic dashboard and preference scopes as the default', async () => {
        const { container } = mount(false);
        await act(async () => {});
        expect(screen.getByRole('heading', { name: 'Admin-Dashboard' })).toBeInTheDocument();
        expect(container.querySelector('.admin-workspace')).toBeNull();
        expect(screen.getByTestId('scope-calendar')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Zeitprüfung' }));
        expect(screen.getByTestId('location')).toHaveTextContent('/admin/dashboard?tab=time');
    });

    it('filters real data consistently and keeps time-review navigation in the new route', async () => {
        mount(); await loaded();
        expect(data().vacations).toEqual([12, 13]);
        expect(screen.getByTestId('scope-workspace-calendar')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Team Atelier' }));
        expect(data()).toMatchObject({ users: ['Mirjam'], vacations: [12], corrections: [], balances: ['Mirjam'], team: 'Atelier' });
        fireEvent.click(screen.getByRole('button', { name: 'Prüftag öffnen' }));
        await waitFor(() => expect(mocks.focus).toHaveBeenCalledWith('Mirjam', '2026-09-11', 'request_focus'));
        expect(screen.getByTestId('location').textContent).toContain('/admin/dashboard-neu?');
        expect(screen.getByTestId('location').textContent).toContain('team=Atelier');
        expect(screen.getByTestId('location').textContent).toContain('week=2026-09-07');
    });

    it('opens employee profiles in the new route and reuses the real calendar action', async () => {
        mount(); await loaded();
        fireEvent.click(screen.getByRole('button', { name: 'Urlaub eintragen' }));
        await waitFor(() => expect(mocks.createVacation).toHaveBeenCalledOnce());
        expect(screen.getByTestId('location')).toHaveTextContent('/admin/dashboard-neu?tab=calendar');
        fireEvent.click(screen.getByRole('button', { name: 'Übersicht' }));
        fireEvent.click(screen.getByRole('button', { name: 'Profil öffnen' }));
        expect(screen.getByTestId('location')).toHaveTextContent('/admin/dashboard-neu/mitarbeiter/Mirjam');
    });

    it('saves through the existing API and refreshes without unmounting an open draft', async () => {
        mount(); await loaded();
        const note = screen.getByRole('textbox', { name: 'Notiz' });
        fireEvent.change(note, { target: { value: 'Bleibt offen' } });
        mocks.api.get.mockImplementation(url => url === '/api/vacation/all' ? Promise.resolve({ data: [{ ...vacations[0], approved: true }] }) : defaultGet(url));
        fireEvent.click(screen.getByRole('button', { name: 'Test genehmigen' }));
        await waitFor(() => expect(mocks.api.put).toHaveBeenCalledWith('/api/vacation/12', { approved: true, denied: false, adminNote: 'Passt' }));
        await waitFor(() => expect(data().vacations).toEqual([12]));
        expect(screen.getByRole('textbox', { name: 'Notiz' })).toBe(note);
        expect(note).toHaveValue('Bleibt offen');
    });

    it('preserves prior data and shows a partial refresh failure', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        mount(); await loaded();
        mocks.api.get.mockImplementation(url => url === '/api/vacation/all' ? Promise.reject(new Error('offline')) : defaultGet(url));
        await act(async () => { await mocks.refresh(); });
        expect(data().vacations).toEqual([12, 13]);
        expect(screen.getByTestId('load-error')).toHaveTextContent('Einige Daten konnten nicht aktualisiert werden');
        expect(screen.getByTestId('vacation-can-manage')).toHaveTextContent('false');
        expect(screen.getByTestId('correction-can-manage')).toHaveTextContent('false');
        fireEvent.click(screen.getByRole('button', { name: 'Test genehmigen' }));
        await act(async () => {});
        expect(mocks.api.put).not.toHaveBeenCalled();
        expect(mocks.notify).toHaveBeenCalledWith(expect.stringContaining('Freigaben sind erst'), 'warning');
    });

    it('ignores an older response after a more recent reload', async () => {
        const old = deferred(); let calls = 0;
        mocks.api.get.mockImplementation(url => url === '/api/vacation/all' && ++calls === 1 ? old.promise : defaultGet(url));
        mount();
        await act(async () => { await mocks.refresh(); });
        expect(data().vacations).toEqual([12, 13]);
        await act(async () => { old.resolve({ data: [{ id: 999, username: 'Mirjam' }] }); });
        expect(data().vacations).toEqual([12, 13]);
    });
});

describe('dashboard actions across real cached workspace panes', () => {
    it.each([
        ['Urlaub eintragen', 'createVacation', 'create'],
        ['Abwesenheit öffnen', 'openAbsence', '2026-09-21'],
    ])('runs %s only in the destination calendar after its data has loaded', async (button, type, dialog) => {
        mountPanes(); await paneLoaded();
        const source = activePane();
        const sourceId = source.dataset.workspacePane;
        const pending = deferred();
        mocks.api.get.mockImplementation(url => url === '/api/vacation/all' ? pending.promise : defaultGet(url));
        fireEvent.click(active().getByRole('button', { name: button }));
        await waitFor(() => expect(activePane()?.dataset.workspacePane).not.toBe(sourceId));
        expect(mocks.action).not.toHaveBeenCalled();
        await act(async () => { pending.resolve({ data: vacations }); });
        await paneLoaded();
        await waitFor(() => expect(active().getByTestId('calendar-dialog')).toHaveTextContent(dialog));
        expect(mocks.action).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ type, pane: activePane().dataset.workspacePane }));
        expect(within(source).getByTestId('calendar-dialog')).toBeEmptyDOMElement();
        expect(screen.getByTestId('navigation-state').textContent).not.toContain('adminDashboardAction');
        expect(screen.getByTestId('location').textContent).toBe('/admin/dashboard-neu?tab=calendar');
        await act(async () => { await mocks.refresh(); });
        expect(mocks.action).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByRole('button', { name: 'Tab overview' })); await paneLoaded();
        fireEvent.click(screen.getByRole('button', { name: 'Tab calendar' })); await paneLoaded();
        expect(mocks.action).toHaveBeenCalledTimes(1);
        expect(active().getByTestId('calendar-dialog')).toHaveTextContent(dialog);
    });

    it.each([
        ['Zeiten drucken', 'printOverview'],
        ['Minussalden', 'focusNegativeBalances'],
        ['Überstundensalden', 'focusPositiveBalances'],
    ])('applies %s once in a separate time pane and preserves later local changes', async (button, type) => {
        mountPanes(); await paneLoaded();
        const source = activePane();
        fireEvent.click(active().getByRole('button', { name: button }));
        await paneLoaded();
        await waitFor(() => expect(mocks.action).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ type, pane: activePane().dataset.workspacePane })));
        expect(activePane()).not.toBe(source);
        expect(within(source).getByRole('textbox', { name: 'Zeitsuche', hidden: true })).toHaveValue('');
        fireEvent.change(active().getByRole('textbox', { name: 'Zeitsuche' }), { target: { value: 'Jonas' } });
        await act(async () => { await mocks.refresh(); });
        fireEvent.click(screen.getByRole('button', { name: 'Tab overview' })); await paneLoaded();
        fireEvent.click(screen.getByRole('button', { name: 'Tab time' })); await paneLoaded();
        expect(active().getByRole('textbox', { name: 'Zeitsuche' })).toHaveValue('Jonas');
        expect(mocks.action).toHaveBeenCalledTimes(1);
    });

    it('focuses an employee once and clears the local time filters when the team changes', async () => {
        mountPanes(); await paneLoaded();
        fireEvent.click(active().getByRole('button', { name: 'Team Atelier' }));
        fireEvent.click(active().getByRole('button', { name: 'Prüftag öffnen' }));
        await paneLoaded();
        await waitFor(() => expect(active().getByRole('textbox', { name: 'Zeitsuche' })).toHaveValue('Mirjam'));
        expect(mocks.focus).toHaveBeenCalledExactlyOnceWith('Mirjam', '2026-09-11', 'request_focus');
        expect(mocks.action).toHaveBeenCalledWith(expect.objectContaining({ pane: activePane().dataset.workspacePane, search: expect.stringContaining('week=2026-09-07') }));
        fireEvent.change(active().getByRole('textbox', { name: 'Zeitsuche' }), { target: { value: 'Eigene Suche' } });
        await act(async () => { await mocks.refresh(); });
        expect(active().getByRole('textbox', { name: 'Zeitsuche' })).toHaveValue('Eigene Suche');
        expect(mocks.focus).toHaveBeenCalledTimes(1);
        fireEvent.change(active().getByRole('combobox', { name: 'Team' }), { target: { value: 'Beratung' } });
        await paneLoaded();
        expect(active().getByRole('textbox', { name: 'Zeitsuche' })).toHaveValue('');
        expect(active().getByTestId('time-users')).toHaveTextContent('Jonas');
        expect(screen.getByTestId('location').textContent).not.toContain('focusUser');
        expect(mocks.focus).toHaveBeenCalledTimes(1);
    });

    it.each([
        ['Urlaubscenter öffnen', 'vacation-signal'],
        ['Korrekturcenter öffnen', 'correction-signal'],
    ])('opens %s in the target request pane and jumps from there to the requested day', async (label, signal) => {
        mountPanes(); await paneLoaded();
        const source = activePane();
        command(label);
        await paneLoaded();
        await waitFor(() => expect(active().getByTestId(signal)).toHaveTextContent('1'));
        expect(within(source).getByTestId(signal)).toHaveTextContent('0');
        fireEvent.click(active().getByRole('button', { name: 'Antrag in Zeitprüfung' }));
        await paneLoaded();
        await waitFor(() => expect(mocks.focus).toHaveBeenCalledExactlyOnceWith('Mirjam', '2026-09-21', 'request_focus'));
        expect(mocks.action).toHaveBeenCalledWith(expect.objectContaining({ type: 'focusUserDate', pane: activePane().dataset.workspacePane }));
    });

    it('counts grouped requests in the chosen team and limits the low-risk command to that team', async () => {
        const correction = { approved: false, requestDate: '2026-09-14', reason: 'Korrektur', originalTimestamp: '2026-09-14T08:00:00', desiredTimestamp: '2026-09-14T08:05:00' };
        mocks.api.get.mockImplementation(url => url === '/api/correction/all' ? Promise.resolve({ data: [
            { ...correction, id: 20, username: 'Mirjam' }, { ...correction, id: 21, username: 'Mirjam' },
            { ...correction, id: 22, username: 'Jonas' },
        ] }) : defaultGet(url));
        mountPanes(); await paneLoaded();
        expect(active().getByRole('button', { name: 'Anträge 3' })).toBeInTheDocument();
        fireEvent.click(active().getByRole('button', { name: 'Team Atelier' }));
        await paneLoaded();
        expect(active().getByRole('button', { name: 'Anträge 2' })).toBeInTheDocument();
        command(/Offene ≤15\s*Min fixen/);
        await waitFor(() => expect(mocks.api.post).toHaveBeenCalledTimes(2));
        expect(mocks.api.post.mock.calls.map(call => call[0]).sort()).toEqual(['/api/correction/approve/20', '/api/correction/approve/21']);
        mocks.api.get.mockImplementation(url => url === '/api/vacation/all' ? Promise.reject(new Error('offline')) : defaultGet(url));
        await act(async () => { await mocks.refresh(); });
        command(/Offene ≤15\s*Min fixen/);
        await act(async () => {});
        expect(mocks.api.post).toHaveBeenCalledTimes(2);
        expect(mocks.notify).toHaveBeenCalledWith(expect.stringContaining('Freigaben sind erst'), 'warning');
    });
});
