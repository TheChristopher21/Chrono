// src/utils/api.js
import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080', // Backend-URL
    headers: { 'Content-Type': 'application/json' }
});

// Automatisches Anhängen des JWT, falls vorhanden
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default api;
