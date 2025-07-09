// src/context/NotificationContext.jsx
import React, { createContext, useState, useContext } from 'react';
import ModalOverlay from '../components/ModalOverlay';
import { useTranslation } from './LanguageContext';

export const NotificationContext = createContext({
    notify: () => {},
});

export function NotificationProvider({ children }) {
    const [message, setMessage] = useState('');
    const [visible, setVisible] = useState(false);
    const { t } = useTranslation();

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
                <ModalOverlay visible className="notification-overlay">
                    <div className="notification-modal">
                        <p>{message}</p>
                        <button onClick={closeNotification}>{t('ok')}</button>
                    </div>
                </ModalOverlay>
            )}
        </NotificationContext.Provider>
    );
}

export function useNotification() {
    return useContext(NotificationContext);
}
