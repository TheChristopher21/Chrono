/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
}));

const notifyMock = vi.hoisted(() => vi.fn());
const translateMock = vi.hoisted(() => (key, fallback, options = {}) => String(fallback ?? key).replace(/{{\s*(\w+)\s*}}/g, (match, token) => (
    Object.prototype.hasOwnProperty.call(options, token) ? String(options[token] ?? '') : match
)));
const authUserMock = vi.hoisted(() => ({
    username: 'manager',
    roles: ['ROLE_ADMIN'],
    companyFeatureKeys: [],
    pagePermissions: { adminUsers: 'MANAGE' },
}));

vi.mock('../../../utils/api', () => ({ default: apiMock }));
vi.mock('../../../components/Navbar', () => ({ default: () => <div>Navbar</div> }));
vi.mock('../../../context/AuthContext.jsx', () => ({
    useAuth: () => ({
        currentUser: authUserMock,
    }),
}));
vi.mock('../../../context/NotificationContext', () => ({
    useNotification: () => ({ notify: notifyMock }),
}));
vi.mock('../../../context/LanguageContext', () => ({
    useTranslation: () => ({
        t: translateMock,
    }),
}));

import AdminUserManagementPage from '../AdminUserManagementPage.jsx';

const managedUser = {
    id: 42,
    username: 'team-admin',
    firstName: 'Team',
    lastName: 'Admin',
    email: 'team-admin@example.test',
    mobilePhone: '+4912345',
    personnelNumber: '42',
    roles: ['ROLE_ADMIN'],
    country: 'DE',
    taxClass: '1',
    isHourly: false,
    isPercentage: false,
    monthlySalary: 4500,
    hourlyRate: null,
    annualVacationDays: 25,
    breakDuration: 30,
    dailyWorkHours: 8,
    expectedWorkDays: 5,
    scheduleCycle: 1,
    weeklySchedule: [{
        monday: 8,
        tuesday: 8,
        wednesday: 8,
        thursday: 8,
        friday: 8,
        saturday: 0,
        sunday: 0,
    }],
    scheduleEffectiveDate: '2026-05-05',
    employmentModelEffectiveFrom: '2025-10-01',
    entryDate: '2025-10-01',
    includeInTimeTracking: true,
    pagePermissions: { adminUsers: 'MANAGE' },
    companyFeatureKeys: [],
    color: '#4f46e5',
};

const superAdminUser = {
    ...managedUser,
    id: 7,
    username: 'root-super',
    roles: ['ROLE_SUPERADMIN'],
    includeInTimeTracking: true,
};

