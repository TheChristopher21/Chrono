/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';

const testState = vi.hoisted(() => ({
    auth: null,
    projects: null,
    customers: null,
    tasks: null
}));

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
}));

const notifyMock = vi.hoisted(() => vi.fn());
const translateMock = vi.hoisted(() => (_key, fallback) => fallback ?? _key);

vi.mock('../../components/Navbar', () => ({ default: () => <nav>Navigation</nav> }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ currentUser: testState.auth }) }));
vi.mock('../../context/LanguageContext', () => ({
    useTranslation: () => ({ t: translateMock })
}));
vi.mock('../../context/NotificationContext', () => ({
    useNotification: () => ({ notify: notifyMock })
}));
vi.mock('../../context/ProjectContext', () => ({ useProjects: () => testState.projects }));
vi.mock('../../context/CustomerContext', () => ({ useCustomers: () => testState.customers }));
vi.mock('../../context/TaskContext', () => ({ useTasks: () => testState.tasks }));
vi.mock('../../utils/api', () => ({ default: apiMock }));

import AdminProjectsPage from './AdminProjectsPage.jsx';

const LocationProbe = () => {
    const location = useLocation();
    return <output data-testid="location">{location.pathname}{location.search}</output>;
};

const companyUser = (pagePermissions, roles = ['ROLE_USER']) => ({
    id: 8,
    companyId: 21,
    roles,
    customerTrackingEnabled: true,
    companyFeatureKeys: ['projects'],
    pagePermissions
});

const renderPage = (initialEntry = '/admin/projects') => render(
    <MemoryRouter initialEntries={[initialEntry]}>
        <AdminProjectsPage />
        <LocationProbe />
    </MemoryRouter>
);

