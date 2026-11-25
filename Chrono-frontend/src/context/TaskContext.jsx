import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';
import { useRefreshOnMutation } from '../hooks/useRefreshOnMutation.js';
import { hasPageAccess, hasProjectsFeature } from '../utils/pageAccess.js';

export const TaskContext = createContext();

const logUnexpectedError = (label, error) => {
  if (![401, 403].includes(error?.response?.status)) {
    console.error(label, error);
  }
};

export const TaskProvider = ({ children }) => {
  const [tasks, setTasks] = useState([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [tasksError, setTasksError] = useState(null);
  const { notify } = useNotification();
  const { t } = useTranslation();
  const { authToken, currentUser } = useAuth();
  const activeProjectIdRef = useRef(null);
  const requestSequenceRef = useRef(0);
  const companyContextKey = currentUser?.company?.id ?? currentUser?.companyId ?? null;
  const activeCompanyContextRef = useRef(companyContextKey);
  activeCompanyContextRef.current = companyContextKey;

  const projectsFeatureEnabled = hasProjectsFeature(currentUser);
  const canReadTasks = projectsFeatureEnabled && ['dashboard', 'adminProjects', 'adminTasks']
    .some((pageKey) => hasPageAccess(currentUser, pageKey, 'VIEW'));

  const fetchTasks = useCallback(async (projectId) => {
    const requestSequence = ++requestSequenceRef.current;
    activeProjectIdRef.current = projectId || null;
    if (!projectId || !canReadTasks) {
      setTasks([]);
      setTasksError(null);
      setTasksLoading(false);
      return;
    }
    setTasks([]);
    setTasksError(null);
    setTasksLoading(true);
    try {
      const res = await api.get('/api/tasks', { params: { projectId } });
      if (requestSequence === requestSequenceRef.current) {
        setTasks(Array.isArray(res.data) ? res.data : []);
      }
    } catch (err) {
      if (requestSequence === requestSequenceRef.current) {
        logUnexpectedError('Error loading tasks', err);
        const authorizationFailed = [401, 403].includes(err?.response?.status);
        if (!authorizationFailed) {
          notify(t('task.loadError', 'Fehler beim Laden der Aufgaben'), 'error');
        }
        setTasksError(authorizationFailed
          ? t('project.access.changed', 'Deine Berechtigung hat sich geändert. Bitte lade die Seite neu.')
          : t('task.loadError', 'Fehler beim Laden der Aufgaben'));
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setTasksLoading(false);
      }
    }
  }, [canReadTasks, companyContextKey, notify, t]);

  const refreshActiveTasks = useCallback(() => {
    if (!activeProjectIdRef.current) return Promise.resolve();
    return fetchTasks(activeProjectIdRef.current);
  }, [fetchTasks]);

  useRefreshOnMutation(['tasks', 'projects'], refreshActiveTasks, {
    enabled: Boolean(authToken && canReadTasks && activeProjectIdRef.current),
    refreshOnLocalMutation: false,
    refreshOnFocus: true,
    focusThrottleMs: 30_000,
  });

  const createTask = useCallback(async (projectId, name, budgetMinutes, billable) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      const res = await api.post('/api/tasks', {
        name: name.trim(),
        project: { id: projectId },
        budgetMinutes,
        billable
      });
      if (
        mutationCompanyKey === activeCompanyContextRef.current &&
        String(activeProjectIdRef.current) === String(projectId)
      ) {
        setTasks(prev => [...prev, res.data]);
      }
      return res.data;
    } catch (err) {
      throw err;
    }
  }, []);

  const updateTask = useCallback(async (id, name, budgetMinutes, billable) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      const res = await api.put(`/api/tasks/${id}`, {
        name: name.trim(),
        budgetMinutes,
        billable
      });
      if (mutationCompanyKey === activeCompanyContextRef.current) {
        setTasks(prev => prev.map(t => t.id === id ? res.data : t));
      }
      return res.data;
    } catch (err) {
      throw err;
    }
  }, []);

  const deleteTask = useCallback(async (id) => {
    const mutationCompanyKey = activeCompanyContextRef.current;
    try {
      await api.delete(`/api/tasks/${id}`);
      if (mutationCompanyKey === activeCompanyContextRef.current) {
        setTasks(prev => prev.filter(t => t.id !== id));
      }
    } catch (err) {
      throw err;
    }
  }, []);

  useEffect(() => {
    requestSequenceRef.current += 1;
    activeProjectIdRef.current = null;
    setTasks([]);
    setTasksError(null);
    setTasksLoading(false);
  }, [authToken, companyContextKey, canReadTasks]);

  return (
    <TaskContext.Provider value={{
      tasks,
      tasksLoading,
      tasksError,
      fetchTasks,
      createTask,
      updateTask,
      deleteTask
    }}>
      {children}
    </TaskContext.Provider>
  );
};

export const useTasks = () => useContext(TaskContext);
