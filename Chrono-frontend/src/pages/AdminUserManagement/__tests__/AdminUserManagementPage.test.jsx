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
        t: (key, fallback, options = {}) => String(fallback ?? key).replace(/{{\s*(\w+)\s*}}/g, (match, token) => (
            Object.prototype.hasOwnProperty.call(options, token) ? String(options[token] ?? '') : match
        )),
    }),
}));

import AdminUserManagementPage from '../AdminUserManagementPage.jsx';

const managedUser = {
    id: 42,
    username: 'team-admin',
    firstName: 'Team',
    lastName: 'Admin',
    email: 'team-admin@example.test',
    mobilePhone: '',
    personnelNumber: '',
    roles: ['ROLE_ADMIN'],
    country: 'DE',
    taxClass: '',
    isHourly: false,
    isPercentage: false,
    monthlySalary: null,
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

describe('AdminUserManagementPage time tracking visibility', () => {
    beforeEach(() => {
        apiMock.get.mockReset();
        apiMock.post.mockReset();
        apiMock.put.mockReset();
        apiMock.patch.mockReset();
        apiMock.delete.mockReset();
        notifyMock.mockClear();

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
});
