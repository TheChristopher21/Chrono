// src/App.jsx
import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import UserDashboard from './pages/UserDashboard';
import AdminDashboard from './pages/AdminDashboard';
import AdminUserManagementPage from './pages/AdminUserManagementPage';
import PersonalDataPage from './pages/PersonalDataPage';
import PrivateRoute from './components/PrivateRoute';
import './styles/global.css';

function App() {
    return (
        <Routes>
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
            <Route path="*" element={<Login />} />
        </Routes>
    );
}

export default App;
