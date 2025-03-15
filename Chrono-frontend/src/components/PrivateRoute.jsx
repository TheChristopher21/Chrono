import 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const PrivateRoute = ({ children, requiredRole }) => {
    const { authToken, currentUser } = useAuth();

    if (!authToken) {
        return <Navigate to="/login" replace />;
    }
    if (requiredRole && !currentUser?.roles?.includes(requiredRole)) {
        return <Navigate to="/" replace />;
    }
    return children;
};

export default PrivateRoute;
