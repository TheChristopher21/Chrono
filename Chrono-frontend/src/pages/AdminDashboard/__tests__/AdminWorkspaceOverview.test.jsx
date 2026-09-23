import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminWorkspaceOverview from '../AdminWorkspaceOverview';
import { LanguageProvider, useTranslation } from '../../../context/LanguageContext';

const apiMock = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../utils/api', () => ({ default: apiMock }));

const t = (_key, fallback) => fallback;
const users = [
    { username: 'Mirjam', firstName: 'Mirjam', lastName: 'Burkart', departmentName: 'Atelier' },
    { username: 'Luca', firstName: 'Luca', lastName: 'Meier', departmentName: 'Atelier' },
    { username: 'Jonas', firstName: 'Jonas', lastName: 'Weber', departmentName: 'Beratung' },
];
const correction = (id, extra = {}) => ({
    id, username: 'Mirjam', requestDate: '2026-09-12', reason: 'Termin dauerte länger.',
    originalTimestamp: '2026-09-11T16:30:00', desiredTimestamp: '2026-09-11T16:45:00',
    originalPunchType: 'ENDE', desiredPunchType: 'ENDE', approved: false, denied: false, ...extra,
});
const correctionDays = count => Array.from({ length: count }, (_, index) => {
    const date = `2026-09-${String(index + 1).padStart(2, '0')}`;
    return correction(index + 1, { requestDate: date, originalTimestamp: `${date}T16:30:00`, desiredTimestamp: `${date}T16:45:00` });
});
const props = extra => ({
    t, currentUser: { username: 'admin', roles: ['ROLE_SUPERADMIN'] }, users, issueRows: [],
    allVacations: [], allCorrections: [], allSickLeaves: [], weeklyBalances: [],
    onOpenEmployee: vi.fn(), onFocusEmployee: vi.fn(), onOpenRequests: vi.fn(), onOpenTime: vi.fn(),
    onOpenCalendar: vi.fn(), onOpenAbsence: vi.fn(), onRetry: vi.fn(),
    onFocusNegativeBalances: vi.fn(), onFocusOvertimeLeaders: vi.fn(),
    onApproveCorrection: vi.fn().mockResolvedValue(undefined), onDenyCorrection: vi.fn().mockResolvedValue(undefined),
    onApproveVacation: vi.fn().mockResolvedValue(undefined), onDenyVacation: vi.fn().mockResolvedValue(undefined),
    ...extra,
});
const deferred = () => {
    let resolve, reject;
    const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
    return { promise, resolve, reject };
};
const openCorrection = () => fireEvent.click(screen.getByRole('button', { name: 'Prüfen: Mirjam Burkart · Zeitkorrektur' }));

