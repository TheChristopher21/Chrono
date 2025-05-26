// src/pages/AdminUserManagement/AdminUserManagementPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

import '../../styles/AdminUserManagementPageScoped.css'; // Ihre überarbeitete CSS-Datei

import AdminUserList from './AdminUserList';
import AdminUserForm from './AdminUserForm';
import DeleteConfirmModal from './DeleteConfirmModal';
import { STANDARD_COLORS, defaultWeeklySchedule } from './adminUserManagementUtils';

// Hilfsfunktion, um das heutige Datum als YYYY-MM-DD String zu bekommen
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
        email: '',
        password: '',
        roles: ['ROLE_USER'],
        expectedWorkDays: 5.0,
        dailyWorkHours: 8.5,
        breakDuration: 30,
        annualVacationDays: 25,
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        scheduleEffectiveDate: getTodayISOString(),
        isHourly: false,
        isPercentage: false,
        workPercentage: 100,
        trackingBalanceInMinutes: 0,
        companyId: null
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
                isHourly: user.isHourly || false,
                isPercentage: user.isPercentage || false,
                workPercentage: user.workPercentage !== null && user.workPercentage !== undefined ? user.workPercentage : 100,
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

            if (field === 'isPercentage') {
                newState.isHourly = value ? false : prev.isHourly;
                if (value) {
                    newState.workPercentage = prev.workPercentage !== null && prev.workPercentage !== undefined ? prev.workPercentage : 100;
                    newState.dailyWorkHours = null;
                    newState.expectedWorkDays = null;
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else if (!newState.isHourly) {
                    newState.workPercentage = null;
                    newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                    newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                    newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                    newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                    newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
                }
            } else if (field === 'isHourly') {
                newState.isPercentage = value ? false : prev.isPercentage;
                if (value) {
                    newState.workPercentage = null;
                    newState.dailyWorkHours = prev.dailyWorkHours;
                    newState.expectedWorkDays = null;
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else if (!newState.isPercentage) {
                    newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                    newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                    newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                    newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                    newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
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
                delete payloadForUpdate.password;
                await api.put(`/api/admin/users`, payloadForUpdate);

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
            ...initialNewUserState,
            ...userToEdit,
            password: '',
            roles: userToEdit.roles && userToEdit.roles.length > 0 ? userToEdit.roles : ['ROLE_USER'],
            scheduleEffectiveDate: userToEdit.scheduleEffectiveDate ? userToEdit.scheduleEffectiveDate.toString() : getTodayISOString(),
            weeklySchedule: userToEdit.weeklySchedule && userToEdit.weeklySchedule.length > 0
                ? userToEdit.weeklySchedule
                : [{ ...defaultWeeklySchedule }],
            scheduleCycle: userToEdit.scheduleCycle || 1,
            workPercentage: userToEdit.workPercentage !== null && userToEdit.workPercentage !== undefined ? userToEdit.workPercentage : (userToEdit.isPercentage ? 100 : null)
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
                fetchUsers();
            } catch (err) {
                console.error(t("userManagement.errorDeletingUser"), err);
                notify(t("userManagement.errorDeletingUser", "Fehler beim Löschen des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
            setDeleteConfirm({ show: false, userId: null, username: '' });
        }
    };

    async function handleProgramCard(user) {
        try {
            const payload = { type: "PROGRAM", data: user.username };
            const response = await api.post('/api/nfc/command', payload);

            if (response.data && response.data.id) {
                setProgramStatus(t("userManagement.nfcProgramStart"));
                const commandId = response.data.id;
                let maxTries = 10;
                let delay = 1500;

                const pollStatus = async () => {
                    try {
                        const res = await api.get(`/api/nfc/command/status/${commandId}`);
                        if (res.data.status === "done") {
                            setProgramStatus(t("userManagement.programCardSuccess"));
                            setTimeout(() => setProgramStatus(""), 7000);
                        } else if (maxTries-- > 0) {
                            setTimeout(pollStatus, delay);
                        } else {
                            setProgramStatus(t("userManagement.programCardErrorTimeout"));
                            setTimeout(() => setProgramStatus(""), 7000);
                        }
                    } catch (pollError) {
                        console.error("NFC poll error:", pollError);
                        setProgramStatus(t("userManagement.programCardErrorComm"));
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
                <div className={`nfc-status-message ${programStatus.includes(t("userManagement.programCardSuccess")) ? 'success' : 'error'}`}>{programStatus}</div>
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
                    setUserData={handleFormChange} // Stellt sicher, dass die Logik zum Zurücksetzen greift
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