// src/pages/Register.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../context/NotificationContext'; // Neu

const Register = () => {
    const navigate = useNavigate();
    const { registerUser, error } = useAuth();
    const [form, setForm] = useState({
        username: '',
        password: '',
        firstName: '',
        lastName: '',
        email: ''
    });

    // Neu
    const { notify } = useNotification();

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        const result = await registerUser(
            form.username,
            form.password,
            form.firstName,
            form.lastName,
            form.email
        );
        if (result.success) {
            notify('Registered successfully!');
            navigate('/dashboard');
        } else {
            console.log('Registration error detail:', result.message);
            notify('Registration failed!');
        }
    };

    return (
        <div className="register-container">
            <h2>Register</h2>
            {error && <p style={{ color: 'red' }}>{error}</p>}
            <form onSubmit={handleSubmit}>
                <div>
                    <label>Username:
                        <input
                            name="username"
                            value={form.username}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <div>
                    <label>Password:
                        <input
                            name="password"
                            type="password"
                            value={form.password}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <div>
                    <label>First Name:
                        <input
                            name="firstName"
                            value={form.firstName}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <div>
                    <label>Last Name:
                        <input
                            name="lastName"
                            value={form.lastName}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <div>
                    <label>Email:
                        <input
                            name="email"
                            type="email"
                            value={form.email}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <button type="submit">Register</button>
            </form>
        </div>
    );
};

export default Register;