describe('AdminProjectsPage permissions and operational states', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        testState.auth = companyUser({ adminProjects: 'VIEW' });
        testState.projects = {
            projects: [{
                id: 1,
                name: 'Website Relaunch',
                customer: { id: 4, name: 'Beispiel AG' },
                budgetMinutes: 600,
                hourlyRate: 140
            }],
            projectHierarchy: [{
                id: 1,
                name: 'Website Relaunch',
                customerName: 'Beispiel AG',
                budgetMinutes: 600,
                children: []
            }],
            projectsLoading: false,
            projectsError: null,
            fetchProjects: vi.fn(),
            createProject: vi.fn().mockResolvedValue({ id: 2 }),
            updateProject: vi.fn().mockResolvedValue({ id: 1 }),
            deleteProject: vi.fn().mockResolvedValue(undefined)
        };
        testState.customers = {
            customers: [{ id: 4, name: 'Beispiel AG' }],
            customersLoading: false,
            customersError: null,
            fetchCustomers: vi.fn(),
            createCustomer: vi.fn().mockResolvedValue({ id: 5, name: 'Neu AG' }),
            updateCustomer: vi.fn().mockResolvedValue({ id: 4, name: 'Beispiel AG' }),
            deleteCustomer: vi.fn().mockResolvedValue(undefined)
        };
        testState.tasks = {
            tasks: [{ id: 10, name: 'Konzept', budgetMinutes: 120, billable: true }],
            tasksLoading: false,
            tasksError: null,
            fetchTasks: vi.fn().mockResolvedValue(undefined),
            createTask: vi.fn().mockResolvedValue({ id: 11 }),
            updateTask: vi.fn().mockResolvedValue({ id: 10 }),
            deleteTask: vi.fn().mockResolvedValue(undefined)
        };
        apiMock.get.mockImplementation((url) => {
            if (url === '/api/report/analytics/projects') return Promise.resolve({ data: [] });
            return Promise.resolve({ data: [] });
        });
        apiMock.post.mockResolvedValue({ data: {} });
        apiMock.put.mockResolvedValue({ data: {} });
        apiMock.delete.mockResolvedValue({ data: {} });
    });

    it('falls back to the first permitted tab and canonicalizes a forbidden tab query', async () => {
        testState.auth = companyUser({ adminCustomers: 'MANAGE' }, ['ROLE_ADMIN']);

        renderPage('/admin/projects?tab=projects');

        expect(screen.getByRole('tab', { name: 'Kunden' })).toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Projekte' })).not.toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Aufgaben' })).not.toBeInTheDocument();
        expect(screen.getByRole('main')).toHaveClass('admin-projects-page');
        expect(screen.getByText('Kunden gesamt')).toBeInTheDocument();
        expect(screen.queryByText('Projekte gesamt')).not.toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByTestId('location')).toHaveTextContent('/admin/projects?tab=customers');
        });
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('does not mount company-scoped tools without a company context', () => {
        testState.auth = {
            ...companyUser({ adminProjects: 'MANAGE' }, ['ROLE_ADMIN']),
            companyId: null
        };

        renderPage();

        expect(screen.getByRole('heading', { name: 'Projektmodul nicht verfügbar' })).toBeInTheDocument();
        expect(screen.queryByRole('tab')).not.toBeInTheDocument();
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('keeps a project VIEW grant read-only and does not load admin-only tools', async () => {
        const user = userEvent.setup();
        renderPage();

        expect(screen.getByRole('tab', { name: 'Projekte' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByText('Projekte gesamt')).toBeInTheDocument();
        expect(screen.getByText('Kunden gesamt')).toBeInTheDocument();
        expect(screen.getByText('In der Kundenverwaltung erfasst')).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Kunden' })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Aufgaben' })).toBeInTheDocument();
        expect(screen.getByText('Du kannst Projekte ansehen, aber nicht verändern.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Neues Projekt starten' })).not.toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Neues Projekt anlegen' })).not.toBeInTheDocument();
        expect(screen.queryByText(/Integrationen/)).not.toBeInTheDocument();
        expect(screen.queryByText('Automatisierte Abrechnung')).not.toBeInTheDocument();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledWith('/api/report/analytics/projects', expect.any(Object));
        });
        expect(apiMock.get).not.toHaveBeenCalledWith('/api/integrations');
        expect(apiMock.get).not.toHaveBeenCalledWith('/api/audit', expect.anything());

        await user.click(screen.getByRole('tab', { name: 'Kunden' }));
        expect(screen.getByText('Du kannst Kunden ansehen, aber nicht verändern.')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Neuen Kunden anlegen' })).not.toBeInTheDocument();

        await user.click(screen.getByRole('tab', { name: 'Aufgaben' }));
        expect(screen.getByText('Du kannst Aufgaben ansehen, aber nicht verändern.')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Neue Aufgabe anlegen' })).not.toBeInTheDocument();
    });

    it('does not expose or call unfinished integration, billing, or global audit prototypes', async () => {
        testState.auth = companyUser({ adminProjects: 'MANAGE' }, ['ROLE_ADMIN']);

        renderPage();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledWith('/api/report/analytics/projects', expect.any(Object));
        });
        expect(screen.queryByText(/Integrationen/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Compliance & Audit/)).not.toBeInTheDocument();
        expect(screen.queryByText('Automatisierte Abrechnung')).not.toBeInTheDocument();
        expect(apiMock.get.mock.calls.some(([url]) => String(url).startsWith('/api/integrations'))).toBe(false);
        expect(apiMock.get.mock.calls.some(([url]) => String(url).startsWith('/api/audit'))).toBe(false);
        expect(apiMock.post.mock.calls.some(([url]) => String(url).startsWith('/api/billing'))).toBe(false);
    });

    it('shows only the task workspace for a task-only grant and omits unrelated hero KPIs', async () => {
        testState.auth = companyUser({ adminTasks: 'VIEW' }, ['ROLE_ADMIN']);

        const { container } = renderPage('/admin/projects?tab=projects');

        expect(screen.getByRole('tab', { name: 'Aufgaben' })).toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Projekte' })).not.toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Kunden' })).not.toBeInTheDocument();
        expect(container.querySelector('.hero-stats')).not.toBeInTheDocument();

        await waitFor(() => {
            expect(testState.tasks.fetchTasks).toHaveBeenCalledWith(1);
            expect(screen.getByTestId('location')).toHaveTextContent('/admin/projects?tab=tasks');
        });
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('emits exactly one page-level toast for a customer mutation', async () => {
        const user = userEvent.setup();
        testState.auth = companyUser({ adminCustomers: 'MANAGE' }, ['ROLE_ADMIN']);
        renderPage('/admin/projects?tab=customers');

        await user.type(screen.getByLabelText('Kundenname'), 'Neu AG');
        await user.click(screen.getByRole('button', { name: 'Anlegen' }));

        await waitFor(() => {
            expect(testState.customers.createCustomer).toHaveBeenCalledWith('Neu AG');
            expect(notifyMock).toHaveBeenCalledTimes(1);
        });
        expect(notifyMock).toHaveBeenCalledWith('Kunde erfolgreich angelegt!', 'success');
    });

    it('turns expected authorization failures into inline states without console errors', async () => {
        testState.auth = companyUser({ adminProjects: 'VIEW' }, ['ROLE_ADMIN']);
        const forbidden = { response: { status: 403 } };
        apiMock.get.mockRejectedValue(forbidden);
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

        renderPage();

        await waitFor(() => {
            expect(screen.getByText(/Deine Berechtigung hat sich geändert/)).toBeInTheDocument();
        });
        expect(consoleError.mock.calls.some(([label]) => String(label).startsWith('Error loading'))).toBe(false);
        consoleError.mockRestore();
    });

    it('validates analytics date ranges before issuing another request', async () => {
        const user = userEvent.setup();
        renderPage();

        await waitFor(() => {
            expect(apiMock.get).toHaveBeenCalledWith('/api/report/analytics/projects', expect.any(Object));
        });
        const initialCalls = apiMock.get.mock.calls.filter(([url]) => url === '/api/report/analytics/projects').length;

        await user.clear(screen.getByLabelText('Von'));
        await user.type(screen.getByLabelText('Von'), '2026-09-30');
        await user.clear(screen.getByLabelText('Bis'));
        await user.type(screen.getByLabelText('Bis'), '2026-09-01');
        await user.click(screen.getByRole('button', { name: 'Analytics aktualisieren' }));

        expect(await screen.findByText('Das Startdatum muss vor oder am Enddatum liegen.')).toBeInTheDocument();
        expect(apiMock.get.mock.calls.filter(([url]) => url === '/api/report/analytics/projects')).toHaveLength(initialCalls);
    });
});
