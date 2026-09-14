import { Activity, useState } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminWorkspaceRequests from '../AdminWorkspaceRequests';

const apiMock = vi.hoisted(() => ({ get: vi.fn(), delete: vi.fn() }));
vi.mock('../../../utils/api', () => ({ default: apiMock }));
const t = (_key, fallback) => fallback;
const users = [
    { username: 'mirjam', firstName: 'Mirjam', lastName: 'Burkart', departmentName: 'Atelier' },
    { username: 'luca', firstName: 'Luca', lastName: 'Meier', departmentName: 'Atelier' },
    { username: 'jonas', firstName: 'Jonas', lastName: 'Weber', departmentName: 'Beratung' },
];
const correction = (id, extra = {}) => ({ id, username: 'mirjam', requestDate: '2026-09-14', reason: 'Termin dauerte länger.',
    originalTimestamp: '2026-09-10T16:30:00', desiredTimestamp: '2026-09-10T16:45:00',
    originalPunchType: 'ENDE', desiredPunchType: 'ENDE', approved: false, denied: false, ...extra });
const vacation = (id, startDate = '2026-09-20', extra = {}) => ({ id, username: 'luca', startDate, endDate: startDate,
    approved: false, denied: false, ...extra });
const props = extra => ({ t, currentUser: { id: 1, username: 'admin', roles: ['ROLE_SUPERADMIN'] }, users,
    allVacations: [], allCorrections: [], allSickLeaves: [], onReload: vi.fn().mockResolvedValue(undefined), onRetry: vi.fn(),
    onApproveVacation: vi.fn().mockResolvedValue(undefined), onDenyVacation: vi.fn().mockResolvedValue(undefined),
    onApproveCorrection: vi.fn().mockResolvedValue(undefined), onDenyCorrection: vi.fn().mockResolvedValue(undefined),
    onOpenEmployee: vi.fn(), onOpenInTimeReview: vi.fn(), ...extra });
