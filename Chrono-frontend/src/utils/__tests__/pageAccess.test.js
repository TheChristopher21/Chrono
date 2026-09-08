import { describe, expect, it } from 'vitest';
import {
    ACCESS_MANAGE,
    ACCESS_VIEW,
    getDefaultLandingPage,
    getRouteForPage,
    hasFeatureAccess,
    hasProjectsFeature,
} from '../pageAccess.js';

describe('hasProjectsFeature', () => {
    it('accepts the legacy customer-tracking flag during a rolling deployment', () => {
        expect(hasProjectsFeature({
            companyId: 4,
            customerTrackingEnabled: true,
            companyFeatureKeys: [],
        })).toBe(true);
        expect(hasFeatureAccess({
            companyId: 4,
            customerTrackingEnabled: true,
            companyFeatureKeys: [],
        }, 'projects')).toBe(true);
    });

    it('accepts the canonical projects feature when the legacy flag is stale', () => {
        expect(hasProjectsFeature({
            companyId: 4,
            customerTrackingEnabled: false,
            companyFeatureKeys: ['projects'],
        })).toBe(true);
        expect(hasFeatureAccess({
            companyId: 4,
            customerTrackingEnabled: false,
            companyFeatureKeys: ['projects'],
        }, 'projects')).toBe(true);
    });

    it('never enables tenant data without a company context', () => {
        expect(hasProjectsFeature({
            roles: ['ROLE_SUPERADMIN'],
            customerTrackingEnabled: true,
            companyFeatureKeys: ['projects'],
        })).toBe(false);
    });
});

describe('getDefaultLandingPage', () => {
    it('sends admins to the admin dashboard before the personal dashboard', () => {
        const admin = {
            roles: ['ROLE_ADMIN'],
            pagePermissions: {
                dashboard: ACCESS_VIEW,
                adminDashboard: ACCESS_MANAGE,
            },
        };

        expect(getDefaultLandingPage(admin)).toBe(getRouteForPage('adminDashboard'));
    });

    it('keeps regular users on their personal dashboard', () => {
        const user = {
            roles: ['ROLE_USER'],
            pagePermissions: {
                dashboard: ACCESS_VIEW,
                adminDashboard: ACCESS_MANAGE,
            },
        };

        expect(getDefaultLandingPage(user)).toBe(getRouteForPage('dashboard'));
    });

    it('keeps superadmins on company management instead of the admin dashboard', () => {
        const superadmin = {
            roles: ['ROLE_SUPERADMIN'],
            pagePermissions: {
                dashboard: ACCESS_MANAGE,
                adminDashboard: ACCESS_MANAGE,
                companyManagement: ACCESS_MANAGE,
            },
        };

        expect(getDefaultLandingPage(superadmin)).toBe(getRouteForPage('companyManagement'));
    });
});
