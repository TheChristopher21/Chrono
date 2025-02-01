// src/components/PrivateRoute.jsx
import 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const PrivateRoute = ({ children, requiredRole }) => {
    const { authToken, currentUser } = useAuth();
    if (!authToken) {
        return <Navigate to="/login" />;
    }
    if (requiredRole && (!currentUser?.roles || !currentUser.roles.includes(requiredRole))) {
        return <Navigate to="/login" />;
    }
    return children;
};

export default PrivateRoute;
