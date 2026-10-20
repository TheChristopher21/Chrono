import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';
import { useRefreshOnMutation } from '../hooks/useRefreshOnMutation.js';
import { hasPageAccess, hasProjectsFeature } from '../utils/pageAccess.js';

export const ProjectContext = createContext();

const logUnexpectedError = (label, error) => {
  if (![401, 403].includes(error?.response?.status)) {
    console.error(label, error);
  }
};

export const ProjectProvider = ({ children }) => {
  const [projects, setProjects] = useState([]);
  const [projectHierarchy, setProjectHierarchy] = useState([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [projectsError, setProjectsError] = useState(null);
  const { notify } = useNotification();
  const { t } = useTranslation();
  const { authToken, currentUser } = useAuth();
  const isMountedRef = useRef(true);
  const requestSequenceRef = useRef(0);
  const notifyRef = useRef(notify);
  const translateRef = useRef(t);

  useEffect(() => {
    isMountedRef.current = true;
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
    if (message) notifyRef.current?.(message, type);
  }, []);

  const companyContextKey = currentUser?.company?.id ?? currentUser?.companyId ?? null;
  const activeCompanyContextRef = useRef(companyContextKey);
  activeCompanyContextRef.current = companyContextKey;

  const projectsFeatureEnabled = hasProjectsFeature(currentUser);
  const canReadProjects = projectsFeatureEnabled && [
    'dashboard',
    'adminProjects',
    'adminProjectReport',
    'adminTasks'
  ].some((pageKey) => hasPageAccess(currentUser, pageKey, 'VIEW'));

  const fetchProjects = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    if (!canReadProjects) {
      if (isMountedRef.current) {
        setProjects([]);
        setProjectHierarchy([]);
        setProjectsError(null);
        setProjectsLoading(false);
      }
      return;
    }
    if (isMountedRef.current) {
      setProjectsLoading(true);
      setProjectsError(null);
    }
    try {
      const [listResult, hierarchyResult] = await Promise.allSettled([
        api.get('/api/projects'),
        api.get('/api/projects/hierarchy')
      ]);

      if (!isMountedRef.current || requestSequence !== requestSequenceRef.current) {
        return;
      }

      const authorizationFailed = [listResult, hierarchyResult].some(
        (result) => result.status === 'rejected' && [401, 403].includes(result.reason?.response?.status)
      );

      if (authorizationFailed) {
        setProjects([]);
        setProjectHierarchy([]);
        setProjectsError(translate('project.access.changed', 'Deine Berechtigung hat sich geändert. Bitte lade die Seite neu.'));
        return;
      }

      if (listResult.status === 'fulfilled') {
        setProjects(Array.isArray(listResult.value?.data) ? listResult.value.data : []);
      } else {
        setProjects([]);
        logUnexpectedError('Error loading project list', listResult.reason);
      }
      if (hierarchyResult.status === 'fulfilled') {
        setProjectHierarchy(Array.isArray(hierarchyResult.value?.data) ? hierarchyResult.value.data : []);
      } else {
        setProjectHierarchy([]);
        logUnexpectedError('Error loading project hierarchy', hierarchyResult.reason);
      }
      if (listResult.status === 'rejected' || hierarchyResult.status === 'rejected') {
        const message = translate('project.loadError', 'Projekte konnten nicht vollständig geladen werden.');
        setProjectsError(message);
        pushNotification(message, 'error');
      }
    } catch (err) {
      if (isMountedRef.current && requestSequence === requestSequenceRef.current) {
        logUnexpectedError('Error loading projects', err);
        setProjects([]);
        setProjectHierarchy([]);
        const message = translate('project.loadError', 'Projekte konnten nicht geladen werden.');
        setProjectsError(message);
        pushNotification(message, 'error');
      }
    } finally {
      if (isMountedRef.current && requestSequence === requestSequenceRef.current) {
        setProjectsLoading(false);
      }
    }
  }, [canReadProjects, companyContextKey, pushNotification, translate]);

  const createProject = useCallback(async ({ name, customerId, budgetMinutes, parentId, hourlyRate }) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      const payload = {
        name: name.trim(),
        customer: { id: customerId },
        budgetMinutes,
        parent: parentId ? { id: parentId } : null,
        hourlyRate
      };
      const res = await api.post('/api/projects', payload);
      if (mutationCompanyKey === activeCompanyContextRef.current) {
        await fetchProjects();
      }
      return res.data;
    } catch (err) {
      throw err;
    }
  }, [fetchProjects]);

  const updateProject = useCallback(async (id, { name, customerId, budgetMinutes, parentId, hourlyRate }) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      const payload = {
        name: name.trim(),
        customer: { id: customerId },
        budgetMinutes,
        parent: parentId ? { id: parentId } : null,
        hourlyRate
      };
      const res = await api.put(`/api/projects/${id}`, payload);
      if (mutationCompanyKey === activeCompanyContextRef.current) {
        await fetchProjects();
      }
      return res.data;
    } catch (err) {
      throw err;
    }
  }, [fetchProjects]);

  const deleteProject = useCallback(async (id) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      await api.delete(`/api/projects/${id}`);
      if (mutationCompanyKey === activeCompanyContextRef.current) {
        await fetchProjects();
      }
    } catch (err) {
      throw err;
    }
  }, [fetchProjects]);

  useRefreshOnMutation(['projects', 'customers'], fetchProjects, {
    enabled: Boolean(authToken && canReadProjects),
    refreshOnLocalMutation: false,
    refreshOnFocus: true,
    focusThrottleMs: 30_000,
  });

  useEffect(() => {
    requestSequenceRef.current += 1;
    setProjects([]);
    setProjectHierarchy([]);
    setProjectsError(null);
    setProjectsLoading(false);

    if (!authToken) {
      return;
    }

    if (!canReadProjects) {
      return;
    }

    fetchProjects();
  }, [fetchProjects, authToken, companyContextKey, canReadProjects]);


  return (
    <ProjectContext.Provider value={{
      projects,
      projectHierarchy,
      projectsLoading,
      projectsError,
      fetchProjects,
      createProject,
      updateProject,
      deleteProject
    }}>
      {children}
    </ProjectContext.Provider>
  );
};

export const useProjects = () => useContext(ProjectContext);
