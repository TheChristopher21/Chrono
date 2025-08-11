// src/pages/AdminProjects/AdminProjectsPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useProjects } from '../../context/ProjectContext';
import { useCustomers } from '../../context/CustomerContext';

import '../../styles/AdminProjectsPageScoped.css';

const asIntOrNull = (val) => {
    if (val === '' || val === null || val === undefined) return null;
    const n = Number.parseInt(val, 10);
    return Number.isNaN(n) ? null : n;
};

const AdminProjectsPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const { projects, createProject, updateProject, deleteProject } = useProjects();
    const { customers } = useCustomers();

    // Create form state
    const [newName, setNewName] = useState('');
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [newBudget, setNewBudget] = useState('');

    // Edit form state
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');
    const [editingCustomerId, setEditingCustomerId] = useState('');
    const [editingBudget, setEditingBudget] = useState('');

    // Preselect first customer on mount/update
    useEffect(() => {
        if (customers?.length > 0 && !selectedCustomerId) {
            setSelectedCustomerId(customers[0].id);
        }
    }, [customers, selectedCustomerId]);

    const resetCreateForm = () => {
        setNewName('');
        setNewBudget('');
        if (customers?.length) setSelectedCustomerId(customers[0].id ?? '');
        else setSelectedCustomerId('');
    };

    const startEdit = (project) => {
        setEditingId(project.id);
        setEditingName(project.name ?? '');
        setEditingCustomerId(project.customer?.id ?? '');
        setEditingBudget(project.budgetMinutes ?? '');
    };

    const cancelEdit = useCallback(() => {
        setEditingId(null);
        setEditingName('');
        setEditingCustomerId('');
        setEditingBudget('');
    }, []);

    // ESC cancels edit
    useEffect(() => {
        const onKey = (e) => {
            if (e.key === 'Escape' && editingId) cancelEdit();
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [editingId, cancelEdit]);

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!newName.trim() || !selectedCustomerId) {
            notify(t('project.create.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
            return;
        }
        const budget = asIntOrNull(newBudget);
        if (budget !== null && budget < 0) {
            notify(t('project.create.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        try {
            await createProject(newName.trim(), selectedCustomerId, budget);
            resetCreateForm();
            notify(t('project.create.success', 'Projekt erfolgreich angelegt!'), 'success');
        } catch (err) {
            notify(t('project.create.error', 'Fehler beim Anlegen des Projekts.'), 'error');
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        if (!editingName.trim() || !editingCustomerId) {
            notify(t('project.update.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
            return;
        }
        const budget = asIntOrNull(editingBudget);
        if (budget !== null && budget < 0) {
            notify(t('project.update.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        try {
            await updateProject(editingId, editingName.trim(), editingCustomerId, budget);
            cancelEdit();
            notify(t('project.update.success', 'Projekt erfolgreich gespeichert!'), 'success');
        } catch (err) {
            notify(t('project.update.error', 'Fehler beim Speichern des Projekts.'), 'error');
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm(t('project.delete.confirm', 'Sind Sie sicher, dass Sie dieses Projekt löschen möchten?'))) return;
        try {
            await deleteProject(id);
            notify(t('project.delete.success', 'Projekt erfolgreich gelöscht!'), 'success');
        } catch (err) {
            notify(t('project.delete.error', 'Fehler beim Löschen des Projekts.'), 'error');
        }
    };

    const hasCustomers = customers && customers.length > 0;
    const hasProjects = projects && projects.length > 0;

    return (
        <>
            <Navbar />
            <div className="admin-projects-page scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t('project.management.title', 'Projektverwaltung')}</h1>
                </header>

                {/* Create */}
                <section className="content-section">
                    <h3 className="section-title">{t('project.create.title', 'Neues Projekt anlegen')}</h3>

                    {!hasCustomers && (
                        <div className="empty-state">
                            <h4>{t('project.create.noCustomersTitle', 'Noch keine Kunden angelegt')}</h4>
                            <p>
                                {t(
                                    'project.create.noCustomersDesc',
                                    'Lege zuerst einen Kunden an, um Projekte zuordnen zu können.'
                                )}
                            </p>
                        </div>
                    )}

                    <form onSubmit={handleCreate} className="create-form" aria-label={t('project.create.form', 'Projekt anlegen')}>
                        <label className="sr-only" htmlFor="newProjectName">
                            {t('project.create.nameLabel', 'Projektname')}
                        </label>
                        <input
                            id="newProjectName"
                            type="text"
                            placeholder={t('project.create.namePlaceholder', 'Name des neuen Projekts')}
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            required
                            autoComplete="off"
                        />

                        <label className="sr-only" htmlFor="newProjectCustomer">
                            {t('project.create.customerLabel', 'Kunde')}
                        </label>
                        <select
                            id="newProjectCustomer"
                            value={selectedCustomerId}
                            onChange={(e) => setSelectedCustomerId(e.target.value)}
                            required
                            disabled={!hasCustomers}
                        >
                            <option value="" disabled>
                                {t('project.create.customerPlaceholder', 'Kunde auswählen...')}
                            </option>
                            {customers?.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {c.name}
                                </option>
                            ))}
                        </select>

                        <label className="sr-only" htmlFor="newProjectBudget">
                            {t('project.create.budgetLabel', 'Budget (Minuten)')}
                        </label>
                        <input
                            id="newProjectBudget"
                            type="number"
                            min="0"
                            inputMode="numeric"
                            placeholder={t('project.create.budgetPlaceholder', 'Budget (Minuten)')}
                            value={newBudget}
                            onChange={(e) => setNewBudget(e.target.value)}
                        />

                        <button type="submit" className="button-primary" disabled={!hasCustomers}>
                            {t('create', 'Anlegen')}
                        </button>
                    </form>
                </section>

                {/* List */}
                <section className="content-section">
                    <h3 className="section-title">{t('project.list.title', 'Bestehende Projekte')}</h3>

                    {!hasProjects ? (
                        <div className="empty-state">
                            <h4>{t('project.list.emptyTitle', 'Noch keine Projekte')}</h4>
                            <p>
                                {t(
                                    'project.list.emptyDesc',
                                    'Lege oben ein neues Projekt an. Projekte können optional ein Budget in Minuten haben.'
                                )}
                            </p>
                        </div>
                    ) : (
                        <div className="item-list-container">
                            <ul className="item-list project-list">
                                {projects.map((p) => (
                                    <li key={p.id} className="list-item">
                                        {editingId === p.id ? (
                                            <form
                                                onSubmit={handleUpdate}
                                                className="edit-form"
                                                aria-label={t('project.edit.form', 'Projekt bearbeiten')}
                                            >
                                                <label className="sr-only" htmlFor="editProjectName">
                                                    {t('project.edit.nameLabel', 'Projektname')}
                                                </label>
                                                <input
                                                    id="editProjectName"
                                                    type="text"
                                                    value={editingName}
                                                    onChange={(e) => setEditingName(e.target.value)}
                                                    required
                                                    autoFocus
                                                    autoComplete="off"
                                                />

                                                <label className="sr-only" htmlFor="editProjectCustomer">
                                                    {t('project.edit.customerLabel', 'Kunde')}
                                                </label>
                                                <select
                                                    id="editProjectCustomer"
                                                    value={editingCustomerId}
                                                    onChange={(e) => setEditingCustomerId(e.target.value)}
                                                    required
                                                >
                                                    <option value="" disabled>
                                                        {t('project.edit.customerPlaceholder', 'Kunde auswählen...')}
                                                    </option>
                                                    {customers?.map((c) => (
                                                        <option key={c.id} value={c.id}>
                                                            {c.name}
                                                        </option>
                                                    ))}
                                                </select>

                                                <label className="sr-only" htmlFor="editProjectBudget">
                                                    {t('project.edit.budgetLabel', 'Budget (Minuten)')}
                                                </label>
                                                <input
                                                    id="editProjectBudget"
                                                    type="number"
                                                    min="0"
                                                    inputMode="numeric"
                                                    placeholder={t('project.edit.budgetPlaceholder', 'Budget (Minuten)')}
                                                    value={editingBudget}
                                                    onChange={(e) => setEditingBudget(e.target.value)}
                                                />

                                                <div className="form-actions">
                                                    <button type="submit" className="button-primary">
                                                        {t('save', 'Speichern')}
                                                    </button>
                                                    <button type="button" onClick={cancelEdit} className="button-secondary">
                                                        {t('cancel', 'Abbrechen')}
                                                    </button>
                                                </div>
                                            </form>
                                        ) : (
                                            <>
                                                <div className="item-details">
                          <span className="item-name" title={p.name}>
                            {p.name}
                          </span>
                                                    <div className="item-meta">
                            <span className="item-chip">
                              {p.customer?.name || t('project.noCustomer', 'Kein Kunde zugewiesen')}
                            </span>
                                                        {p.budgetMinutes !== undefined && p.budgetMinutes !== null && (
                                                            <span className="item-chip">
                                {p.budgetMinutes} {t('project.budget.unit', 'Min')}
                              </span>
                                                        )}
                                                    </div>
                                                </div>
                                                <div className="item-actions">
                                                    <button onClick={() => startEdit(p)} className="button-secondary">
                                                        {t('edit', 'Bearbeiten')}
                                                    </button>
                                                    <button onClick={() => handleDelete(p.id)} className="button-danger">
                                                        {t('delete', 'Löschen')}
                                                    </button>
                                                </div>
                                            </>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </section>
            </div>
        </>
    );
};

export default AdminProjectsPage;