const mount = input => render(<div className="admin-workspace"><AdminWorkspaceRequests {...input} /></div>);
const list = () => screen.getByRole('list', { name: 'Antragsliste' });
const rowKeys = () => [...list().children].map(row => row.dataset.requestKey);
const choose = key => fireEvent.click(within(list().querySelector(`[data-request-key='${key}']`)).getByRole('button'));
const change = (label, value) => fireEvent.change(screen.getByRole('combobox', { name: label }), { target: { value } });
const note = value => fireEvent.change(screen.getByRole('textbox', { name: 'Kommentar zur Entscheidung' }), { target: { value } });
const deferred = () => { let resolve, reject; const promise = new Promise((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; };

beforeEach(() => {
    apiMock.get.mockReset().mockResolvedValue({ data: 12 });
    apiMock.delete.mockReset().mockResolvedValue({ data: {} });
    HTMLElement.prototype.scrollIntoView = vi.fn();
});
afterEach(() => { cleanup(); delete HTMLElement.prototype.scrollIntoView; vi.restoreAllMocks(); });

describe('AdminWorkspaceRequests', () => {
    it('sorts one mixed list by real dates, keeps equal-date order stable, and distinguishes the request day from arrival', async () => {
        const input = props({ allVacations: [vacation(2), vacation(99, '2026-09-05')],
            allCorrections: [correction(30), correction(31, { desiredTimestamp: '2026-09-10T17:00:00' })] });
        const { rerender } = mount(input);
        await screen.findByText('12 Tage verfügbar');
        expect(rowKeys()).toEqual(['vacation:99', expect.stringContaining('correction:'), 'vacation:2']);
        expect(within(list()).getByText('05.09.2026')).toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Reihenfolge' })).toHaveValue('asc');
        change('Reihenfolge', 'desc');
        expect(rowKeys()).toEqual(['vacation:2', expect.stringContaining('correction:'), 'vacation:99']);
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter' }));
        change('Datumsbasis', 'request');
        expect(screen.getByRole('option', { name: 'Erfasster Korrekturtag' })).toHaveProperty('selected', true);
        expect(screen.getByText(/Im Korrekturantrag gewählter Arbeitstag, kein Zeitstempel des Eingangs/)).toBeInTheDocument();
        expect(rowKeys()).toEqual([expect.stringContaining('correction:'), 'vacation:2', 'vacation:99']);
        expect(within(list()).getAllByText('Nicht erfasst')).toHaveLength(2);
        const before = rowKeys();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} allVacations={[...input.allVacations].reverse()} allCorrections={[...input.allCorrections].reverse()} /></div>);
        expect(rowKeys()).toEqual(before);
        await act(async () => {});
    });

    it('filters exact person, name, type, status and actual dates without splitting correction groups', async () => {
        function Host() {
            const [employee, setEmployee] = useState('');
            return <AdminWorkspaceRequests {...props({ allVacations: [vacation(9, '2026-09-20', { endDate: '2026-09-25' }), vacation(10, '2026-09-10', { approved: true })],
                allCorrections: [correction(1), correction(2, { approved: true, adminComment: 'Bereits geprüft', processedByAdminInitials: 'AS' })] })} employeeFilter={employee} onEmployeeFilterChange={setEmployee} />;
        }
        render(<Host />);
        change('Person', 'mirjam');
        expect(rowKeys()).toHaveLength(1);
        expect(screen.getAllByText('Gestempelt')).toHaveLength(2);
        expect(screen.getByText('Bereits geprüft', { exact: false })).toBeInTheDocument();
        expect(screen.getByText('AdmK AS')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Personenfilter entfernen/ }));
        fireEvent.change(screen.getByRole('searchbox', { name: 'Namenssuche' }), { target: { value: 'Meier' } });
        expect(rowKeys()).toEqual(['vacation:9']);
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter' }));
        fireEvent.change(screen.getByLabelText('Datum eingrenzen'), { target: { value: '2026-09-23' } });
        expect(rowKeys()).toEqual(['vacation:9']);
        change('Datumsbasis', 'request');
        expect(screen.getByText('Keine Anträge für diese Filter.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }));
        change('Status', 'approved'); change('Antragsart', 'correction');
        expect(rowKeys()).toHaveLength(1);
        expect(screen.getAllByText('Gestempelt')).toHaveLength(2);
        await act(async () => {});
    });

    it('waits for all group denials and retries only failures while retaining the note and successful history', async () => {
        const first = deferred(), second = deferred();
        const deny = vi.fn(id => id === 1 ? first.promise : deny.mock.calls.length <= 2 ? second.promise : Promise.resolve());
        const input = props({ allCorrections: [correction(1), correction(2), correction(3, { approved: true })], onDenyCorrection: deny });
        mount(input); note('Mit Mirjam besprochen.');
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        fireEvent.click(screen.getByRole('button', { name: 'Wird gespeichert…' }));
        await waitFor(() => expect(deny).toHaveBeenCalledTimes(2));
        expect(deny.mock.calls).toEqual([[1, 'Mit Mirjam besprochen.'], [2, 'Mit Mirjam besprochen.']]);
        await act(async () => first.resolve());
        expect(screen.getByRole('button', { name: 'Wird gespeichert…' })).toBeDisabled();
        expect(input.onReload).not.toHaveBeenCalled();
        await act(async () => second.reject(new Error('Konflikt bei Änderung 2')));
        expect(screen.getByRole('alert')).toHaveTextContent('1 Änderung gespeichert. Konflikt bei Änderung 2');
        expect(screen.getByRole('textbox', { name: 'Kommentar zur Entscheidung' })).toHaveValue('Mit Mirjam besprochen.');
        expect(screen.getAllByText('Gestempelt')).toHaveLength(3);
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        await waitFor(() => expect(deny.mock.calls.map(([id]) => id)).toEqual([1, 2, 2]));
        expect(input.onReload).toHaveBeenCalledTimes(2);
        change('Status', 'approved');
        expect(rowKeys()).toHaveLength(1);
        expect(screen.getAllByText('Gestempelt')).toHaveLength(3);
        expect(screen.queryByRole('button', { name: 'Genehmigen' })).not.toBeInTheDocument();
    });

    it('keeps advanced date filters collapsed by default and preserves their values and the decision draft when collapsed', () => {
        mount(props({ allCorrections: [correction(1), correction(2, { desiredTimestamp: '2026-09-11T17:00:00' })] }));
        expect(screen.queryByRole('combobox', { name: 'Datumsbasis' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weitere Filter' })).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter' }));
        fireEvent.change(screen.getByLabelText('Datum eingrenzen'), { target: { value: '2026-09-10' } });
        expect(rowKeys()).toHaveLength(1); note('Entscheidung vorbereitet');
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter · aktiv' }));
        expect(screen.queryByLabelText('Datum eingrenzen')).not.toBeInTheDocument();
        expect(rowKeys()).toHaveLength(1);
        const scrollRegion = screen.getByRole('region', { name: 'Antragsdetails' });
        expect(within(scrollRegion).getByText('Gestempelt')).toBeInTheDocument();
        expect(within(scrollRegion).queryByRole('button', { name: 'Genehmigen' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeEnabled();
        expect(screen.getByRole('textbox', { name: 'Kommentar zur Entscheidung' })).toHaveValue('Entscheidung vorbereitet');
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter · aktiv' }));
        expect(screen.getByLabelText('Datum eingrenzen')).toHaveValue('2026-09-10');
        fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }));
        expect(rowKeys()).toHaveLength(2);
    });

    it('previews all reasons in the backend day scope and approves the entire group with one API call', async () => {
        const input = props({ allCorrections: [correction(1, { reason: 'Früh begonnen', desiredTimestamp: '2026-09-10T08:00:00' }),
            correction(2, { reason: 'Später beendet', requestDate: '2026-09-15' }),
            correction(3, { reason: 'Anderer Tag', desiredTimestamp: '2026-09-11T17:00:00' })],
            onReload: vi.fn().mockRejectedValue(new Error('offline')) });
        mount(input); expect(rowKeys()).toHaveLength(2);
        const details = screen.getByRole('region', { name: 'Antragsdetails' });
        expect(details).toHaveTextContent('Früh begonnen'); expect(details).toHaveTextContent('Später beendet');
        expect(details).not.toHaveTextContent('Anderer Tag');
        note('Tageskorrektur geprüft');
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        await waitFor(() => expect(input.onApproveCorrection).toHaveBeenCalledExactlyOnceWith(1, 'Tageskorrektur geprüft'));
        await screen.findByRole('alert');
        expect(rowKeys()).toHaveLength(1);
        change('Status', 'approved');
        expect(rowKeys()).toHaveLength(1);
        expect(screen.getAllByText('Gestempelt')).toHaveLength(2);
        expect(screen.queryByRole('button', { name: 'Genehmigen' })).not.toBeInTheDocument();
        expect(input.onReload).toHaveBeenCalledOnce();
    });

    it('isolates unknown desired dates, blocks their approval and permits individual denial', async () => {
        const input = props({ allCorrections: [correction(1, { desiredTimestamp: null }),
            correction(2, { desiredTimestamp: '2026-09-10T99:00:00' })] });
        mount(input); expect(rowKeys()).toHaveLength(2);
        expect(within(list()).getAllByText('Zeitkorrektur · 1 Änderung')).toHaveLength(2);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Ablehnen' })).toBeEnabled();
        expect(screen.getByText(/Genehmigen ist gesperrt/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        await waitFor(() => expect(input.onDenyCorrection).toHaveBeenCalledTimes(1));
        expect(input.onApproveCorrection).not.toHaveBeenCalled();
        expect(rowKeys()).toHaveLength(1);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
    });

    it('reconciles optimistic history with server decisions and does not reuse an overlay after a server reset', async () => {
        const input = props({ allCorrections: [correction(1), correction(2)], onReload: vi.fn().mockRejectedValue(new Error('offline')) });
        const { rerender } = mount(input); note('Lokale Notiz');
        fireEvent.click(screen.getByRole('button', { name: 'Genehmigen' }));
        await screen.findByRole('alert'); change('Status', 'all');
        expect(screen.getAllByText(/Lokale Notiz/)).toHaveLength(2);
        const serverRows = [correction(1, { approved: true, adminComment: 'Server hat bestätigt' }), correction(2, { denied: true, adminComment: 'Zeitgleich abgelehnt' })];
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} allCorrections={serverRows} /></div>);
        expect(screen.queryByText(/Lokale Notiz/)).not.toBeInTheDocument();
        expect(screen.getByText(/Server hat bestätigt/)).toBeInTheDocument();
        expect(screen.getByText(/Zeitgleich abgelehnt/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Genehmigen' })).not.toBeInTheDocument();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} allCorrections={[correction(1), serverRows[1]]} /></div>);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeEnabled();
    });

    it('does not present an unloaded or failed initial inbox as zero open requests', () => {
        const input = props({ loading: true });
        const { rerender } = mount(input);
        expect(screen.getByText('– Offen')).toBeInTheDocument();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} loading={false} loadError="Offline" /></div>);
        expect(screen.getByText('– Offen')).toBeInTheDocument();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} loading={false} /></div>);
        expect(screen.getByText('0 Offen')).toBeInTheDocument();
    });

    it('sorts mixed history by username or status in either direction through advanced controls', async () => {
        mount(props({ allVacations: [vacation(1, '2026-09-05', { username: 'jonas', denied: true }),
            vacation(2, '2026-09-06', { username: 'luca', approved: true })], allCorrections: [correction(3)] }));
        change('Status', 'all');
        expect(rowKeys()).toEqual(['vacation:1', 'vacation:2', expect.stringContaining('correction:')]);
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Filter' }));
        change('Sortieren nach', 'username'); change('Reihenfolge', 'desc');
        expect(rowKeys()).toEqual([expect.stringContaining('correction:'), 'vacation:2', 'vacation:1']);
        change('Sortieren nach', 'status'); change('Reihenfolge', 'asc');
        expect(rowKeys()).toEqual([expect.stringContaining('correction:'), 'vacation:2', 'vacation:1']);
        change('Reihenfolge', 'desc');
        expect(rowKeys()).toEqual(['vacation:1', 'vacation:2', expect.stringContaining('correction:')]);
        await act(async () => {});
    });

    it('keeps successful vacation decisions in history when reloading fails and preserves denied history', async () => {
        const input = props({ allVacations: [vacation(9), vacation(10, '2026-09-24', { approved: true }), vacation(11, '2026-09-26', { denied: true, adminNote: 'Kein Ersatz' })],
            onReload: vi.fn().mockRejectedValue(new Error('reload offline')) });
        mount(input); await screen.findByText('12 Tage verfügbar'); note('Plan passt');
        fireEvent.click(screen.getByRole('button', { name: 'Ablehnen' }));
        await waitFor(() => expect(input.onDenyVacation).toHaveBeenCalledExactlyOnceWith(9, 'Plan passt'));
        expect(await screen.findByRole('alert')).toHaveTextContent('Gespeichert. Die Liste konnte noch nicht aktualisiert werden.');
        expect(screen.getByText('Keine Anträge für diese Filter.')).toBeInTheDocument();
        change('Status', 'denied');
        expect(rowKeys()).toEqual(['vacation:9', 'vacation:11']);
        expect(screen.getByText('Plan passt', { exact: false })).toBeInTheDocument();
        choose('vacation:11');
        expect(screen.getByText('Kein Ersatz', { exact: false })).toBeInTheDocument();
        change('Status', 'approved'); expect(rowKeys()).toEqual(['vacation:10']);
        await act(async () => {});
    });

    it('confirms vacation deletion, keeps API failures in the dialog, and does not resurrect a saved deletion after reload failure', async () => {
        const pending = deferred();
        apiMock.delete.mockRejectedValueOnce(new Error('Löschen fehlgeschlagen')).mockImplementationOnce(() => pending.promise);
        const input = props({ allVacations: [vacation(5, '2026-09-10', { usesOvertime: true, approved: true, overtimeDeductionMinutes: 120 })], onReload: vi.fn().mockRejectedValue(new Error('offline')) });
        mount(input); change('Status', 'approved'); await screen.findByText('12 Tage verfügbar');
        fireEvent.click(screen.getByRole('button', { name: 'Urlaub löschen' }));
        let dialog = screen.getByRole('dialog', { name: 'Urlaub löschen bestätigen' });
        expect(dialog).toHaveTextContent('Luca Meier'); expect(dialog).toHaveTextContent('10.09.2026');
        expect(dialog).toHaveTextContent('abgezogenen Stunden');
        expect(apiMock.delete).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Ja, löschen' }));
        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Löschen fehlgeschlagen');
        fireEvent.click(within(dialog).getByRole('button', { name: 'Ja, löschen' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Wird gespeichert…' }));
        expect(apiMock.delete).toHaveBeenCalledTimes(2);
        expect(apiMock.delete).toHaveBeenLastCalledWith('/api/vacation/5', { params: { adminUsername: 'admin' } });
        await act(async () => pending.resolve({ data: {} }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByText('Keine Anträge für diese Filter.')).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent('Gespeichert. Die Liste konnte noch nicht aktualisiert werden.');
        expect(apiMock.delete).toHaveBeenCalledTimes(2);
    });

    it('preserves selection and notes during updates, blocks incomplete-data writes, and navigates with the actual request day', async () => {
        const input = props({ allCorrections: [correction(1)] });
        const { rerender } = mount(input); note('Entwurf bleibt');
        fireEvent.click(screen.getByRole('button', { name: 'Mitarbeiterprofil' }));
        fireEvent.click(screen.getByRole('button', { name: 'In Zeitprüfung öffnen ↗' }));
        expect(input.onOpenEmployee).toHaveBeenCalledWith('mirjam');
        expect(input.onOpenInTimeReview).toHaveBeenCalledWith(expect.objectContaining({ type: 'correction', id: 1, username: 'mirjam', dateIso: '2026-09-10', requestDate: '2026-09-14', entries: [correction(1)] }));
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} loading /></div>);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} loadError="Verbindung fehlt" /></div>);
        expect(screen.getByRole('textbox', { name: 'Kommentar zur Entscheidung' })).toHaveValue('Entwurf bleibt');
        expect(screen.getByRole('button', { name: 'Ablehnen' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(input.onRetry).toHaveBeenCalledOnce();
        rerender(<div className="admin-workspace"><AdminWorkspaceRequests {...input} currentUser={{ username: 'viewer', roles: ['ROLE_ADMIN'], pagePermissions: { adminDashboard: 'VIEW' } }} /></div>);
        expect(screen.getByRole('button', { name: 'Genehmigen' })).toBeDisabled();
        expect(input.onApproveCorrection).not.toHaveBeenCalled();
        await act(async () => {});
    });

    it('shows server vacation balances and only overlapping absences in the same team', async () => {
        const input = props({ allVacations: [vacation(5, '2026-09-20', { username: 'mirjam', endDate: '2026-09-25', halfDay: true }),
            vacation(6, '2026-09-23', { username: 'luca', endDate: '2026-09-29', approved: true }),
            vacation(7, '2026-09-21', { username: 'jonas', approved: true })],
            allSickLeaves: [{ id: 4, username: 'luca', startDate: '2026-09-24', endDate: '2026-09-24' }] });
        mount(input);
        await screen.findByText('12 Tage verfügbar');
        expect(apiMock.get).toHaveBeenCalledWith('/api/vacation/remaining', expect.objectContaining({ params: { username: 'mirjam', year: 2026 } }));
        const overlaps = screen.getByRole('heading', { name: 'Überschneidungen im Team' }).closest('section');
        expect(overlaps).toHaveTextContent('Luca Meier · 23.09.2026 – 25.09.2026 · Urlaub');
        expect(overlaps).toHaveTextContent('Luca Meier · 24.09.2026 · Krank');
        expect(overlaps).not.toHaveTextContent('Jonas Weber');
        fireEvent.click(screen.getByRole('button', { name: 'Urlaub löschen' }));
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toHaveFocus();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(apiMock.delete).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: 'Urlaub löschen' })).toHaveFocus();
    });

    it('reveals an explicitly focused history member despite stale filters and does not replay open signals after a cached-pane return', async () => {
        function Host() {
            const [employee, setEmployee] = useState('luca');
            const [focused, setFocused] = useState(null);
            const [visible, setVisible] = useState(true);
            return <><button onClick={() => setFocused({ type: 'correction', id: 2 })}>Fokus wechseln</button><button onClick={() => setVisible(value => !value)}>Pane wechseln</button>
                <Activity mode={visible ? 'visible' : 'hidden'}><AdminWorkspaceRequests {...props({ allCorrections: [correction(1), correction(2, { approved: true })] })}
                    employeeFilter={employee} onEmployeeFilterChange={setEmployee} focusedRequest={focused} correctionOpenSignal={1} /></Activity></>;
        }
        render(<Host />);
        change('Person', 'luca');
        fireEvent.change(screen.getByRole('searchbox', { name: 'Namenssuche' }), { target: { value: 'niemand' } });
        fireEvent.click(screen.getByRole('button', { name: 'Fokus wechseln' }));
        expect(screen.getByRole('combobox', { name: 'Person' })).toHaveValue('mirjam');
        expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('all');
        expect(screen.getByRole('searchbox', { name: 'Namenssuche' })).toHaveValue('');
        expect(screen.getAllByText('Gestempelt')).toHaveLength(2); note('Pane-Entwurf');
        fireEvent.click(screen.getByRole('button', { name: 'Pane wechseln' }));
        fireEvent.click(screen.getByRole('button', { name: 'Pane wechseln' }));
        expect(screen.getByRole('combobox', { name: 'Person' })).toHaveValue('mirjam');
        expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('all');
        expect(screen.getByRole('textbox', { name: 'Kommentar zur Entscheidung' })).toHaveValue('Pane-Entwurf');
    });
});
