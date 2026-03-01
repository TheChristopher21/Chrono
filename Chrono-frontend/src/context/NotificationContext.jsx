// src/context/NotificationContext.jsx
import React, { createContext, useState, useContext, useRef, useEffect } from 'react';
import { useTranslation } from './LanguageContext';
import "../styles/Notification.css";

export const NotificationContext = createContext({
    notify: () => {},
});

export function NotificationProvider({ children }) {
    const { t } = useTranslation();
    const [toast, setToast] = useState({ message: "", type: "info" });
    const [visible, setVisible] = useState(false);
    const timerRef = useRef(null);

    function close() {
        if (timerRef.current) clearTimeout(timerRef.current);
        timerRef.current = null;
        setVisible(false);
    }

    /**
     * notify("Text")
     * notify({ message: "Text", type: "success" | "info" | "warn" | "error", duration: 3500 })
     */
    function notify(input) {
        let message = "";
        let type = "info";
        let duration = 3500;

        if (typeof input === "string") {
            message = input;
        } else if (input && typeof input === "object") {
            message = input.message ?? "";
            type = input.type ?? "info";
            duration = Number(input.duration) || 3500;
        }

        if (!message) return;

        if (timerRef.current) clearTimeout(timerRef.current);
        setToast({ message, type });
        setVisible(true);
        timerRef.current = setTimeout(() => {
            setVisible(false);
            timerRef.current = null;
        }, duration);
    }

    // ESC zum Schließen
    useEffect(() => {
        const onKey = (e) => e.key === "Escape" && close();
        if (visible) window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [visible]);

    const ariaLive = toast.type === "error" ? "assertive" : "polite";
    const ariaRole = toast.type === "error" ? "alert" : "status";

    return (
        <NotificationContext.Provider value={{ notify }}>
            {children}

            {/* Korrigierte Struktur: .scoped-notification -> .notification-portal -> .notification-toast */}
            <div className="scoped-notification">
                <div
                    className="notification-portal"
                    aria-live={ariaLive}
                    aria-atomic="true"
                    role={ariaRole}
                >
                    <div
                        className={`notification-toast ${visible ? "show" : "hide"} ${toast.type}`}
                        onMouseEnter={() => {
                            if (timerRef.current) {
                                clearTimeout(timerRef.current);
                                timerRef.current = null;
                            }
                        }}
                        onMouseLeave={() => {
                            if (!timerRef.current && visible) {
                                timerRef.current = setTimeout(() => {
                                    setVisible(false);
                                    timerRef.current = null;
                                }, 1200);
                            }
                        }}
                    >
                        <div className="notification-content">
                            <p className="notification-text">{toast.message}</p>
                            <button
                                type="button"
                                className="notification-close"
                                aria-label={t('close', 'Schließen')}
                                onClick={close}
                            >
                                ×
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </NotificationContext.Provider>
    );
}

export function useNotification() {
    return useContext(NotificationContext);
}
