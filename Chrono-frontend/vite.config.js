// vite.config.js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    // Use an absolute base path so assets remain reachable when refreshing
    // deep links in the SPA. Relative paths caused the browser to request
    // route-prefixed asset URLs, which Nginx rewrote to index.html and broke
    // module loading after a reload.
    base: '/',
    build: {
        chunkSizeWarningLimit: 10000
    },
    // Ihr bestehender Proxy-Eintrag für die lokale Entwicklung bleibt erhalten
    server: {
        // NEU HINZUFÜGEN: Erzwingt das Hören auf allen IP-Adressen (0.0.0.0)
        host: true,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            }
        }
    },
    define: {
        'process.env': {
            APIURL: 'http://localhost:8080'
        }
    },

    // ====================================================================
    // HIER IST DER NEUE BLOCK FÜR DIE VITEST-KONFIGURATION
    // ====================================================================
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: './src/test/setup.js', // Pfad zu Ihrer Setup-Datei
    },
})
