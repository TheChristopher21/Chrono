import React from 'react';
import useAuth from '../hooks/useAuth';

const Dashboard = () => {
    const { logout } = useAuth();

    return (
        <div className="dashboard-container">
            <h1>Welcome to the Dashboard</h1>
            <p>This is a protected page. Only authenticated users can see this.</p>
            <button onClick={logout}>Logout</button>
        </div>
    );
};

export default Dashboard;
