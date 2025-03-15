// vite.config.js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        chunkSizeWarningLimit: 10000
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
