/* ------------------------------------------------------------------------
   App.jsx · zentrale Routen‐Definition
   ------------------------------------------------------------------------ */
import React            from "react";
import { Routes, Route, Navigate } from "react-router-dom";

/* ---------- globale Styles -------------------------------------------- */
import "./styles/global.css";
import "./styles/Login.css";
import "./styles/Navbar.css";
import "./styles/PercentageDashboardScoped.css";

/* ---------- Seiten‐Komponenten ---------------------------------------- */
import LandingPage             from "./pages/LandingPage.jsx";
import Login                   from "./pages/Login.jsx";
import Registration            from "./pages/Registration.jsx";
import UserDashboard           from "./pages/UserDashboard/UserDashboard.jsx";
import PercentagePunch         from "./pages/PercentageDashboard/PercentageDashboard.jsx";
import PersonalDataPage        from "./pages/PersonalDataPage.jsx";
import AdminDashboard          from "./pages/AdminDashboard/AdminDashboard.jsx";
import AdminUserManagementPage from "./pages/AdminUserManagement/AdminUserManagementPage.jsx";
import AdminChangePassword     from "./pages/AdminChangePassword.jsx";
import CompanyManagementPage   from "./pages/CompanyManagementPage.jsx";
import PrintReport             from "./pages/PrintReport.jsx";
import Impressum               from "./pages/Impressum.jsx";
import AGB                     from "./pages/AGB.jsx";

import PrivateRoute from "./components/PrivateRoute.jsx";

function App() {
    return (
        <div>
            <Routes>
                <Route path="/"          element={<LandingPage />} />
                <Route path="/login"     element={<Login />} />
                <Route path="/register"  element={<Registration />} />
                <Route path="/impressum" element={<Impressum />} />
                <Route path="/agb"       element={<AGB />} />

                <Route
                    path="/user"
                    element={
                        <PrivateRoute requiredRole="ROLE_USER">
                            <UserDashboard />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/percentage-punch"
                    element={
                        <PrivateRoute requiredRole="ROLE_USER">
                            <PercentagePunch />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/profile"
                    element={
                        <PrivateRoute requiredRole="ROLE_USER">
                            <PersonalDataPage />
                        </PrivateRoute>
                    }
                />

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
                    path="/admin/change-password"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <AdminChangePassword />
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
