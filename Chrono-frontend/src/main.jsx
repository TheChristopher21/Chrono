// src/main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { NotificationProvider } from "./context/NotificationContext";
import { LanguageProvider } from "./context/LanguageContext";
import { CustomerProvider } from "./context/CustomerContext";
import { ProjectProvider } from "./context/ProjectContext";
import { TaskProvider } from "./context/TaskContext";
import "./styles/global.css";

const structuredData = document.createElement("script");
structuredData.type = "application/ld+json";
structuredData.textContent = JSON.stringify({
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "Chrono-Logisch",
    alternateName: ["Chrono", "Chrono logisch", "Chrono-logisch"],
    url: "https://chrono-logisch.ch/",
    applicationCategory: "BusinessApplication",
    operatingSystem: "Web, Windows",
    description: "Schweizer Unternehmenssoftware für Zeiterfassung, Urlaubsfreigaben und Lohnvorbereitung.",
    areaServed: ["CH", "DE"],
    creator: {
        "@type": "Organization",
        name: "Chrono-Logisch",
        url: "https://chrono-logisch.ch/",
        address: {
            "@type": "PostalAddress",
            addressLocality: "Mogelsberg",
            addressRegion: "St. Gallen",
            addressCountry: "CH",
        },
    },
});
document.head.appendChild(structuredData);

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    <BrowserRouter>
        {/* LanguageProvider muss die Provider umschließen, die useTranslation() verwenden */}
        <LanguageProvider>
            <AuthProvider>
                <CustomerProvider>
                    <ProjectProvider>
                        <TaskProvider>
                            <NotificationProvider>
                                <React.StrictMode>
                                    <App />
                                </React.StrictMode>
                            </NotificationProvider>
                        </TaskProvider>
                    </ProjectProvider>
                </CustomerProvider>
            </AuthProvider>
        </LanguageProvider>
    </BrowserRouter>
);
