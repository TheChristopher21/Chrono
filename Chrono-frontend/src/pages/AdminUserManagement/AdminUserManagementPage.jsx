// src/pages/AdminUserManagement/AdminUserManagementPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

import '../../styles/AdminUserManagementPageScoped.css';

import AdminUserList from './AdminUserList';
import AdminUserForm from './AdminUserForm';
import DeleteConfirmModal from './DeleteConfirmModal';
import { STANDARD_COLORS, defaultWeeklySchedule } from './adminUserManagementUtils';

const getTodayISOString = () => {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const AdminUserManagementPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [users, setUsers] = useState([]);
    const [editingUser, setEditingUser] = useState(null);
    const [isCreatingNewUser, setIsCreatingNewUser] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState({ show: false, userId: null, username: '' });
    const [programStatus, setProgramStatus] = useState("");

    const initialNewUserState = {
        username: '',
        firstName: '',
        lastName: '',
        address: '',
        department: '',
        birthDate: '',
        entryDate: '',
        country: 'DE',
        taxClass: '',
        tarifCode: '',
        canton: '',
        civilStatus: '',
        children: 0,
        religion: '',
        federalState: '',
        churchTax: false,
        gkvAdditionalRate: 0.0125,
        bankAccount: '',
        socialSecurityNumber: '',
        healthInsurance: '',
        personnelNumber: '',
        email: '',
        mobilePhone: '',
        landlinePhone: '',
        password: '',
        roles: ['ROLE_USER'],
        expectedWorkDays: 5.0, // Default für alle neuen User
        dailyWorkHours: 8.5,
        breakDuration: 30,
        annualVacationDays: 25,
        hourlyWage: null,
        monthlySalary: null,
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        scheduleEffectiveDate: getTodayISOString(),
        isHourly: false,
        isPercentage: false,
        workPercentage: 100, // Default, falls isPercentage true wird
        trackingBalanceInMinutes: 0,
        companyId: null // Wird später serverseitig gesetzt oder für Superadmin-Erstellung
    };

    const [currentUserFormData, setCurrentUserFormData] = useState({ ...initialNewUserState });

    const fetchUsers = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/users');
            const userList = Array.isArray(res.data) ? res.data : [];
            setUsers(userList.map(user => ({
                ...user,
                scheduleEffectiveDate: user.scheduleEffectiveDate ? user.scheduleEffectiveDate.toString() : getTodayISOString(),
                weeklySchedule: user.weeklySchedule && user.weeklySchedule.length > 0 ? user.weeklySchedule : [{ ...defaultWeeklySchedule }],
                scheduleCycle: user.scheduleCycle || 1,
                country: user.country || 'DE',
                tarifCode: user.tarifCode || '',
                canton: user.canton || '',
                civilStatus: user.civilStatus || '',
                children: user.children || 0,
                religion: user.religion || '',
                federalState: user.federalState || '',
                churchTax: !!user.churchTax,
                gkvAdditionalRate: user.gkvAdditionalRate ?? 0.0125,
                bankAccount: user.bankAccount || '',
                socialSecurityNumber: user.socialSecurityNumber || '',
                mobilePhone: user.mobilePhone || '',
                landlinePhone: user.landlinePhone || '',
                isHourly: user.isHourly || false,
                isPercentage: user.isPercentage || false,
                workPercentage: user.workPercentage !== null && user.workPercentage !== undefined ? user.workPercentage : (user.isPercentage ? 100 : null),
                expectedWorkDays: user.expectedWorkDays !== null && user.expectedWorkDays !== undefined ? user.expectedWorkDays : (user.isHourly ? null : 5.0), // Default 5.0 if not hourly and not set
                trackingBalanceInMinutes: user.trackingBalanceInMinutes !== null && user.trackingBalanceInMinutes !== undefined ? user.trackingBalanceInMinutes : 0,
                roles: user.roles && user.roles.length > 0 ? user.roles : ['ROLE_USER']
            })));
        } catch (err) {
            console.error(t("userManagement.errorLoadingUsers", "Fehler beim Laden der Benutzer."), err);
            notify(t("userManagement.errorLoadingUsers", "Fehler beim Laden der Benutzer.") + `: ${err.message || ''}`, "error");
        }
    }, [t, notify]);

    useEffect(() => {
        fetchUsers();
    }, [fetchUsers]);

    const resetAndShowCreateForm = () => {
        setEditingUser(null);
        setCurrentUserFormData({
            ...initialNewUserState,
            scheduleEffectiveDate: getTodayISOString(),
            color: STANDARD_COLORS[Math.floor(Math.random() * STANDARD_COLORS.length)]
        });
        setIsCreatingNewUser(true);
    };

    const handleCancelForm = () => {
        setEditingUser(null);
        setIsCreatingNewUser(false);
        setCurrentUserFormData({ ...initialNewUserState, scheduleEffectiveDate: getTodayISOString() });
    };

    const handleFormChange = (field, value) => {
        setCurrentUserFormData(prev => {
            const newState = { ...prev, [field]: value };

            if (field === 'country') {
                if (value === 'DE') {
                    newState.tarifCode = '';
                    newState.canton = '';
                } else if (value === 'CH') {
                    newState.taxClass = '';
                }
            }

            if (field === 'isHourly') {
                if (value) { // Wird stündlich
                    newState.isPercentage = false;
                    newState.workPercentage = null; // oder 100, je nach Logik
                    newState.expectedWorkDays = null; // Stündliche haben dies nicht für die Soll-Berechnung
                    newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.0; // Standardwert für Stundenzettel
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else { // Wird NICHT stündlich
                    // Wenn isPercentage false ist, wird es Standard User, sonst Percentage
                    if (!newState.isPercentage) { // Standard User
                        newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                        newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                        newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                        newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
                    } else { // Bleibt Percentage User
                        newState.workPercentage = prev.workPercentage !== null && prev.workPercentage !== undefined ? prev.workPercentage : 100;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0; // Behalte oder setze Default
                        // dailyWorkHours etc. bleiben null für Percentage
                    }
                }
            } else if (field === 'isPercentage') {
                if (value) { // Wird prozentual
                    newState.isHourly = false;
                    newState.workPercentage = prev.workPercentage !== null && prev.workPercentage !== undefined ? prev.workPercentage : 100;
                    // ANPASSUNG HIER: expectedWorkDays beibehalten oder auf Standard setzen
                    newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                    newState.dailyWorkHours = null;
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else { // Wird NICHT prozentual
                    // Wenn isHourly false ist, wird es Standard User
                    if (!newState.isHourly) { // Standard User
                        newState.workPercentage = null; // Oder 100, aber Standard User hat kein workPercentage-Feld direkt
                        newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                        newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                        newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                        newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
                    }
                    // Wenn isHourly true ist, bleibt es stündlich (wurde oben schon behandelt)
                }
            }
            return newState;
        });
    };

    const handleScheduleCycleChangeInForm = (newCycleInput) => {
        const newCycle = Number(newCycleInput);
        if (isNaN(newCycle) || newCycle < 1) return;

        setCurrentUserFormData(prev => {
            let newSchedule = prev.weeklySchedule ? [...prev.weeklySchedule] : [];
            if (!Array.isArray(newSchedule)) newSchedule = [];

            const currentLength = newSchedule.length;

            if (newCycle > currentLength) {
                for (let i = 0; i < (newCycle - currentLength); i++) {
                    newSchedule.push({ ...defaultWeeklySchedule });
                }
            } else if (newCycle < currentLength) {
                newSchedule = newSchedule.slice(0, newCycle);
            }
            return {...prev, scheduleCycle: newCycle, weeklySchedule: newSchedule};
        });
    };

    const handleWeeklyScheduleDayChangeInForm = (weekIndex, dayKey, dayValueInput) => {
        const dayValue = dayValueInput === '' ? null : Number(dayValueInput);

        setCurrentUserFormData(prev => {
            if (!prev.weeklySchedule || !Array.isArray(prev.weeklySchedule) || weekIndex >= prev.weeklySchedule.length) {
                const newGeneratedSchedule = [];
                const cycle = prev.scheduleCycle || 1;
                for (let i = 0; i < cycle; i++) {
                    newGeneratedSchedule.push(i === weekIndex ? { ...defaultWeeklySchedule, [dayKey]: dayValue } : { ...defaultWeeklySchedule });
                }
                return { ...prev, weeklySchedule: newGeneratedSchedule };
            }

            const newWeeklySchedule = prev.weeklySchedule.map((week, idx) => {
                if (idx === weekIndex) {
                    return { ...week, [dayKey]: dayValue };
                }
                return week;
            });
            return { ...prev, weeklySchedule: newWeeklySchedule };
        });
    };

    const handleSubmitForm = async (e) => {
        e.preventDefault();
        const dataToSend = { ...currentUserFormData };

        if (dataToSend.roles && !Array.isArray(dataToSend.roles) && typeof dataToSend.roles === 'string') {
            dataToSend.roles = [dataToSend.roles];
        } else if (!dataToSend.roles || dataToSend.roles.length === 0) {
            dataToSend.roles = ["ROLE_USER"];
        }
        if (Object.prototype.hasOwnProperty.call(dataToSend, 'role')) {
            delete dataToSend.role;
        }

        if (Object.prototype.hasOwnProperty.call(dataToSend, 'hourlyWage')) {
            dataToSend.hourlyRate = dataToSend.hourlyWage;
            delete dataToSend.hourlyWage;
        }

        // Sicherstellen, dass Felder für das Backend korrekt formatiert/gesetzt sind
        if (dataToSend.isHourly) {
            dataToSend.isPercentage = false;
            dataToSend.workPercentage = null;
            dataToSend.scheduleCycle = null;
            dataToSend.weeklySchedule = null;
            dataToSend.scheduleEffectiveDate = null;
            dataToSend.expectedWorkDays = null; // Explizit null für stündliche User
        } else if (dataToSend.isPercentage) {
            dataToSend.isHourly = false;
            dataToSend.dailyWorkHours = null;
            dataToSend.scheduleCycle = null;
            dataToSend.weeklySchedule = null;
            dataToSend.scheduleEffectiveDate = null;
            // expectedWorkDays wird für Percentage-User beibehalten/gesetzt
            if (dataToSend.expectedWorkDays === null || dataToSend.expectedWorkDays === undefined) {
                dataToSend.expectedWorkDays = 5.0; // Default, falls nicht gesetzt
            }
        } else { // Standard User
            dataToSend.isHourly = false;
            dataToSend.isPercentage = false;
            dataToSend.workPercentage = null; // Oder 100, aber DTO erwartet es so
            if (dataToSend.expectedWorkDays === null || dataToSend.expectedWorkDays === undefined) {
                dataToSend.expectedWorkDays = 5.0; // Default für Standard, falls nicht gesetzt
            }
        }


        if (isCreatingNewUser) {
            if (!dataToSend.password || dataToSend.password.trim() === '') {
                notify(t("userManagement.errorPasswordRequired", "Passwort ist für neue Benutzer erforderlich."), "error");
                return;
            }
            try {
                await api.post('/api/admin/users', dataToSend);
                notify(t("userManagement.userAddedSuccess", "Benutzer erfolgreich hinzugefügt!"), "success");
                fetchUsers();
                handleCancelForm();
            } catch (err) {
                console.error(t("userManagement.errorAddingUser"), err);
                notify(t("userManagement.errorAddingUser", "Fehler beim Hinzufügen des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
        } else if (editingUser) {
            try {
                const payloadForUpdate = { ...dataToSend, id: editingUser.id };
                // Passwort wird nur gesendet, wenn es geändert werden soll (über separates Feld/Request im Backend)
                // Im AdminUserController wird 'newPassword' als RequestParam erwartet, nicht im Body des DTOs für Update.
                // Das DTO 'password' Feld ist hier also nicht direkt für Update gedacht.
                delete payloadForUpdate.password;

                await api.put(`/api/admin/users`, payloadForUpdate); // Ggf. newPassword als QueryParam anhängen, falls benötigt

                notify(t("userManagement.userUpdatedSuccess", "Benutzer erfolgreich aktualisiert!"), "success");
                fetchUsers();
                handleCancelForm();
            } catch (err) {
                console.error(t("userManagement.errorUpdatingUser"), err);
                notify(t("userManagement.errorUpdatingUser", "Fehler beim Aktualisieren des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
        }
    };

    const handleEditUser = (userToEdit) => {
        setEditingUser(userToEdit);
        setCurrentUserFormData({
            ...initialNewUserState, // Start mit Defaults
            ...userToEdit,          // Überschreibe mit User-Daten
            password: '',           // Passwort nicht vorausfüllen für Bearbeitung
            roles: userToEdit.roles && userToEdit.roles.length > 0 ? userToEdit.roles : ['ROLE_USER'],
            scheduleEffectiveDate: userToEdit.scheduleEffectiveDate ? userToEdit.scheduleEffectiveDate.toString() : getTodayISOString(),
            weeklySchedule: userToEdit.weeklySchedule && userToEdit.weeklySchedule.length > 0
                ? userToEdit.weeklySchedule
                : [{ ...defaultWeeklySchedule }],
            scheduleCycle: userToEdit.scheduleCycle || 1,
            country: userToEdit.country || 'DE',
            tarifCode: userToEdit.tarifCode || '',
            canton: userToEdit.canton || '',
            civilStatus: userToEdit.civilStatus || '',
            children: userToEdit.children || 0,
            religion: userToEdit.religion || '',
            bankAccount: userToEdit.bankAccount || '',
            socialSecurityNumber: userToEdit.socialSecurityNumber || '',
            mobilePhone: userToEdit.mobilePhone || '',
            landlinePhone: userToEdit.landlinePhone || '',
            workPercentage: userToEdit.workPercentage !== null && userToEdit.workPercentage !== undefined ? userToEdit.workPercentage : (userToEdit.isPercentage ? 100 : null),
            expectedWorkDays: userToEdit.expectedWorkDays !== null && userToEdit.expectedWorkDays !== undefined ? userToEdit.expectedWorkDays : (userToEdit.isHourly ? null : 5.0) // Wichtig für Edit
            ,hourlyWage: userToEdit.hourlyRate ?? null
            ,monthlySalary: userToEdit.monthlySalary ?? null
        });
        setIsCreatingNewUser(false);
    };

    const requestDeleteUser = (user) => {
        setDeleteConfirm({ show: true, userId: user.id, username: user.username });
    };
    const cancelDelete = () => {
        setDeleteConfirm({ show: false, userId: null, username: '' });
    };
    const confirmDelete = async () => {
        if (deleteConfirm.userId) {
            try {
                await api.delete(`/api/admin/users/${deleteConfirm.userId}`);
                notify(t("userManagement.userDeletedSuccess", "Benutzer erfolgreich gelöscht."), "success");
                fetchUsers(); // Liste neu laden
                if (editingUser && editingUser.id === deleteConfirm.userId) { // Falls der bearbeitete User gelöscht wurde
                    handleCancelForm(); // Formular zurücksetzen
                }
            } catch (err) {
                console.error(t("userManagement.errorDeletingUser"), err);
                notify(t("userManagement.errorDeletingUser", "Fehler beim Löschen des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
            setDeleteConfirm({ show: false, userId: null, username: '' });
        }
    };

    async function handleProgramCard(user) {
        try {
            // Konvertiere Username zu Hex (max 16 ASCII Chars -> 32 Hex Chars)
            // Annahme: stringToHex16 ist korrekt implementiert und verfügbar
            // const hexUsername = stringToHex16(user.username); // stringToHex16 ist nicht in diesem Snippet definiert
            // Stattdessen senden wir den Username direkt und der Service kümmert sich ggf. um die Konvertierung, falls nötig, oder es wird anders gehandhabt.
            // Für den aktuellen NFCCommandService wird der 'data'-String direkt verwendet.
            const dataPayload = user.username; // Oder eine spezifische ID/Formatierung

            const payload = { type: "PROGRAM", data: dataPayload };
            const response = await api.post('/api/nfc/command', payload);

            if (response.data && response.data.id) {
                setProgramStatus(t("userManagement.nfcProgramStart", "Kartenprogrammierung gestartet. Bitte NFC-Karte auflegen..."));
                const commandId = response.data.id;
                let maxTries = 20; // Erhöhte Versuche für längere Wartezeit (20 * 1.5s = 30s)
                let delay = 1500;

                const pollStatus = async () => {
                    try {
                        const res = await api.get(`/api/nfc/command/status/${commandId}`);
                        if (res.data.status === "done") {
                            setProgramStatus(t("userManagement.programCardSuccess", "Karte erfolgreich programmiert!"));
                            setTimeout(() => setProgramStatus(""), 7000);
                        } else if (res.data.status === "pending" && maxTries-- > 0) {
                            setProgramStatus(t("userManagement.nfcProgramProgress", `Warte auf NFC-Agent... (${maxTries} Versuche übrig)`));
                            setTimeout(pollStatus, delay);
                        } else {
                            setProgramStatus(t("userManagement.programCardErrorTimeout", "Zeitüberschreitung bei Kartenprogrammierung oder Befehl nicht gefunden."));
                            setTimeout(() => setProgramStatus(""), 7000);
                        }
                    } catch (pollError) {
                        console.error("NFC poll error:", pollError);
                        setProgramStatus(t("userManagement.programCardErrorComm", "Kommunikationsfehler mit NFC-Agent."));
                        setTimeout(() => setProgramStatus(""), 7000);
                    }
                };
                pollStatus();
            } else {
                notify(t("userManagement.programCardError", "Fehler beim Starten der Kartenprogrammierung."), 'error');
            }
        } catch (err) {
            console.error("Fehler beim Kartenprogrammieren:", err);
            notify(t("userManagement.programCardError", "Fehler beim Kartenprogrammieren.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, 'error');
        }
    }


    return (
        <div className="admin-user-management-page scoped-dashboard">
            <Navbar />

            {programStatus && (
                <div className={`nfc-status-message ${programStatus.includes(t("userManagement.programCardSuccess")) ? 'success' : (programStatus.includes("Fehler") || programStatus.includes("error") || programStatus.includes("Zeitüberschreitung") ? 'error' : 'info')}`}>{programStatus}</div>
            )}


            <header className="page-header">
                <h2>{t("userManagement.title")}</h2>
            </header>

            {!isCreatingNewUser && !editingUser && (
                <div className="add-user-button-container">
                    <button onClick={resetAndShowCreateForm} className="button-primary add-user-button">
                        {t("userManagement.button.addNewUser", "Neuen Benutzer hinzufügen")}
                    </button>
                </div>
            )}

            {(editingUser || isCreatingNewUser) && (
                <AdminUserForm
                    t={t}
                    isEditing={!!editingUser}
                    userData={currentUserFormData}
                    setUserData={handleFormChange}
                    onSubmit={handleSubmitForm}
                    onCancel={handleCancelForm}
                    onScheduleCycleChange={handleScheduleCycleChangeInForm}
                    onWeeklyScheduleDayChange={handleWeeklyScheduleDayChangeInForm}
                />
            )}

            <AdminUserList
                users={users}
                t={t}
                handleEditUser={handleEditUser}
                requestDeleteUser={requestDeleteUser}
                handleProgramCard={handleProgramCard}
            />

            <DeleteConfirmModal
                visible={deleteConfirm.show}
                title={t("userManagement.deleteConfirmTitle", "Benutzer löschen")}
                message={t("userManagement.deleteConfirmMessage", "Möchten Sie diesen Benutzer wirklich löschen?")}
                userName={deleteConfirm.username}
                onConfirm={confirmDelete}
                onCancel={cancelDelete}
            />
        </div>
    );
};

export default AdminUserManagementPage;