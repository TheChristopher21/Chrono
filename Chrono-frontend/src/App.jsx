import "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

// NEU: Imports für Auth-Status und ActionButtons
import { useAuth } from "./context/AuthContext.jsx";
import ActionButtons from "./components/ActionButtons.jsx";
import MobileTabBar from "./components/MobileTabBar.jsx";
import AnalyticsTracker from "./components/AnalyticsTracker.jsx";

// Globale Styles
import "./styles/global.css";
import "./styles/mobile-tokens.css";
import "./styles/Button.css";
import "./styles/Login.css";
import "./styles/PercentageDashboardScoped.css";

// Seiten-Komponenten
import LandingPage from "./pages/LandingPage.jsx";
import AboutChrono from "./pages/AboutChrono.jsx";
import Login from "./pages/Login.jsx";
import Registration from "./pages/Registration.jsx";
import UserDashboard from "./pages/UserDashboard/UserDashboard.jsx";
import PercentagePunch from "./pages/PercentageDashboard/PercentageDashboard.jsx";
import PersonalDataPage from "./pages/PersonalDataPage.jsx";
import AdminDashboard from "./pages/AdminDashboard/AdminDashboard.jsx";
import AdminEmployeeOverviewPage from "./pages/AdminDashboard/AdminEmployeeOverviewPage.jsx";
import AdminUserManagementPage from "./pages/AdminUserManagement/AdminUserManagementPage.jsx";
import AdminChangePassword from "./pages/AdminChangePassword.jsx";
import AdminCustomersPage from "./pages/AdminCustomers/AdminCustomersPage.jsx";
import AdminProjectsPage from "./pages/AdminProjects/AdminProjectsPage.jsx";
import AdminProjectReportPage from "./pages/AdminProjectReport/AdminProjectReportPage.jsx";
import AdminTasksPage from "./pages/AdminTasks/AdminTasksPage.jsx";
import AdminPayslipsPage from "./pages/AdminPayslipsPage.jsx";
import AdminSchedulePlannerPage from "./pages/AdminSchedulePlanner/AdminSchedulePlannerPage.jsx";
import AdminAnalyticsPage from "./pages/AdminAnalytics/AdminAnalyticsPage.jsx";
// NEU: Import der Seite für Schichtregeln
import AdminShiftRulesPage from "./pages/AdminSchedulePlanner/AdminScheduleRulesPage.jsx";
import PrintSchedule from "./pages/AdminSchedulePlanner/PrintSchedule.jsx";
import AdminKnowledgePage from "./pages/AdminKnowledge/AdminKnowledgePage.jsx";
import CompanyManagementPage from "./pages/CompanyManagementPage.jsx";
import TimeTrackingImport from "./TimeTrackingImport.jsx";
import PrintReport from "./pages/PrintReport.jsx";
import WhatsNewPage from "./pages/WhatsNewPage.jsx";
import AGB from "./pages/AGB.jsx";
import Impressum from "./pages/Impressum.jsx";
import Datenschutz from "./pages/Datenschutz.jsx";
import CompanySettingsPage from "./pages/CompanySettingsPage.jsx";
import PayslipsPage from "./pages/PayslipsPage.jsx";
import DemoTour from "./pages/DemoTour.jsx";
import AdminAccountingPage from "./pages/AdminAccounting/AdminAccountingPage.jsx";
import SupplyChainDashboard from "./pages/SupplyChain/SupplyChainDashboard.jsx";
import ChronoTwoDashboard from "./pages/ChronoTwo/ChronoTwoDashboard.jsx";
import CrmDashboard from "./pages/CRM/CrmDashboard.jsx";
import BankingOperationsPage from "./pages/AdminBanking/BankingOperationsPage.jsx";
import WorkTimeCalculatorPage from "./pages/WorkTimeCalculator/WorkTimeCalculatorPage.jsx";

// Hilfs-Komponenten
import PrivateRoute from "./components/PrivateRoute.jsx";
import ErrorBoundary from "./components/ErrorBoundary.jsx";

const queryClient = new QueryClient();

