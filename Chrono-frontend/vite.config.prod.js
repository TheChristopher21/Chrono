import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// ➊  Lese alle `.env.production.*`-Variablen ein
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), "");

    return {
        plugins: [react()],

        /*
         * Statische Assets müssen auch nach einem direkten Seiten-Reload auf
         * einer Deep-Link-Route (z. B. /adminDashboard) korrekt geladen
         * werden. Mit einem relativen Pfad ("./") versucht der Browser die
         * Module unter /adminDashboard/assets/... zu laden, was von Nginx auf
         * die index.html umgeleitet wird und den "Failed to load module
         * script"-Fehler auslöst. Durch einen absoluten Basis-Pfad bleiben die
         * Asset-URLs immer bei /assets/… verankert.
         */
        base: "/",

        build: { chunkSizeWarningLimit: 10_000 },

        /**
         * Nur für den Dev-Server relevant.
         * Sobald du `vite preview` oder den Nginx-Host nutzt,
         * werden absolute URLs (https://api.chrono-logisch.ch/…) verwendet.
         */
        server: {
            proxy: {
                "/api": {
                    target: env.VITE_API_BASE_URL || "https://api.chrono-logisch.ch",
                    changeOrigin: true
                }
            }
        },

        /**
         * Diese Zeile brennt die Variable in den finalen Bundle:
         *  • Prod-Build → Wert aus .env.production.development
         *  • Dev-Server → Wert aus .env.production.development
         *  • Fallback   → https://api.chrono-logisch.ch
         */
        define: {
            "import.meta.env.VITE_API_BASE_URL": JSON.stringify(
                env.VITE_API_BASE_URL || "https://api.chrono-logisch.ch"
            )
        },

        /* Vitest-Konfiguration (unverändert) */
        test: {
            reporter: "dot",
            globals: true,
            environment: "jsdom",
            setupFiles: "./src/setupTests.js"
        }
    };
});
