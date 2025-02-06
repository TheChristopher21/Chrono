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

    const fetchUsers = async () => {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error("Error fetching users", err);
        }
    };

    useEffect(() => {
        fetchUsers();
    }, []);

    const handleAddUser = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/admin/users', newUser);
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
            console.error("Error adding user", err);
        }
    };

    const handleEditUser = (user) => {
        setEditingUser(user);
    };

    const handleUpdateUser = async (e) => {
        e.preventDefault();
        try {
            await api.put('/api/admin/users', editingUser);
            setEditingUser(null);
            fetchUsers();
        } catch (err) {
            console.error("Error updating user", err);
        }
    };

    const handleDeleteUser = async (id) => {
        try {
            await api.delete(`/api/admin/users/${id}`);
            fetchUsers();
        } catch (err) {
            console.error("Error deleting user", err);
        }
    };

    return (
        <div className="admin-user-management">
            <Navbar />
            <header className="page-header">
                <h2>User Management</h2>
            </header>
            <section className="user-list">
                {users.length === 0 ? (
                    <p>No users found.</p>
                ) : (
                    <table>
                        <thead>
                        <tr>
                            <th>Username</th>
                            <th>Name</th>
                            <th>Email</th>
                            <th>Role</th>
                            <th>Expected Work Days</th>
                            <th>Daily Work Hours</th>
                            <th>Break Duration</th>
                            <th>Actions</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map(user => (
                            <tr key={user.id}>
                                <td>{user.username}</td>
                                <td>{user.firstName} {user.lastName}</td>
                                <td>{user.email}</td>
                                <td>{user.roles && user.roles.length > 0 ? user.roles[0] : 'ROLE_USER'}</td>
                                <td>{user.expectedWorkDays || '-'}</td>
                                <td>{user.dailyWorkHours || '-'}</td>
                                <td>{user.breakDuration || '-'}</td>
                                <td>
                                    <button onClick={() => handleEditUser(user)}>Edit</button>
                                    <button onClick={() => handleDeleteUser(user.id)}>Delete</button>
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
                        <h3>Edit User</h3>
                        <form onSubmit={handleUpdateUser}>
                            <input
                                type="text"
                                placeholder="Username"
                                value={editingUser.username || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="First Name"
                                value={editingUser.firstName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Last Name"
                                value={editingUser.lastName || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder="Email"
                                value={editingUser.email || ''}
                                onChange={(e) => setEditingUser({ ...editingUser, email: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>Role:</label>
                                <select
                                    value={editingUser.roles && editingUser.roles.length > 0 ? editingUser.roles[0] : 'ROLE_USER'}
                                    onChange={(e) => setEditingUser({ ...editingUser, roles: [e.target.value] })}
                                >
                                    <option value="ROLE_USER">User</option>
                                    <option value="ROLE_ADMIN">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Expected Work Days:</label>
                                <input
                                    type="number"
                                    placeholder="Expected Work Days"
                                    value={editingUser.expectedWorkDays || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Daily Work Hours:</label>
                                <input
                                    type="number"
                                    step="0.1"
                                    placeholder="Daily Work Hours"
                                    value={editingUser.dailyWorkHours || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, dailyWorkHours: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Break Duration (min):</label>
                                <input
                                    type="number"
                                    placeholder="Break Duration"
                                    value={editingUser.breakDuration || ''}
                                    onChange={(e) => setEditingUser({ ...editingUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <button type="submit">Update User</button>
                            <button type="button" onClick={() => setEditingUser(null)}>Cancel</button>
                        </form>
                    </>
                ) : (
                    <>
                        <h3>Add New User</h3>
                        <form onSubmit={handleAddUser}>
                            <input
                                type="text"
                                placeholder="Username"
                                value={newUser.username || ''}
                                onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="First Name"
                                value={newUser.firstName || ''}
                                onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })}
                                required
                            />
                            <input
                                type="text"
                                placeholder="Last Name"
                                value={newUser.lastName || ''}
                                onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })}
                                required
                            />
                            <input
                                type="email"
                                placeholder="Email"
                                value={newUser.email || ''}
                                onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                                required
                            />
                            <input
                                type="password"
                                placeholder="Password"
                                value={newUser.password || ''}
                                onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                                required
                            />
                            <div className="form-group">
                                <label>Role:</label>
                                <select
                                    value={newUser.role || 'ROLE_USER'}
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
                                    placeholder="Expected Work Days"
                                    value={newUser.expectedWorkDays || ''}
                                    onChange={(e) => setNewUser({ ...newUser, expectedWorkDays: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Daily Work Hours:</label>
                                <input
                                    type="number"
                                    step="0.1"
                                    placeholder="Daily Work Hours"
                                    value={newUser.dailyWorkHours || ''}
                                    onChange={(e) => setNewUser({ ...newUser, dailyWorkHours: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>Break Duration (min):</label>
                                <input
                                    type="number"
                                    placeholder="Break Duration"
                                    value={newUser.breakDuration || ''}
                                    onChange={(e) => setNewUser({ ...newUser, breakDuration: e.target.value })}
                                />
                            </div>
                            <button type="submit">Add User</button>
                        </form>
                    </>
                )}
            </section>
        </div>
    );
};

export default AdminUserManagementPage;
