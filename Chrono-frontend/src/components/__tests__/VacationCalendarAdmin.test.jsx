import { describe, it, beforeEach, expect, vi } from 'vitest';
import { createRef } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VacationCalendarAdmin from '../VacationCalendarAdmin';
import api from '../../utils/api';
import { formatLocalDateYMD } from '../../pages/AdminDashboard/adminDashboardUtils';

const notifyMock = vi.fn();
const currentUserMock = {
    username: 'admin',
    roles: ['ROLE_ADMIN'],
    companyId: 7,
    company: { cantonAbbreviation: 'BE' }
};

vi.mock('../../utils/api', () => {
    return {
        default: {
            get: vi.fn(),
            post: vi.fn(),
            put: vi.fn(),
            delete: vi.fn(),
        },
    };
});

vi.mock('../../context/AuthContext', () => ({
    useAuth: () => ({ currentUser: currentUserMock }),
}));

vi.mock('../../context/NotificationContext', () => ({
    useNotification: () => ({ notify: notifyMock }),
}));

vi.mock('../../context/LanguageContext', () => ({
    useTranslation: () => ({ t: (_key, fallback) => fallback ?? _key }),
}));

describe('VacationCalendarAdmin employee vacation planning', () => {
    const companyUsers = [
        { id: 1, username: 'employee1', firstName: 'Erika', lastName: 'Mustermann', isPercentage: true, company: { cantonAbbreviation: 'BE' } },
        { id: 2, username: 'employee2', firstName: 'Max', lastName: 'Muster', isPercentage: false, company: { cantonAbbreviation: 'BE' } },
    ];
    let nextVacationId;

    beforeEach(() => {
        notifyMock.mockReset();
        api.get.mockReset().mockImplementation((url) => Promise.resolve({ data: url.includes('/api/holidays/details') ? {} : [] }));
        api.put.mockReset();
        api.delete.mockReset();
        nextVacationId = 200;
        api.post.mockReset().mockImplementation((_url, _body, { params }) => Promise.resolve({
            data: { ...params, id: nextVacationId++, approved: true, denied: false, color: '#336699' },
        }));
    });

    const openPlanner = async (props = {}) => {
        const user = userEvent.setup();
        const onReloadVacations = props.onReloadVacations || vi.fn().mockResolvedValue(undefined);
        render(<VacationCalendarAdmin vacationRequests={[]} companyUsers={companyUsers} focusUsername="employee1" onReloadVacations={onReloadVacations} {...props} />);
        await user.click(screen.getByRole('button', { name: 'Urlaub manuell erstellen' }));
        return { user, dialog: screen.getByRole('dialog', { name: 'Neuen Urlaub für Mitarbeiter anlegen' }), onReloadVacations };
    };

    const setDates = (dialog, startDate, endDate = startDate) => {
        fireEvent.change(within(dialog).getByLabelText(/Startdatum/), { target: { value: startDate } });
        fireEvent.change(within(dialog).getByLabelText(/Enddatum/), { target: { value: endDate } });
    };
    const addPeriod = (user, dialog) => user.click(within(dialog).getByRole('button', { name: /Weiteren Zeitraum vormerken/ }));
    const savePeriods = (user, dialog) => user.click(within(dialog).getByRole('button', { name: /^(Zeiträume speichern|Urlaub erstellen)$/ }));
    const queuedPeriods = (dialog) => within(dialog).queryByRole('list', { name: 'Vorgemerkte Zeiträume' });
    const dayButton = (dialog, label) => within(dialog).getByLabelText(label).closest('button');

    it('opens a planner through the dashboard ref without changing the existing calendar', async () => {
        const ref = createRef();
        const { container } = render(<VacationCalendarAdmin ref={ref} vacationRequests={[]} companyUsers={companyUsers} focusUsername="employee1" />);
        const calendar = container.querySelector('.calendar-lg');
        await act(async () => ref.current.createVacation());
        const dialog = screen.getByRole('dialog', { name: 'Neuen Urlaub für Mitarbeiter anlegen' });
        expect(within(dialog).getByRole('combobox', { name: /Benutzer Auswahl/ })).toHaveValue('employee1');
        expect(container.querySelector('.calendar-lg')).toBe(calendar);
        expect(api.post).not.toHaveBeenCalled();
    });

    it('applies the selected team to sickness markers and the employee picker', async () => {
        const today = formatLocalDateYMD(new Date());
        api.get.mockImplementation(url => Promise.resolve({ data: url.includes('/api/holidays/details') ? {} : url.includes('/sick-leave/') ? [
            { id: 1, username: 'employee1', startDate: today, endDate: today },
            { id: 2, username: 'employee2', startDate: today, endDate: today },
        ] : [] }));
        render(<VacationCalendarAdmin vacationRequests={[]} companyUsers={companyUsers} visibleUsernames={['employee1']} />);
        await screen.findByRole('button', { name: /Erika Mustermann/ });
        expect(screen.queryByRole('button', { name: /Max Muster/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Urlaub manuell erstellen' }));
        const picker = within(screen.getByRole('dialog')).getByRole('combobox', { name: /Benutzer Auswahl/ });
        expect(within(picker).queryByRole('option', { name: 'Max Muster' })).not.toBeInTheDocument();
    });

    it('keeps the focused employee selected on first open and reopen, without company-vacation controls', async () => {
        const { user, dialog } = await openPlanner();
        const employeeSelect = within(dialog).getByRole('combobox', { name: /Benutzer Auswahl/ });
        expect(employeeSelect).toHaveValue('employee1');
        expect(employeeSelect).toBeDisabled();
        expect(within(employeeSelect).queryByRole('option', { name: 'Max Muster' })).not.toBeInTheDocument();
        expect(within(dialog).queryByRole('checkbox', { name: 'Betriebsurlaub' })).not.toBeInTheDocument();

        setDates(dialog, '2027-01-14');
        await addPeriod(user, dialog);
        await user.click(within(dialog).getByRole('button', { name: 'Schließen' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Urlaub manuell erstellen' }));
        const reopened = screen.getByRole('dialog');
        expect(within(reopened).getByRole('combobox')).toHaveValue('employee1');
        expect(within(reopened).queryByRole('checkbox', { name: 'Betriebsurlaub' })).not.toBeInTheDocument();
        expect(queuedPeriods(reopened)).not.toBeInTheDocument();
        expect(within(reopened).getByLabelText(/Startdatum/)).toHaveValue('');
    });

    it('navigates the real creation calendar and selects a range spanning two months', async () => {
        const { user, dialog } = await openPlanner();
        setDates(dialog, '2027-01-01');
        expect(within(dialog).getByText('Januar 2027')).toBeInTheDocument();
        await user.click(dayButton(dialog, '28. Januar 2027'));
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('2027-01-28');
        expect(within(dialog).getByLabelText(/Enddatum/)).toHaveValue('');

        await user.click(within(dialog).getByRole('button', { name: '›' }));
        expect(within(dialog).getByText('Februar 2027')).toBeInTheDocument();
        await user.click(dayButton(dialog, '3. Februar 2027'));
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('2027-01-28');
        expect(within(dialog).getByLabelText(/Enddatum/)).toHaveValue('2027-02-03');
        await savePeriods(user, dialog);
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/vacation/adminCreate', null, {
            params: { adminUsername: 'admin', username: 'employee1', startDate: '2027-01-28', endDate: '2027-02-03', halfDay: false, usesOvertime: false },
        }));
        expect(dialog).toBeInTheDocument();
    });

    it('saves two separate periods with their own options and shows saved markers without closing', async () => {
        const { user, dialog, onReloadVacations } = await openPlanner();
        setDates(dialog, '2027-01-14');
        await user.click(within(dialog).getByRole('checkbox', { name: 'Halbtags Urlaub' }));
        await user.click(within(dialog).getByRole('checkbox', { name: 'Überstunden nutzen' }));
        fireEvent.change(within(dialog).getByLabelText(/Überstunden Insgesamt/), { target: { value: '3.5' } });
        await addPeriod(user, dialog);
        expect(dayButton(dialog, '14. Januar 2027')).toHaveClass('vacation-day-queued');

        setDates(dialog, '2027-02-08', '2027-02-12');
        await user.click(within(dialog).getByRole('checkbox', { name: 'Überstunden nutzen' }));
        await addPeriod(user, dialog);
        expect(within(queuedPeriods(dialog)).getAllByRole('listitem')).toHaveLength(2);
        await savePeriods(user, dialog);

        await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(1));
        expect(api.post).toHaveBeenCalledTimes(2);
        expect(api.post).toHaveBeenNthCalledWith(1, '/api/vacation/adminCreate', null, {
            params: { adminUsername: 'admin', username: 'employee1', startDate: '2027-01-14', endDate: '2027-01-14', halfDay: true, usesOvertime: true, overtimeDeductionMinutes: 210 },
        });
        expect(api.post).toHaveBeenNthCalledWith(2, '/api/vacation/adminCreate', null, {
            params: { adminUsername: 'admin', username: 'employee1', startDate: '2027-02-08', endDate: '2027-02-12', halfDay: false, usesOvertime: false },
        });
        expect(dialog).toBeInTheDocument();
        expect(within(dialog).getByRole('status')).toHaveTextContent(/gespeichert/);
        expect(queuedPeriods(dialog)).not.toBeInTheDocument();
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('');
        expect(dayButton(dialog, '8. Februar 2027')).toHaveClass('vacation-day-saved');
        await user.click(within(dialog).getByRole('button', { name: '‹' }));
        expect(dayButton(dialog, '14. Januar 2027')).toHaveClass('vacation-day-saved');
    });

    it('starts a fresh calendar range after completing the previous range with a typed end date', async () => {
        const { user, dialog, onReloadVacations } = await openPlanner();
        setDates(dialog, '2027-01-01');
        await user.click(dayButton(dialog, '8. Januar 2027'));
        expect(within(dialog).getByLabelText(/Enddatum/)).toHaveValue('');
        fireEvent.change(within(dialog).getByLabelText(/Enddatum/), { target: { value: '2027-01-12' } });
        await addPeriod(user, dialog);

        await user.click(dayButton(dialog, '20. Januar 2027'));
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('2027-01-20');
        expect(within(dialog).getByLabelText(/Enddatum/)).toHaveValue('');
        await user.click(dayButton(dialog, '22. Januar 2027'));
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('2027-01-20');
        expect(within(dialog).getByLabelText(/Enddatum/)).toHaveValue('2027-01-22');
        await savePeriods(user, dialog);
        await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(1));
        expect(api.post.mock.calls.map(([, , { params }]) => [params.startDate, params.endDate])).toEqual([
            ['2027-01-08', '2027-01-12'],
            ['2027-01-20', '2027-01-22'],
        ]);
    });

    it.each(['edit', 'delete'])('updates locally saved markers after %s when the initial parent refresh failed', async (action) => {
        const onReloadVacations = vi.fn()
            .mockRejectedValueOnce(new Error('Refresh unavailable'))
            .mockResolvedValue(undefined);
        const { user, dialog } = await openPlanner({ onReloadVacations });
        const today = new Date();
        const originalDay = new Date(today.getFullYear(), today.getMonth(), 7);
        const updatedDay = new Date(today.getFullYear(), today.getMonth(), 11);
        const originalDate = formatLocalDateYMD(originalDay);
        const updatedDate = formatLocalDateYMD(updatedDay);
        const label = (date) => date.toLocaleDateString('de-DE', { day: 'numeric', month: 'long', year: 'numeric' });
        setDates(dialog, originalDate);
        await savePeriods(user, dialog);
        await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Schließen' })).toBeEnabled());
        expect(onReloadVacations).toHaveBeenCalledTimes(1);
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Urlaub gespeichert. Die Übersicht konnte noch nicht aktualisiert werden.', type: 'warning' });
        expect(dayButton(dialog, label(originalDay))).toHaveClass('vacation-day-saved');
        await user.click(within(dialog).getByRole('button', { name: 'Schließen' }));
        await user.click(screen.getByRole('button', { name: new RegExp(`Erika Mustermann.*${originalDate}`) }));
        const editDialog = screen.getByRole('dialog', { name: 'Urlaubseintrag bearbeiten' });

        if (action === 'edit') {
            api.put.mockResolvedValue({ data: { id: 200, username: 'employee1', startDate: updatedDate, endDate: updatedDate } });
            setDates(editDialog, updatedDate);
            await user.click(within(editDialog).getByRole('button', { name: 'Urlaub aktualisieren' }));
            await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/vacation/200', expect.objectContaining({ startDate: updatedDate, endDate: updatedDate })));
            expect(screen.queryByRole('button', { name: new RegExp(`Erika Mustermann.*${originalDate}`) })).not.toBeInTheDocument();
            expect(screen.getByRole('button', { name: new RegExp(`Erika Mustermann.*${updatedDate}`) })).toBeInTheDocument();
        } else {
            api.delete.mockResolvedValue({ data: {} });
            await user.click(within(editDialog).getByRole('button', { name: 'Urlaub loeschen' }));
            await user.click(screen.getByRole('button', { name: 'Ja, loeschen' }));
            await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/vacation/200', { params: { adminUsername: 'admin' } }));
            expect(screen.queryByRole('button', { name: /Erika Mustermann/ })).not.toBeInTheDocument();
        }
        await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(2));
        await user.click(screen.getByRole('button', { name: 'Urlaub manuell erstellen' }));
        const reopened = screen.getByRole('dialog', { name: 'Neuen Urlaub für Mitarbeiter anlegen' });
        expect(dayButton(reopened, label(originalDay))).not.toHaveClass('vacation-day-saved');
        if (action === 'edit') expect(dayButton(reopened, label(updatedDay))).toHaveClass('vacation-day-saved');
    });

    it('retains only failed periods and retries without duplicating a successful period', async () => {
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
        try {
            const { user, dialog, onReloadVacations } = await openPlanner();
            setDates(dialog, '2027-01-14');
            await addPeriod(user, dialog);
            setDates(dialog, '2027-02-08', '2027-02-12');
            await addPeriod(user, dialog);
            api.post
                .mockResolvedValueOnce({ data: { id: 301, username: 'employee1', startDate: '2027-01-14', endDate: '2027-01-14', approved: true } })
                .mockRejectedValueOnce({ response: { data: { message: 'Temporärer Fehler' } } });
            await savePeriods(user, dialog);
            await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(1));
            expect(api.post).toHaveBeenCalledTimes(2);
            const remaining = within(queuedPeriods(dialog)).getAllByRole('listitem');
            expect(remaining).toHaveLength(1);
            expect(remaining[0]).toHaveTextContent('8.2.2027');
            expect(remaining[0]).not.toHaveTextContent('14.1.2027');
            expect(notifyMock).toHaveBeenCalledWith({ message: 'Fehler beim Anlegen des Urlaubs: Temporärer Fehler', type: 'error' });

            await savePeriods(user, dialog);
            await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(2));
            expect(api.post).toHaveBeenCalledTimes(3);
            expect(api.post.mock.calls[2]).toEqual(api.post.mock.calls[1]);
            expect(api.post.mock.calls.filter(([, , request]) => request.params.startDate === '2027-01-14')).toHaveLength(1);
            expect(queuedPeriods(dialog)).not.toBeInTheDocument();
            expect(dialog).toBeInTheDocument();
        } finally {
            errorSpy.mockRestore();
        }
    });

    it('blocks duplicate submissions and closing until both creation and the awaited reload finish', async () => {
        let resolvePost;
        let resolveReload;
        api.post.mockImplementation(() => new Promise((resolve) => { resolvePost = resolve; }));
        const onReloadVacations = vi.fn(() => new Promise((resolve) => { resolveReload = resolve; }));
        const { user, dialog } = await openPlanner({ onReloadVacations });
        setDates(dialog, '2027-01-14');
        await user.dblClick(within(dialog).getByRole('button', { name: 'Urlaub erstellen' }));
        expect(api.post).toHaveBeenCalledTimes(1);
        expect(within(dialog).getByRole('button', { name: 'Wird gespeichert …' })).toBeDisabled();
        expect(within(dialog).getByRole('button', { name: 'Schließen' })).toBeDisabled();
        await user.keyboard('{Escape}');
        expect(dialog).toBeInTheDocument();

        await act(async () => resolvePost({ data: { id: 401, username: 'employee1', startDate: '2027-01-14', endDate: '2027-01-14', approved: true } }));
        await waitFor(() => expect(onReloadVacations).toHaveBeenCalledTimes(1));
        expect(within(dialog).getByRole('button', { name: 'Wird gespeichert …' })).toBeDisabled();
        fireEvent.submit(dialog.querySelector('form'));
        expect(api.post).toHaveBeenCalledTimes(1);
        await act(async () => resolveReload());
        expect(within(dialog).queryByRole('button', { name: 'Wird gespeichert …' })).not.toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: 'Schließen' })).toBeEnabled();
        expect(dayButton(dialog, '14. Januar 2027')).toHaveClass('vacation-day-saved');
    });

    it('rejects a period overlapping the queue without adding or saving it', async () => {
        const { user, dialog } = await openPlanner();
        setDates(dialog, '2027-01-14', '2027-01-18');
        await addPeriod(user, dialog);
        setDates(dialog, '2027-01-18', '2027-01-20');
        await addPeriod(user, dialog);
        expect(within(queuedPeriods(dialog)).getAllByRole('listitem')).toHaveLength(1);
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Dieser Zeitraum überschneidet sich mit einem vorgemerkten Urlaub.', type: 'warning' });
        await savePeriods(user, dialog);
        expect(api.post).not.toHaveBeenCalled();
        expect(within(dialog).getByLabelText(/Startdatum/)).toHaveValue('2027-01-18');
    });

    it('rejects half-day vacation across multiple days in both queue and save actions', async () => {
        const { user, dialog } = await openPlanner();
        setDates(dialog, '2027-01-14', '2027-01-15');
        await user.click(within(dialog).getByRole('checkbox', { name: 'Halbtags Urlaub' }));
        await addPeriod(user, dialog);
        expect(queuedPeriods(dialog)).not.toBeInTheDocument();
        await savePeriods(user, dialog);
        expect(api.post).not.toHaveBeenCalled();
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Halbtags Urlaub ist nur für einen einzelnen Tag möglich.', type: 'error' });
    });
});

