import { describe, expect, it } from 'vitest';
import { buildDefaultPagePermissions, canManagePmsSettings, normalizePagePermissionsForRole } from '../../utils/pageAccess.js';

describe('PMS master permission', () => {
    it('requires an individual admin account with PMS and settings permission', () => {
        const pagePermissions = { pms: 'MANAGE', pmsSettings: 'MANAGE' };
        expect(canManagePmsSettings({ roles: ['ROLE_USER'], pagePermissions })).toBe(false);
        expect(canManagePmsSettings({ roles: ['ROLE_ADMIN'], pagePermissions })).toBe(true);
        expect(canManagePmsSettings({ roles: ['ROLE_ADMIN'], pagePermissions: { ...pagePermissions, pmsSettings: 'VIEW' } })).toBe(false);
    });
    it('does not allow assigning master permission to reception', () => {
        expect(buildDefaultPagePermissions('ROLE_ADMIN', ['pms']).pmsSettings).toBe('MANAGE');
        expect(normalizePagePermissionsForRole('ROLE_USER', ['pms'], { pmsSettings: 'MANAGE' }).pmsSettings).toBe('NONE');
    });
});
