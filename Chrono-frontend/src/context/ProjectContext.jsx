import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
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
  const isMountedRef = useRef(true);
  const notifyRef = useRef(notify);
  const translateRef = useRef(t);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    notifyRef.current = notify;
  }, [notify]);

  useEffect(() => {
    translateRef.current = t;
  }, [t]);

  const translate = useCallback((key, fallback) => translateRef.current?.(key, fallback) ?? fallback ?? key, []);

  const pushNotification = useCallback((message, type = 'info') => {
    if (!message) {
      return;
    }
    notifyRef.current?.({ message, type });
  }, []);

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

      if (isMountedRef.current) {
        setProjects(listData);
        setProjectHierarchy(hierarchyData);
      }
    } catch (err) {
      console.error('Error loading projects', err);
      pushNotification(translate('projectSaveError', 'Fehler beim Laden der Projekte'), 'error');
    }
  }, [pushNotification, translate]);

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
      pushNotification(translate('projectSaveError', 'Fehler beim Anlegen'), 'error');
      throw err;
    }
  }, [fetchProjects, pushNotification, translate]);

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
      pushNotification(translate('projectSaveError', 'Fehler beim Speichern'), 'error');
      throw err;
    }
  }, [fetchProjects, pushNotification, translate]);

  const deleteProject = useCallback(async (id) => {
    try {
      await api.delete(`/api/projects/${id}`);
      await fetchProjects();
    } catch (err) {
      console.error('Error deleting project', err);
      pushNotification(translate('projectSaveError', 'Fehler beim Löschen'), 'error');
      throw err;
    }
  }, [fetchProjects, pushNotification, translate]);

  const customerTrackingEnabled = currentUser?.customerTrackingEnabled;

  useEffect(() => {
    if (!authToken) {
      setProjects([]);
      setProjectHierarchy([]);
      return;
    }

    if (customerTrackingEnabled == null) {
      // Warten, bis der Benutzer vollständig geladen wurde, um unnötige 403-Fehler zu vermeiden.
      return;
    }

    if (customerTrackingEnabled === false) {
      setProjects([]);
      setProjectHierarchy([]);
      return;
    }

    fetchProjects();
  }, [fetchProjects, authToken, customerTrackingEnabled]);


  return (
    <ProjectContext.Provider value={{ projects, projectHierarchy, fetchProjects, createProject, updateProject, deleteProject }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProjects = () => useContext(ProjectContext);
