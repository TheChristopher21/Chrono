// vite.config.js
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    base: "./", // relative Pfade, damit die Assets in dist korrekt gefunden werden
    server: {
        proxy: {
            "/api": "http://localhost:8080"
        }
    }
});
