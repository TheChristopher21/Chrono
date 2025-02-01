import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom"; // Importiere BrowserRouter
import App from "./App";
import { AuthProvider } from "./context/AuthContext"; // AuthProvider

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    <BrowserRouter>
        <AuthProvider> {/* 🔥 Stelle sicher, dass dies korrekt um die gesamte App gewrappt ist */}
            <React.StrictMode>
                <App />
            </React.StrictMode>
        </AuthProvider>
    </BrowserRouter>
);
