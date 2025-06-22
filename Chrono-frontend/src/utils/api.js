import axios from "axios";

/**
 * Basis‐URL aus den Vite-Env-Variablen.
 *  – Im Prod-Build:  .env.production.development  → https://api.chrono-logisch.ch
 *  – Im Dev-Server: .env.production.development        → http://localhost:8080
 *  – Fallback      :                 → /api  (falls du doch Reverse-Proxy „/api → backend“ nutzt)
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || "/api";

const api = axios.create({
    baseURL,
    headers: { "Content-Type": "application/json" },
});

/* JWT automatisch anhängen */
api.interceptors.request.use((cfg) => {
    const t = localStorage.getItem("token");
    if (t) cfg.headers.Authorization = `Bearer ${t}`;
    return cfg;
});

export default api;