beforeEach(() => {
    localStorage.clear();
    apiMock.get.mockReset().mockResolvedValue({ data: 10 });
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-14T12:00:00'));
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

describe('AdminWorkspaceOverview', () => {
    it('counts grouped requests, distinct affected people, registered absences and available balances', () => {
        const { container } = render(<AdminWorkspaceOverview {...props({
            users: [...users, { username: 'hourly', isHourly: true }],
            allCorrections: [correction(1), correction(2), correction(3, { desiredTimestamp: '2026-09-10T17:00:00' }), correction(4, { approved: true, reason: 'Erledigt' })],
            allVacations: [
                { id: 1, username: 'Jonas', startDate: '2026-09-14', endDate: '2026-09-15' },
                { id: 2, username: 'Mirjam', startDate: '2026-09-14', endDate: '2026-09-14', approved: true, halfDay: true },
                { id: 3, username: 'Luca', startDate: '2026-09-14', endDate: '2026-09-14', denied: true },
            ],
            allSickLeaves: [
                { id: 4, username: 'Mirjam', startDate: '2026-09-14', endDate: '2026-09-14' },
                { id: 5, username: 'Luca', startDate: '2026-09-14', endDate: '2026-09-14' },
            ],
            issueRows: [
                { id: 'm1', username: 'Mirjam', issues: [{ type: 'missing', dateIso: '2026-09-10' }] },
                { id: 'm2', username: 'Mirjam', issues: [{ type: 'incomplete', dateIso: '2026-09-11' }] },
                { id: 'l', username: 'Luca', issues: [{ type: 'holiday_pending_decision', dateIso: '2026-09-12' }] },
            ],
            weeklyBalances: [{ username: 'Mirjam', trackingBalance: -120 }, { username: 'Luca', trackingBalance: 0 }, { username: 'Jonas', trackingBalance: null }, { username: 'hourly', trackingBalance: -300 }],
        })} />);
        const metric = key => container.querySelector(`[data-metric="${key}"]`);
        expect(metric('requests').querySelector('strong')).toHaveTextContent(/^3$/);
        expect(metric('requests')).toHaveTextContent('1 Urlaub · 2 Korrekturen');
        expect(metric('issues').querySelector('strong')).toHaveTextContent(/^2$/);
        expect(metric('absences').querySelector('strong')).toHaveTextContent('2 / 4');
        expect(metric('negative').querySelector('strong')).toHaveTextContent(/^1$/);
        expect(metric('negative')).toHaveTextContent('Saldo für 2 Personen');
        expect(screen.queryByText(/anwesend|verfügbar im Team/i)).not.toBeInTheDocument();
    });

    it('makes the metric strip navigate to the relevant real tasks and existing views', () => {
        const input = props({ allCorrections: [correction(1)], issueRows: [{ id: 'Luca', username: 'Luca', issues: [{ type: 'missing' }] }] });
        const { container } = render(<AdminWorkspaceOverview {...input} />);
        fireEvent.click(screen.getByRole('button', { name: 'Offene Anträge anzeigen' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(1);
        expect(screen.getByRole('button', { name: 'Anträge 1' })).toHaveAttribute('aria-pressed', 'true');
        fireEvent.click(screen.getByRole('button', { name: 'Mitarbeitende mit Zeitproblemen anzeigen' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(1);
        expect(screen.getByRole('button', { name: 'Zeitprüfung 1' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('region', { name: 'Zu erledigen' })).toHaveFocus();
        fireEvent.click(screen.getByRole('button', { name: 'Heutige Abwesenheiten im Kalender anzeigen' }));
        expect(input.onOpenCalendar).toHaveBeenCalledOnce();
        fireEvent.click(screen.getByRole('button', { name: 'Negative Zeitkonten anzeigen' }));
        expect(input.onFocusNegativeBalances).toHaveBeenCalledOnce();
    });

    it('shows unknown figures for initial or failed loads, but retains figures during a background refresh', () => {
        const input = props({ allCorrections: [correction(1)], weeklyBalances: [{ username: 'Mirjam', trackingBalance: 0 }] });
        const { container, rerender } = render(<AdminWorkspaceOverview {...input} initialLoading loading />);
        const values = () => [...container.querySelectorAll('.aw-metric > strong')].map(node => node.textContent);
        expect(values()).toEqual(['–', '–', '–', '–']);
        rerender(<AdminWorkspaceOverview {...input} initialLoading={false} loading dataUpdatedAt="2026-09-14T09:00:00" />);
        expect(values()).toEqual(['1', '0', '0 / 3', '0']);
        rerender(<AdminWorkspaceOverview {...input} initialLoading={false} loadError="Offline" dataUpdatedAt="2026-09-14T09:00:00" />);
        expect(values()).toEqual(['–', '–', '–', '–']);
        expect(container.querySelector('.aw-data-context')).toHaveTextContent('Datenstand 14.09.2026 · 09:00 · Nicht aktuell');
        expect(container.querySelector('.aw-balances')).not.toHaveTextContent('0h 00m');
        openCorrection();
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
    });

    it('does not turn an initial failure or an unfinished issue calculation into zero or an empty-success claim', () => {
        const input = props({ issueRows: null });
        const { container, rerender } = render(<AdminWorkspaceOverview {...input} initialLoading loadError="Offline" />);
        expect(screen.getByText('Für diese Ansicht liegen keine vollständig geladenen Vorgänge vor.')).toBeInTheDocument();
        expect(screen.queryByText('Vorgänge werden geladen…')).not.toBeInTheDocument();
        expect(container.querySelector('.aw-data-context')).toHaveTextContent('Datenstand nicht verfügbar');
        rerender(<AdminWorkspaceOverview {...input} initialLoading={false} />);
        expect(container.querySelector('[data-metric="requests"] > strong')).toHaveTextContent(/^0$/);
        expect(container.querySelector('[data-metric="issues"] > strong')).toHaveTextContent('–');
        expect(container.querySelector('.aw-inbox-foot')).toHaveTextContent('Anzahl derzeit nicht verfügbar');
    });

    it('puts approaching vacation first and otherwise sorts affected dates without implying a submission age', () => {
        const { container } = render(<AdminWorkspaceOverview {...props({
            allCorrections: [correction(1, { requestDate: '2026-01-01', desiredTimestamp: '2026-09-10T17:00:00' }), correction(2, { requestDate: '2026-09-30', desiredTimestamp: '2026-09-05T17:00:00' })],
            allVacations: [
                { id: 1, username: 'Luca', startDate: '2026-10-01', endDate: '2026-10-05', requestDate: '2026-01-01', createdAt: '2025-01-01' },
                { id: 2, username: 'Luca', startDate: '2026-09-15', endDate: '2026-09-18' },
            ],
            issueRows: [{ id: 'Jonas', username: 'Jonas', dateIso: '2026-09-01', issues: [{ type: 'missing', dateIso: '2026-09-01' }] }],
        })} />);
        const rows = [...container.querySelectorAll('.aw-task')];
        expect(rows[0]).toHaveAttribute('data-task-key', 'vacation:2');
        expect(rows[0]).toHaveTextContent('Beginn morgen');
        expect(rows[1]).toHaveAttribute('data-task-key', 'time:Jonas');
        expect(rows[2]).toHaveTextContent('Zeitkorrektur · 05.09.2026');
        expect(rows[3]).toHaveTextContent('Zeitkorrektur · 10.09.2026');
        expect(rows[4]).toHaveAttribute('data-task-key', 'vacation:1');
        // Vacation DTOs have no request timestamp; never present their start as an age.
        expect(rows[4]).toHaveTextContent('Offener Antrag');
        expect(container).not.toHaveTextContent(/Seit.*offen|ältesten Vorgänge|Heute beantragt/);
    });

    it('shows three tasks on short desktop viewports, with explicit links to every task and further absence', () => {
        vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() });
        const corrections = correctionDays(6);
        const input = props({ allCorrections: corrections, allVacations: [
            { id: 10, username: 'Mirjam', startDate: '2026-09-14', endDate: '2026-09-15', approved: true },
            { id: 11, username: 'Luca', startDate: '2026-09-14', endDate: '2026-09-16', approved: true },
            { id: 12, username: 'Mirjam', startDate: '2026-09-21', endDate: '2026-09-25', approved: true },
            { id: 13, username: 'Luca', startDate: '2026-09-28', endDate: '2026-09-30', approved: true },
        ] });
        const { container } = render(<AdminWorkspaceOverview {...input} />);
        expect(window.matchMedia).toHaveBeenCalledWith('(min-width: 1200px) and (max-height: 820px)');
        expect(container.querySelector('.aw-overview')).toHaveClass('aw-overview-compact');
        expect(container.querySelectorAll('.aw-task')).toHaveLength(3);
        expect(container.querySelectorAll('.aw-absence')).toHaveLength(2);
        expect(container.querySelector('[data-metric="requests"] > strong')).toHaveTextContent(/^6$/);
        fireEvent.click(screen.getByRole('button', { name: 'Alle anzeigen (6)' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(6);
        fireEvent.click(screen.getByRole('button', { name: 'Weniger anzeigen' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(3);
        fireEvent.click(screen.getAllByRole('button', { name: '+1 weitere im Kalender ↗' })[0]);
        expect(input.onOpenCalendar).toHaveBeenCalledOnce();
    });

    it('keeps an expanded decision and its note mounted when the viewport becomes shorter', () => {
        let onChange;
        const media = { matches: false, addEventListener: vi.fn((_event, callback) => { onChange = callback; }), removeEventListener: vi.fn() };
        vi.spyOn(window, 'matchMedia').mockReturnValue(media);
        const corrections = correctionDays(6);
        const { container, unmount } = render(<AdminWorkspaceOverview {...props({ allCorrections: corrections })} />);
        const fifthRow = container.querySelectorAll('.aw-task')[4];
        fireEvent.click(within(fifthRow).getByRole('button', { name: /Prüfen:/ }));
        const note = screen.getByRole('textbox', { name: /Kommentar zur Entscheidung/ });
        fireEvent.change(note, { target: { value: 'Weiter prüfen' } });
        act(() => { media.matches = true; onChange(); });
        expect(container.querySelector('.aw-overview')).toHaveClass('aw-overview-compact');
        expect(screen.getByRole('textbox', { name: /Kommentar zur Entscheidung/ })).toBe(note);
        expect(note).toHaveValue('Weiter prüfen');
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeVisible();
        unmount();
        expect(media.removeEventListener).toHaveBeenCalledWith('change', onChange);
    });

    it('uses four preview rows on medium-height desktops and five when the compact desktop queries do not match', () => {
        const medium = { matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() };
        const noMatch = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
        const matchMedia = vi.spyOn(window, 'matchMedia').mockImplementation(query => query.includes('940px') ? medium : noMatch);
        const corrections = correctionDays(6);
        const { container, unmount } = render(<AdminWorkspaceOverview {...props({ allCorrections: corrections })} />);
        expect(container.querySelectorAll('.aw-task')).toHaveLength(4);
        unmount();
        matchMedia.mockReturnValue(noMatch);
        const normal = render(<AdminWorkspaceOverview {...props({ allCorrections: corrections })} />);
        expect(normal.container.querySelectorAll('.aw-task')).toHaveLength(5);
        expect(normal.container.querySelector('.aw-overview')).not.toHaveClass('aw-overview-compact');
    });

    it('shows the period belonging to real issue rows until the next period has actually been calculated', () => {
        const input = props({ timeRangeStart: '2026-09-14', timeRangeEnd: '2026-09-20', dataUpdatedAt: '2026-09-14T09:00:00',
            issueRows: [{ id: 'Mirjam', username: 'Mirjam', dateIso: '2026-09-11', periodStart: '2026-09-07', periodEnd: '2026-09-13', issues: [{ type: 'missing', dateIso: '2026-09-11' }] }] });
        const { container, rerender } = render(<AdminWorkspaceOverview {...input} />);
        expect(container.querySelector('.aw-review-context')).toHaveTextContent('07.09.2026 – 13.09.2026');
        fireEvent.click(screen.getByRole('button', { name: 'Zeitraum ändern ↗' }));
        expect(input.onOpenTime).toHaveBeenCalledOnce();
        rerender(<AdminWorkspaceOverview {...input} issueRows={[]} />);
        expect(container.querySelector('.aw-review-context')).toHaveTextContent('14.09.2026 – 20.09.2026');
    });

    it('shows the full server day group and every reason, then approves that group with exactly one request', async () => {
        const pending = deferred();
        const input = props({ allCorrections: [correction(1), correction(2, { requestDate: '2026-08-10', reason: 'Zweiter Grund', desiredTimestamp: '2026-09-11T17:00:00' }), correction(3, { approved: true })],
            onApproveCorrection: vi.fn(() => pending.promise) });
        render(<AdminWorkspaceOverview {...input} />);
        openCorrection();
        expect(screen.getByText('offene Änderungen dieses Kalendertags werden gemeinsam entschieden.', { exact: false })).toHaveTextContent('2');
        expect(screen.getByText('Termin dauerte länger.')).toBeInTheDocument();
        expect(screen.getByText('Zweiter Grund')).toBeInTheDocument();
        fireEvent.change(screen.getByRole('textbox', { name: /Kommentar zur Entscheidung/ }), { target: { value: 'Mit Mitarbeiter geprüft.' } });
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        await waitFor(() => expect(input.onApproveCorrection).toHaveBeenCalledOnce());
        expect(input.onApproveCorrection).toHaveBeenCalledWith(1, 'Mit Mitarbeiter geprüft.');
        expect(input.onApproveCorrection).not.toHaveBeenCalledWith(2, expect.anything());
        expect(input.onApproveCorrection).not.toHaveBeenCalledWith(3, expect.anything());
        expect(screen.getByRole('button', { name: 'Ablehnen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Wird gespeichert…' })).toBeDisabled();
        await act(async () => pending.resolve());
        expect(await screen.findByRole('status')).toHaveTextContent('Antrag genehmigt.');
        expect(screen.queryByRole('button', { name: 'Prüfen: Mirjam Burkart · Zeitkorrektur' })).not.toBeInTheDocument();
    });

    it('keeps failed denials and note for retry without resubmitting successful single-record denials', async () => {
        const deny = vi.fn().mockImplementation(id => id === 2 && deny.mock.calls.length <= 2 ? Promise.reject(new Error('Konflikt beim Speichern')) : Promise.resolve());
        render(<AdminWorkspaceOverview {...props({ allCorrections: [correction(1), correction(2, { reason: 'Zweiter Grund' })], onDenyCorrection: deny })} />);
        openCorrection();
        fireEvent.change(screen.getByRole('textbox', { name: /Kommentar/ }), { target: { value: 'Notiz bleibt' } });
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('1 Änderung gespeichert. Konflikt beim Speichern');
        expect(screen.getByRole('textbox', { name: /Kommentar/ })).toHaveValue('Notiz bleibt');
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        await waitFor(() => expect(deny).toHaveBeenCalledTimes(3));
        expect(deny.mock.calls.map(([id]) => id)).toEqual([1, 2, 2]);
    });

    it('blocks an unscoped day approval for a missing desired date while allowing an explicit single denial', async () => {
        const input = props({ allCorrections: [correction(1, { desiredTimestamp: null })] });
        render(<AdminWorkspaceOverview {...input} />);
        openCorrection();
        expect(screen.getByRole('alert')).toHaveTextContent('Der gewünschte Kalendertag fehlt');
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        await waitFor(() => expect(input.onDenyCorrection).toHaveBeenCalledWith(1, ''));
        expect(input.onApproveCorrection).not.toHaveBeenCalled();
    });

    it('keeps an expanded draft during background refresh and blocks stale-data decisions', () => {
        const input = props({ allCorrections: [correction(1)] });
        const { rerender } = render(<AdminWorkspaceOverview {...input} />);
        openCorrection();
        const textarea = screen.getByRole('textbox', { name: /Kommentar/ });
        fireEvent.change(textarea, { target: { value: 'Nicht verlieren' } });
        rerender(<AdminWorkspaceOverview {...input} loading />);
        expect(screen.getByRole('textbox', { name: /Kommentar/ })).toBe(textarea);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        rerender(<AdminWorkspaceOverview {...input} loadError="Server nicht erreichbar" />);
        expect(screen.getByRole('textbox', { name: /Kommentar/ })).toHaveValue('Nicht verlieren');
        expect(screen.getByRole('button', { name: 'Ablehnen' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(input.onRetry).toHaveBeenCalledOnce();
    });

    it('uses real problem dates and treats the employee name as a separate profile action', () => {
        const input = props({ issueRows: [{ id: 'Mirjam', username: 'Mirjam', dateIso: '2026-09-11', issues: [{ type: 'incomplete_work_end_missing', dateIso: '2026-09-11' }] }] });
        render(<AdminWorkspaceOverview {...input} />);
        fireEvent.click(screen.getByRole('button', { name: 'Mirjam Burkart' }));
        expect(input.onOpenEmployee).toHaveBeenCalledWith('Mirjam');
        fireEvent.click(screen.getByRole('button', { name: 'Prüfen: Mirjam Burkart · Arbeitsende fehlt' }));
        expect(input.onFocusEmployee).toHaveBeenCalledWith('Mirjam', '2026-09-11');
        fireEvent.click(screen.getByRole('button', { name: 'Details: Mirjam Burkart' }));
        expect(screen.getAllByText('11.09.2026').length).toBeGreaterThan(0);
    });

    it('shows only five task rows initially and filters grouped requests separately from time issues', () => {
        const corrections = correctionDays(6);
        const { container } = render(<AdminWorkspaceOverview {...props({ allCorrections: corrections, issueRows: [{ id: 'Luca', username: 'Luca', issues: [{ type: 'missing', dateIso: null }] }] })} />);
        expect(container.querySelectorAll('.aw-task')).toHaveLength(5);
        fireEvent.click(screen.getByRole('button', { name: 'Alle anzeigen (7)' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(7);
        fireEvent.click(screen.getByRole('button', { name: 'Zeitprüfung 1' }));
        expect(container.querySelectorAll('.aw-task')).toHaveLength(1);
        expect(screen.getByRole('button', { name: 'Prüfen: Luca Meier · Fehlende Stempelungen' })).toBeInTheDocument();
    });

    it('shows only approved absences, clips team overlaps, and passes the original absence record', async () => {
        const approved = { id: 10, username: 'Mirjam', startDate: '2026-09-21', endDate: '2026-09-25', approved: true, halfDay: true };
        const input = props({ allVacations: [approved, { id: 11, username: 'Luca', startDate: '2026-09-23', endDate: '2026-09-28' }],
            allSickLeaves: [{ id: 9, username: 'Jonas', startDate: '2026-09-23', endDate: '2026-09-25' }] });
        const { container } = render(<AdminWorkspaceOverview {...input} />);
        fireEvent.click(screen.getByRole('button', { name: 'Prüfen: Luca Meier · Urlaub' }));
        await screen.findByText('10 Tage verfügbar');
        const overlap = screen.getByText('Überschneidungen im Team').closest('.aw-overlap-list');
        expect(overlap).toHaveTextContent('Mirjam Burkart · 23.09.2026 – 25.09.2026');
        expect(overlap).not.toHaveTextContent('Jonas Weber');
        expect(container.querySelectorAll('.aw-absence')).toHaveLength(2);
        const card = [...container.querySelectorAll('.aw-absence')].find(node => node.textContent.includes('Mirjam'));
        fireEvent.click(within(card).getByRole('button', { name: /Details/ }));
        expect(input.onOpenAbsence).toHaveBeenCalledWith(expect.objectContaining({ id: 10, kind: 'vacation', type: 'vacation', dateIso: '2026-09-21', raw: approved }));
    });

    it('does not claim an empty queue is complete while loading, on error, or before issue calculation', () => {
        const input = props({ issueRows: null });
        const { rerender } = render(<AdminWorkspaceOverview {...input} loading />);
        expect(screen.getByText('Vorgänge werden geladen…')).toBeInTheDocument();
        rerender(<AdminWorkspaceOverview {...input} loadError="Fehler" />);
        expect(screen.getByText('Für diese Ansicht liegen keine vollständig geladenen Vorgänge vor.')).toBeInTheDocument();
        expect(screen.queryByText('Keine offenen Vorgänge in dieser Ansicht.')).not.toBeInTheDocument();
        rerender(<AdminWorkspaceOverview {...input} />);
        expect(screen.getByText('Zeitprüfung wird berechnet…')).toBeInTheDocument();
    });

    it('preserves zero balances, blocks read-only decisions and writes only its own view settings', () => {
        localStorage.setItem('adminDashboard_layout', 'untouched');
        const { container } = render(<AdminWorkspaceOverview {...props({ currentUser: { username: 'view', roles: ['ROLE_USER'] },
            allCorrections: [correction(1)], weeklyBalances: [{ username: 'Mirjam', trackingBalance: 0 }] })} />);
        openCorrection();
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        expect(container.querySelector('.aw-balances')).toHaveTextContent('0h 00m');
        fireEvent.click(screen.getByRole('checkbox', { name: 'Zeitkonten' }));
        expect(container.querySelector('.aw-balances')).not.toBeInTheDocument();
        expect(localStorage.getItem('adminDashboard_layout')).toBe('untouched');
        expect(JSON.parse(localStorage.getItem('chrono:admin-workspace-overview:v1::view'))).toEqual({ team: true, balances: false });
    });

    it('loads authoritative vacation balance only when a request is expanded', async () => {
        render(<AdminWorkspaceOverview {...props({ allVacations: [{ id: 0, username: 'Luca', startDate: '2026-12-28', endDate: '2027-01-05' }] })} />);
        expect(apiMock.get).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Prüfen: Luca Meier · Urlaub' }));
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(2));
        expect(apiMock.get).toHaveBeenCalledWith('/api/vacation/remaining', expect.objectContaining({ params: { username: 'Luca', year: 2026 } }));
        expect(apiMock.get).toHaveBeenCalledWith('/api/vacation/remaining', expect.objectContaining({ params: { username: 'Luca', year: 2027 } }));
    });

    it('uses string translation keys with the real language provider', () => {
        const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
        function TranslatedOverview() {
            const { t: translate } = useTranslation();
            return <AdminWorkspaceOverview {...props({ t: translate, allVacations: [
                { id: 0, username: 'Luca', startDate: '2026-10-01', endDate: '2026-10-05' },
                { id: 1, username: 'Mirjam', startDate: '2026-09-21', endDate: '2026-09-25', approved: true },
            ] })} />;
        }
        render(<LanguageProvider><TranslatedOverview /></LanguageProvider>);
        expect(screen.getByRole('button', { name: 'Prüfen: Luca Meier · Urlaub' })).toBeInTheDocument();
        expect(warning.mock.calls.filter(([message]) => String(message).includes('Expected a string'))).toEqual([]);
    });
});
