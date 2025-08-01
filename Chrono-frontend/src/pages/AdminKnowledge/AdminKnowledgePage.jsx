import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import '../../styles/AdminKnowledgePageScoped.css';

const AdminKnowledgePage = () => {
  const { notify } = useNotification();
  const { t } = useTranslation();

  const [docs, setDocs] = useState([]);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [access, setAccess] = useState('ALL');

  const fetchDocs = () => {
    api.get('/api/admin/knowledge').then(res => {
      setDocs(Array.isArray(res.data) ? res.data : []);
    });
  };

  useEffect(() => {
    fetchDocs();
  }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;
    try {
      await api.post('/api/admin/knowledge', {
        title: title.trim(),
        content: content.trim(),
        accessLevel: access
      });
      setTitle('');
      setContent('');
      notify(t('knowledge.createSuccess', 'Gespeichert'), 'success');
      fetchDocs();
    } catch (err) {
      console.error('Error creating document', err);
      notify(t('knowledge.createError', 'Fehler beim Speichern'), 'error');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('knowledge.deleteConfirm', 'Wirklich löschen?'))) return;
    try {
      await api.delete(`/api/admin/knowledge/${id}`);
      fetchDocs();
    } catch (err) {
      console.error('Error deleting document', err);
      notify(t('knowledge.deleteError', 'Fehler beim Löschen'), 'error');
    }
  };

  return (
    <>
      <Navbar />
      <div className="admin-knowledge-page scoped-dashboard">
        <header className="dashboard-header">
          <h1>{t('knowledge.managementTitle', 'Wissensdokumente')}</h1>
        </header>

        <section className="content-section">
          <h3 className="section-title">{t('knowledge.createTitle', 'Neues Dokument')}</h3>
          <form onSubmit={handleCreate} className="create-form">
            <input
              type="text"
              placeholder={t('knowledge.titleLabel', 'Titel')}
              value={title}
              onChange={e => setTitle(e.target.value)}
              required
            />
            <select value={access} onChange={e => setAccess(e.target.value)}>
              <option value="ALL">{t('knowledge.accessAll', 'Alle')}</option>
              <option value="ADMIN_ONLY">{t('knowledge.accessAdmin', 'Nur Admins')}</option>
            </select>
            <button type="submit" className="button-primary">{t('save', 'Speichern')}</button>
          </form>
          <textarea
            className="content-input"
            value={content}
            onChange={e => setContent(e.target.value)}
            placeholder={t('knowledge.contentLabel', 'Inhalt')}
            rows="6"
          />
        </section>

        <section className="content-section">
          <h3 className="section-title">{t('knowledge.listTitle', 'Dokumente')}</h3>
          <div className="item-list-container">
            <ul className="item-list">
              {docs.map(d => (
                <li key={d.id} className="list-item">
                  <div className="item-details">
                    <span className="item-name">{d.title}</span>
                    <span className="item-meta">
                      {d.accessLevel === 'ADMIN_ONLY' ? t('knowledge.accessAdmin', 'Nur Admins') : t('knowledge.accessAll', 'Alle')}
                    </span>
                  </div>
                  <div className="item-actions">
                    <button onClick={() => handleDelete(d.id)} className="button-danger">{t('delete', 'Löschen')}</button>
                  </div>
                </li>
              ))}
              {docs.length === 0 && (
                <li className="list-item">{t('knowledge.noDocs', 'Keine Dokumente')}</li>
              )}
            </ul>
          </div>
        </section>
      </div>
    </>
  );
};

export default AdminKnowledgePage;
