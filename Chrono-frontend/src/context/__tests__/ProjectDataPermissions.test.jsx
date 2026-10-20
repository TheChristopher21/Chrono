/** @vitest-environment jsdom */
import React from 'react';
import { act, render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
}));

const authState = vi.hoisted(() => ({ value: null }));
const translateMock = vi.hoisted(() => (_key, fallback) => fallback ?? _key);
const notifyMock = vi.hoisted(() => vi.fn());

vi.mock('../../utils/api', () => ({ default: apiMock }));
vi.mock('../AuthContext', () => ({ useAuth: () => authState.value }));
vi.mock('../LanguageContext', () => ({
    useTranslation: () => ({ t: translateMock })
}));
vi.mock('../NotificationContext', () => ({
    useNotification: () => ({ notify: notifyMock })
}));

import { CustomerProvider, useCustomers } from '../CustomerContext.jsx';
import { TaskProvider, useTasks } from '../TaskContext.jsx';

const userWith = (pagePermissions, companyId = 12) => ({
    id: 5,
    companyId,
    roles: ['ROLE_USER'],
    customerTrackingEnabled: true,
    companyFeatureKeys: ['projects'],
    pagePermissions
});

const renderCustomerContext = () => {
    const latest = { current: null };
    const Consumer = () => {
        latest.current = useCustomers();
        return null;
    };
    const renderTree = () => (
        <CustomerProvider>
            <Consumer />
        </CustomerProvider>
    );
    const result = render(renderTree());
    return { latest, rerenderContext: () => result.rerender(renderTree()), ...result };
};

const renderTaskContext = () => {
    const latest = { current: null };
    const Consumer = () => {
        latest.current = useTasks();
        return null;
    };
    return {
        latest,
        ...render(
            <TaskProvider>
                <Consumer />
            </TaskProvider>
        )
    };
};

describe('permission-aware project data contexts', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        authState.value = {
            authToken: 'token',
            currentUser: userWith({ dashboard: 'VIEW' })
        };
    });

    it('loads customers for dashboard users supported by the API permission matrix', async () => {
        apiMock.get.mockResolvedValue({ data: [{ id: 1, name: 'Dashboard Kunde' }] });

        const { latest } = renderCustomerContext();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledWith('/api/customers');
            expect(latest.current.customers).toEqual([{ id: 1, name: 'Dashboard Kunde' }]);
        });
    });

    it('does not load customers for a task-only grant', async () => {
        authState.value.currentUser = userWith({ adminTasks: 'VIEW' });

        const { latest } = renderCustomerContext();

        await waitFor(() => expect(latest.current.customersLoading).toBe(false));
        expect(apiMock.get).not.toHaveBeenCalled();
        expect(latest.current.customers).toEqual([]);
    });

    it('keeps the existing customer load toast for unexpected failures', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        apiMock.get.mockRejectedValue({ response: { status: 500 } });

        const { latest } = renderCustomerContext();

        await waitFor(() => {
            expect(latest.current.customersError).toBe('Kunden konnten nicht geladen werden.');
            expect(notifyMock).toHaveBeenCalledWith('Kunden konnten nicht geladen werden.', 'error');
        });
        consoleError.mockRestore();
    });

    it('ignores a late customer response after the company changes', async () => {
        let resolveOldRequest;
        apiMock.get
            .mockReturnValueOnce(new Promise((resolve) => { resolveOldRequest = resolve; }))
            .mockResolvedValueOnce({ data: [{ id: 2, name: 'Neue Firma' }] });

        const { latest, rerenderContext } = renderCustomerContext();
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(1));

        authState.value = {
            authToken: 'new-token',
            currentUser: userWith({ dashboard: 'VIEW' }, 99)
        };
        rerenderContext();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledTimes(2);
            expect(latest.current.customers).toEqual([{ id: 2, name: 'Neue Firma' }]);
        });

        await act(async () => {
            resolveOldRequest({ data: [{ id: 1, name: 'Alte Firma' }] });
            await Promise.resolve();
        });
        expect(latest.current.customers).toEqual([{ id: 2, name: 'Neue Firma' }]);
    });

    it('fetches tasks only when a project and a supported page grant are present', async () => {
        authState.value.currentUser = userWith({ adminTasks: 'VIEW' });
        apiMock.get.mockResolvedValue({ data: [{ id: 4, name: 'Planung' }] });
        const { latest } = renderTaskContext();

        await act(async () => {
            await latest.current.fetchTasks(17);
        });
        expect(apiMock.get).toHaveBeenCalledWith('/api/tasks', { params: { projectId: 17 } });
        expect(latest.current.tasks).toEqual([{ id: 4, name: 'Planung' }]);

        await act(async () => {
            await latest.current.fetchTasks(null);
        });
        expect(latest.current.tasks).toEqual([]);
    });

    it('does not request tasks without a supported page permission', async () => {
        authState.value.currentUser = userWith({ adminCustomers: 'VIEW' });
        const { latest } = renderTaskContext();

        await act(async () => {
            await latest.current.fetchTasks(17);
        });

        expect(apiMock.get).not.toHaveBeenCalled();
        expect(latest.current.tasks).toEqual([]);
    });
});
