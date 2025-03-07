// src/context/NotificationContext.jsx
import React, { createContext, useState, useContext } from 'react';

/**
 * NotificationContext stellt eine einfache API bereit:
 *   - notify(message): Zeigt eine Meldung in einem Modal an
 */
const NotificationContext = createContext({
    notify: () => {},
});

export function NotificationProvider({ children }) {
    const [message, setMessage] = useState('');
    const [visible, setVisible] = useState(false);

    // Wird aufgerufen statt alert(...)
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
