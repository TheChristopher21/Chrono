// vite.config.js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        chunkSizeWarningLimit: 10000
    },
    // Hier der Proxy-Eintrag für lokale Entwicklung
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                // optional, je nach Bedarf:
                // rewrite: (path) => path.replace(/^\/api/, '')
            }
        }
    },
    define: {
        // Option A: Du kannst in dev-Mode einfach die process.env.APIURL überschreiben
        'process.env': {
            APIURL: 'http://localhost:8080'
        }
    }
})
