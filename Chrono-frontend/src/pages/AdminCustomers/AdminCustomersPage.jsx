import React from 'react';
import { Navigate } from 'react-router-dom';

const AdminCustomersPage = () => (
    <Navigate to="/admin/projects?tab=customers" replace />
);

export default AdminCustomersPage;
