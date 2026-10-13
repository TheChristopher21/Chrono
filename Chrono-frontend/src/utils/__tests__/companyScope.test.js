// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCompanyScope, getCompanyScopeId, openCompanyScope, leaveCompanyScope, reloadStaleCompanyScope, COMPANY_SCOPE_KEY } from '../companyScope.js';
import api from '../api.js';
import { hasFeatureAccess, hasPageAccess } from '../pageAccess.js';

const token = username => `header.${Buffer.from(JSON.stringify({ sub: username })).toString('base64url')}.signature`;
const user = { username: 'adminä', roles: ['ROLE_SUPERADMIN'] };
beforeEach(() => {
    localStorage.clear(); sessionStorage.clear(); localStorage.setItem('token', token(user.username));
    vi.stubGlobal('window', { location: { origin: 'http://localhost', assign: vi.fn(), reload: vi.fn() } });
});
afterEach(() => vi.unstubAllGlobals());

describe('company context', () => {
    it('keeps the selection in this browser session and starts the chosen dashboard fresh', () => {
        openCompanyScope({ id: 20 }, user);
        expect(getCompanyScopeId()).toBe(20);
        expect(localStorage.getItem(COMPANY_SCOPE_KEY)).toBeNull();
        expect(window.location.assign).toHaveBeenCalledWith('/admin/dashboard-neu');
        localStorage.setItem('token', token('other-account'));
        expect(getCompanyScopeId()).toBeNull();
    });
    it('does not allow ordinary users or malformed ids to start a company view', () => {
        openCompanyScope({ id: 20 }, { ...user, roles: ['ROLE_ADMIN'] });
        openCompanyScope({ id: 0 }, user);
        expect(getCompanyScopeId()).toBeNull();
        expect(window.location.assign).not.toHaveBeenCalled();
    });
    it('attaches the selected company to requests while keeping the company chooser independent', async () => {
        openCompanyScope({ id: 20 }, user);
        const requests = [];
        const adapter = async config => { requests.push(config); return { data: [], status: 200, statusText: 'OK', headers: {}, config }; };
        await api.get('/api/admin/users', { adapter });
        await api.get('/api/superadmin/companies', { adapter });
        expect(requests[0].headers['X-Chrono-Company']).toBe('20');
        expect(requests[1].headers['X-Chrono-Company']).toBeUndefined();
        openCompanyScope({ id: 30 }, user);
        await api.get('/api/correction/all', { adapter });
        expect(requests[2].headers['X-Chrono-Company']).toBe('30');
    });
    it('returns to platform management without carrying the company header', () => {
        openCompanyScope({ id: 20 }, user);
        leaveCompanyScope();
        expect(getCompanyScopeId()).toBeNull();
        expect(window.location.assign).toHaveBeenLastCalledWith('/admin/company');
        clearCompanyScope();
    });
    it('shows company modules and hides personal or global work areas in a company view', () => {
        const scoped = { ...user, selectedCompanyId: 20, companyId: 20, companyFeatureKeys: ['payroll'] };
        expect(hasFeatureAccess(scoped, 'payroll')).toBe(true);
        expect(hasFeatureAccess(scoped, 'pms')).toBe(false);
        expect(hasPageAccess(scoped, 'adminDashboardWorkspace')).toBe(true);
        expect(hasPageAccess(scoped, 'companyManagement')).toBe(false);
        expect(hasPageAccess(scoped, 'dashboard')).toBe(false);
        expect(hasPageAccess(user, 'companyManagement')).toBe(true);
    });
    it('reloads a restored document only when its company differs from the current selection', () => {
        openCompanyScope({ id: 30 }, user);
        reloadStaleCompanyScope(null);
        reloadStaleCompanyScope({ ...user, selectedCompanyId: 30 });
        expect(window.location.reload).not.toHaveBeenCalled();
        reloadStaleCompanyScope({ ...user, selectedCompanyId: 20 });
        expect(window.location.reload).toHaveBeenCalledTimes(1);
        clearCompanyScope();
        reloadStaleCompanyScope({ ...user, selectedCompanyId: 30 });
        expect(window.location.reload).toHaveBeenCalledTimes(2);
    });
});
