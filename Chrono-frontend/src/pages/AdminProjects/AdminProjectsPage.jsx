// src/pages/AdminProjects/AdminProjectsPage.jsx
import React, { useState, useEffect } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useProjects } from '../../context/ProjectContext';
import { useCustomers } from '../../context/CustomerContext';

// Importiere die zentralen, einheitlichen Dashboard-Styles
import '../../styles/HourlyDashboardScoped.css';
// Importiere die spezifischen Styles für diese Seite
import '../../styles/AdminProjectsPageScoped.css';

const AdminProjectsPage = () => {
  const { notify } = useNotification();
  const { t } = useTranslation();

  const { projects, createProject, updateProject, deleteProject } = useProjects();
  const { customers } = useCustomers();

  // State für das Erstellen-Formular
  const [newName, setNewName] = useState('');
  const [selectedCustomerId, setSelectedCustomerId] = useState('');

  // State für das Bearbeiten-Formular
  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');
  const [editingCustomerId, setEditingCustomerId] = useState('');

  useEffect(() => {
    if (customers.length > 0 && !selectedCustomerId) {
      setSelectedCustomerId(customers[0].id);
    }
  }, [customers, selectedCustomerId]);


  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newName.trim() || !selectedCustomerId) {
      notify(t('project.create.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
      return;
    }
    try {
      await createProject(newName, selectedCustomerId);
      setNewName('');
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
    try {
      await updateProject(editingId, editingName, editingCustomerId);
      setEditingId(null);
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

  const startEdit = (project) => {
    setEditingId(project.id);
    setEditingName(project.name);
    setEditingCustomerId(project.customer?.id || '');
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingName('');
    setEditingCustomerId('');
  };

  return (
      <>
        <Navbar />
        <div className="admin-projects-page scoped-dashboard">
          <header className="dashboard-header">
            <h1>{t('project.management.title', 'Projektverwaltung')}</h1>
          </header>

          <section className="content-section">
            <h3 className="section-title">{t('project.create.title', 'Neues Projekt anlegen')}</h3>
            <form onSubmit={handleCreate} className="create-form">
              <input
                  type="text"
                  placeholder={t('project.create.namePlaceholder', 'Name des neuen Projekts')}
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  required
              />
              <select
                  value={selectedCustomerId}
                  onChange={(e) => setSelectedCustomerId(e.target.value)}
                  required
              >
                <option value="" disabled>{t('project.create.customerPlaceholder', 'Kunde auswählen...')}</option>
                {customers.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              <button type="submit" className="button-primary">{t('create', 'Anlegen')}</button>
            </form>
          </section>

          <section className="content-section">
            <h3 className="section-title">{t('project.list.title', 'Bestehende Projekte')}</h3>
            <div className="item-list-container">
              <ul className="item-list project-list">
                {projects.map(p => (
                    <li key={p.id} className="list-item">
                      {editingId === p.id ? (
                          <form onSubmit={handleUpdate} className="edit-form">
                            <input
                                type="text"
                                value={editingName}
                                onChange={(e) => setEditingName(e.target.value)}
                                required autoFocus
                            />
                            <select
                                value={editingCustomerId}
                                onChange={(e) => setEditingCustomerId(e.target.value)}
                                required
                            >
                              <option value="" disabled>{t('project.edit.customerPlaceholder', 'Kunde auswählen...')}</option>
                              {customers.map(c => (
                                  <option key={c.id} value={c.id}>{c.name}</option>
                              ))}
                            </select>
                            <div className="form-actions">
                              <button type="submit" className="button-primary">{t('save', 'Speichern')}</button>
                              <button type="button" onClick={cancelEdit} className="button-secondary">
                                {t('cancel', 'Abbrechen')}
                              </button>
                            </div>
                          </form>
                      ) : (
                          <>
                            <div className="item-details">
                              <span className="item-name">{p.name}</span>
                              <span className="item-meta">{p.customer?.name || t('project.noCustomer', 'Kein Kunde zugewiesen')}</span>
                            </div>
                            <div className="item-actions">
                              <button onClick={() => startEdit(p)} className="button-secondary">{t('edit', 'Bearbeiten')}</button>
                              <button onClick={() => handleDelete(p.id)} className="button-danger">{t('delete', 'Löschen')}</button>
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

export default AdminProjectsPage;