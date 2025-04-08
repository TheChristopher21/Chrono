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
            '/api': 'https://api.chrono-logisch.ch'
        }
    },
    test: {
        reporter: 'dot',
        globals: true,
        environment: 'jsdom',
        setupFiles: './src/setupTests.js'
    },
    define: {
        "process.env": {
            "APIURL": "https://api.chrono-logisch.ch"
        },
    }
})
