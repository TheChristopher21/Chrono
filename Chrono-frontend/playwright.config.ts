import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: 'e2e',
    use: {
        headless: true,
        baseURL: 'http://localhost:5173', // wenn dein Vite-Frontend läuft
    },
});
