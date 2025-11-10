import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';

export const ProjectContext = createContext();

export const ProjectProvider = ({ children }) => {
  const [projects, setProjects] = useState([]);
  const [projectHierarchy, setProjectHierarchy] = useState([]);
  const { notify } = useNotification();
  const { t } = useTranslation();
  const { authToken, currentUser } = useAuth();

  const fetchProjects = useCallback(async () => {
    try {
      const [listResult, hierarchyResult] = await Promise.allSettled([
        api.get('/api/projects'),
        api.get('/api/projects/hierarchy')
      ]);

      if (listResult.status === 'rejected') {
        console.error('Error loading project list', listResult.reason);
      }
      if (hierarchyResult.status === 'rejected') {
        console.error('Error loading project hierarchy', hierarchyResult.reason);
      }

      const listData =
        listResult.status === 'fulfilled' && Array.isArray(listResult.value?.data)
          ? listResult.value.data
          : [];
      const hierarchyData =
        hierarchyResult.status === 'fulfilled' && Array.isArray(hierarchyResult.value?.data)
          ? hierarchyResult.value.data
          : [];

      setProjects(listData);
      setProjectHierarchy(hierarchyData);
    } catch (err) {
      console.error('Error loading projects', err);
      notify({ message: t('projectSaveError', 'Fehler beim Laden der Projekte'), type: 'error' });
    }
  }, [notify, t]);

  const createProject = useCallback(async ({ name, customerId, budgetMinutes, parentId, hourlyRate }) => {
    try {
      const payload = {
        name: name.trim(),
        customer: { id: customerId },
        budgetMinutes,
        parent: parentId ? { id: parentId } : null,
        hourlyRate
      };
      const res = await api.post('/api/projects', payload);
      await fetchProjects();
      return res.data;
    } catch (err) {
      console.error('Error creating project', err);
      notify({ message: t('projectSaveError', 'Fehler beim Anlegen'), type: 'error' });
      throw err;
    }
  }, [fetchProjects, notify, t]);

  const updateProject = useCallback(async (id, { name, customerId, budgetMinutes, parentId, hourlyRate }) => {
    try {
      const payload = {
        name: name.trim(),
        customer: { id: customerId },
        budgetMinutes,
        parent: parentId ? { id: parentId } : null,
        hourlyRate
      };
      const res = await api.put(`/api/projects/${id}`, payload);
      await fetchProjects();
      return res.data;
    } catch (err) {
      console.error('Error updating project', err);
      notify({ message: t('projectSaveError', 'Fehler beim Speichern'), type: 'error' });
      throw err;
    }
  }, [fetchProjects, notify, t]);

  const deleteProject = useCallback(async (id) => {
    try {
      await api.delete(`/api/projects/${id}`);
      await fetchProjects();
    } catch (err) {
      console.error('Error deleting project', err);
      notify({ message: t('projectSaveError', 'Fehler beim Löschen'), type: 'error' });
      throw err;
    }
  }, [fetchProjects, notify, t]);

  useEffect(() => {
    if (authToken && currentUser?.customerTrackingEnabled) {

      fetchProjects();
    } else {
      setProjects([]);
      setProjectHierarchy([]);
    }
  }, [fetchProjects, authToken, currentUser]);


  return (
    <ProjectContext.Provider value={{ projects, projectHierarchy, fetchProjects, createProject, updateProject, deleteProject }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProjects = () => useContext(ProjectContext);
