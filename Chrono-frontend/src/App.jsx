// src/App.jsx
import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";

import "./styles/global.css";
import "./styles/Login.css";
import "./styles/Navbar.css";
import "./styles/PercentageDashboardScoped.css";

import Login                         from "./pages/Login.jsx";
import Registration                  from "./pages/Registration.jsx";
import UserDashboard                 from "./pages/UserDashboard/UserDashboard.jsx";
import AdminDashboard                from "./pages/AdminDashboard/AdminDashboard.jsx";
import AdminUserManagementPage       from "./pages/AdminUserManagement/AdminUserManagementPage.jsx";
import PersonalDataPage              from "./pages/PersonalDataPage.jsx";
import AdminChangePassword           from "./pages/AdminChangePassword.jsx";
import PercentagePunch               from "./pages/PercentageDashboard/PercentageDashboard.jsx";
import PrintReport                   from "./pages/PrintReport.jsx";

import PrivateRoute                  from "./components/PrivateRoute.jsx";

function App() {
    return (
        <div>
            <Routes>
                {/* Root-Weiterleitung */}
                <Route path="/" element={<Navigate to="/login" replace />} />

                {/* Öffentliche Seiten */}
                <Route path="/login"    element={<Login />} />
                <Route path="/register" element={<Registration />} />

                {/* Geschützte User-Bereiche */}
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

                {/* Geschützte Admin-Bereiche */}
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

                {/* PDF-/Druck-Seite – MUSS vor dem Catch-All stehen */}
                <Route path="/print-report" element={<PrintReport />} />

                {/* Catch-All (Not-Found / Redirect) **ganz zum Schluss** */}
                <Route path="*" element={<Navigate to="/login" replace />} />
            </Routes>
        </div>
    );
}

export default App;
