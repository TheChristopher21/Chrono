import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

// NEU: Imports für Auth-Status und ActionButtons
import { useAuth } from "./context/AuthContext.jsx";
import ActionButtons from "./components/ActionButtons.jsx";

// Globale Styles
import "./styles/global.css";
import "./styles/Button.css";
import "./styles/Login.css";
import "./styles/Navbar.css";
import "./styles/PercentageDashboardScoped.css";

// Seiten-Komponenten
import LandingPage from "./pages/LandingPage.jsx";
import Login from "./pages/Login.jsx";
import Registration from "./pages/Registration.jsx";
import UserDashboard from "./pages/UserDashboard/UserDashboard.jsx";
import PercentagePunch from "./pages/PercentageDashboard/PercentageDashboard.jsx";
import PersonalDataPage from "./pages/PersonalDataPage.jsx";
import AdminDashboard from "./pages/AdminDashboard/AdminDashboard.jsx";
import AdminUserManagementPage from "./pages/AdminUserManagement/AdminUserManagementPage.jsx";
import AdminChangePassword from "./pages/AdminChangePassword.jsx";
import AdminCustomersPage from "./pages/AdminCustomers/AdminCustomersPage.jsx";
import AdminProjectsPage from "./pages/AdminProjects/AdminProjectsPage.jsx";
import AdminProjectReportPage from "./pages/AdminProjectReport/AdminProjectReportPage.jsx";
import AdminTasksPage from "./pages/AdminTasks/AdminTasksPage.jsx";
import AdminPayslipsPage from "./pages/AdminPayslipsPage.jsx";
import AdminSchedulePlannerPage from "./pages/AdminSchedulePlanner/AdminSchedulePlannerPage.jsx";
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
import CompanySettingsPage from "./pages/CompanySettingsPage.jsx";
import PayslipsPage from "./pages/PayslipsPage.jsx";

// Hilfs-Komponenten
import PrivateRoute from "./components/PrivateRoute.jsx";
import ErrorBoundary from "./components/ErrorBoundary.jsx";

const queryClient = new QueryClient();

function App() {
    // NEU: Auth-Token prüfen, um zu entscheiden, ob der Chatbot angezeigt wird
    const { authToken } = useAuth();

    return (
        <QueryClientProvider client={queryClient}>
            <ErrorBoundary>
                <div className="App">
                    <Routes>
                        {/* Öffentliche Routen */}
                        <Route path="/" element={<LandingPage />} />
                        <Route path="/login" element={<Login />} />
                        <Route path="/register" element={<Registration />} />
                        <Route path="/agb" element={<AGB />} />
                        <Route path="/impressum" element={<Impressum />} />

                        {/* Geschützte Benutzer-Routen */}
                        <Route path="/dashboard" element={<PrivateRoute><UserDashboard /></PrivateRoute>} />
                        <Route path="/percentage-punch" element={<PrivateRoute><PercentagePunch /></PrivateRoute>} />
                        <Route path="/personal-data" element={<PrivateRoute><PersonalDataPage /></PrivateRoute>} />
                        <Route path="/payslips" element={<PrivateRoute><PayslipsPage /></PrivateRoute>} />

                        {/* Admin-Routen */}
                        <Route path="/admin/dashboard" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminDashboard /></PrivateRoute>} />
                        <Route path="/admin/users" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminUserManagementPage /></PrivateRoute>} />
                        <Route path="/admin/change-password" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminChangePassword /></PrivateRoute>} />
                        <Route path="/admin/customers" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminCustomersPage /></PrivateRoute>} />
                        <Route path="/admin/projects" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminProjectsPage /></PrivateRoute>} />
                        <Route path="/admin/tasks" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminTasksPage /></PrivateRoute>} />
                        <Route path="/admin/project-report" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminProjectReportPage /></PrivateRoute>} />
                        <Route path="/admin/company" element={<PrivateRoute requiredRole="ROLE_ADMIN"><CompanyManagementPage /></PrivateRoute>} />
                        <Route path="/admin/payslips" element={<PrivateRoute requiredRole={["ROLE_ADMIN", "ROLE_PAYROLL_ADMIN"]}><AdminPayslipsPage /></PrivateRoute>} />
                        <Route path="/admin/schedule" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminSchedulePlannerPage /></PrivateRoute>} />
                        <Route path="/admin/print-schedule" element={<PrivateRoute requiredRole="ROLE_ADMIN"><PrintSchedule /></PrivateRoute>} />

                        <Route path="/admin/knowledge" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminKnowledgePage /></PrivateRoute>} />
                        <Route path="/admin/company-settings" element={<PrivateRoute requiredRole="ROLE_ADMIN"><CompanySettingsPage /></PrivateRoute>} />

                        {/* NEU: Route für die Schicht-Einstellungsseite */}
                        <Route
                            path="/admin/shift-rules"
                            element={
                                <PrivateRoute requiredRole="ROLE_ADMIN">
                                    <AdminShiftRulesPage />
                                </PrivateRoute>
                            }
                        />

                        <Route path="/admin/import-times" element={<PrivateRoute requiredRole="ROLE_ADMIN"><TimeTrackingImport /></PrivateRoute>} />
                        <Route path="/whats-new" element={<PrivateRoute><WhatsNewPage /></PrivateRoute>} />
                        <Route path="/print-report" element={<PrintReport />} />
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>

                    {/* NEU: Fügt den Chatbot (via ActionButtons) auf allen Seiten für eingeloggte User hinzu */}
                    {authToken && <ActionButtons />}
                </div>
            </ErrorBoundary>
        </QueryClientProvider>
    );
}

export default App;
