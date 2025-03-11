// vite.config.js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    base: './', // relative Pfade, damit die Assets in dist korrekt gefunden werden
    build: {
        chunkSizeWarningLimit: 5000 // Erhöht das Limit auf 5000 kB
    },
    server: {
        proxy: {
            '/api': 'http://localhost:8080'
        }
    },
    test: {
        reporter: 'dot',
        globals: true,
        environment: 'jsdom',
        setupFiles: './src/setupTests.js'
    }
})
