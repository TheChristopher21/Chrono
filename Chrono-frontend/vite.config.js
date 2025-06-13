// vite.config.js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        chunkSizeWarningLimit: 10000
    },
    // Ihr bestehender Proxy-Eintrag für die lokale Entwicklung bleibt erhalten
    server: {
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