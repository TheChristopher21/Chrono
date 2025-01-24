import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const apiClient = axios.create({
    baseURL: API_BASE_URL,
});

// Interceptor, um den Authorization-Header hinzuzufügen
apiClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem("token"); // Token aus dem lokalen Speicher
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Beispiel für deine API-Aufrufe
export const login = async (credentials) => {
    const response = await apiClient.post("/auth/login", credentials);
    // Speichere den Token nach dem Login
    localStorage.setItem("token", response.data.token);
    return response.data;
};

export const getLatestTimeTracking = async (userId) => {
    const response = await apiClient.get(`/time-tracking/latest/${userId}`);
    return response.data;
};

export const checkIn = async (userId) => {
    const response = await apiClient.post(`/time-tracking/check-in/${userId}`);
    return response.data;
};

export const checkOut = async (userId) => {
    const response = await apiClient.post(`/time-tracking/check-out/${userId}`);
    return response.data;
};
