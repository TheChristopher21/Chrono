// C:\Users\siefe\IdeaProjects\Chrono\Chrono-frontend\vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],                 // <- sorgt für korrekten JSX-Transform im Test
    test: {
        environment: 'jsdom',
        setupFiles: ['src/test/setup.js'], // <- jest-dom & Mocks laden
        exclude: [
            '**/node_modules/**',
            '**/dist/**',
            '**/e2e/**'                      // <- Playwright-Ordner ausschließen
        ],
        css: true,                         // CSS-Imports in Komponenten erlauben
        globals: true
    }
});
