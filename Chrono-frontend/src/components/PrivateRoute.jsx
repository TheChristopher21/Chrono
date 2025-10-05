import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * PrivateRoute schützt Routen.
 * - requiredRole kann String **oder Array** sein.
 * - Bei fehlender Auth → Redirect zu /login?next=<uri>
 * - Bei fehlender Berechtigung → Redirect zur Landing-Page
 */
const PrivateRoute = ({ children, requiredRole, requiredFeature, redirectTo = '/' }) => {
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

    if (requiredFeature) {
        const isSuperAdmin = currentUser?.roles?.includes('ROLE_SUPERADMIN');
        if (!isSuperAdmin) {
            const featureKeysRaw = currentUser?.companyFeatureKeys;
            const featureList = Array.isArray(featureKeysRaw)
                ? featureKeysRaw
                : featureKeysRaw
                    ? Object.values(featureKeysRaw)
                    : [];

            const requiredFeatures = Array.isArray(requiredFeature)
                ? requiredFeature
                : [requiredFeature];

            const hasFeature = requiredFeatures.some((featureKey) => featureList.includes(featureKey));

            if (!hasFeature) {
                return <Navigate to={redirectTo} replace />;
            }
        }
    }

    return children;
};

export default PrivateRoute;
