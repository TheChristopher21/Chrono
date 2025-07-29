import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';

export const ProjectContext = createContext();

export const ProjectProvider = ({ children }) => {
  const [projects, setProjects] = useState([]);
  const { notify } = useNotification();
  const { t } = useTranslation();
  const { authToken, currentUser } = useAuth();

  const fetchProjects = useCallback(async () => {
    try {
      const res = await api.get('/api/projects');
      setProjects(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error('Error loading projects', err);
      notify(t('projectSaveError', 'Fehler beim Laden der Projekte'), 'error');
    }
  }, [notify, t]);

  const createProject = useCallback(async (name, customerId) => {
    try {
      const res = await api.post('/api/projects', { name: name.trim(), customer: { id: customerId } });
      setProjects(prev => [...prev, res.data]);
      return res.data;
    } catch (err) {
      console.error('Error creating project', err);
      notify(t('projectSaveError', 'Fehler beim Anlegen'), 'error');
      throw err;
    }
  }, [notify, t]);

  const updateProject = useCallback(async (id, name, customerId) => {
    try {
      const res = await api.put(`/api/projects/${id}`, { name: name.trim(), customer: { id: customerId } });
      setProjects(prev => prev.map(p => p.id === id ? res.data : p));
      return res.data;
    } catch (err) {
      console.error('Error updating project', err);
      notify(t('projectSaveError', 'Fehler beim Speichern'), 'error');
      throw err;
    }
  }, [notify, t]);

  const deleteProject = useCallback(async (id) => {
    try {
      await api.delete(`/api/projects/${id}`);
      setProjects(prev => prev.filter(p => p.id !== id));
    } catch (err) {
      console.error('Error deleting project', err);
      notify(t('projectSaveError', 'Fehler beim Löschen'), 'error');
      throw err;
    }
  }, [notify, t]);

  useEffect(() => {
    if (authToken && currentUser?.customerTrackingEnabled) {

      fetchProjects();
    } else {
      setProjects([]);
    }
  }, [fetchProjects, authToken, currentUser]);


  return (
    <ProjectContext.Provider value={{ projects, fetchProjects, createProject, updateProject, deleteProject }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProjects = () => useContext(ProjectContext);
