/* ------------------------------------------------------------------------
    App.jsx · zentrale Routen‐Definition (Korrigiert)
    ------------------------------------------------------------------------ */
import "react";
import { Routes, Route, Navigate } from "react-router-dom";

/* ---------- globale Styles -------------------------------------------- */
import "./styles/global.css";
import "./styles/Button.css";
import "./styles/Login.css";
import "./styles/Navbar.css";
import "./styles/PercentageDashboardScoped.css";

/* ---------- Seiten‐Komponenten ---------------------------------------- */
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
import CompanyManagementPage from "./pages/CompanyManagementPage.jsx";
import PrintReport from "./pages/PrintReport.jsx";
import Impressum from "./pages/Impressum.jsx";
import AGB from "./pages/AGB.jsx";
import PayslipsPage from "./pages/PayslipsPage.jsx";
import AdminPayslipsPage from "./pages/AdminPayslipsPage.jsx";
import PrivateRoute from "./components/PrivateRoute";
import { useAuth } from "./context/AuthContext";

// NEU: Importieren Sie die WhatsNewPage
import WhatsNewPage from "./pages/WhatsNewPage.jsx";
import TimeTrackingImport from "./TimeTrackingImport.jsx"; // HINZUGEFÜGT


function App() {
    const { currentUser } = useAuth();

    return (
        <div className="App">
            <Routes>
                {/* Public routes */}
                <Route path="/" element={<LandingPage />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Registration />} />
                <Route path="/impressum" element={<Impressum />} />
                <Route path="/agb" element={<AGB />} />

                {/* Private routes */}
                <Route
                    path="/user"
                    element={
                        <PrivateRoute>
                            <UserDashboard />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/percentage-punch"
                    element={
                        <PrivateRoute>
                            <PercentagePunch />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/profile"
                    element={
                        <PrivateRoute>
                            <PersonalDataPage />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/payslips"
                    element={
                        <PrivateRoute>
                            <PayslipsPage />
                        </PrivateRoute>
                    }
                />

                {/* Admin routes */}
                <Route
                    path="/superadmin/companies"
                    element={
                        <PrivateRoute requiredRole="ROLE_SUPERADMIN">
                            <CompanyManagementPage />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <AdminDashboard />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin/users"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <AdminUserManagementPage />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin/customers"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            {currentUser?.customerTrackingEnabled ? ( // Dies ist die korrekte Version
                                <AdminCustomersPage />
                            ) : (
                                <Navigate to="/admin" replace />
                            )}
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin/projects"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            {currentUser?.customerTrackingEnabled ? (
                                <AdminProjectsPage />
                            ) : (
                                <Navigate to="/admin" replace />
                            )}
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin/change-password"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <AdminChangePassword />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/admin/payslips"
                    element={
                        <PrivateRoute requiredRole={["ROLE_ADMIN","ROLE_PAYROLL_ADMIN"]}>
                            <AdminPayslipsPage />
                        </PrivateRoute>
                    }
                />
                {/* NEU: Route für den Zeitimport (als Admin-Route) */}
                <Route
                    path="/admin/import-times" // Du kannst diesen Pfad bei Bedarf ändern
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <TimeTrackingImport />
                        </PrivateRoute>
                    }
                />

                {/* NEU: Fügen Sie die Route für den Update-Verlauf hinzu */}
                <Route
                    path="/whats-new"
                    element={
                        <PrivateRoute>
                            <WhatsNewPage />
                        </PrivateRoute>
                    }
                />

                <Route path="/print-report" element={<PrintReport />} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </div>
    );
}

export default App;