import React, { useState, useEffect } from 'react';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/AdminUserManagementPage.css';

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
        breakDuration: ''
    });
    const [editingUser, setEditingUser] = useState(null);

    // Benutzer laden
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

    // Neuen Benutzer anlegen
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
                breakDuration: newUser.breakDuration ? Number(newUser.breakDuration) : null
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
                breakDuration: ''
            });
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Anlegen des Benutzers", err);
        }
    };

    // Bearbeiten
    const handleEditUser = (user) => {
        setEditingUser({
            ...user,
            role: user.roles && user.roles.length > 0 ? user.roles[0] : 'ROLE_USER'
        });
    };

    // Update
    const handleUpdateUser = async (e) => {
        e.preventDefault();
        try {
            await api.put('/api/admin/users', {
                id: editingUser.id,
                username: editingUser.username,
                firstName: editingUser.firstName,
                lastName: editingUser.lastName,
                email: editingUser.email,
                password: editingUser.password,
                roles: [{ roleName: editingUser.role }],
                expectedWorkDays: editingUser.expectedWorkDays ? Number(editingUser.expectedWorkDays) : null,
                dailyWorkHours: editingUser.dailyWorkHours ? Number(editingUser.dailyWorkHours) : null,
                breakDuration: editingUser.breakDuration ? Number(editingUser.breakDuration) : null
            });
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Updaten des Benutzers", err);
        }
    };

    // Löschen
    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error("Fehler beim Löschen des Benutzers", err);
        }
    };

    // Karte programmieren via Electron IPC
    const handleProgramCard = async (user) => {
        if (!window.electronAPI?.invoke) {
            alert("Electron-API nicht verfügbar. Läuft die App in Electron?");
            return;
        }
        // Beispiel: Verwende den Benutzernamen als Kennung
        const userId = user.username;
        const res = await window.electronAPI.invoke('write-card', userId);
        if (res.success) {
            alert("Karte erfolgreich beschrieben mit: " + userId);
        } else {
            alert("Fehler beim Kartenbeschreiben: " + res.error);
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
                            <tr key={user.id}>
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
                            <button type="submit">Anlegen</button>
                        </form>
                    </>
                )}
            </section>
        </div>
    );
};

export default AdminUserManagementPage;
