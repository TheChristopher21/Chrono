import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import UserDashboard from './pages/UserDashboard.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import AdminUserManagementPage from './pages/AdminUserManagementPage.jsx';
import PersonalDataPage from './pages/PersonalDataPage.jsx';
import PrivateRoute from './components/PrivateRoute.jsx';
import PrintReport from "./components/PrintReport.jsx";

function App() {
    return (
        <Routes>
            <Route path="*" element={<Navigate to="/login" replace />} />

            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />

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
