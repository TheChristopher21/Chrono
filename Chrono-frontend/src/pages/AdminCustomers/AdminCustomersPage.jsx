// src/pages/AdminCustomers/AdminCustomersPage.jsx
import React, { useState } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useCustomers } from '../../context/CustomerContext';

// Importiere die zentralen, einheitlichen Dashboard-Styles
import '../../styles/HourlyDashboardScoped.css';
// Importiere die spezifischen Styles für diese Seite
import '../../styles/AdminProjectsPageScoped.css';

const AdminCustomersPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const { customers, createCustomer, updateCustomer, deleteCustomer } = useCustomers();
    const [newName, setNewName] = useState('');
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;
        try {
            await createCustomer(newName);
            setNewName('');
            notify(t('customer.createSuccess', 'Kunde erfolgreich angelegt!'), 'success');
        } catch (err) {
            console.error('Error creating customer', err);
            notify(t('customer.createError', 'Fehler beim Anlegen des Kunden.'), 'error');
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        try {
            await updateCustomer(editingId, editingName);
            setEditingId(null);
            setEditingName('');
            notify(t('customer.updateSuccess', 'Kunde erfolgreich gespeichert!'), 'success');
        } catch (err) {
            console.error('Error updating customer', err);
            notify(t('customer.updateError', 'Fehler beim Speichern des Kunden.'), 'error');
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm(t('customer.deleteConfirm', 'Sind Sie sicher, dass Sie diesen Kunden löschen möchten?'))) return;
        try {
            await deleteCustomer(id);
            notify(t('customer.deleteSuccess', 'Kunde erfolgreich gelöscht!'), 'success');
        } catch (err) {
            console.error('Error deleting customer', err);
            notify(t('customer.deleteError', 'Fehler beim Löschen des Kunden.'), 'error');
        }
    };

    const startEdit = (c) => {
        setEditingId(c.id);
        setEditingName(c.name);
    };

    const cancelEdit = () => {
        setEditingId(null);
        setEditingName('');
    };

    return (
        <>
            <Navbar />
            <div className="admin-customers-page scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t('customer.management.title', 'Kundenverwaltung')}</h1>
                </header>

                <section className="content-section">
                    <h3 className="section-title">{t('customer.create.title', 'Neuen Kunden anlegen')}</h3>
                    <form onSubmit={handleCreate} className="create-form">
                        <input
                            type="text"
                            placeholder={t('customer.create.placeholder', 'Name des neuen Kunden')}
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            required
                        />
                        {/* Leeres div für Grid-Layout Konsistenz */}
                        <div></div>
                        <button type="submit" className="button-primary">{t('create', 'Anlegen')}</button>
                    </form>
                </section>

                <section className="content-section">
                    <h3 className="section-title">{t('customer.list.title', 'Bestehende Kunden')}</h3>
                    <div className="item-list-container">
                        <ul className="item-list customer-list">
                            {customers.map(c => (
                                <li key={c.id} className="list-item">
                                    {editingId === c.id ? (
                                        <form onSubmit={handleUpdate} className="edit-form">
                                            <input
                                                type="text"
                                                value={editingName}
                                                onChange={(e) => setEditingName(e.target.value)}
                                                required
                                                autoFocus
                                            />
                                            {/* Leeres div für Grid-Layout Konsistenz */}
                                            <div></div>
                                            <div className="form-actions">
                                                <button type="submit" className="button-primary">{t('save', 'Speichern')}</button>
                                                <button type="button" onClick={cancelEdit} className="button-secondary">
                                                    {t('cancel', 'Abbrechen')}
                                                </button>
                                            </div>
                                        </form>
                                    ) : (
                                        <>
                                            <span className="item-name">{c.name}</span>
                                            <div className="item-actions">
                                                <button onClick={() => startEdit(c)} className="button-secondary">{t('edit', 'Bearbeiten')}</button>
                                                <button onClick={() => handleDelete(c.id)} className="button-danger">{t('delete', 'Löschen')}</button>
                                            </div>
                                        </>
                                    )}
                                </li>
                            ))}
                        </ul>
                    </div>
                </section>
            </div>
        </>
    );
};

export default AdminCustomersPage;