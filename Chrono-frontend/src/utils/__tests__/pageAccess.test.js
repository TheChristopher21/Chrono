import { describe, expect, it } from 'vitest';
import {
    ACCESS_MANAGE,
    ACCESS_VIEW,
    getDefaultLandingPage,
    getRouteForPage,
} from '../pageAccess.js';

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
