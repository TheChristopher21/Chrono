import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        "Content-Type": "application/json",
    },
});

// Token hinzufügen
apiClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem("token"); // Token aus dem lokalen Speicher
        if (token) {
            config.headers.Authorization = `Bearer ${token}`; // Token hinzufügen
        }
        return config;
    },
    (error) => {
        console.error("Request Error:", error);
        return Promise.reject(error);
    }
);

// Login-Methode
export const login = async (credentials) => {
    const response = await apiClient.post("/auth/login", credentials);
    // Speichere den Token nach erfolgreichem Login
    localStorage.setItem("token", response.data.token);
    return response.data; // Benutzerinformationen zurückgeben
};

// Abrufen der neuesten Zeiterfassungsdaten
export const getLatestTimeTracking = async (userId) => {
    const response = await apiClient.get(`/time-tracking/latest/${userId}`);
    return response.data;
};

// Einchecken
export const checkIn = async (userId) => {
    const response = await apiClient.post(`/time-tracking/check-in/${userId}`);
    return response.data;
};

// Auschecken
export const checkOut = async (userId) => {
    const response = await apiClient.post(`/time-tracking/check-out/${userId}`);
    return response.data;
};

// Beispiel für eine weitere API-Methode
export const register = async (userData) => {
    const response = await apiClient.post("/auth/register", userData);
    return response.data;
};
