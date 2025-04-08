// src/App.jsx
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import './styles/global.css';
import './styles/Login.css';
import './styles/Navbar.css';
import Login from './pages/Login.jsx';
import Registration from './pages/Registration.jsx';
import UserDashboard from './pages/UserDashboard/UserDashboard.jsx';
import AdminDashboard from './pages/AdminDashboard/AdminDashboard.jsx';
import AdminUserManagementPage from './pages/AdminUserManagement/AdminUserManagementPage.jsx';
import PersonalDataPage from './pages/PersonalDataPage.jsx';
import PrivateRoute from './components/PrivateRoute.jsx';
import PrintReport from './components/PrintReport.jsx';
import AdminChangePassword from './pages/AdminChangePassword.jsx';

function App() {
    return (
        <div>
            <Routes>
                <Route path="/" element={<Navigate to="/login" replace />} />
                <Route path="*" element={<Navigate to="/login" replace />} />

                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Registration />} />

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
                <Route
                    path="/admin/change-password"
                    element={
                        <PrivateRoute requiredRole="ROLE_ADMIN">
                            <AdminChangePassword />
                        </PrivateRoute>
                    }
                />
            </Routes>
        </div>
    );
}

export default App;
