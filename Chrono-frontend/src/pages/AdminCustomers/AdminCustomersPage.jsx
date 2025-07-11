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
    // State for editing
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');

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
            console.error('Error creating customer', err);
            notify('Fehler beim Anlegen', 'error');
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        try {
            const res = await api.put(`/api/customers/${editingId}`, { name: editingName.trim() });
            setCustomers(prev => prev.map(c => c.id === editingId ? res.data : c));
            setEditingId(null);
            setEditingName('');
        } catch (err) {
            console.error('Error updating customer', err);
            notify('Fehler beim Speichern', 'error');
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm('Löschen?')) return;
        try {
            await api.delete(`/api/customers/${id}`);
            setCustomers(prev => prev.filter(c => c.id !== id));
        } catch (err) {
            console.error('Error deleting customer', err);
            notify('Fehler beim Löschen', 'error');
        }
    };

    const startEdit = (c) => {
        setEditingId(c.id);
        setEditingName(c.name);
    };

    return (
        <div className="admin-customers-page scoped-dashboard">
            <Navbar />
            <h2>{t('customer.management.title', 'Kundenverwaltung')}</h2>
            <section className="cmp-section">
                <form onSubmit={handleCreate} className="cmp-form">
                    <input
                        type="text"
                        placeholder="Kundenname"
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        required
                    />
                    <button type="submit">{t('create', 'Anlegen')}</button>
                </form>
            </section>
            <section className="cmp-section">
                <ul className="customer-list">
                    {customers.map(c => (
                        <li key={c.id}>
                            {editingId === c.id ? (
                                <form onSubmit={handleUpdate} className="inline-form">
                                    <input
                                        type="text"
                                        value={editingName}
                                        onChange={(e) => setEditingName(e.target.value)}
                                        required
                                    />
                                    <button type="submit">{t('save', 'Speichern')}</button>
                                    <button type="button" onClick={() => { setEditingId(null); setEditingName(''); }}>{t('cancel', 'Abbruch')}</button>
                                </form>
                            ) : (
                                <>
                                    <span>{c.name}</span>
                                    <div>
                                      <button onClick={() => startEdit(c)}>{t('edit', 'Bearbeiten')}</button>
                                      <button onClick={() => handleDelete(c.id)}>{t('delete', 'Löschen')}</button>
                                    </div>
                                </>
                            )}
                        </li>
                    ))}
                </ul>
            </section>
        </div>
    );
};

export default AdminCustomersPage;