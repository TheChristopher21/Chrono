// src/main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { NotificationProvider } from "./context/NotificationContext";
import { LanguageProvider } from "./context/LanguageContext";
import "./styles/global.css";
import "./styles/chrono-theme.css";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    <HashRouter>
        {/* LanguageProvider muss die Provider umschließen, die useTranslation() verwenden */}
        <LanguageProvider>
            <AuthProvider>
                <NotificationProvider>
                    <React.StrictMode>
                        <App />
                    </React.StrictMode>
                </NotificationProvider>
            </AuthProvider>
        </LanguageProvider>
    </HashRouter>);