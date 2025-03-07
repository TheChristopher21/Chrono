// src/pages/PersonalDataPage.jsx
import React, { useState, useEffect, useContext } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/PersonalDataPage.css';
import { useNotification } from '../context/NotificationContext';
import { useTranslation, LanguageContext } from '../context/LanguageContext';

const PersonalDataPage = () => {
    const { currentUser, setCurrentUser } = useAuth();
    const [personalData, setPersonalData] = useState({
        firstName: currentUser?.firstName || '',
        lastName: currentUser?.lastName || '',
        email: currentUser?.email || ''
    });
    const [passwordData, setPasswordData] = useState({
        currentPassword: '',
        newPassword: ''
    });
    const [message, setMessage] = useState('');

    const { notify } = useNotification();
    const { t } = useTranslation();

    const fetchPersonalData = async () => {
        try {
            const res = await api.get('/api/auth/me');
            setPersonalData({
                firstName: res.data.firstName,
                lastName: res.data.lastName,
                email: res.data.email
            });
        } catch (err) {
            console.error(t("personalData.errorLoading"), err);
        }
    };

    useEffect(() => {
        if (currentUser) {
            fetchPersonalData();
        }
    }, [currentUser]);

    const handlePersonalDataUpdate = async (e) => {
        e.preventDefault();
        try {
            const res = await api.put('/api/user/update', {
                username: currentUser.username,
                firstName: personalData.firstName,
                lastName: personalData.lastName,
                email: personalData.email
            });
            notify(t("personalData.saved"));
            setCurrentUser(res.data);
        } catch (err) {
            console.error(t("personalData.errorUpdating"), err);
            notify(t("personalData.errorUpdating"));
        }
    };

    const handlePasswordChange = async (e) => {
        e.preventDefault();
        try {
            await api.put('/api/user/change-password', null, {
                params: {
                    username: currentUser.username,
                    currentPassword: passwordData.currentPassword,
                    newPassword: passwordData.newPassword
                }
            });
            setMessage(t("personalData.passwordChanged"));
            notify(t("personalData.passwordChanged"));
            setPasswordData({ currentPassword: '', newPassword: '' });
        } catch (err) {
            console.error(t("personalData.errorChangingPassword"), err);
            setMessage(t("personalData.errorChangingPassword"));
            notify(t("personalData.errorChangingPassword"));
        }
    };

    return (
        <div className="personal-data-page">
            <Navbar />
            <header className="page-header">
                <h2>{t("personalData.title")}</h2>
            </header>
            <section className="personal-data-section">
                <form onSubmit={handlePersonalDataUpdate} className="form-personal">
                    <div className="form-group">
                        <label>{t("personalData.firstName")}:</label>
                        <input
                            type="text"
                            name="firstName"
                            value={personalData.firstName}
                            onChange={(e) => setPersonalData({ ...personalData, firstName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("personalData.lastName")}:</label>
                        <input
                            type="text"
                            name="lastName"
                            value={personalData.lastName}
                            onChange={(e) => setPersonalData({ ...personalData, lastName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("personalData.email")}:</label>
                        <input
                            type="email"
                            name="email"
                            value={personalData.email}
                            onChange={(e) => setPersonalData({ ...personalData, email: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">{t("personalData.saveButton")}</button>
                </form>
            </section>
            <section className="password-change-section">
                <h3>{t("personalData.changePassword")}</h3>
                <form onSubmit={handlePasswordChange} className="form-password">
                    <div className="form-group">
                        <label>{t("personalData.currentPassword")}:</label>
                        <input
                            type="password"
                            name="currentPassword"
                            value={passwordData.currentPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("personalData.newPassword")}:</label>
                        <input
                            type="password"
                            name="newPassword"
                            value={passwordData.newPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">{t("personalData.changePassword")}</button>
                </form>
                {message && <p>{message}</p>}
            </section>
        </div>
    );
};

export default PersonalDataPage;
