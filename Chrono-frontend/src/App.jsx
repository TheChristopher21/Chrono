import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

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
import AdminPayslipsPage from "./pages/AdminPayslipsPage.jsx";
import AdminSchedulePlannerPage from "./pages/AdminSchedulePlanner/AdminSchedulePlannerPage.jsx";
// NEU: Import der Seite für Schichtregeln
import AdminShiftRulesPage from "./pages/AdminSchedulePlanner/AdminScheduleRulesPage.jsx";
import CompanyManagementPage from "./pages/CompanyManagementPage.jsx";
import TimeTrackingImport from "./TimeTrackingImport.jsx";
import PrintReport from "./pages/PrintReport.jsx";
import WhatsNewPage from "./pages/WhatsNewPage.jsx";
import AGB from "./pages/AGB.jsx";
import Impressum from "./pages/Impressum.jsx";

// Hilfs-Komponenten
import PrivateRoute from "./components/PrivateRoute.jsx";

const queryClient = new QueryClient();

function App() {
    return (
        <QueryClientProvider client={queryClient}>
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

                    {/* Admin-Routen */}
                    <Route path="/admin/dashboard" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminDashboard /></PrivateRoute>} />
                    <Route path="/admin/users" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminUserManagementPage /></PrivateRoute>} />
                    <Route path="/admin/change-password" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminChangePassword /></PrivateRoute>} />
                    <Route path="/admin/customers" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminCustomersPage /></PrivateRoute>} />
                    <Route path="/admin/projects" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminProjectsPage /></PrivateRoute>} />
                    <Route path="/admin/company" element={<PrivateRoute requiredRole="ROLE_ADMIN"><CompanyManagementPage /></PrivateRoute>} />
                    <Route path="/admin/payslips" element={<PrivateRoute requiredRole={["ROLE_ADMIN","ROLE_PAYROLL_ADMIN"]}><AdminPayslipsPage /></PrivateRoute>} />
                    <Route path="/admin/schedule" element={<PrivateRoute requiredRole="ROLE_ADMIN"><AdminSchedulePlannerPage /></PrivateRoute>} />

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
            </div>
        </QueryClientProvider>
    );
}

export default App;