// src/main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { NotificationProvider } from "./context/NotificationContext";
import { LanguageProvider } from "./context/LanguageContext";
import { CustomerProvider } from "./context/CustomerContext";
import { ProjectProvider } from "./context/ProjectContext";
import { OnboardingProvider } from "./context/OnboardingContext";
import "./styles/global.css";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    <HashRouter>
        {/* LanguageProvider muss die Provider umschließen, die useTranslation() verwenden */}
        <LanguageProvider>
            <AuthProvider>
                <CustomerProvider>
                    <ProjectProvider>
                        <NotificationProvider>
                            <OnboardingProvider>
                                <React.StrictMode>
                                    <App />
                                </React.StrictMode>
                            </OnboardingProvider>
                        </NotificationProvider>
                    </ProjectProvider>
                </CustomerProvider>
            </AuthProvider>
        </LanguageProvider>
    </HashRouter>
);