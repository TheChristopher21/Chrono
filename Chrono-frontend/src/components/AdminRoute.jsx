import 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const AdminRoute = ({ children }) => {
    const { authToken, currentUser } = useAuth();

    if (!authToken || !currentUser) {
        return <Navigate to="/login" replace />;
    }
    // Angenommen, du hast die Admin-Rolle in currentUser oder im decoded Token
    if (!currentUser.roles?.includes('ADMIN')) {
        return <Navigate to="/dashboard" replace />;
    }
    return children;
};

export default AdminRoute;
