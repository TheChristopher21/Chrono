// src/pages/PersonalDataPage.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/PersonalDataPageScoped.css';
import CalendarExportModal from '../components/CalendarExportModal';

import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

const PersonalDataPage = () => {
    const { currentUser, setCurrentUser } = useAuth();
    const [personalData, setPersonalData] = useState({
        firstName: currentUser?.firstName || '',
        lastName: currentUser?.lastName || '',
        email: currentUser?.email || '',
        address: currentUser?.address || '',
        mobilePhone: currentUser?.mobilePhone || '',
        landlinePhone: currentUser?.landlinePhone || '',
        civilStatus: currentUser?.civilStatus || '',
        children: currentUser?.children ?? 0,
        bankAccount: currentUser?.bankAccount || '',
        emailNotifications: currentUser?.emailNotifications ?? true,
    });
    const [passwordData, setPasswordData] = useState({
        currentPassword: '',
        newPassword: '',
    });
    const [message, setMessage] = useState('');
    const [showExportModal, setShowExportModal] = useState(false);

    const { notify } = useNotification();
    const { t } = useTranslation();

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
                address: res.data.address,
                mobilePhone: res.data.mobilePhone,
                landlinePhone: res.data.landlinePhone,
                civilStatus: res.data.civilStatus,
                children: res.data.children,
                bankAccount: res.data.bankAccount,
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
                address: personalData.address,
                mobilePhone: personalData.mobilePhone,
                landlinePhone: personalData.landlinePhone,
                civilStatus: personalData.civilStatus,
                children: personalData.children,
                bankAccount: personalData.bankAccount,
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
            await api.put('/api/user/change-password', {
                username: currentUser.username,
                currentPassword: passwordData.currentPassword,
                newPassword: passwordData.newPassword,
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

    const baseUrl = api.defaults.baseURL.replace(/\/$/, "");
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const icsUrl = currentUser
        ? `${baseUrl}/api/report/timesheet/ics-feed/${currentUser.username}?zone=${encodeURIComponent(timeZone)}`
        : '';

    function handleCopyLink() {
        navigator.clipboard.writeText(icsUrl);
        notify(t("personalData.linkCopied", "Link kopiert"));
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
                        <label>{t("personalData.address", "Adresse")}:</label>
                        <input
                            type="text"
                            name="address"
                            value={personalData.address}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, address: e.target.value })
                            }
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.mobilePhone", "Handynummer")}:</label>
                        <input
                            type="text"
                            name="mobilePhone"
                            value={personalData.mobilePhone}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, mobilePhone: e.target.value })
                            }
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.landlinePhone", "Festnetz (optional)")}:</label>
                        <input
                            type="text"
                            name="landlinePhone"
                            value={personalData.landlinePhone}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, landlinePhone: e.target.value })
                            }
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.civilStatus", "Zivilstand")}:</label>
                        <input
                            type="text"
                            name="civilStatus"
                            value={personalData.civilStatus}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, civilStatus: e.target.value })
                            }
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.children", "Kinder")}:</label>
                        <input
                            type="number"
                            name="children"
                            min="0"
                            value={personalData.children}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, children: parseInt(e.target.value, 10) })
                            }
                        />
                    </div>

                    <div className="form-group">
                        <label>{t("personalData.bankAccount", "Bankverbindung")}:</label>
                        <input
                            type="text"
                            name="bankAccount"
                            value={personalData.bankAccount}
                            onChange={(e) =>
                                setPersonalData({ ...personalData, bankAccount: e.target.value })
                            }
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

              <section className="calendar-feed-section">
                  <h3>{t("personalData.calendarFeed", "Kalender-Feed")}</h3>
                  <p>{t("personalData.calendarFeedInfo", "Nutze diese URL, um deinen Kalender zu abonnieren.")}</p>
                  <button type="button" onClick={() => setShowExportModal(true)}>
                      {t("personalData.exportButton", "Exportieren")}
                  </button>
                  {showExportModal && (
                      <CalendarExportModal
                          icsUrl={icsUrl}
                          onClose={() => setShowExportModal(false)}
                          onCopyLink={handleCopyLink}
                      />
                  )}
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

            </section>
        </div>
    );
};

export default PersonalDataPage;
