/** @vitest-environment jsdom */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../components/ActionButtons.jsx', () => ({ default: () => null }));
vi.mock('../components/MobileTabBar.jsx', () => ({ default: () => null }));
vi.mock('../components/ErrorBoundary.jsx', () => ({
    default: ({ children }) => <>{children}</>,
}));

vi.mock('../pages/LandingPage.jsx', () => ({ default: () => <div>Landing page</div> }));
vi.mock('../pages/AboutChrono.jsx', () => ({ default: () => <div>About Chrono page</div> }));
vi.mock('../pages/Login.jsx', () => ({ default: () => <div>Login page</div> }));
vi.mock('../pages/Registration.jsx', () => ({ default: () => <div>Registration page</div> }));
vi.mock('../pages/UserDashboard/UserDashboard.jsx', () => ({ default: () => <div>User dashboard</div> }));
vi.mock('../pages/PercentageDashboard/PercentageDashboard.jsx', () => ({ default: () => <div>Percentage punch</div> }));
vi.mock('../pages/PersonalDataPage.jsx', () => ({ default: () => <div>Personal data</div> }));
vi.mock('../pages/AdminDashboard/AdminDashboard.jsx', () => ({ default: () => <div>Admin dashboard</div> }));
vi.mock('../pages/AdminDashboard/AdminEmployeeOverviewPage.jsx', () => ({ default: () => <div>Admin employee overview</div> }));
vi.mock('../pages/AdminUserManagement/AdminUserManagementPage.jsx', () => ({ default: () => <div>Admin users</div> }));
vi.mock('../pages/AdminChangePassword.jsx', () => ({ default: () => <div>Admin change password</div> }));
vi.mock('../pages/AdminCustomers/AdminCustomersPage.jsx', () => ({ default: () => <div>Admin customers</div> }));
vi.mock('../pages/AdminProjects/AdminProjectsPage.jsx', () => ({ default: () => <div>Admin projects</div> }));
vi.mock('../pages/AdminProjectReport/AdminProjectReportPage.jsx', () => ({ default: () => <div>Admin project report</div> }));
vi.mock('../pages/AdminTasks/AdminTasksPage.jsx', () => ({ default: () => <div>Admin tasks</div> }));
vi.mock('../pages/AdminPayslipsPage.jsx', () => ({ default: () => <div>Admin payslips</div> }));
vi.mock('../pages/AdminSchedulePlanner/AdminSchedulePlannerPage.jsx', () => ({ default: () => <div>Admin schedule</div> }));
vi.mock('../pages/AdminAnalytics/AdminAnalyticsPage.jsx', () => ({ default: () => <div>Admin analytics</div> }));
vi.mock('../pages/AdminSchedulePlanner/AdminScheduleRulesPage.jsx', () => ({ default: () => <div>Admin shift rules</div> }));
vi.mock('../pages/AdminSchedulePlanner/PrintSchedule.jsx', () => ({ default: () => <div>Print schedule</div> }));
vi.mock('../pages/AdminKnowledge/AdminKnowledgePage.jsx', () => ({ default: () => <div>Admin knowledge</div> }));
vi.mock('../pages/CompanyManagementPage.jsx', () => ({ default: () => <div>Company management</div> }));
vi.mock('../TimeTrackingImport.jsx', () => ({ default: () => <div>Time import</div> }));
vi.mock('../pages/PrintReport.jsx', () => ({ default: () => <div>Print report page</div> }));
vi.mock('../pages/WhatsNewPage.jsx', () => ({ default: () => <div>Whats new</div> }));
vi.mock('../pages/AGB.jsx', () => ({ default: () => <div>AGB</div> }));
vi.mock('../pages/Impressum.jsx', () => ({ default: () => <div>Impressum</div> }));
vi.mock('../pages/Datenschutz.jsx', () => ({ default: () => <div>Datenschutz</div> }));
vi.mock('../pages/CompanySettingsPage.jsx', () => ({ default: () => <div>Company settings</div> }));
vi.mock('../pages/PayslipsPage.jsx', () => ({ default: () => <div>Payslips</div> }));
vi.mock('../pages/DemoTour.jsx', () => ({ default: () => <div>Demo tour</div> }));
vi.mock('../pages/AdminAccounting/AdminAccountingPage.jsx', () => ({ default: () => <div>Admin accounting</div> }));
vi.mock('../pages/SupplyChain/SupplyChainDashboard.jsx', () => ({ default: () => <div>Supply chain</div> }));
vi.mock('../pages/ChronoTwo/ChronoTwoDashboard.jsx', () => ({ default: () => <div>Chrono two</div> }));
vi.mock('../pages/CRM/CrmDashboard.jsx', () => ({ default: () => <div>CRM</div> }));
vi.mock('../pages/AdminBanking/BankingOperationsPage.jsx', () => ({ default: () => <div>Banking</div> }));

import App from '../App.jsx';
import { AuthContext } from '../context/AuthContext.jsx';

function renderApp(authValue, initialPath) {
    return render(
        <AuthContext.Provider value={authValue}>
            <MemoryRouter initialEntries={[initialPath]}>
                <App />
            </MemoryRouter>
        </AuthContext.Provider>
    );
}

describe('App print report routing', () => {
    it('redirects anonymous users from print report to login', () => {
        renderApp(
            {
                authToken: null,
                currentUser: null,
                isAuthLoading: false,
            },
            '/print-report'
        );

        expect(screen.getByText('Login page')).toBeInTheDocument();
        expect(screen.queryByText('Print report page')).not.toBeInTheDocument();
    });

    it('allows authenticated users to open print report', () => {
        renderApp(
            {
                authToken: 'token',
                currentUser: {
                    username: 'alice',
                    roles: ['ROLE_USER'],
                    companyFeatureKeys: [],
                    pagePermissions: { printReport: 'VIEW' },
                },
                isAuthLoading: false,
            },
            '/print-report'
        );

        expect(screen.getByText('Print report page')).toBeInTheDocument();
        expect(screen.queryByText('Login page')).not.toBeInTheDocument();
    });
});
