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
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { language, setLanguage } = useContext(LanguageContext);

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
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        isHourly: false
    });
    const [editingUser, setEditingUser] = useState(null);
    const [showColorPicker, setShowColorPicker] = useState(false);

    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error(t("userManagement.errorLoadingUsers"), err);
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
                expectedWorkDays: newUser.isHourly ? null : (newUser.expectedWorkDays ? Number(newUser.expectedWorkDays) : null),
                breakDuration: newUser.isHourly ? null : (newUser.breakDuration ? Number(newUser.breakDuration) : null),
                color: newUser.color,
                scheduleCycle: newUser.isHourly ? null : newUser.scheduleCycle,
                weeklySchedule: newUser.isHourly ? null : newUser.weeklySchedule,
                isHourly: newUser.isHourly
            });
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
                weeklySchedule: [{ ...defaultWeeklySchedule }],
                isHourly: false
            });
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorAddingUser"), err);
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
                expectedWorkDays: editingUser.isHourly ? null : (editingUser.expectedWorkDays ? Number(editingUser.expectedWorkDays) : null),
                breakDuration: editingUser.isHourly ? null : (editingUser.breakDuration ? Number(editingUser.breakDuration) : null),
                color: editingUser.color,
                role: editingUser.role,
                scheduleCycle: editingUser.isHourly ? null : editingUser.scheduleCycle,
                weeklySchedule: editingUser.isHourly ? null : editingUser.weeklySchedule,
                isHourly: editingUser.isHourly
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
            console.error(t("userManagement.errorUpdatingUser"), err);
        }
    };

    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error(t("userManagement.errorDeletingUser"), err);
        }
    };

    const handleProgramCard = async (user) => {
        try {
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
            const response = await fetch('http://localhost:8080/api/nfc/write-sector0', {
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

    return (
        <div className="admin-user-management">
            <Navbar />
            <header className="page-header">
                <h2>{t("userManagement.title")}</h2>
            </header>
            <section className="user-list">
                {users.length === 0 ? (
                    <p>{t("userManagement.noUsers")}</p>
                ) : (
                    <table>
                        <thead>
                        <tr>
                            <th>{t("userManagement.username")}</th>
                            <th>{t("userManagement.firstName")} {t("userManagement.lastName")}</th>
                            <th>{t("userManagement.email")}</th>
                            <th>{t("userManagement.role")}</th>
                            <th>{t("userManagement.expectedWorkDays")}</th>
                            <th>{t("userManagement.breakDuration")}</th>
                            <th>{t("userManagement.table.actions")}</th>
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
                                    <button onClick={() => handleEditUser(user)}>{t("userManagement.table.edit")}</button>
                                    <button onClick={() => handleDeleteUser(user.id)}>{t("userManagement.table.delete")}</button>
                                    <button onClick={() => handleProgramCard(user)}>{t("userManagement.table.programCard")}</button>
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
                        <h3>{t("userManagement.editUser")}</h3>
                        <form onSubmit={handleUpdateUser}>
                            <input
                                type="text"
                                placeholder={t("userManagement.username")}
                                value={editingUser.username || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("userManagement.firstName")}
                                value={editingUser.firstName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("userManagement.lastName")}
                                value={editingUser.lastName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder={t("userManagement.email")}
                                value={editingUser.email || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, email: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>{t("userManagement.role")}:</label>
                                <select
                                    value={editingUser.role || 'ROLE_USER'}
                                    onChange={(e) => setEditingUser({ ...editingUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Farbe:</label>
                                <div className="color-picker">
                                    {STANDARD_COLORS.map((color, index) => (
                                        <div key={index}
                                             className={`color-swatch ${editingUser.color === color ? 'selected' : ''}`}
                                             style={{
                                                 backgroundColor: color,
                                                 width: "20px",
                                                 height: "20px",
                                                 display: "inline-block",
                                                 margin: "0 5px",
                                                 cursor: "pointer"
                                             }}
                                             onClick={() => setEditingUser({ ...editingUser, color })}
                                        ></div>
                                    ))}
                                </div>
                            </div>
                            <div className="form-group">
                                <label>Stundenbasiert:</label>
                                <input
                                    type="checkbox"
                                    checked={editingUser.isHourly}
                                    onChange={(e) => setEditingUser({ ...editingUser, isHourly: e.target.checked })}
                                />
                            </div>
                            {!editingUser.isHourly && (
                                <>
                                    <div className="form-group">
                                        <label>{t("userManagement.expectedWorkDays")}:</label>
                                        <input
                                            type="number"
                                            placeholder="z.B. 5"
                                            value={editingUser.expectedWorkDays || ''}
                                            onChange={(e) => setEditingUser({ ...editingUser, expectedWorkDays: e.target.value })}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label>{t("userManagement.breakDuration")}:</label>
                                        <input
                                            type="number"
                                            placeholder="z.B. 30"
                                            value={editingUser.breakDuration || ''}
                                            onChange={(e) => setEditingUser({ ...editingUser, breakDuration: e.target.value })}
                                        />
                                    </div>
                                    <h4>{t("userManagement.scheduleConfig")}</h4>
                                    <div className="form-group">
                                        <label>{t("userManagement.cycleLength")}</label>
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
                                                <h5>{t("userManagement.week")} {index + 1}</h5>
                                                {['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map(dayKey => (
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
                                </>
                            )}
                            <div className="form-group">
                                <label>{t("userManagement.currentPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("userManagement.currentPassword")}
                                    value={editingUser.currentPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, currentPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("userManagement.newPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("userManagement.newPassword")}
                                    value={editingUser.newPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, newPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t("userManagement.userPassword")}:</label>
                                <input
                                    type="password"
                                    placeholder={t("userManagement.userPassword")}
                                    value={editingUser.userPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, userPassword: e.target.value })}
                                    required
                                />
                            </div>
                            <button type="submit">{t("userManagement.button.save")}</button>
                            <button type="button" onClick={() => setEditingUser(null)}>{t("userManagement.button.cancel")}</button>
                        </form>
                    </>
                ) : (
                    <>
                        <h3>{t("userManagement.newUser")}</h3>
                        <form onSubmit={handleAddUser}>
                            <input
                                type="text"
                                placeholder={t("userManagement.username")}
                                value={newUser.username}
                                onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("userManagement.firstName")}
                                value={newUser.firstName}
                                onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder={t("userManagement.lastName")}
                                value={newUser.lastName}
                                onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder={t("userManagement.email")}
                                value={newUser.email}
                                onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                                required
                            />
                            <input
                                type="password"
                                placeholder={t("userManagement.password")}
                                value={newUser.password}
                                onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>{t("userManagement.role")}:</label>
                                <select
                                    value={newUser.role}
                                    onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Farbe:</label>
                                <div className="color-picker">
                                    {STANDARD_COLORS.map((color, index) => (
                                        <div key={index}
                                             className={`color-swatch ${newUser.color === color ? 'selected' : ''}`}
                                             style={{
                                                 backgroundColor: color,
                                                 width: "20px",
                                                 height: "20px",
                                                 display: "inline-block",
                                                 margin: "0 5px",
                                                 cursor: "pointer"
                                             }}
                                             onClick={() => setNewUser({ ...newUser, color })}
                                        ></div>
                                    ))}
                                </div>
                            </div>
                            <div className="form-group">
                                <label>Stundenbasiert:</label>
                                <input
                                    type="checkbox"
                                    checked={newUser.isHourly}
                                    onChange={(e) => setNewUser({ ...newUser, isHourly: e.target.checked })}
                                />
                            </div>
                            {!newUser.isHourly && (
                                <>
                                    <div className="form-group">
                                        <label>{t("userManagement.expectedWorkDays")}:</label>
                                        <input
                                            type="number"
                                            placeholder="z.B. 5"
                                            value={newUser.expectedWorkDays}
                                            onChange={(e) => setNewUser({ ...newUser, expectedWorkDays: e.target.value })}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label>{t("userManagement.breakDuration")}:</label>
                                        <input
                                            type="number"
                                            placeholder="z.B. 30"
                                            value={newUser.breakDuration}
                                            onChange={(e) => setNewUser({ ...newUser, breakDuration: e.target.value })}
                                        />
                                    </div>
                                    <h4>{t("userManagement.scheduleConfig")}</h4>
                                    <div className="form-group">
                                        <label>{t("userManagement.cycleLength")}</label>
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
                                                <h5>{t("userManagement.week")} {index + 1}</h5>
                                                {['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map(dayKey => (
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
                                </>
                            )}
                            <button type="submit">{t("userManagement.button.save")}</button>
                        </form>
                    </>
                )}
            </section>
        </div>
    );
};

export default AdminUserManagementPage;
