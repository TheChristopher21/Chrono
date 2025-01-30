import 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const PrivateRoute = ({ children }) => {
    const { authToken } = useAuth();

    if (!authToken) {
        return <Navigate to="/login" replace />;
    }

    return children;
};

export default PrivateRoute;
