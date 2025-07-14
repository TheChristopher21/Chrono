import React, { useState } from 'react';
import Navbar from '../../components/Navbar';
import { useProjects } from '../../context/ProjectContext';
import { useCustomers } from '../../context/CustomerContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import '../../styles/AdminCustomersPageScoped.css';

const AdminProjectsPage = () => {
  const { projects, createProject, updateProject, deleteProject } = useProjects();
  const { customers } = useCustomers();
  const { notify } = useNotification();
  const { t } = useTranslation();

  const [newName, setNewName] = useState('');
  const [newCustomerId, setNewCustomerId] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');
  const [editingCustomerId, setEditingCustomerId] = useState('');

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newName.trim() || !newCustomerId) return;
    try {
      await createProject(newName, newCustomerId);
      setNewName('');
      setNewCustomerId('');
    } catch (err) {
      console.error('Error creating project', err);
      notify('Fehler beim Anlegen', 'error');
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    try {
      await updateProject(editingId, editingName, editingCustomerId);
      setEditingId(null);
      setEditingName('');
      setEditingCustomerId('');
    } catch (err) {
      console.error('Error updating project', err);
      notify('Fehler beim Speichern', 'error');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Löschen?')) return;
    try {
      await deleteProject(id);
    } catch (err) {
      console.error('Error deleting project', err);
      notify('Fehler beim Löschen', 'error');
    }
  };

  const startEdit = (p) => {
    setEditingId(p.id);
    setEditingName(p.name);
    setEditingCustomerId(p.customer?.id || '');
  };

  return (
    <div className="admin-customers-page scoped-dashboard">
      <Navbar />
      <h2>{t('project.management.title', 'Projektverwaltung')}</h2>
      <section className="cmp-section">
        <form onSubmit={handleCreate} className="cmp-form">
          <input
            type="text"
            placeholder="Projektname"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            required
          />
          <select value={newCustomerId} onChange={(e) => setNewCustomerId(e.target.value)} required>
            <option value="">{t('customerLabel', 'Kunde')}</option>
            {customers.map(c => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          <button type="submit">{t('create', 'Anlegen')}</button>
        </form>
      </section>
      <section className="cmp-section">
        <ul className="customer-list">
          {projects.map(p => (
            <li key={p.id}>
              {editingId === p.id ? (
                <form onSubmit={handleUpdate} className="inline-form">
                  <input
                    type="text"
                    value={editingName}
                    onChange={(e) => setEditingName(e.target.value)}
                    required
                  />
                  <select value={editingCustomerId} onChange={(e) => setEditingCustomerId(e.target.value)} required>
                    {customers.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                  <button type="submit">{t('save', 'Speichern')}</button>
                  <button type="button" onClick={() => { setEditingId(null); setEditingName(''); }}>
                    {t('cancel', 'Abbruch')}
                  </button>
                </form>
              ) : (
                <>
                  <span>{p.name} ({p.customer?.name || '-'})</span>
                  <div>
                    <button onClick={() => startEdit(p)}>{t('edit', 'Bearbeiten')}</button>
                    <button onClick={() => handleDelete(p.id)}>{t('delete', 'Löschen')}</button>
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

export default AdminProjectsPage;
