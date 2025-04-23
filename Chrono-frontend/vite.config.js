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
    define: {
        'process.env': {
            APIURL: JSON.stringify(process.env.VITE_API_URL)
        }
    }
})
