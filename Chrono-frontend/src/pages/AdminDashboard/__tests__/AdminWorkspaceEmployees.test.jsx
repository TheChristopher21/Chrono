import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminWorkspaceEmployees from '../AdminWorkspaceEmployees';

const t = (_key, fallback) => fallback || _key;
const users = [
    { username: 'anna', firstName: 'Anna', lastName: 'Meier', departmentName: 'Atelier' },
    { username: 'bea', firstName: 'Bea', lastName: 'Keller', departmentName: 'Beratung' },
    { username: 'cleo', firstName: 'Cleo', lastName: 'Frei', departmentName: 'Atelier' },
];
const corrections = [1, 2].map(id => ({ id, username: 'anna', requestDate: '2026-09-10', reason: 'Fehler', desiredTimestamp: `2026-09-10T${id === 1 ? '08' : '17'}:00:00` }));
const base = { t, users, corrections, balances: [{ username: 'anna', trackingBalance: -45 }, { username: 'bea', trackingBalance: 0 }],
    vacations: [{ id: 3, username: 'anna', startDate: '2026-10-01', endDate: '2026-10-02', approved: false }],
    sickLeaves: [{ id: 4, username: 'bea', startDate: '2026-09-14', endDate: '2026-09-16', halfDay: true }],
    issueRows: [{ username: 'cleo', dateIso: '2026-09-11', issues: [{ type: 'missing', dateIso: '2026-09-11' }] }], canManage: true };
const row = username => document.querySelector(`[data-employee="${username}"]`);

beforeEach(() => { vi.useFakeTimers({ toFake: ['Date'] }); vi.setSystemTime(new Date(2026, 8, 15, 12)); });
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('employee action desk', () => {
    it('prioritizes actual issues and counts a correction group as one request', () => {
        render(<AdminWorkspaceEmployees {...base} />);
        expect(document.querySelector('.aw-people-list > li')).toHaveAttribute('data-employee', 'cleo');
        expect(within(row('anna')).getByRole('button', { name: '2 Anträge' })).toBeInTheDocument();
        expect(within(row('bea')).getByText(/Krank · ½ Tag/)).toHaveTextContent('16.09.2026');
        expect(screen.queryByText(/^Anwesend$/)).not.toBeInTheDocument();
    });

    it('routes every shortcut to the employee and the actual problem date', () => {
        const onOpenEmployee = vi.fn(), onOpenTime = vi.fn(), onOpenRequests = vi.fn(), onCreateVacation = vi.fn();
        render(<AdminWorkspaceEmployees {...base} {...{ onOpenEmployee, onOpenTime, onOpenRequests, onCreateVacation }} />);
        fireEvent.click(within(row('cleo')).getByRole('button', { name: /Zeitprüfung/ }));
        expect(onOpenTime).toHaveBeenCalledWith('cleo', '2026-09-11');
        fireEvent.click(within(row('anna')).getByRole('button', { name: '2 Anträge' }));
        expect(onOpenRequests).toHaveBeenCalledWith('anna');
        fireEvent.click(within(row('bea')).getByRole('button', { name: /Urlaub/ }));
        expect(onCreateVacation).toHaveBeenCalledWith('bea');
        fireEvent.click(within(row('anna')).getByRole('button', { name: 'Anna Meier' }));
        expect(onOpenEmployee).toHaveBeenCalledWith('anna');
    });

    it('filters by actual tasks, absence and balance without treating unavailable balances as zero', () => {
        render(<AdminWorkspaceEmployees {...base} />);
        expect(within(row('cleo')).getByText('–')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Handlungsbedarf/ }));
        expect(row('bea')).toBeNull();
        expect(row('anna')).not.toBeNull();
        fireEvent.click(screen.getByRole('button', { name: /Heute abwesend/ }));
        expect(row('bea')).not.toBeNull();
        expect(row('anna')).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: /Minussaldo/ }));
        expect(row('anna')).not.toBeNull();
        expect(row('bea')).toBeNull();
    });

    it('keeps all employees reachable and resets the page for a narrower search', async () => {
        const many = Array.from({ length: 15 }, (_, index) => ({ username: `person${index}`, firstName: `Person ${String(index).padStart(2, '0')}`, departmentName: 'Team' }));
        render(<AdminWorkspaceEmployees {...base} users={many} />);
        expect(document.querySelectorAll('.aw-people-list > li')).toHaveLength(6);
        fireEvent.click(screen.getByRole('button', { name: 'Nächste Seite' }));
        expect(row('person6')).not.toBeNull();
        fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'Person 14' } });
        await act(async () => {});
        expect(row('person14')).not.toBeNull();
        expect(screen.getByRole('button', { name: 'Nächste Seite' })).toBeDisabled();
    });

    it.each([{ loading: true }, { loadError: 'Server nicht erreichbar' }, { canManage: false }])('blocks creation when access or reliable data is missing: %j', props => {
        render(<AdminWorkspaceEmployees {...base} {...props} />);
        for (const button of screen.getAllByRole('button', { name: /\+ Urlaub/ })) expect(button).toBeDisabled();
    });
});
