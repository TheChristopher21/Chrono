import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';

export const TaskContext = createContext();

export const TaskProvider = ({ children }) => {
  const [tasks, setTasks] = useState([]);
  const { notify } = useNotification();
  const { t } = useTranslation();
  const { authToken, currentUser } = useAuth();

  const fetchTasks = useCallback(async (projectId) => {
    if (!projectId) {
      setTasks([]);
      return;
    }
    try {
      const res = await api.get('/api/tasks', { params: { projectId } });
      setTasks(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error('Error loading tasks', err);
      notify(t('task.loadError', 'Fehler beim Laden der Aufgaben'), 'error');
    }
  }, [notify, t]);

  const createTask = useCallback(async (projectId, name, budgetMinutes, billable) => {
    try {
      const res = await api.post('/api/tasks', {
        name: name.trim(),
        project: { id: projectId },
        budgetMinutes,
        billable
      });
      setTasks(prev => [...prev, res.data]);
      return res.data;
    } catch (err) {
      console.error('Error creating task', err);
      notify(t('task.saveError', 'Fehler beim Anlegen'), 'error');
      throw err;
    }
  }, [notify, t]);

  const updateTask = useCallback(async (id, name, budgetMinutes, billable) => {
    try {
      const res = await api.put(`/api/tasks/${id}`, {
        name: name.trim(),
        budgetMinutes,
        billable
      });
      setTasks(prev => prev.map(t => t.id === id ? res.data : t));
      return res.data;
    } catch (err) {
      console.error('Error updating task', err);
      notify(t('task.saveError', 'Fehler beim Speichern'), 'error');
      throw err;
    }
  }, [notify, t]);

  const deleteTask = useCallback(async (id) => {
    try {
      await api.delete(`/api/tasks/${id}`);
      setTasks(prev => prev.filter(t => t.id !== id));
    } catch (err) {
      console.error('Error deleting task', err);
      notify(t('task.saveError', 'Fehler beim Löschen'), 'error');
      throw err;
    }
  }, [notify, t]);

  useEffect(() => {
    if (!(authToken && currentUser?.customerTrackingEnabled)) {
      setTasks([]);
    }
  }, [authToken, currentUser]);

  return (
    <TaskContext.Provider value={{ tasks, fetchTasks, createTask, updateTask, deleteTask }}>
      {children}
    </TaskContext.Provider>
  );
};

export const useTasks = () => useContext(TaskContext);
