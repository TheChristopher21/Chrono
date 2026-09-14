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
const props = extra => ({
    t, currentUser: { username: 'admin', roles: ['ROLE_SUPERADMIN'] }, users, issueRows: [],
    allVacations: [], allCorrections: [], allSickLeaves: [], weeklyBalances: [],
    onOpenEmployee: vi.fn(), onFocusEmployee: vi.fn(), onOpenRequests: vi.fn(), onOpenTime: vi.fn(),
    onOpenCalendar: vi.fn(), onOpenAbsence: vi.fn(), onRetry: vi.fn(),
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
    it('decides a full correction group once, preserving its comment and waiting for every member', async () => {
        const first = deferred(), second = deferred();
        const input = props({ allCorrections: [correction(1), correction(2, { desiredTimestamp: '2026-09-11T17:00:00' }), correction(3, { approved: true })],
            onApproveCorrection: vi.fn(id => id === 1 ? first.promise : second.promise) });
        render(<AdminWorkspaceOverview {...input} />);
        openCorrection();
        expect(screen.getByText('zusammengehörige Änderungen werden gemeinsam entschieden.', { exact: false })).toHaveTextContent('2');
        fireEvent.change(screen.getByRole('textbox', { name: /Kommentar zur Entscheidung/ }), { target: { value: 'Mit Mitarbeiter geprüft.' } });
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        await waitFor(() => expect(input.onApproveCorrection).toHaveBeenCalledTimes(2));
        expect(input.onApproveCorrection).toHaveBeenCalledWith(1, 'Mit Mitarbeiter geprüft.');
        expect(input.onApproveCorrection).toHaveBeenCalledWith(2, 'Mit Mitarbeiter geprüft.');
        expect(input.onApproveCorrection).not.toHaveBeenCalledWith(3, expect.anything());
        expect(screen.getByRole('button', { name: 'Ablehnen' })).toBeDisabled();
        await act(async () => first.resolve());
        expect(screen.getByRole('button', { name: 'Wird gespeichert…' })).toBeDisabled();
        await act(async () => second.resolve());
        expect(await screen.findByRole('status')).toHaveTextContent('Antrag genehmigt.');
        expect(screen.queryByRole('button', { name: 'Prüfen: Mirjam Burkart · Zeitkorrektur' })).not.toBeInTheDocument();
    });

    it('keeps failed group members and note for retry without resubmitting successful decisions', async () => {
        const approve = vi.fn().mockImplementation(id => id === 2 && approve.mock.calls.length <= 2 ? Promise.reject(new Error('Konflikt beim Speichern')) : Promise.resolve());
        render(<AdminWorkspaceOverview {...props({ allCorrections: [correction(1), correction(2)], onApproveCorrection: approve })} />);
        openCorrection();
        fireEvent.change(screen.getByRole('textbox', { name: /Kommentar/ }), { target: { value: 'Notiz bleibt' } });
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('1 Änderung gespeichert. Konflikt beim Speichern');
        expect(screen.getByRole('textbox', { name: /Kommentar/ })).toHaveValue('Notiz bleibt');
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        await waitFor(() => expect(approve).toHaveBeenCalledTimes(3));
        expect(approve.mock.calls.map(([id]) => id)).toEqual([1, 2, 2]);
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
        const corrections = Array.from({ length: 6 }, (_, i) => correction(i + 1, { reason: `Grund ${i}` }));
        const { container } = render(<AdminWorkspaceOverview {...props({ allCorrections: corrections, issueRows: [{ id: 'Luca', username: 'Luca', issues: [{ type: 'missing', dateIso: null }] }] })} />);
        expect(container.querySelectorAll('.aw-task')).toHaveLength(5);
        fireEvent.click(screen.getByRole('button', { name: 'Weitere anzeigen (2)' }));
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
