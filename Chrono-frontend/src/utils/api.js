
import axios from 'axios';
import { API_BASE_URL } from './constants';

// Wir legen eine Axios-Instanz an
const api = axios.create({
    baseURL: API_BASE_URL,
});

// Interceptor zum Hinzufügen des JWT Tokens (falls vorhanden)
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default api;
