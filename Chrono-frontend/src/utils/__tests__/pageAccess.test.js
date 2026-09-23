import { describe, expect, it } from 'vitest';
import {
    ACCESS_MANAGE,
    ACCESS_NONE,
    ACCESS_VIEW,
    buildDefaultPagePermissions,
    getDashboardPagesForContext,
    getDefaultLandingPage,
    getPermissionSectionsForRole,
    getRouteForPage,
    hasFeatureAccess,
    hasPageAccess,
    hasProjectsFeature,
    normalizePagePermissionsForRole,
} from '../pageAccess.js';

describe('superadmin dashboard access', () => {
    it.each(['ROLE_ADMIN', 'ROLE_USER', 'ROLE_PAYROLL_ADMIN'])('does not allow %s page grants to bypass the superadmin role', (role) => {
        const user = { roles: [role], pagePermissions: { adminDashboardWorkspace: ACCESS_MANAGE } };
        expect(hasPageAccess(user, 'adminDashboardWorkspace', ACCESS_VIEW)).toBe(false);
        expect(hasPageAccess(user, 'adminDashboardWorkspace', ACCESS_MANAGE)).toBe(false);
        expect(getDashboardPagesForContext(user, 'admin').some(page => page.key === 'adminDashboardWorkspace')).toBe(false);
        expect(buildDefaultPagePermissions(role).adminDashboardWorkspace).toBe(ACCESS_NONE);
        expect(normalizePagePermissionsForRole(role, [], user.pagePermissions).adminDashboardWorkspace).toBe(ACCESS_NONE);
        expect(getPermissionSectionsForRole(role).flatMap(section => section.pages).some(page => page.key === 'adminDashboardWorkspace')).toBe(false);
    });

    it('grants a superadmin access without requiring a new server permission', () => {
        const user = { roles: ['ROLE_SUPERADMIN'], pagePermissions: { adminDashboardWorkspace: ACCESS_NONE } };
        expect(hasPageAccess(user, 'adminDashboardWorkspace', ACCESS_MANAGE)).toBe(true);
        expect(getDashboardPagesForContext(user, 'admin')).toEqual(expect.arrayContaining([
            expect.objectContaining({ key: 'adminDashboardWorkspace', path: '/admin/dashboard-neu', label: 'Neues Dashboard' }),
        ]));
        expect(getRouteForPage('adminDashboard')).toBe('/admin/dashboard');
    });
});

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
