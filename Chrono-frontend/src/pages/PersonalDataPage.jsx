import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/PersonalDataPage.css';

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

    const fetchPersonalData = async () => {
        try {
            const res = await api.get('/api/auth/me');
            setPersonalData({
                firstName: res.data.firstName,
                lastName: res.data.lastName,
                email: res.data.email
            });
        } catch (err) {
            console.error('Fehler beim Laden der Profil-Daten', err);
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
            alert('Profil aktualisiert');
            setCurrentUser(res.data);
        } catch (err) {
            console.error('Fehler beim Aktualisieren des Profils', err);
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
            setMessage('Passwort erfolgreich geändert');
            setPasswordData({ currentPassword: '', newPassword: '' });
        } catch (err) {
            console.error('Fehler beim Ändern des Passworts', err);
            setMessage('Fehler beim Ändern des Passworts');
        }
    };

    return (
        <div className="personal-data-page">
            <Navbar />
            <header className="page-header">
                <h2>Mein Profil</h2>
            </header>
            <section className="personal-data-section">
                <form onSubmit={handlePersonalDataUpdate} className="form-personal">
                    <div className="form-group">
                        <label>Vorname:</label>
                        <input
                            type="text"
                            name="firstName"
                            value={personalData.firstName}
                            onChange={(e) => setPersonalData({ ...personalData, firstName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Nachname:</label>
                        <input
                            type="text"
                            name="lastName"
                            value={personalData.lastName}
                            onChange={(e) => setPersonalData({ ...personalData, lastName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>E-Mail:</label>
                        <input
                            type="email"
                            name="email"
                            value={personalData.email}
                            onChange={(e) => setPersonalData({ ...personalData, email: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">Speichern</button>
                </form>
            </section>
            <section className="password-change-section">
                <h3>Passwort ändern</h3>
                <form onSubmit={handlePasswordChange} className="form-password">
                    <div className="form-group">
                        <label>Aktuelles Passwort:</label>
                        <input
                            type="password"
                            name="currentPassword"
                            value={passwordData.currentPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Neues Passwort:</label>
                        <input
                            type="password"
                            name="newPassword"
                            value={passwordData.newPassword}
                            onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">Passwort ändern</button>
                </form>
                {message && <p className="message">{message}</p>}
            </section>
        </div>
    );
};

export default PersonalDataPage;
