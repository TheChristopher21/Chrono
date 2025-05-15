import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * PrivateRoute schützt Routen.
 * - requiredRole kann String **oder Array** sein.
 * - Bei fehlender Auth → Redirect zu /login?next=<uri>
 * - Bei fehlender Berechtigung → Redirect zur Landing-Page
 */
const PrivateRoute = ({ children, requiredRole }) => {
    const { authToken, currentUser } = useAuth();
    const location = useLocation();

    /* -------- nicht eingeloggt -------------------- */
    if (!authToken) {
        return (
            <Navigate
                to={`/login?next=${encodeURIComponent(location.pathname)}`}
                replace
            />
        );
    }

    /* -------- Rolle prüfen (optional) ------------- */
    if (requiredRole) {
        const roles = currentUser?.roles || [];
        const allowed = Array.isArray(requiredRole)
            ? requiredRole.some((r) => roles.includes(r))
            : roles.includes(requiredRole);

        if (!allowed) {
            return <Navigate to="/" replace />;
        }
    }

    return children;
};

export default PrivateRoute;
