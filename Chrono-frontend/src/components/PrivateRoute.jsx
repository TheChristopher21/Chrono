import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { hasFeatureAccess, hasPageAccess } from '../utils/pageAccess.js';

/**
 * PrivateRoute protects routes.
 * - requiredRole can be a string or an array
 * - Missing auth redirects to /login?next=<uri>
 * - Missing permissions redirect to the configured fallback
 */
const PrivateRoute = ({
    children,
    requiredRole,
    requiredFeature,
    requiredPagePermission,
    requiredAccess = 'VIEW',
    redirectTo = '/',
}) => {
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
        const requiredFeatures = Array.isArray(requiredFeature)
            ? requiredFeature
            : [requiredFeature];

        const featureAllowed = requiredFeatures.some((featureKey) => hasFeatureAccess(currentUser, featureKey));

        if (!featureAllowed) {
            return <Navigate to={redirectTo} replace />;
        }
    }

    if (requiredPagePermission) {
        const requiredPermissions = Array.isArray(requiredPagePermission)
            ? requiredPagePermission
            : [requiredPagePermission];
        const permissionAllowed = requiredPermissions.some((pageKey) =>
            hasPageAccess(currentUser, pageKey, requiredAccess)
        );

        if (!permissionAllowed) {
            return <Navigate to={redirectTo} replace />;
        }
    }

    return children;
};

export default PrivateRoute;
