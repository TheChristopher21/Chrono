// src/pages/PersonalDataPage.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/PersonalDataPageScoped.css';

import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import { useOnboarding } from '../context/OnboardingContext';

const PersonalDataPage = () => {
    const { currentUser, setCurrentUser } = useAuth();
    const [personalData, setPersonalData] = useState({
        firstName: currentUser?.firstName || '',
        lastName: currentUser?.lastName || '',
        email: currentUser?.email || '',
        emailNotifications: currentUser?.emailNotifications ?? true,
    });
    const [passwordData, setPasswordData] = useState({
        currentPassword: '',
        newPassword: '',
    });
    const [message, setMessage] = useState('');

    const { notify } = useNotification();
    const { t } = useTranslation();
    const { start } = useOnboarding();

    useEffect(() => {
        if (currentUser) {
            fetchPersonalData();
        }
        // eslint-disable-next-line
    }, [currentUser]);

    async function fetchPersonalData() {
        try {
            const res = await api.get('/api/auth/me');
            setPersonalData({
                firstName: res.data.firstName,
                lastName: res.data.lastName,
                email: res.data.email,
                emailNotifications: res.data.emailNotifications,
            });
        } catch (err) {
            console.error(t("personalData.errorLoading"), err);
            notify(t("personalData.errorLoading"));
        }
    }

    async function handlePersonalDataUpdate(e) {
        e.preventDefault();
        try {
            const res = await api.put('/api/user/update', {
                username: currentUser.username,
                firstName: personalData.firstName,
                lastName: personalData.lastName,
                email: personalData.email,
                emailNotifications: personalData.emailNotifications,
            });
            notify(t("personalData.saved", "Gespeichert"));
            setCurrentUser(res.data);
        } catch (err) {
            console.error(t("personalData.errorUpdating"), err);
            notify(t("personalData.errorUpdating"));
        }
    }

    async function handlePasswordChange(e) {
        e.preventDefault();
        try {
            await api.put('/api/user/change-password', null, {
                params: {
                    username: currentUser.username,
                    currentPassword: passwordData.currentPassword,
                    newPassword: passwordData.newPassword,
                },
            });
            setMessage(t("personalData.passwordChanged", "Passwort erfolgreich geändert"));
            notify(t("personalData.passwordChanged", "Passwort erfolgreich geändert"));
            setPasswordData({ currentPassword: '', newPassword: '' });
        } catch (err) {
            console.error(t("personalData.errorChangingPassword"), err);
            setMessage(t("personalData.errorChangingPassword"));
            notify(t("personalData.errorChangingPassword"));
        }
    }

    return (
        <div className="personal-data-page scoped-personal-data">
            <Navbar />

            <header className="page-header">
                <h2>{t("personalData.title", "Meine Daten")}</h2>
            </header>

            <section className="personal-data-section">
                <form onSubmit={handlePersonalDataUpdate} className="form-personal">
                    <div className="form-group">
                        <label>{t("personalData.firstName", "Vorname")}:</label>
                        <input
                            type="text"
                            name="firstName"
                            value={personalData.firstName}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, firstName: e.target.value })
                            }
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.lastName", "Nachname")}:</label>
                        <input
                            type="text"
                            name="lastName"
                            value={personalData.lastName}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, lastName: e.target.value })
                            }
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.email", "E-Mail")}:</label>
                        <input
                            type="email"
                            name="email"
                            value={personalData.email}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, email: e.target.value })
                            }
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>
                            {t('personalData.emailNotifications')}
                        </label>
                        <input
                            type="checkbox"
                            checked={personalData.emailNotifications}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, emailNotifications: e.target.checked })
                            }
                        />
                    </div>

                    <button type="submit">
                        {t("personalData.saveButton", "Speichern")}
                    </button>
                </form>
            </section>

            <section className="password-change-section">
                <h3>{t("personalData.changePassword", "Passwort ändern")}</h3>
                <form onSubmit={handlePasswordChange} className="form-password">
                    <div className="form-group">
                        <label>
                            {t("personalData.currentPassword", "Aktuelles Passwort")}:
                        </label>
                        <input
                            type="password"
                            name="currentPassword"
                            value={passwordData.currentPassword}
                            onChange={(e) =>
                                setPasswordData({
                                    ...passwordData,
                                    currentPassword: e.target.value,
                                })
                            }
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.newPassword", "Neues Passwort")}:</label>
                        <input
                            type="password"
                            name="newPassword"
                            value={passwordData.newPassword}
                            onChange={(e) =>
                                setPasswordData({ ...passwordData, newPassword: e.target.value })
                            }
                            required
                        />
                    </div>

                    <button type="submit">
                        {t("personalData.changePassword", "Passwort ändern")}
                    </button>
                </form>

                {message && <p>{message}</p>}

                <button type="button" onClick={() => start()}>Tutorial erneut anzeigen</button>
            </section>
        </div>
    );
};

export default PersonalDataPage;
