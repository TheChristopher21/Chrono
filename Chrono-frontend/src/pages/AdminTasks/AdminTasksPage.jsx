// src/pages/AdminTasks/AdminTasksPage.jsx
import React, { useState, useEffect } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useProjects } from '../../context/ProjectContext';
import { useTasks } from '../../context/TaskContext';

// Importiere die zentralen, einheitlichen Dashboard-Styles
// Importiere die spezifischen Styles für diese Seite
import '../../styles/AdminTasksPageScoped.css';


const AdminTasksPage = () => {
  const { notify } = useNotification();
  const { t } = useTranslation();

  const { projects } = useProjects();
  const { tasks, fetchTasks, createTask, updateTask, deleteTask } = useTasks();

  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [newName, setNewName] = useState('');
  const [newBudget, setNewBudget] = useState('');
  const [newBillable, setNewBillable] = useState(false);

  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');
  const [editingBudget, setEditingBudget] = useState('');
  const [editingBillable, setEditingBillable] = useState(false);

  useEffect(() => {
    if (projects.length > 0 && !selectedProjectId) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  useEffect(() => {
    if (selectedProjectId) {
      fetchTasks(selectedProjectId);
    }
  }, [selectedProjectId, fetchTasks]);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newName.trim() || !selectedProjectId) {
      notify(t('task.create.validationError', 'Bitte Projekt auswählen und Namen eingeben.'), 'warning');
      return;
    }
    try {
      await createTask(selectedProjectId, newName, newBudget ? parseInt(newBudget,10) : null, newBillable);
      setNewName('');
      setNewBudget('');
      setNewBillable(false);
      notify(t('task.create.success', 'Aufgabe erfolgreich angelegt!'), 'success');
    } catch (err) {
      notify(t('task.create.error', 'Fehler beim Anlegen der Aufgabe.'), 'error');
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    if (!editingName.trim()) {
      notify(t('task.update.validationError', 'Bitte Namen eingeben.'), 'warning');
      return;
    }
    try {
      await updateTask(editingId, editingName, editingBudget ? parseInt(editingBudget,10) : null, editingBillable);
      setEditingId(null);
      notify(t('task.update.success', 'Aufgabe erfolgreich gespeichert!'), 'success');
    } catch (err) {
      notify(t('task.update.error', 'Fehler beim Speichern der Aufgabe.'), 'error');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('task.delete.confirm', 'Sind Sie sicher, dass Sie diese Aufgabe löschen möchten?'))) return;
    try {
      await deleteTask(id);
      notify(t('task.delete.success', 'Aufgabe erfolgreich gelöscht!'), 'success');
    } catch (err) {
      notify(t('task.delete.error', 'Fehler beim Löschen der Aufgabe.'), 'error');
    }
  };

  const startEdit = (task) => {
    setEditingId(task.id);
    setEditingName(task.name);
    setEditingBudget(task.budgetMinutes ?? '');
    setEditingBillable(task.billable ?? false);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingName('');
    setEditingBudget('');
    setEditingBillable(false);
  };

  return (
    <>
      <Navbar />
      <div className="admin-projects-page scoped-dashboard">
        <header className="dashboard-header">
          <h1>{t('task.management.title', 'Aufgabenverwaltung')}</h1>
        </header>

        <section className="content-section">
          <h3 className="section-title">{t('task.projectSelection', 'Projekt auswählen')}</h3>
          <select
            value={selectedProjectId}
            onChange={(e) => setSelectedProjectId(e.target.value)}
            className="project-selector"
          >
            {projects.map(p => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        </section>

        <section className="content-section">
          <h3 className="section-title">{t('task.create.title', 'Neue Aufgabe anlegen')}</h3>
          <form onSubmit={handleCreate} className="create-form">
            <input
              type="text"
              placeholder={t('task.create.namePlaceholder', 'Name der neuen Aufgabe')}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              required
            />
            <input
              type="number"
              placeholder={t('task.create.budgetPlaceholder', 'Budget (Minuten)')}
              value={newBudget}
              onChange={(e) => setNewBudget(e.target.value)}
            />
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={newBillable}
                onChange={(e) => setNewBillable(e.target.checked)}
              />
              {t('task.create.billable', 'Abrechenbar')}
            </label>
            <button type="submit" className="button-primary">{t('create', 'Anlegen')}</button>
          </form>
        </section>

        <section className="content-section">
          <h3 className="section-title">{t('task.list.title', 'Bestehende Aufgaben')}</h3>
          <div className="item-list-container">
            <ul className="item-list project-list">
              {tasks.map(tk => (
                <li key={tk.id} className="list-item">
                  {editingId === tk.id ? (
                    <form onSubmit={handleUpdate} className="edit-form">
                      <input
                        type="text"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        required
                        autoFocus
                      />
                      <input
                        type="number"
                        placeholder={t('task.edit.budgetPlaceholder', 'Budget (Minuten)')}
                        value={editingBudget}
                        onChange={(e) => setEditingBudget(e.target.value)}
                      />
                      <label className="checkbox-field">
                        <input
                          type="checkbox"
                          checked={editingBillable}
                          onChange={(e) => setEditingBillable(e.target.checked)}
                        />
                        {t('task.edit.billable', 'Abrechenbar')}
                      </label>
                      <div className="form-actions">
                        <button type="submit" className="button-primary">{t('save', 'Speichern')}</button>
                        <button type="button" onClick={cancelEdit} className="button-secondary">{t('cancel', 'Abbrechen')}</button>
                      </div>
                    </form>
                  ) : (
                    <>
                      <div className="item-details">
                        <span className="item-name">{tk.name}</span>
                        {tk.budgetMinutes !== undefined && tk.budgetMinutes !== null && (
                          <span className="item-meta">{tk.budgetMinutes} {t('task.budget.unit', 'Min')}</span>
                        )}
                        {tk.billable && <span className="item-meta">{t('task.billable', 'Abrechenbar')}</span>}
                      </div>
                      <div className="item-actions">
                        <button onClick={() => startEdit(tk)} className="button-secondary">{t('edit', 'Bearbeiten')}</button>
                        <button onClick={() => handleDelete(tk.id)} className="button-danger">{t('delete', 'Löschen')}</button>
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

export default AdminTasksPage;