describe('AdminUserManagementPage complete user management', () => {
    beforeEach(() => {
        apiMock.get.mockReset();
        apiMock.post.mockReset();
        apiMock.put.mockReset();
        apiMock.patch.mockReset();
        apiMock.delete.mockReset();
        notifyMock.mockClear();
        authUserMock.pagePermissions = { adminUsers: 'MANAGE' };

        apiMock.get.mockResolvedValue({ data: [managedUser, superAdminUser] });
        apiMock.patch.mockResolvedValue({ data: { ...managedUser, includeInTimeTracking: false } });
    });

    it('saves a visibility-only toggle through the dedicated endpoint', async () => {
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        expect(screen.queryByText('root-super')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await userEvent.click(screen.getByRole('button', { name: 'Rolle ändern' }));

        fireEvent.click(screen.getByRole('button', { name: /Eingeschlossen in Zeit/i }));

        await screen.findByRole('button', { name: /Von Zeit/i });
        expect(screen.getByText(/1 ungespeicherte/i)).toBeInTheDocument();

        const saveButtons = screen.getAllByRole('button', { name: /Speichern|Änderungen speichern/i });
        expect(saveButtons[saveButtons.length - 1]).toBeEnabled();
        await userEvent.click(saveButtons[saveButtons.length - 1]);

        await waitFor(() => {
            expect(apiMock.patch).toHaveBeenCalledWith(
                '/api/admin/users/42/time-tracking-visibility',
                { includeInTimeTracking: false }
            );
        });
        expect(apiMock.put).not.toHaveBeenCalled();
    });

    it('keeps the active create-user input focused while typing multiple characters', async () => {
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: /Neuen Benutzer hinzuf/i }));

        await userEvent.click(screen.getByLabelText(/Benutzername/i));
        await userEvent.keyboard('a');

        const usernameInput = screen.getByLabelText(/Benutzername/i);
        expect(usernameInput).toHaveValue('a');
        expect(document.activeElement).toBe(usernameInput);

        await userEvent.keyboard('lice');

        expect(screen.getByLabelText(/Benutzername/i)).toHaveValue('alice');
    });

    it('uses the visible schedule start as the history effective date', async () => {
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await userEvent.click(screen.getByRole('tab', { name: 'Arbeitszeit' }));

        fireEvent.change(screen.getByLabelText(/Plan g.ltig ab/i), {
            target: { value: '2026-05-04' },
        });

        await userEvent.click(screen.getByRole('tab', { name: 'Payroll' }));

        expect(screen.getByLabelText(/nderung g.ltig ab/i)).toHaveValue('2026-05-04');
    });

    it('keeps the effective date returned by the backend when reopening a user', async () => {
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await userEvent.click(screen.getByRole('tab', { name: 'Payroll' }));

        expect(screen.getByLabelText(/nderung g.ltig ab/i)).toHaveValue('2025-10-01');
    });

    it('sends a changed backdated effective date even when the work model itself is unchanged', async () => {
        apiMock.put.mockResolvedValue({ data: managedUser });
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await userEvent.click(screen.getByRole('tab', { name: 'Payroll' }));
        fireEvent.change(screen.getByLabelText(/nderung g.ltig ab/i), {
            target: { value: '2025-09-01' },
        });

        const saveButtons = screen.getAllByRole('button', { name: /Speichern|Änderungen speichern/i });
        await userEvent.click(saveButtons[saveButtons.length - 1]);

        await waitFor(() => expect(apiMock.put).toHaveBeenCalledTimes(1));
        const payload = apiMock.put.mock.calls[0][1];
        expect(payload.employmentModelEffectiveFrom).toBe('2025-09-01');
        expect(payload.isHourly).toBe(false);
        expect(apiMock.patch).not.toHaveBeenCalled();
    });

    it('normalizes an hourly-model update and preserves its selected effective date', async () => {
        apiMock.put.mockResolvedValue({ data: { ...managedUser, isHourly: true } });
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        await userEvent.click(screen.getByRole('tab', { name: 'Payroll' }));
        await userEvent.click(screen.getByLabelText(/Stundenbasiert abrechnen/i));
        fireEvent.change(screen.getByLabelText(/Stundenlohn \(Brutto\)/i), { target: { value: '25' } });
        fireEvent.change(screen.getByLabelText(/nderung g.ltig ab/i), {
            target: { value: '2025-10-01' },
        });

        const saveButtons = screen.getAllByRole('button', { name: /Speichern|Änderungen speichern/i });
        await userEvent.click(saveButtons[saveButtons.length - 1]);

        await waitFor(() => expect(apiMock.put).toHaveBeenCalledTimes(1));
        const payload = apiMock.put.mock.calls[0][1];
        expect(payload).toMatchObject({
            isHourly: true,
            isPercentage: false,
            hourlyRate: 25,
            employmentModelEffectiveFrom: '2025-10-01',
            scheduleCycle: null,
            weeklySchedule: null,
            scheduleEffectiveDate: null,
            expectedWorkDays: null,
        });
    });

    it('creates a complete user and sends the normalized form payload', async () => {
        apiMock.post.mockResolvedValue({ data: { id: 99 } });
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: /Neuen Benutzer hinzuf/i }));
        await userEvent.type(screen.getByLabelText(/Benutzername/i), 'maria');
        await userEvent.type(screen.getByLabelText(/Passwort/i), 'secret');
        await userEvent.type(screen.getByLabelText(/Vorname/i), 'Maria');
        await userEvent.type(screen.getByLabelText(/Nachname/i), 'Wetzel');
        await userEvent.type(screen.getByLabelText(/E-Mail/i), 'maria@example.test');
        await userEvent.type(screen.getByLabelText(/Handynummer/i), '+49123');

        await userEvent.click(screen.getByRole('button', { name: /^4 Payroll$/ }));
        await userEvent.type(screen.getByLabelText(/Personalnummer/i), '62');
        await userEvent.type(screen.getByLabelText(/Steuerklasse/i), '1');
        fireEvent.change(screen.getByLabelText(/Monatslohn \(Brutto\)/i), { target: { value: '4000' } });
        await userEvent.click(screen.getByRole('button', { name: /^6 Vorschau$/ }));
        await userEvent.click(screen.getByRole('button', { name: /Benutzer erstellen/i }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledTimes(1));
        expect(apiMock.post.mock.calls[0][0]).toBe('/api/admin/users');
        expect(apiMock.post.mock.calls[0][1]).toMatchObject({
            username: 'maria',
            firstName: 'Maria',
            lastName: 'Wetzel',
            country: 'DE',
            taxClass: '1',
            personnelNumber: '62',
            monthlySalary: 4000,
            roles: ['ROLE_USER'],
            isHourly: false,
            isPercentage: false,
        });
    });

    it('deletes a user only after confirmation and reloads the list', async () => {
        apiMock.delete.mockResolvedValue({ data: {} });
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        await userEvent.click(screen.getByRole('button', { name: 'Loschen' }));
        expect(screen.getByText('"team-admin"')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'userManagement.deleteConfirmConfirm' }));

        await waitFor(() => expect(apiMock.delete).toHaveBeenCalledWith('/api/admin/users/42'));
        expect(apiMock.get).toHaveBeenCalledTimes(2);
    });

    it('shows API loading errors through the notification system', async () => {
        apiMock.get.mockRejectedValueOnce(new Error('network down'));

        render(<AdminUserManagementPage />);

        await waitFor(() => {
            expect(notifyMock).toHaveBeenCalledWith(
                expect.stringContaining('network down'),
                'error'
            );
        });
    });

    it('enforces view-only permissions in the user list', async () => {
        authUserMock.pagePermissions = { adminUsers: 'VIEW' };
        render(<AdminUserManagementPage />);

        await screen.findByText('team-admin');
        expect(screen.getByText(/Nur Ansicht: Dieser Benutzer/i)).toBeInTheDocument();
        expect(screen.getByText('Nur Ansicht')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Neuen Benutzer hinzuf/i })).not.toBeInTheDocument();
    });
});