function App() {
    // NEU: Auth-Token prüfen, um zu entscheiden, ob der Chatbot angezeigt wird
    const { authToken, currentUser } = useAuth();
    const chatbotEnabled = Boolean(authToken && currentUser?.companyFeatureKeys?.includes("chatbot"));

    return (
        <QueryClientProvider client={queryClient}>
            <ErrorBoundary>
                <div className="App">
                    <AnalyticsTracker />
                    <Routes>
                        {/* Öffentliche Routen */}
                        <Route path="/" element={<LandingPage />} />
                        <Route path="/ueber-chrono-logisch" element={<AboutChrono />} />
                        <Route path="/login" element={<Login />} />
                        <Route path="/register" element={<Registration />} />
                        <Route path="/preise" element={<LandingPage />} />
                        <Route path="/arbeitszeit-rechner" element={<WorkTimeCalculatorPage />} />
                        <Route path="/agb" element={<AGB />} />
                        <Route path="/datenschutz" element={<Datenschutz />} />
                        <Route path="/impressum" element={<Impressum />} />

                        {/* Geschützte Benutzer-Routen */}
                        <Route path="/dashboard" element={<PrivateRoute requiredPagePermission="dashboard"><UserDashboard /></PrivateRoute>} />
                        <Route path="/percentage-punch" element={<PrivateRoute requiredPagePermission="dashboard"><PercentagePunch /></PrivateRoute>} />
                        <Route path="/personal-data" element={<PrivateRoute requiredPagePermission="personalData"><PersonalDataPage /></PrivateRoute>} />
                        <Route path="/payslips" element={<PrivateRoute requiredPagePermission="payslips"><PayslipsPage /></PrivateRoute>} />
                        <Route path="/demo-tour" element={<PrivateRoute requiredPagePermission="demoTour"><DemoTour /></PrivateRoute>} />
                        <Route
                            path="/workspace/supply-chain"
                            element={
                                <PrivateRoute
                                    requiredFeature="supplyChain"
                                    requiredPagePermission="supplyChain"
                                    redirectTo="/dashboard"
                                >
                                    <SupplyChainDashboard />
                                </PrivateRoute>
                            }
                        />

                        {/* Admin-Routen */}
                        <Route path="/admin/dashboard" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="adminDashboard"><AdminDashboard /></PrivateRoute>} />
                        <Route path="/admin/dashboard/mitarbeiter/:username" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="adminDashboard"><AdminEmployeeOverviewPage /></PrivateRoute>} />
                        <Route path="/admin/users" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="adminUsers"><AdminUserManagementPage /></PrivateRoute>} />
                        <Route path="/admin/change-password" element={<PrivateRoute requiredPagePermission="adminChangePassword"><AdminChangePassword /></PrivateRoute>} />
                        <Route
                            path="/admin/customers"
                            element={
                                <PrivateRoute
                                    requiredFeature="projects"
                                    requiredPagePermission={["adminCustomers", "adminProjects"]}
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminCustomersPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/projects"
                            element={
                                <PrivateRoute
                                    requiredFeature="projects"
                                    requiredPagePermission={["adminCustomers", "adminProjects", "adminTasks"]}
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminProjectsPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/tasks"
                            element={
                                <PrivateRoute
                                    requiredFeature="projects"
                                    requiredPagePermission={["adminProjects", "adminTasks"]}
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminTasksPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/analytics"
                            element={
                                <PrivateRoute
                                    requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]}
                                    requiredFeature="analytics"
                                    requiredPagePermission="adminAnalytics"
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminAnalyticsPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/accounting"
                            element={
                                <PrivateRoute
                                    requiredFeature="accounting"
                                    requiredPagePermission="adminAccounting"
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminAccountingPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/chrono-two"
                            element={
                                <PrivateRoute
                                    requiredFeature="chrono2"
                                    requiredPagePermission="chronoTwo"
                                    redirectTo="/admin/dashboard"
                                >
                                    <ChronoTwoDashboard />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/supply-chain"
                            element={
                                <PrivateRoute
                                    requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]}
                                    requiredFeature="supplyChain"
                                    requiredPagePermission="supplyChain"
                                    redirectTo="/admin/dashboard"
                                >
                                    <SupplyChainDashboard />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/crm"
                            element={
                                <PrivateRoute
                                    requiredFeature="crm"
                                    requiredPagePermission="crm"
                                    redirectTo="/admin/dashboard"
                                >
                                    <CrmDashboard />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/banking"
                            element={
                                <PrivateRoute
                                    requiredFeature="banking"
                                    requiredPagePermission="banking"
                                    redirectTo="/admin/dashboard"
                                >
                                    <BankingOperationsPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/project-report"
                            element={
                                <PrivateRoute
                                    requiredFeature="projects"
                                    requiredPagePermission="adminProjectReport"
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminProjectReportPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/company"
                            element={
                                <PrivateRoute requiredRole={["ROLE_SUPERADMIN"]} requiredPagePermission="companyManagement">
                                    <CompanyManagementPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/payslips"
                            element={
                                <PrivateRoute
                                    requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_PAYROLL_ADMIN"]}
                                    requiredFeature="payroll"
                                    requiredPagePermission="adminPayslips"
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminPayslipsPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/schedule"
                            element={
                                <PrivateRoute
                                    requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]}
                                    requiredFeature="roster"
                                    requiredPagePermission="adminSchedule"
                                    redirectTo="/admin/dashboard"
                                >
                                    <AdminSchedulePlannerPage />
                                </PrivateRoute>
                            }
                        />
                        <Route
                            path="/admin/print-schedule"
                            element={
                                <PrivateRoute
                                    requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]}
                                    requiredFeature="roster"
                                    requiredPagePermission="adminPrintSchedule"
                                    redirectTo="/admin/dashboard"
                                >
                                    <PrintSchedule />
                                </PrivateRoute>
                            }
                        />

                        <Route path="/admin/knowledge" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="adminKnowledge"><AdminKnowledgePage /></PrivateRoute>} />
                        <Route path="/admin/company-settings" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="companySettings"><CompanySettingsPage /></PrivateRoute>} />

                        {/* NEU: Route für die Schicht-Einstellungsseite */}
                        <Route
                            path="/admin/shift-rules"
                            element={
                                <PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredFeature="roster" requiredPagePermission="adminShiftRules">
                                    <AdminShiftRulesPage />
                                </PrivateRoute>
                            }
                        />

                        <Route path="/admin/import-times" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_SUPERADMIN"]} requiredPagePermission="adminImportTimes"><TimeTrackingImport /></PrivateRoute>} />
                        <Route path="/whats-new" element={<PrivateRoute><WhatsNewPage /></PrivateRoute>} />
                        <Route path="/print-report" element={<PrivateRoute requiredPagePermission="printReport"><PrintReport /></PrivateRoute>} />
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>

                    {/* NEU: Fügt den Chatbot nur als freigeschaltetes Firmen-Feature hinzu */}
                    {authToken && <>
                        {chatbotEnabled && <ActionButtons />}
                        <MobileTabBar />
                    </>}
                </div>
            </ErrorBoundary>
        </QueryClientProvider>
    );
}

export default App;
