import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/AdminChangePassword.css';

const AdminChangePassword = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [passwordData, setPasswordData] = useState({ currentPassword: '', newPassword: '' });
    const [message, setMessage] = useState('');

    const handlePasswordChange = async (e) => {
        e.preventDefault();
        try {
            await api.put('/api/user/change-password', {
                username: currentUser.username,
                currentPassword: passwordData.currentPassword,
                newPassword: passwordData.newPassword,
            });
            setMessage(t("personalData.passwordChanged", "Passwort erfolgreich geändert"));
            notify(t("personalData.passwordChanged", "Passwort erfolgreich geändert"));
            setPasswordData({ currentPassword: '', newPassword: '' });
        } catch (err) {
            console.error(t("personalData.errorChangingPassword", "Fehler beim Ändern des Passworts"), err);
            setMessage(t("personalData.errorChangingPassword", "Fehler beim Ändern des Passworts"));
            notify(t("personalData.errorChangingPassword", "Fehler beim Ändern des Passworts"));
        }
    };

    return (
        <>
            {/* Navbar außerhalb des Containers */}
            <Navbar />

            <div className="admin-change-password-page">
                <header className="page-header">
                    <h2>{t("admin.changePasswordTitle", "Passwort ändern")}</h2>
                </header>
                <form onSubmit={handlePasswordChange} className="form-password">
                    <div className="form-group">
                        <label>{t("personalData.currentPassword", "Aktuelles Passwort")}:</label>
                        <input
                            type="password"
                            name="currentPassword"
                            value={passwordData.currentPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("personalData.newPassword", "Neues Passwort")}:</label>
                        <input
                            type="password"
                            name="newPassword"
                            value={passwordData.newPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">{t("admin.changePasswordButton", "Passwort ändern")}</button>
                </form>

                {message && <p className="message">{message}</p>}
            </div>
        </>
    );
};

export default AdminChangePassword;
