import axios from "axios";
import { publishMutationRefresh } from "./dataRefresh.js";
import { getCompanyScopeId } from './companyScope.js';

/**
 * Basis‐URL aus den Vite-Env-Variablen.
 * – Im Prod-Build:  .env.production  → https://api.chrono-logisch.ch
 * – Im Dev-Server: .env.local        → http://localhost:8080
 */
let baseURL = import.meta.env.VITE_API_BASE_URL || "/api";

const api = axios.create({
    baseURL,
    headers: { "Content-Type": "application/json" },
});

/* JWT automatisch anhängen */
api.interceptors.request.use((cfg) => {
    const companyId = getCompanyScopeId();
    const path = new URL(cfg.url, window.location.origin).pathname;
    if (companyId && !path.startsWith('/api/superadmin/companies') && !['/api/auth/login', '/api/auth/demo', '/api/auth/refresh'].includes(path)) {
        cfg.headers['X-Chrono-Company'] = String(companyId);
    }
    // Chrono inactivity ends only this browser tab; another tab may still run
    // an unattended PMS using the shared finite token.
    if (sessionStorage.getItem('chrono:tabIdleSignOut')) {
        delete cfg.headers.Authorization;
        return cfg;
    }
    const t = localStorage.getItem("token");
    if (t) cfg.headers.Authorization = `Bearer ${t}`;
    return cfg;
});

/* Erfolgreiche fachliche Änderungen zentral an aktive Datenansichten melden. */
api.interceptors.response.use((response) => {
    publishMutationRefresh(response);
    return response;
});

export default api;
