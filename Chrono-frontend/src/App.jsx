// App.jsx
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';

import Login from './pages/Login.jsx';                // oder './pages/Login/Login.jsx' falls du es in einen Ordner verschoben hast
import Register from './pages/Register.jsx';          // dito
import UserDashboard from './pages/UserDashboard/UserDashboard.jsx'; // oder './pages/UserDashboard/UserDashboard.jsx'
import AdminDashboard from './pages/AdminDashboard/AdminDashboard.jsx';
import AdminUserManagementPage from './pages/AdminUserManagement/AdminUserManagementPage.jsx';
import PersonalDataPage from './pages/PersonalDataPage.jsx';
import PrivateRoute from './components/PrivateRoute.jsx';
import PrintReport from './components/PrintReport.jsx';

function App() {
    return (
        <Routes>
            {/* Standard-Redirect auf /login, falls Nutzer unbekannte Route eingibt */}
            <Route path="*" element={<Navigate to="/login" replace />} />

            {/* Öffentliche Routen */}
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />

            {/* Private Routen: nur eingeloggte Nutzer mit Rolle "ROLE_USER" */}
            <Route
                path="/user"
                element={
                    <PrivateRoute requiredRole="ROLE_USER">
                        <UserDashboard />
                    </PrivateRoute>
                }
            />
            <Route path="/print-report" element={<PrintReport />} />
            <Route
                path="/profile"
                element={
                    <PrivateRoute requiredRole="ROLE_USER">
                        <PersonalDataPage />
                    </PrivateRoute>
                }
            />

            {/* Private Route: nur Admins (ROLE_ADMIN) */}
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
        </Routes>
    );
}

export default App;
