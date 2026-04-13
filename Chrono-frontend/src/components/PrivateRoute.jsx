import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * PrivateRoute protects routes.
 * - requiredRole can be a string or an array
 * - Missing auth redirects to /login?next=<uri>
 * - Missing permissions redirect to the configured fallback
 */
const PrivateRoute = ({ children, requiredRole, requiredFeature, redirectTo = '/' }) => {
    const { authToken, currentUser, isAuthLoading } = useAuth();
    const location = useLocation();

    if (authToken && isAuthLoading && !currentUser) {
        return (
            <div className="route-auth-loading" data-testid="route-auth-loading" aria-live="polite">
                <div className="route-auth-loading__card">Dashboard wird geladen...</div>
            </div>
        );
    }

    if (!authToken) {
        return (
            <Navigate
                to={`/login?next=${encodeURIComponent(location.pathname)}`}
                replace
            />
        );
    }

    if (!currentUser) {
        return (
            <Navigate
                to={`/login?next=${encodeURIComponent(location.pathname)}`}
                replace
            />
        );
    }

    if (requiredRole) {
        const roles = currentUser.roles || [];
        const allowed = Array.isArray(requiredRole)
            ? requiredRole.some((role) => roles.includes(role))
            : roles.includes(requiredRole);

        if (!allowed) {
            return <Navigate to="/" replace />;
        }
    }

    if (requiredFeature) {
        const isSuperAdmin = currentUser.roles?.includes('ROLE_SUPERADMIN');
        if (!isSuperAdmin) {
            const featureKeysRaw = currentUser.companyFeatureKeys;
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
