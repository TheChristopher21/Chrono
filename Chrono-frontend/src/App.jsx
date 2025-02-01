// src/App.jsx
import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Login from './pages/Login';
import UserDashboard from './pages/UserDashboard';
import AdminDashboard from './pages/AdminDashboard';
import PrivateRoute from './components/PrivateRoute';

function App() {
    return (
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route
                path="/user"
                element={
                    <PrivateRoute requiredRole="ROLE_USER">
                        <UserDashboard />
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
            {/* Fallback: Bei unbekannter Route wieder zum Login */}
            <Route path="*" element={<Login />} />
        </Routes>
    );
}

export default App;
