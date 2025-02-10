import React, { useState, useEffect } from 'react';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/AdminUserManagementPage.css';

const STANDARD_COLORS = [
    "#FF5733", "#33FF57", "#3357FF", "#F0FF33",
    "#FF33F0", "#33FFF0", "#FF8C00", "#8A2BE2",
    "#FF1493", "#00BFFF", "#ADFF2F", "#FFD700",
    "#FF4500", "#00FA9A", "#7B68EE", "#FF6347"
];

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
        dailyWorkHours: '',
        breakDuration: '',
        color: STANDARD_COLORS[0]
    });
    const [editingUser, setEditingUser] = useState(null);
    const [showColorPicker, setShowColorPicker] = useState(false);

    const fetchUsers = async () => {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Benutzer", err);
        }
    };

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
                dailyWorkHours: newUser.dailyWorkHours ? Number(newUser.dailyWorkHours) : null,
                breakDuration: newUser.breakDuration ? Number(newUser.breakDuration) : null,
                color: newUser.color
            });
            setNewUser({
                username: '',
                firstName: '',
                lastName: '',
                email: '',
                password: '',
                role: 'ROLE_USER',
                expectedWorkDays: '',
                dailyWorkHours: '',
                breakDuration: '',
                color: STANDARD_COLORS[0]
            });
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Anlegen des Benutzers", err);
        }
    };

    const handleEditUser = (user) => {
        setEditingUser({
            ...user,
            role: user.roles && user.roles.length > 0 ? user.roles[0] : 'ROLE_USER'
        });
    };

    const handleUpdateUser = async (e) => {
        e.preventDefault();
        try {
            await api.put('/api/admin/users', {
                id: editingUser.id,
                username: editingUser.username,
                firstName: editingUser.firstName,
                lastName: editingUser.lastName,
                email: editingUser.email,
                // Passwort aktualisieren: Nur wenn ein neues Passwort eingegeben wurde, andernfalls nicht ändern.
                password: editingUser.newPassword ? editingUser.newPassword : editingUser.password,
                roles: [{ roleName: editingUser.role }],
                expectedWorkDays: editingUser.expectedWorkDays ? Number(editingUser.expectedWorkDays) : null,
                dailyWorkHours: editingUser.dailyWorkHours ? Number(editingUser.dailyWorkHours) : null,
                breakDuration: editingUser.breakDuration ? Number(editingUser.breakDuration) : null,
                color: editingUser.color
            });
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Updaten des Benutzers", err);
        }
    };

    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Löschen des Benutzers", err);
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
                alert(`Karte erfolgreich beschrieben mit: ${user.username}`);
            } else {
                alert(`Fehler beim Kartenbeschreiben: ${result.message}`);
            }
        } catch (err) {
            console.error("Fehler beim Kartenbeschreiben:", err);
            alert("Fehler beim Kartenbeschreiben: " + err.message);
        }
    };

    return (
        <div className="admin-user-management">
            <Navbar />
            <header className="page-header">
                <h2>Benutzerverwaltung</h2>
            </header>
            <section className="user-list">
                {users.length === 0 ? (
                    <p>Keine Benutzer gefunden.</p>
                ) : (
                    <table>
                        <thead>
                        <tr>
                            <th>Benutzername</th>
                            <th>Name</th>
                            <th>Email</th>
                            <th>Rolle</th>
                            <th>Expected Work Days</th>
                            <th>Daily Work Hours</th>
                            <th>Break Duration</th>
                            <th>Aktionen</th>
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
                                <td>{user.dailyWorkHours ?? '-'}</td>
                                <td>{user.breakDuration ?? '-'}</td>
                                <td>
                                    <button onClick={() => handleEditUser(user)}>Bearbeiten</button>
                                    <button onClick={() => handleDeleteUser(user.id)}>Löschen</button>
                                    <button onClick={() => handleProgramCard(user)}>Karte programmieren</button>
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
                        <h3>Benutzer bearbeiten</h3>
                        <form onSubmit={handleUpdateUser}>
                            <input
                                type="text"
                                placeholder="Benutzername"
                                value={editingUser.username || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Vorname"
                                value={editingUser.firstName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Nachname"
                                value={editingUser.lastName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder="E-Mail"
                                value={editingUser.email || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, email: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>Rolle:</label>
                                <select
                                    value={editingUser.role || 'ROLE_USER'}
                                    onChange={(e) => setEditingUser({ ...editingUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Aktuelles Passwort:</label>
                                <input
                                    type="password"
                                    placeholder="Aktuelles Passwort"
                                    value={editingUser.currentPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, currentPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Neues Passwort:</label>
                                <input
                                    type="password"
                                    placeholder="Neues Passwort"
                                    value={editingUser.newPassword || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, newPassword: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Expected Work Days:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 5"
                                    value={editingUser.expectedWorkDays || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Daily Work Hours:</label>
                                <input
                                    type="number"
                                    step="0.1"
                                    placeholder="z.B. 8.0"
                                    value={editingUser.dailyWorkHours || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, dailyWorkHours: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Break Duration (min):</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 30"
                                    value={editingUser.breakDuration || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Farbe:</label>
                                <button type="button" onClick={() => setShowColorPicker(!showColorPicker)}>
                                    Farbe auswählen
                                </button>
                                {showColorPicker && (
                                    <input
                                        type="color"
                                        value={editingUser.color || '#FF5733'}
                                        onChange={(e) => setEditingUser({ ...editingUser, color: e.target.value })}
                                    />
                                )}
                            </div>
                            <button type="submit">Speichern</button>
                            <button type="button" onClick={() => setEditingUser(null)}>Abbrechen</button>
                        </form>
                    </>
                ) : (
                    <>
                        <h3>Neuen Benutzer anlegen</h3>
                        <form onSubmit={handleAddUser}>
                            <input
                                type="text"
                                placeholder="Benutzername"
                                value={newUser.username}
                                onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Vorname"
                                value={newUser.firstName}
                                onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Nachname"
                                value={newUser.lastName}
                                onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder="E-Mail"
                                value={newUser.email}
                                onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                                required
                            />
                            <input
                                type="password"
                                placeholder="Passwort"
                                value={newUser.password}
                                onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>Rolle:</label>
                                <select
                                    value={newUser.role}
                                    onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Expected Work Days:</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 5"
                                    value={newUser.expectedWorkDays}
                                    onChange={(e) => setNewUser({ ...newUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Daily Work Hours:</label>
                                <input
                                    type="number"
                                    step="0.1"
                                    placeholder="z.B. 8.0"
                                    value={newUser.dailyWorkHours}
                                    onChange={(e) => setNewUser({ ...newUser, dailyWorkHours: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Break Duration (min):</label>
                                <input
                                    type="number"
                                    placeholder="z.B. 30"
                                    value={newUser.breakDuration}
                                    onChange={(e) => setNewUser({ ...newUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Farbe:</label>
                                <button type="button" onClick={() => setShowColorPicker(!showColorPicker)}>
                                    Farbe auswählen
                                </button>
                                {showColorPicker && (
                                    <input
                                        type="color"
                                        value={newUser.color || '#FF5733'}
                                        onChange={(e) => setNewUser({ ...newUser, color: e.target.value })}
                                    />
                                )}
                            </div>
                            <button type="submit">Anlegen</button>
                        </form>
                    </>
                )}
            </section>
        </div>
    );
};

export default AdminUserManagementPage;
