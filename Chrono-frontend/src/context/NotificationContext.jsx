// src/context/NotificationContext.jsx
import React, { createContext, useState, useContext } from 'react';
import "../styles/Notification.css"; // Pfad anpassen

export const NotificationContext = createContext({
    notify: () => {},
});

export function NotificationProvider({ children }) {
    const [message, setMessage] = useState('');
    const [visible, setVisible] = useState(false);

    function notify(msg) {
        setMessage(msg);
        setVisible(true);
    }

    function closeNotification() {
        setVisible(false);
        setMessage('');
    }

    return (
        <NotificationContext.Provider value={{ notify }}>
            {children}

            {visible && (
                <div className="notification-overlay">
                    <div className="notification-modal">
                        <p>{message}</p>
                        <button onClick={closeNotification}>OK</button>
                    </div>
                </div>
            )}
        </NotificationContext.Provider>
    );
}

export function useNotification() {
    return useContext(NotificationContext);
}
