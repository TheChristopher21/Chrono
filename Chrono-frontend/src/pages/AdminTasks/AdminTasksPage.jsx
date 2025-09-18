import React from 'react';
import { Navigate } from 'react-router-dom';

const AdminTasksPage = () => (
    <Navigate to="/admin/projects?tab=tasks" replace />
);

export default AdminTasksPage;
