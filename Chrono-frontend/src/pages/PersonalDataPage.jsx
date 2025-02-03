// src/pages/PersonalDataPage.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/PersonalDataPage.css';

const PersonalDataPage = () => {
    const { currentUser, setCurrentUser } = useAuth();
    const [personalData, setPersonalData] = useState({
        firstName: currentUser?.firstName || '',
        lastName: currentUser?.lastName || '',
        email: currentUser?.email || ''
    });

    const fetchPersonalData = async () => {
        try {
            const res = await api.get('/api/auth/me');
            setPersonalData({
                firstName: res.data.firstName,
                lastName: res.data.lastName,
                email: res.data.email
            });
        } catch (err) {
            console.error("Error fetching personal data", err);
        }
    };

    useEffect(() => {
        if (currentUser) {
            fetchPersonalData();
        }
    }, [currentUser]);

    const handlePersonalDataUpdate = async (e) => {
        e.preventDefault();
        try {
            const res = await api.put('/api/user/update', {
                username: currentUser.username,
                firstName: personalData.firstName,
                lastName: personalData.lastName,
                email: personalData.email
            });
            alert("Profile updated");
            setCurrentUser(res.data);
        } catch (err) {
            console.error("Error updating personal data", err);
        }
    };

    return (
        <div className="personal-data-page">
            <Navbar />
            <header className="page-header">
                <h2>My Profile</h2>
            </header>
            <section className="personal-data-section">
                <form onSubmit={handlePersonalDataUpdate} className="form-personal">
                    <div className="form-group">
                        <label>First Name:</label>
                        <input
                            type="text"
                            name="firstName"
                            value={personalData.firstName || ''}
                            onChange={(e) => setPersonalData({ ...personalData, firstName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Last Name:</label>
                        <input
                            type="text"
                            name="lastName"
                            value={personalData.lastName || ''}
                            onChange={(e) => setPersonalData({ ...personalData, lastName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Email:</label>
                        <input
                            type="email"
                            name="email"
                            value={personalData.email || ''}
                            onChange={(e) => setPersonalData({ ...personalData, email: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">Update Profile</button>
                </form>
            </section>
        </div>
    );
};

export default PersonalDataPage;
