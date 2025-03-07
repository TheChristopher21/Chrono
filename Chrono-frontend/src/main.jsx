// src/main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { NotificationProvider } from "./context/NotificationContext";
import { LanguageProvider } from "./context/LanguageContext"; // <-- NEU
import "./styles/global.css";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    <HashRouter>
        <AuthProvider>
            <NotificationProvider>
                {/* NEU: LanguageProvider umschließt alles */}
                <LanguageProvider>
                    <React.StrictMode>
                        <App />
                    </React.StrictMode>
                </LanguageProvider>
            </NotificationProvider>
        </AuthProvider>
    </HashRouter>
);
