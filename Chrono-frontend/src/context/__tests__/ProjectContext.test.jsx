/** @vitest-environment jsdom */
import React from 'react';
import { render, waitFor, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
}));

vi.mock('../../utils/api', () => ({
    default: apiMock
}));

const notifyMock = vi.fn();
vi.mock('../NotificationContext', () => ({
    useNotification: () => ({ notify: notifyMock })
}));

vi.mock('../LanguageContext', () => ({
    useTranslation: () => ({ t: (_key, fallback) => (fallback ?? _key) })
}));

let authState;
vi.mock('../AuthContext', () => ({
    useAuth: () => authState
}));

import { ProjectProvider, useProjects } from '../ProjectContext.jsx';

const renderWithConsumer = () => {
    const latest = { current: null };

    const Consumer = () => {
        const value = useProjects();
        latest.current = value;
        return null;
    };

    return {
        latest,
        ...render(
            <ProjectProvider>
                <Consumer />
            </ProjectProvider>
        )
    };
};

describe('ProjectProvider', () => {
    beforeEach(() => {
        authState = { authToken: 'token', currentUser: { customerTrackingEnabled: true } };
        apiMock.get.mockReset();
        apiMock.post.mockReset();
        apiMock.put.mockReset();
        apiMock.delete.mockReset();
        notifyMock.mockReset();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('loads projects and hierarchy on mount', async () => {
        apiMock.get.mockResolvedValueOnce({ data: [{ id: 1, name: 'Root' }] });
        apiMock.get.mockResolvedValueOnce({ data: [{ id: 1, name: 'Root', children: [] }] });

        const { latest } = renderWithConsumer();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledWith('/api/projects');
            expect(apiMock.get).toHaveBeenCalledWith('/api/projects/hierarchy');
            expect(latest.current.projects).toEqual([{ id: 1, name: 'Root' }]);
            expect(latest.current.projectHierarchy).toEqual([{ id: 1, name: 'Root', children: [] }]);
        });
    });

    it('createProject posts trimmed payload and refreshes data', async () => {
        apiMock.get.mockResolvedValueOnce({ data: [] });
        apiMock.get.mockResolvedValueOnce({ data: [] });
        apiMock.post.mockResolvedValue({ data: { id: 99 } });
        apiMock.get.mockResolvedValueOnce({ data: [{ id: 1, name: 'New' }] });
        apiMock.get.mockResolvedValueOnce({ data: [{ id: 1, name: 'New', children: [] }] });

        const { latest } = renderWithConsumer();

        await waitFor(() => {
            const projectCalls = apiMock.get.mock.calls.filter(([url]) => url === '/api/projects');
            const hierarchyCalls = apiMock.get.mock.calls.filter(([url]) => url === '/api/projects/hierarchy');
            expect(projectCalls.length).toBeGreaterThanOrEqual(1);
            expect(hierarchyCalls.length).toBeGreaterThanOrEqual(1);
        });

        await act(async () => {
            await latest.current.createProject({
                name: '  New  ',
                customerId: 7,
                budgetMinutes: 120,
                parentId: null,
                hourlyRate: 95.5
            });
        });

        expect(apiMock.post).toHaveBeenCalledWith('/api/projects', {
            name: 'New',
            customer: { id: 7 },
            budgetMinutes: 120,
            parent: null,
            hourlyRate: 95.5
        });

        await waitFor(() => {
            const projectCalls = apiMock.get.mock.calls.filter(([url]) => url === '/api/projects');
            const hierarchyCalls = apiMock.get.mock.calls.filter(([url]) => url === '/api/projects/hierarchy');
            expect(projectCalls.length).toBeGreaterThanOrEqual(2);
            expect(hierarchyCalls.length).toBeGreaterThanOrEqual(2);
            expect(latest.current.projects).toEqual([{ id: 1, name: 'New' }]);
            expect(latest.current.projectHierarchy).toEqual([{ id: 1, name: 'New', children: [] }]);
        });
    });
});
