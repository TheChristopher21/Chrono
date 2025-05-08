import React, { useState, useEffect, useContext } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation, LanguageContext } from '../../context/LanguageContext';

import api from '../../utils/api';
import '../../styles/AdminUserManagementPageScoped.css';

import AdminUserList from './AdminUserList';
import AdminUserForm from './AdminUserForm';
import DeleteConfirmModal from './DeleteConfirmModal';
import { STANDARD_COLORS, defaultWeeklySchedule } from './adminUserManagementUtils';

const AdminUserManagementPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { language } = useContext(LanguageContext);

    const [users, setUsers] = useState([]);
    const [editingUser, setEditingUser] = useState(null);
    const [deleteConfirm, setDeleteConfirm] = useState({ show: false, userId: null });
    const [programStatus, setProgramStatus] = useState("");

    const [newUser, setNewUser] = useState({
        username: '',
        firstName: '',
        lastName: '',
        email: '',
        password: '',
        role: 'ROLE_USER',
        expectedWorkDays: '',
        breakDuration: '',
        annualVacationDays: '',
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        isHourly: false,
        isPercentage: false,
        workPercentage: 100
    });

    useEffect(() => {
        fetchUsers();
    }, []);

    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            const userList = Array.isArray(res.data) ? res.data : [];
            setUsers(userList);
        } catch (err) {
            console.error(t("userManagement.errorLoadingUsers"), err);
        }
    }

    const handleAddUser = async (e) => {
        e.preventDefault();
        try {
            const payload = {
                username: newUser.username,
                firstName: newUser.firstName,
                lastName: newUser.lastName,
                email: newUser.email,
                password: newUser.password,
                roles: [{ roleName: newUser.role }],
                expectedWorkDays:
                    newUser.isPercentage || newUser.isHourly
                        ? null
                        : newUser.expectedWorkDays
                            ? Number(newUser.expectedWorkDays)
                            : null,
                breakDuration: newUser.breakDuration
                    ? Number(newUser.breakDuration)
                    : null,
                annualVacationDays: newUser.annualVacationDays
                    ? Number(newUser.annualVacationDays)
                    : null,
                color: newUser.color,
                scheduleCycle:
                    newUser.isPercentage || newUser.isHourly
                        ? null
                        : newUser.scheduleCycle,
                weeklySchedule:
                    newUser.isPercentage || newUser.isHourly
                        ? null
                        : newUser.weeklySchedule,
                isHourly: newUser.isHourly,
                isPercentage: newUser.isPercentage,
                workPercentage: newUser.isPercentage
                    ? Number(newUser.workPercentage)
                    : 100
            };
            await api.post('/api/admin/users', payload);
            setNewUser({
                username: '',
                firstName: '',
                lastName: '',
                email: '',
                password: '',
                role: 'ROLE_USER',
                expectedWorkDays: '',
                breakDuration: '',
                annualVacationDays: '',
                color: STANDARD_COLORS[0],
                scheduleCycle: 1,
                weeklySchedule: [{ ...defaultWeeklySchedule }],
                isHourly: false,
                isPercentage: false,
                workPercentage: 100
            });
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorAddingUser"), err);
            notify(t("userManagement.errorAddingUser"));
        }
    };

    const handleEditUser = (user) => {
        const { password, ...rest } = user;
        setEditingUser({
            ...rest,
            role: user.roles?.[0]?.roleName || "ROLE_USER",
            scheduleCycle: rest.scheduleCycle || 1,
            weeklySchedule: rest.weeklySchedule
                ? Array.isArray(rest.weeklySchedule)
                    ? rest.weeklySchedule
                    : [rest.weeklySchedule]
                : [{ ...defaultWeeklySchedule }]
        });
    };

    const handleUpdateUser = async (e) => {
        e.preventDefault();
        if (!editingUser) return;
        try {
            const payload = {
                id: editingUser.id,
                username: editingUser.username,
                firstName: editingUser.firstName,
                lastName: editingUser.lastName,
                email: editingUser.email,
                roles:
                    editingUser.roles ||
                    [{ roleName: editingUser.role || "ROLE_USER" }],
                color: editingUser.color,
                expectedWorkDays:
                    editingUser.isPercentage || editingUser.isHourly
                        ? null
                        : editingUser.expectedWorkDays
                            ? Number(editingUser.expectedWorkDays)
                            : null,
                breakDuration: editingUser.breakDuration
                    ? Number(editingUser.breakDuration)
                    : null,
                annualVacationDays: editingUser.annualVacationDays
                    ? Number(editingUser.annualVacationDays)
                    : null,
                scheduleCycle:
                    editingUser.isPercentage || editingUser.isHourly
                        ? null
                        : editingUser.scheduleCycle,
                weeklySchedule:
                    editingUser.isPercentage || editingUser.isHourly
                        ? null
                        : editingUser.weeklySchedule,
                isHourly: editingUser.isHourly,
                isPercentage: editingUser.isPercentage,
                workPercentage: editingUser.isPercentage
                    ? Number(editingUser.workPercentage)
                    : 100
            };

            await api.put('/api/admin/users', payload);
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorUpdatingUser"), err);
            notify(t("userManagement.errorUpdatingUser"));
        }
    };

    const requestDeleteUser = (id) => {
        setDeleteConfirm({ show: true, userId: id });
    };
    const cancelDelete = () => {
        setDeleteConfirm({ show: false, userId: null });
    };
    const confirmDelete = async () => {
        if (deleteConfirm.userId) {
            await handleDeleteUser(deleteConfirm.userId);
            setDeleteConfirm({ show: false, userId: null });
        }
    };
    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorDeletingUser"), err);
            notify(t("userManagement.errorDeletingUser"));
        }
    };

    async function handleProgramCard(user) {
        try {
            const payload = {
                type: "PROGRAM",
                data: user.username
            };

            const response = await api.post('/api/nfc/command', payload);

            if (response.data && response.data.id) {
                setProgramStatus(t("userManagement.nfcProgramStart"));
                const commandId = response.data.id;
                let maxTries = 10;
                let delay = 1500;

                const pollStatus = async () => {
                    const res = await api.get(`/api/nfc/command/status/${commandId}`);
                    if (res.data.status === "done") {
                        setProgramStatus(t("userManagement.programCardSuccess"));
                        setTimeout(() => setProgramStatus(""), 10000);
                    } else if (maxTries-- > 0) {
                        setTimeout(pollStatus, delay);
                    } else {
                        setProgramStatus(t("userManagement.programCardErrorTimeout"));
                        setTimeout(() => setProgramStatus(""), 10000);
                    }
                };

                pollStatus();
            } else {
                notify(t("userManagement.programCardError"));
            }
        } catch (err) {
            console.error("Fehler beim Kartenprogrammieren:", err);
            notify(t("userManagement.programCardError"));
        }
    }

    return (
        <div className="admin-user-management scoped-dashboard">
            <Navbar />
            {programStatus && (
                <div className="nfc-status-message">{programStatus}</div>
            )}

            <header className="page-header">
                <h2>{t("userManagement.title")}</h2>
            </header>

            <AdminUserList
                users={users}
                t={t}
                handleEditUser={handleEditUser}
                requestDeleteUser={requestDeleteUser}
                handleProgramCard={handleProgramCard}
            />

            {editingUser ? (
                <AdminUserForm
                    t={t}
                    isEditing={true}
                    userData={editingUser}
                    setUserData={setEditingUser}
                    onSubmit={handleUpdateUser}
                    onCancel={() => setEditingUser(null)}
                />
            ) : (
                <AdminUserForm
                    t={t}
                    isEditing={false}
                    userData={newUser}
                    setUserData={setNewUser}
                    onSubmit={handleAddUser}
                />
            )}

            <DeleteConfirmModal
                visible={deleteConfirm.show}
                title={t("userManagement.deleteConfirmTitle")}
                message={t("userManagement.deleteConfirmMessage")}
                onConfirm={confirmDelete}
                onCancel={cancelDelete}
            />
        </div>
    );
};

export default AdminUserManagementPage;
