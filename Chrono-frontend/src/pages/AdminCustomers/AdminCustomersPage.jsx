import React, { useState, useEffect } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import '../../styles/AdminCustomersPageScoped.css';

const AdminCustomersPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [customers, setCustomers] = useState([]);
    const [newName, setNewName] = useState('');

    const fetchCustomers = async () => {
        try {
            const res = await api.get('/api/customers');
            setCustomers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading customers', err);
            notify('Fehler beim Laden der Kunden', 'error');
        }
    };

    useEffect(() => { fetchCustomers(); }, []);

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;
        try {
            const res = await api.post('/api/customers', { name: newName.trim() });
            setCustomers(prev => [...prev, res.data]);
            setNewName('');
        } catch (err) {
            notify('Fehler beim Anlegen', 'error');
        }
    };

    return (
        <div className="admin-customers-page scoped-dashboard">
            <Navbar />
            <h2>{t('customer.management.title', 'Kundenverwaltung')}</h2>
            <section className="cmp-section">
                <form onSubmit={handleCreate} className="cmp-form">
                    <input
                        type="text"
                        placeholder="Name"
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        required
                    />
                    <button type="submit">{t('create','Anlegen')}</button>
                </form>
            </section>
            <section className="cmp-section">
                <ul className="customer-list">
                    {customers.map(c => (
                        <li key={c.id}>{c.name}</li>
                    ))}
                </ul>
            </section>
        </div>
    );
};

export default AdminCustomersPage;
