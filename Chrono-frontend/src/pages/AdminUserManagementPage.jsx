// src/pages/AdminUserManagementPage.jsx
import React, { useState, useEffect, useContext } from 'react';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/AdminUserManagementPage.css';
import { useNotification } from '../context/NotificationContext';
import { useTranslation, LanguageContext } from '../context/LanguageContext';

const STANDARD_COLORS = [
    "#FF5733", "#33FF57", "#3357FF", "#F0FF33",
    "#FF33F0", "#33FFF0", "#FF8C00", "#8A2BE2",
    "#FF1493", "#00BFFF", "#ADFF2F", "#FFD700",
    "#FF4500", "#00FA9A", "#7B68EE", "#FF6347"
];

const defaultWeeklySchedule = {
    monday: 8,
    tuesday: 8,
    wednesday: 8,
    thursday: 8,
    friday: 8,
    saturday: 0,
    sunday: 0
};

const AdminUserManagementPage = () => {
    const [users, setUsers] = useState([]);
    const [newUser, setNewUser] = useState({
        username: '',
        firstName: '',
        lastName: '',
        email: '',
        password: '',
        role: 'ROLE_USER',
        expectedWorkDays: '',
        breakDuration: '',
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }]
    });
    const [editingUser, setEditingUser] = useState(null);
    const [showColorPicker, setShowColorPicker] = useState(false);

    const { notify } = useNotification();
    const { t } = useTranslation();
    const { language, setLanguage } = useContext(LanguageContext); // Auch hier falls du später dynamische Anpassungen möchtest

    // Benutzer laden
    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error(t("adminUserManagement.errorLoadingUsers"), err);
        }
    }

    useEffect(() => {
        fetchUsers();
    }, []);

    const handleAddUser = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/admin/users', {
                username: newUser.username,
                firstName: newUser.firstName,
                lastName: newUser.lastName,
                email: newUser.email,
                password: newUser.password,
                roles: [{ roleName: newUser.role }],
                expectedWorkDays: newUser.expectedWorkDays ? Number(newUser.expectedWorkDays) : null,
                breakDuration: newUser.breakDuration ? Number(newUser.breakDuration) : null,
                color: newUser.color,
                scheduleCycle: newUser.scheduleCycle,
                weeklySchedule: newUser.weeklySchedule
            });
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
                color: STANDARD_COLORS[0],
                scheduleCycle: 1,
                weeklySchedule: [{ ...defaultWeeklySchedule }]
            });
            fetchUsers();
        } catch (err) {
            console.error(t("adminUserManagement.errorAddingUser"), err);
        }
    };

    const handleEditUser = (user) => {
        setEditingUser({
            ...user,
            currentPassword: '',
            newPassword: '',
            scheduleCycle: user.scheduleCycle || 1,
            weeklySchedule: Array.isArray(user.weeklySchedule)
                ? user.weeklySchedule
                : [{ ...defaultWeeklySchedule }]
        });
    };

    const handleUpdateUser = async (e) => {
        e.preventDefault();
        try {
            const payload = {
                id: editingUser.id,
                username: editingUser.username,
                firstName: editingUser.firstName,
                lastName: editingUser.lastName,
                email: editingUser.email,
                expectedWorkDays: editingUser.expectedWorkDays ? Number(editingUser.expectedWorkDays) : null,
                breakDuration: editingUser.breakDuration ? Number(editingUser.breakDuration) : null,
                color: editingUser.color,
                role: editingUser.role,
                scheduleCycle: editingUser.scheduleCycle,
                weeklySchedule: editingUser.weeklySchedule
            };

            const queryParams = {
                currentPassword: editingUser.currentPassword
            };
            if (editingUser.newPassword && editingUser.newPassword.trim() !== "") {
                queryParams.newPassword = editingUser.newPassword;
            }

            await api.put('/api/admin/users', payload, { params: queryParams });
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error(t("adminUserManagement.errorUpdatingUser"), err);
        }
    };

    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error(t("adminUserManagement.errorDeletingUser"), err);
        }
    };

    const handleProgramCard = async (user) => {
        try {
            const blockNumber = 4;
            const stringToHex16 = (text) => {
                let asciiData = text.slice(0, 16);
                asciiData = asciiData.padEnd(16, '\0');
                let hexResult = '';
                for (let i = 0; i < asciiData.length; i++) {
                    const code = asciiData.charCodeAt(i);
                    hexResult += code.toString(16).padStart(2, '0');
                }
                return hexResult.toUpperCase();
            };
            const hexData = stringToHex16(user.username);
            const response = await fetch('http://localhost:8080/api/nfc/write', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ block: blockNumber, data: hexData })
            });
            const result = await response.json();
            if (result.status === 'success') {
                notify(t("adminUserManagement.programCardSuccess") + ": " + user.username);
            } else {
                notify(t("adminUserManagement.programCardError") + ": " + result.message);
            }
        } catch (err) {
            console.error(t("adminUserManagement.programCardError"), err);
            notify(t("adminUserManagement.programCardError") + ": " + err.message);
        }
    };

    return (
        <div className="admin-user-management">
            <Navbar />
            <header className="page-header">
                <h2>{t("adminUserManagement.title")}</h2>
            </header>
            <section className="user-list">
                {users.length === 0 ? (
                    <p>{t("adminUserManagement.noUsers")}</p>
                ) : (
                    <table>
                        <thead>
                        <tr>
                            <th>{t("adminUserManagement.username")}</th>
                            <th>{t("adminUserManagement.firstName")} {t("adminUserManagement.lastName")}</th>
                            <th>{t("adminUserManagement.email")}</th>
                            <th>{t("adminUserManagement.role")}</th>
                            <th>{t("adminUserManagement.expectedWorkDays")}</th>
                            <th>{t("adminUserManagement.breakDuration")}</th>
                            <th>{t("adminUserManagement.table.actions")}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map(user => (
                            <tr key={user.id} style={{ backgroundColor: user.color || 'transparent' }}>
                                <td>{user.username}</td>
                                <td>{user.firstName} {user.lastName}</td>
                                <td>{user.email}</td>
                                <td>{user.roles && user.roles.length > 0 ? user.roles[0] : 'ROLE_USER'}</td>
                                <td>{user.expectedWorkDays ?? '-'}</td>
                                <td>{user.breakDuration ?? '-'}</td>
                                <td>
                                    <button onClick={() => handleEditUser(user)}>{t("adminUserManagement.table.edit")}</button>
                                    <button onClick={() => handleDeleteUser(user.id)}>{t("adminUserManagement.table.delete")}</button>
                                    <button onClick={() => handleProgramCard(user)}>{t("adminUserManagement.table.programCard")}</button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </section>
            <section className="user-form">
                {editingUser ? (
                    <>
                        <h3>{t("adminUserManagement.editUser")}</h3>
                        <form onSubmit={handleUpdateUser}>
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.username")}
                                value={editingUser.username || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.firstName")}
                                value={editingUser.firstName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.lastName")}
                                value={editingUser.lastName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder={t("adminUserManagement.email")}
                                value={editingUser.email || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, email: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>{t("adminUserManagement.role")}:</label>
                                <select
                                    value={editingUser.role || 'ROLE_USER'}
                                    onChange={(e) => setEditingUser({ ...editingUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.currentPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("adminUserManagement.currentPassword")}
                                    value={editingUser.currentPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, currentPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.newPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("adminUserManagement.newPassword")}
                                    value={editingUser.newPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, newPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.expectedWorkDays")}:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 5"
                                    value={editingUser.expectedWorkDays || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.breakDuration")}:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 30"
                                    value={editingUser.breakDuration || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <h4>{t("adminUserManagement.scheduleConfig")}</h4>
                            <div className="form-group">
                                <label>{t("adminUserManagement.cycleLength")}</label>
                                <input
                                    type="number"
                                    min="1"
                                    value={editingUser.scheduleCycle}
                                    onChange={(e) => {
                                        const newCycle = Number(e.target.value);
                                        let newSchedule = editingUser.weeklySchedule || [];
                                        if (newCycle > newSchedule.length) {
                                            const diff = newCycle - newSchedule.length;
                                            for (let i = 0; i < diff; i++) {
                                                newSchedule.push({ ...defaultWeeklySchedule });
                                            }
                                        } else {
                                            newSchedule = newSchedule.slice(0, newCycle);
                                        }
                                        setEditingUser({
                                            ...editingUser,
                                            scheduleCycle: newCycle,
                                            weeklySchedule: newSchedule
                                        });
                                    }}
                                />
                            </div>
                            <div className="weekly-schedule">
                                {editingUser.weeklySchedule.map((week, index) => (
                                    <div key={index} className="schedule-week">
                                        <h5>{t("adminUserManagement.week")} {index + 1}</h5>
                                        {['monday','tuesday','wednesday','thursday','friday','saturday','sunday'].map(dayKey => (
                                            <div key={dayKey}>
                                                <label>{dayKey.charAt(0).toUpperCase() + dayKey.slice(1)}:</label>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    max="24"
                                                    value={week[dayKey]}
                                                    onChange={(e) => {
                                                        const newVal = Number(e.target.value);
                                                        const newSchedule = editingUser.weeklySchedule.map((w, i) =>
                                                            i === index ? { ...w, [dayKey]: newVal } : w
                                                        );
                                                        setEditingUser({ ...editingUser, weeklySchedule: newSchedule });
                                                    }}
                                                />
                                            </div>
                                        ))}
                                    </div>
                                ))}
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.color")}:</label>
                                <button type="button" onClick={() => setShowColorPicker(!showColorPicker)}>
                                    {t("adminUserManagement.chooseColor")}
                                </button>
                                {showColorPicker && (
                                    <input
                                        type="color"
                                        value={editingUser.color || '#FF5733'}
                                        onChange={(e) => setEditingUser({ ...editingUser, color: e.target.value })}
                                    />
                                )}
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.userPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("adminUserManagement.userPassword")}
                                    value={editingUser.userPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, userPassword: e.target.value })}
                                    required
                                />
                            </div>
                            <button type="submit">{t("adminUserManagement.button.save")}</button>
                            <button type="button" onClick={() => setEditingUser(null)}>{t("adminUserManagement.button.cancel")}</button>
                        </form>
                    </>
                ) : (
                    <>
                        <h3>{t("adminUserManagement.newUser")}</h3>
                        <form onSubmit={handleAddUser}>
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.username")}
                                value={newUser.username}
                                onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.firstName")}
                                value={newUser.firstName}
                                onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("adminUserManagement.lastName")}
                                value={newUser.lastName}
                                onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder={t("adminUserManagement.email")}
                                value={newUser.email}
                                onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                                required
                            />
                            <input
                                type="password"
                                placeholder={t("adminUserManagement.password")}
                                value={newUser.password}
                                onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>{t("adminUserManagement.role")}:</label>
                                <select
                                    value={newUser.role}
                                    onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.expectedWorkDays")}:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 5"
                                    value={newUser.expectedWorkDays}
                                    onChange={(e) => setNewUser({ ...newUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.breakDuration")}:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 30"
                                    value={newUser.breakDuration}
                                    onChange={(e) => setNewUser({ ...newUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <h4>{t("adminUserManagement.scheduleConfig")}</h4>
                            <div className="form-group">
                                <label>{t("adminUserManagement.cycleLength")}</label>
                                <input
                                    type="number"
                                    min="1"
                                    value={newUser.scheduleCycle}
                                    onChange={(e) => {
                                        const newCycle = Number(e.target.value);
                                        let newSchedule = newUser.weeklySchedule || [];
                                        if (newCycle > newSchedule.length) {
                                            const diff = newCycle - newSchedule.length;
                                            for (let i = 0; i < diff; i++) {
                                                newSchedule.push({ ...defaultWeeklySchedule });
                                            }
                                        } else {
                                            newSchedule = newSchedule.slice(0, newCycle);
                                        }
                                        setNewUser({ ...newUser, scheduleCycle: newCycle, weeklySchedule: newSchedule });
                                    }}
                                />
                            </div>
                            <div className="weekly-schedule">
                                {newUser.weeklySchedule.map((week, index) => (
                                    <div key={index} className="schedule-week">
                                        <h5>{t("adminUserManagement.week")} {index + 1}</h5>
                                        {['monday','tuesday','wednesday','thursday','friday','saturday','sunday'].map(dayKey => (
                                            <div key={dayKey}>
                                                <label>{dayKey.charAt(0).toUpperCase() + dayKey.slice(1)}:</label>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    max="24"
                                                    value={week[dayKey]}
                                                    onChange={(e) => {
                                                        const newVal = Number(e.target.value);
                                                        const newSchedule = newUser.weeklySchedule.map((w, i) =>
                                                            i === index ? { ...w, [dayKey]: newVal } : w
                                                        );
                                                        setNewUser({ ...newUser, weeklySchedule: newSchedule });
                                                    }}
                                                />
                                            </div>
                                        ))}
                                    </div>
                                ))}
                            </div>
                            <div className="form-group">
                                <label>{t("adminUserManagement.color")}:</label>
                                <button type="button" onClick={() => setShowColorPicker(!showColorPicker)}>
                                    {t("adminUserManagement.chooseColor")}
                                </button>
                                {showColorPicker && (
                                    <input
                                        type="color"
                                        value={newUser.color || '#FF5733'}
                                        onChange={(e) => setNewUser({ ...newUser, color: e.target.value })}
                                    />
                                )}
                            </div>
                            <button type="submit">{t("adminUserManagement.button.save")}</button>
                        </form>
                    </>
                )}
            </section>
        </div>
    );
};

export default AdminUserManagementPage;
