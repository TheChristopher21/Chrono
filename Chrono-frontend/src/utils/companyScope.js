export const COMPANY_SCOPE_KEY = 'chrono:companyScope';

export const clearCompanyScope = () => sessionStorage.removeItem(COMPANY_SCOPE_KEY);

export function getCompanyScopeId() {
    try {
        const selection = JSON.parse(sessionStorage.getItem(COMPANY_SCOPE_KEY));
        const token = localStorage.getItem('token');
        const payload = atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'));
        const claims = JSON.parse(new TextDecoder().decode(Uint8Array.from(payload, character => character.charCodeAt(0))));
        return selection?.username === claims.sub && Number.isSafeInteger(selection.companyId) && selection.companyId > 0
            ? selection.companyId : null;
    } catch { return null; }
}

export function openCompanyScope(company, user) {
    if (!user?.roles?.includes('ROLE_SUPERADMIN') || !Number.isSafeInteger(Number(company?.id)) || Number(company.id) <= 0) return;
    sessionStorage.setItem(COMPANY_SCOPE_KEY, JSON.stringify({ username: user.username, companyId: Number(company.id) }));
    // A fresh document also clears all module caches and in-flight requests from
    // the previous company. Each browser tab has its own selection.
    window.location.assign('/admin/dashboard-neu');
}

export function leaveCompanyScope() {
    clearCompanyScope();
    window.location.assign('/admin/company');
}

export function reloadStaleCompanyScope(user) {
    // Back/forward can restore an entire document from the browser cache after
    // a company switch. Its old data must not use the new request header.
    if (user && (user.selectedCompanyId ?? null) !== getCompanyScopeId()) {
        window.location.reload();
    }
}
