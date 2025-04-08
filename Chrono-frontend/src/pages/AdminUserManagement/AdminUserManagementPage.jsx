// AdminUserManagementPage.jsx
import  { useState, useEffect, useContext } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation, LanguageContext } from '../../context/LanguageContext';

import api from '../../utils/api';
import '../../styles/AdminUserManagementPage.css'; // Dein CSS

// Eigene Sub-Komponenten
import AdminUserList from './AdminUserList';
import AdminUserForm from './AdminUserForm';
import DeleteConfirmModal from './DeleteConfirmModal';

// Utils / Konstante
import { STANDARD_COLORS, defaultWeeklySchedule, stringToHex16 } from './adminUserManagementUtils';

const AdminUserManagementPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { language } = useContext(LanguageContext);

    const [users, setUsers] = useState([]);
    const [editingUser, setEditingUser] = useState(null);
    const [deleteConfirm, setDeleteConfirm] = useState({ show: false, userId: null });

    // "newUser" wird nur gebraucht, wenn wir WIRKLICH Neuanlegen wollen
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
        isHourly: false
    });

    // 1) Daten laden
    useEffect(() => {
        fetchUsers();
    }, []);

    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error(t("userManagement.errorLoadingUsers"), err);
        }
    }

    // 2) Neuer User => POST
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
                expectedWorkDays: newUser.isHourly
                    ? null
                    : (newUser.expectedWorkDays ? Number(newUser.expectedWorkDays) : null),
                breakDuration: newUser.isHourly
                    ? null
                    : (newUser.breakDuration ? Number(newUser.breakDuration) : null),
                annualVacationDays: newUser.isHourly
                    ? null
                    : (newUser.annualVacationDays ? Number(newUser.annualVacationDays) : null),
                color: newUser.color,
                scheduleCycle: newUser.isHourly ? null : newUser.scheduleCycle,
                weeklySchedule: newUser.isHourly ? null : newUser.weeklySchedule,
                isHourly: newUser.isHourly
            };

            await api.post('/api/admin/users', payload);

            // Reset newUser
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
                isHourly: false
            });
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorAddingUser"), err);
            notify("Fehler beim Hinzufügen des Users.");
        }
    };

    // 3) Editieren (Daten in editingUser laden)
    const handleEditUser = (user) => {
        const { password, ...rest } = user;
        setEditingUser({
            ...rest,
            scheduleCycle: rest.scheduleCycle || 1,
            weeklySchedule: rest.weeklySchedule
                ? (Array.isArray(rest.weeklySchedule) ? rest.weeklySchedule : [rest.weeklySchedule])
                : [{ ...defaultWeeklySchedule }]
        });
    };

    // 4) Update => PUT
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
                role: editingUser.role,
                color: editingUser.color,
                expectedWorkDays: editingUser.isHourly
                    ? null
                    : (editingUser.expectedWorkDays ? Number(editingUser.expectedWorkDays) : null),
                breakDuration: editingUser.isHourly
                    ? null
                    : (editingUser.breakDuration ? Number(editingUser.breakDuration) : null),
                annualVacationDays: editingUser.isHourly
                    ? null
                    : (editingUser.annualVacationDays ? Number(editingUser.annualVacationDays) : null),
                scheduleCycle: editingUser.isHourly ? null : editingUser.scheduleCycle,
                weeklySchedule: editingUser.isHourly ? null : editingUser.weeklySchedule,
                isHourly: editingUser.isHourly
            };

            await api.put('/api/admin/users', payload);
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorUpdatingUser"), err);
            notify("Fehler beim Aktualisieren des Users.");
        }
    };

    // 5) Löschen
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
            notify("Fehler beim Löschen des Users.");
        }
    };

    // 6) Karte programmieren
    const handleProgramCard = async (user) => {
        try {
            const hexData = stringToHex16(user.username);
            const response = await fetch(process.env.APIURL+'/api/nfc/write-sector0', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ data: hexData })
            });
            const result = await response.json();
            if (result.status === 'success') {
                notify(`Karte erfolgreich beschrieben mit: ${user.username}`);
            } else {
                notify(`Fehler beim Kartenbeschreiben: ${result.message}`);
            }
        } catch (err) {
            console.error("Fehler beim Kartenbeschreiben:", err);
            notify("Fehler beim Kartenbeschreiben: " + err.message);
        }
    };

    // 7) Render
    return (
        <div className="admin-user-management">
            <Navbar />
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

            {/* Formular (Unterschied: editingUser=null => Neuanlage, ansonsten Bearbeitung) */}
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

            {/* Custom Delete Confirmation Modal */}
            <DeleteConfirmModal
                visible={deleteConfirm.show}
                onConfirm={confirmDelete}
                onCancel={cancelDelete}
            />
        </div>
    );
};

export default AdminUserManagementPage;
