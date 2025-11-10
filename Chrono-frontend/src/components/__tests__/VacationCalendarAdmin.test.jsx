import { describe, it, beforeEach, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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

describe('VacationCalendarAdmin admin editing', () => {
    beforeEach(() => {
        notifyMock.mockReset();
        api.get.mockReset();
        api.post.mockReset();
        api.put.mockReset();
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
            startDate: '2024-02-05',
            endDate: '2024-02-06',
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

        const marker = await screen.findByRole('button', { name: /employee1/ });
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
        expect(notifyMock).toHaveBeenCalledWith('Urlaubseintrag wurde aktualisiert.');
        expect(onReloadVacations).toHaveBeenCalled();
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

        const marker = await screen.findByRole('button', { name: /employee1/ });
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
        expect(notifyMock).toHaveBeenCalledWith('Krankmeldung wurde aktualisiert.');
        expect(onReloadVacations).toHaveBeenCalled();
    });
});