describe('VacationCalendarAdmin admin editing', () => {
    beforeEach(() => {
        notifyMock.mockReset();
        api.get.mockReset();
        api.post.mockReset();
        api.put.mockReset();
        api.delete.mockReset();
    });

    const companyUsers = [
        {
            id: 1,
            username: 'employee1',
            firstName: 'Erika',
            lastName: 'Mustermann',
            isPercentage: true,
            company: { cantonAbbreviation: 'BE' }
        }
    ];

    it('submits updated overtime vacation payload for edited entries', async () => {
        api.get.mockImplementation((url) => {
            if (url.includes('/api/holidays/details')) {
                return Promise.resolve({ data: {} });
            }
            if (url.includes('/api/sick-leave')) {
                return Promise.resolve({ data: [] });
            }
            return Promise.resolve({ data: [] });
        });
        api.put.mockResolvedValue({ data: {} });

        const vacation = {
            id: 33,
            username: 'employee1',
            startDate: formatLocalDateYMD(new Date()),
            endDate: formatLocalDateYMD(new Date()),
            halfDay: false,
            usesOvertime: true,
            overtimeDeductionMinutes: 480,
            color: '#336699',
            approved: true,
            denied: false,
            companyVacation: false,
        };

        const onReloadVacations = vi.fn();

        render(
            <VacationCalendarAdmin
                vacationRequests={[vacation]}
                onReloadVacations={onReloadVacations}
                companyUsers={companyUsers}
            />
        );

        const marker = await screen.findByRole('button', { name: /Erika Mustermann/ });
        await userEvent.click(marker);

        const startInput = await screen.findByLabelText(/Startdatum/);
        const endInput = await screen.findByLabelText(/Enddatum/);
        const overtimeHoursInput = await screen.findByLabelText(/Überstunden Insgesamt/);

        await userEvent.clear(startInput);
        await userEvent.type(startInput, '2024-02-07');
        await userEvent.clear(endInput);
        await userEvent.type(endInput, '2024-02-07');
        await userEvent.clear(overtimeHoursInput);
        await userEvent.type(overtimeHoursInput, '6');

        await userEvent.click(screen.getByRole('button', { name: /Urlaub aktualisieren/ }));

        await waitFor(() => expect(api.put).toHaveBeenCalled());
        expect(api.put).toHaveBeenCalledWith(
            '/api/vacation/33',
            {
                startDate: '2024-02-07',
                endDate: '2024-02-07',
                halfDay: false,
                usesOvertime: true,
                approved: true,
                denied: false,
                overtimeDeductionMinutes: 360,
            }
        );
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Urlaubseintrag wurde aktualisiert.', type: 'success' });
        expect(onReloadVacations).toHaveBeenCalled();
    });


    it('shows the current month initially instead of the earliest vacation month', async () => {
        api.get.mockImplementation((url) => {
            if (url.includes('/api/holidays/details')) {
                return Promise.resolve({ data: {} });
            }
            if (url.includes('/api/sick-leave')) {
                return Promise.resolve({ data: [] });
            }
            return Promise.resolve({ data: [] });
        });

        render(
            <VacationCalendarAdmin
                vacationRequests={[
                    {
                        id: 100,
                        username: 'employee1',
                        startDate: '2025-04-01',
                        endDate: '2025-04-01',
                        color: '#336699',
                    }
                ]}
                onReloadVacations={vi.fn()}
                companyUsers={companyUsers}
            />
        );

        const expectedCurrentMonth = new Date().toLocaleDateString('de-DE', { month: 'long', year: 'numeric' });
        await waitFor(() => {
            expect(screen.getByText(expectedCurrentMonth)).toBeInTheDocument();
        });
    });

    it('updates sick leave entries with admin-provided values', async () => {
        const todayIso = formatLocalDateYMD(new Date());
        const sickLeave = {
            id: 91,
            username: 'employee1',
            startDate: todayIso,
            endDate: todayIso,
            halfDay: false,
            comment: 'Initial',
            color: '#FF6347'
        };

        api.get.mockImplementation((url) => {
            if (url.includes('/api/holidays/details')) {
                return Promise.resolve({ data: {} });
            }
            if (url.includes('/api/sick-leave')) {
                return Promise.resolve({ data: [sickLeave] });
            }
            return Promise.resolve({ data: [] });
        });
        api.put.mockResolvedValue({ data: {} });

        const onReloadVacations = vi.fn();

        render(
            <VacationCalendarAdmin
                vacationRequests={[]}
                onReloadVacations={onReloadVacations}
                companyUsers={companyUsers}
            />
        );

        const marker = await screen.findByRole('button', { name: /Erika Mustermann/ });
        await userEvent.click(marker);

        const startInput = await screen.findByLabelText(/Startdatum/);
        const endInput = await screen.findByLabelText(/Enddatum/);
        const halfDayCheckbox = await screen.findByLabelText(/Halbtags krank/);
        const commentInput = await screen.findByLabelText(/Kommentar/);

        await userEvent.clear(startInput);
        await userEvent.type(startInput, '2024-03-10');
        await userEvent.clear(endInput);
        await userEvent.type(endInput, '2024-03-10');
        await userEvent.click(halfDayCheckbox);
        await userEvent.clear(commentInput);
        await userEvent.type(commentInput, 'updated');

        await userEvent.click(screen.getByRole('button', { name: /Krankmeldung aktualisieren/ }));

        await waitFor(() => expect(api.put).toHaveBeenCalled());
        expect(api.put).toHaveBeenCalledWith(
            '/api/sick-leave/91',
            {
                startDate: '2024-03-10',
                endDate: '2024-03-10',
                halfDay: true,
                comment: 'updated',
            }
        );
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Krankmeldung wurde aktualisiert.', type: 'success' });
        expect(onReloadVacations).toHaveBeenCalled();
    });

    it('deletes vacation entries after confirmation', async () => {
        api.get.mockImplementation((url) => {
            if (url.includes('/api/holidays/details')) {
                return Promise.resolve({ data: {} });
            }
            if (url.includes('/api/sick-leave')) {
                return Promise.resolve({ data: [] });
            }
            return Promise.resolve({ data: [] });
        });
        api.delete.mockResolvedValue({ data: {} });

        const vacation = {
            id: 44,
            username: 'employee1',
            startDate: formatLocalDateYMD(new Date()),
            endDate: formatLocalDateYMD(new Date()),
            halfDay: false,
            usesOvertime: false,
            color: '#336699',
            approved: true,
            denied: false,
            companyVacation: false,
        };
        const onReloadVacations = vi.fn();

        render(
            <VacationCalendarAdmin
                vacationRequests={[vacation]}
                onReloadVacations={onReloadVacations}
                companyUsers={companyUsers}
            />
        );

        const marker = await screen.findByRole('button', { name: /Erika Mustermann/ });
        await userEvent.click(marker);
        await userEvent.click(screen.getByRole('button', { name: /Urlaub loeschen/ }));
        await userEvent.click(screen.getByRole('button', { name: /Ja, loeschen/ }));

        await waitFor(() => expect(api.delete).toHaveBeenCalled());
        expect(api.delete).toHaveBeenCalledWith(
            '/api/vacation/44',
            { params: { adminUsername: 'admin' } }
        );
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Urlaubseintrag wurde geloescht.', type: 'success' });
        expect(onReloadVacations).toHaveBeenCalled();
    });

    it('deletes sick leave entries after confirmation', async () => {
        const todayIso = formatLocalDateYMD(new Date());
        const sickLeave = {
            id: 92,
            username: 'employee1',
            startDate: todayIso,
            endDate: todayIso,
            halfDay: false,
            comment: 'Initial',
            color: '#FF6347'
        };

        api.get.mockImplementation((url) => {
            if (url.includes('/api/holidays/details')) {
                return Promise.resolve({ data: {} });
            }
            if (url.includes('/api/sick-leave')) {
                return Promise.resolve({ data: [sickLeave] });
            }
            return Promise.resolve({ data: [] });
        });
        api.delete.mockResolvedValue({ data: {} });

        const onReloadVacations = vi.fn();

        render(
            <VacationCalendarAdmin
                vacationRequests={[]}
                onReloadVacations={onReloadVacations}
                companyUsers={companyUsers}
            />
        );

        const marker = await screen.findByRole('button', { name: /Erika Mustermann/ });
        await userEvent.click(marker);
        await userEvent.click(screen.getByRole('button', { name: /Krankmeldung loeschen/ }));
        await userEvent.click(screen.getByRole('button', { name: /Ja, loeschen/ }));

        await waitFor(() => expect(api.delete).toHaveBeenCalled());
        expect(api.delete).toHaveBeenCalledWith('/api/sick-leave/92');
        expect(notifyMock).toHaveBeenCalledWith({ message: 'Krankmeldung wurde geloescht.', type: 'success' });
        expect(onReloadVacations).toHaveBeenCalled();
    });
});
