import axios from "axios";

/**
 * Basis‐URL aus den Vite-Env-Variablen.
 * – Im Prod-Build:  .env.production  → https://api.chrono-logisch.ch
 * – Im Dev-Server: .env.local        → http://localhost:8080
 */
let baseURL = import.meta.env.VITE_API_BASE_URL || "/api";

// # FIX FÜR CAPACITOR/EMULATOR-TESTING #
const isCapacitor = window.Capacitor !== undefined;

if (isCapacitor) {
    // Erzwinge die lokale IP-Adresse des Host-PCs (10.0.2.2) für den Emulator,
    // um das lokal laufende Spring Boot Backend zu erreichen.
    // Wichtig: Nutzen Sie den gleichen Port wie Ihr Spring Boot Backend (vermutlich 8080).
    const EMULATOR_DEV_URL = 'http://10.0.2.2:8080';

    // Überschreibe die aus dem Build stammende URL (z.B. https://api.chrono-logisch.ch)
    // Nur der Teil vor dem /api muss überschrieben werden, wenn der Endpunkt /api enthält.

    // In Ihrem Fall beginnt VITE_API_BASE_URL bereits mit der Basis-URL (z.B. http://localhost:8080),
    // daher sollte dies der korrekte Override sein:
    baseURL = EMULATOR_DEV_URL;

    console.log(`[Capacitor-Override] API URL set to: ${baseURL}`);
}
// # ENDE FIX #


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