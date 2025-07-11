import React, { useState } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useCustomers } from '../../context/CustomerContext';
import '../../styles/AdminCustomersPageScoped.css';

const AdminCustomersPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const { customers, createCustomer, updateCustomer, deleteCustomer } = useCustomers();
    const [newName, setNewName] = useState('');
    // State for editing
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');


    const handleCreate = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;
        try {
            await createCustomer(newName);
            setNewName('');
        } catch (err) {
            console.error('Error creating customer', err);
            notify('Fehler beim Anlegen', 'error');
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        try {
            await updateCustomer(editingId, editingName);
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
            await deleteCustomer(id);
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