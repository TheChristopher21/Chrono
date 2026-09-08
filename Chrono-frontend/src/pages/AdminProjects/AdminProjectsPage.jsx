// src/pages/AdminProjects/AdminProjectsPage.jsx
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAuth } from '../../context/AuthContext';
import { useProjects } from '../../context/ProjectContext';
import { useCustomers } from '../../context/CustomerContext';
import { useTasks } from '../../context/TaskContext';
import { hasPageAccess, hasProjectsFeature } from '../../utils/pageAccess';
import api from '../../utils/api';

import '../../styles/AdminProjectsPageScoped.css';

const asIntOrNull = (val) => {
    if (val === '' || val === null || val === undefined) return null;
    const n = Number.parseInt(val, 10);
    return Number.isNaN(n) ? null : n;
};

const asDecimalOrNull = (val) => {
    if (val === '' || val === null || val === undefined) return null;
    const normalized = String(val).replace(',', '.');
    const parsed = Number.parseFloat(normalized);
    return Number.isNaN(parsed) ? null : parsed;
};

const coerceId = (value) => {
    if (value === '' || value === null || value === undefined) {
        return value;
    }
    const numeric = Number(value);
    return Number.isNaN(numeric) ? value : numeric;
};

const formatDateInput = (date) => {
    const d = new Date(date);
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
};

const isValidDateRange = (start, end) => Boolean(start && end && start <= end);

const getResponseStatus = (error) => error?.response?.status ?? null;

const logUnexpectedError = (label, error) => {
    if (![401, 403].includes(getResponseStatus(error))) {
        console.error(label, error);
    }
};

const getLoadErrorMessage = (error, t, fallbackKey, fallbackMessage) => {
    const status = getResponseStatus(error);
    if (status === 401 || status === 403) {
        return t(
            'project.access.changed',
            'Deine Berechtigung hat sich geändert. Bitte lade die Seite neu oder wende dich an eine Administratorin bzw. einen Administrator.'
        );
    }
    return t(fallbackKey, fallbackMessage);
};

const InlineState = ({ type = 'info', message, onRetry, retryLabel }) => (
    <div
        className={`inline-state inline-state--${type}`}
        role={type === 'error' ? 'alert' : 'status'}
        aria-live={type === 'error' ? 'assertive' : 'polite'}
    >
        <span>{message}</span>
        {onRetry && (
            <button type="button" className="button-secondary" onClick={onRetry}>
                {retryLabel}
            </button>
        )}
    </div>
);

const collectDescendantIdsFromNode = (node) => {
    if (!node?.children) return [];
    return node.children.reduce((acc, child) => {
        acc.push(child.id);
        acc.push(...collectDescendantIdsFromNode(child));
        return acc;
    }, []);
};

const flattenHierarchy = (nodes, depth = 0, acc = []) => {
    nodes?.forEach((node) => {
        acc.push({ id: node.id, name: `${'— '.repeat(depth)}${node.name}` });
        if (node.children?.length) {
            flattenHierarchy(node.children, depth + 1, acc);
        }
    });
    return acc;
};

const ProjectTree = ({ nodes, analyticsMap, t }) => {
    if (!nodes || nodes.length === 0) {
        return (
            <p className="tree-empty">
                {t('project.hierarchy.empty', 'Keine Hierarchie vorhanden.')}
            </p>
        );
    }

    const renderNodes = (items) => (
        <ul className="project-tree">
            {items.map((node) => {
                const metrics = analyticsMap.get(node.id);
                const utilizationPct = metrics?.utilization != null
                    ? Math.max(0, Math.round(metrics.utilization * 100))
                    : null;
                const utilizationBarPct = utilizationPct != null ? Math.min(100, utilizationPct) : null;
                const totalHours = metrics?.totalMinutes != null
                    ? (metrics.totalMinutes / 60).toFixed(1)
                    : null;
                const budgetHours = node.budgetMinutes != null
                    ? (node.budgetMinutes / 60).toFixed(1)
                    : null;

                return (
                    <li key={node.id}>
                        <div className="tree-row">
                            <div className="tree-info">
                                <span className="tree-name">{node.name}</span>
                                {node.customerName && (
                                    <span className="tree-customer">{node.customerName}</span>
                                )}
                            </div>
                            <div className="tree-metrics">
                                {budgetHours !== null && (
                                    <span
                                        className="metric-chip"
                                        title={t('project.hierarchy.metrics.budgetTitle', 'Budget Stunden')}
                                    >
                                        {t('project.hierarchy.metrics.budgetLabel', 'Budget')}: {budgetHours}
                                    </span>
                                )}
                                {totalHours !== null && (
                                    <span
                                        className="metric-chip"
                                        title={t('project.hierarchy.metrics.actualTitle', 'Gebuchte Stunden')}
                                    >
                                        {t('project.hierarchy.metrics.actualLabel', 'Ist')}: {totalHours}
                                    </span>
                                )}
                                {utilizationPct != null && (
                                    <div
                                        className={`metric-progress${utilizationPct > 100 ? ' is-over-budget' : ''}`}
                                        title={t('project.hierarchy.metrics.utilizationTitle', 'Auslastung')}
                                        role="progressbar"
                                        aria-valuemin={0}
                                        aria-valuemax={100}
                                        aria-valuenow={utilizationBarPct}
                                        aria-valuetext={`${utilizationPct}%`}
                                    >
                                        <div
                                            className="metric-progress-bar"
                                            style={{ '--progress': (utilizationBarPct / 100).toString() }}
                                        />
                                        <span>{utilizationPct}%</span>
                                    </div>
                                )}
                            </div>
                        </div>
                        {node.children?.length ? renderNodes(node.children) : null}
                    </li>
                );
            })}
        </ul>
    );

    return renderNodes(nodes);
};

const TAB_KEYS = ['projects', 'customers', 'tasks'];

const AdminProjectsPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const companyContextKey = currentUser?.company?.id ?? currentUser?.companyId ?? null;
    const previousCompanyContextRef = useRef(companyContextKey);

    const {
        projects,
        projectHierarchy,
        projectsLoading = false,
        projectsError = null,
        fetchProjects,
        createProject,
        updateProject,
        deleteProject
    } = useProjects();
    const {
        customers,
        customersLoading = false,
        customersError = null,
        fetchCustomers,
        createCustomer,
        updateCustomer,
        deleteCustomer
    } = useCustomers();
    const {
        tasks,
        tasksLoading = false,
        tasksError = null,
        fetchTasks,
        createTask,
        updateTask,
        deleteTask
    } = useTasks();

    const projectsFeatureEnabled = hasProjectsFeature(currentUser);
    const canViewProjects = projectsFeatureEnabled && hasPageAccess(currentUser, 'adminProjects', 'VIEW');
    const canManageProjects = projectsFeatureEnabled && hasPageAccess(currentUser, 'adminProjects', 'MANAGE');
    const canViewCustomers = projectsFeatureEnabled && (
        canViewProjects || hasPageAccess(currentUser, 'adminCustomers', 'VIEW')
    );
    const canManageCustomers = projectsFeatureEnabled && (
        canManageProjects || hasPageAccess(currentUser, 'adminCustomers', 'MANAGE')
    );
    const canViewTasks = projectsFeatureEnabled && (
        canViewProjects || hasPageAccess(currentUser, 'adminTasks', 'VIEW')
    );
    const canManageTasks = projectsFeatureEnabled && (
        canManageProjects || hasPageAccess(currentUser, 'adminTasks', 'MANAGE')
    );

    const allowedTabs = useMemo(() => [
        canViewProjects && 'projects',
        canViewCustomers && 'customers',
        canViewTasks && 'tasks'
    ].filter(Boolean), [canViewProjects, canViewCustomers, canViewTasks]);

    const [searchParams, setSearchParams] = useSearchParams();
    const [activeTab, setActiveTab] = useState(() => {
        const param = searchParams.get('tab');
        return TAB_KEYS.includes(param) ? param : null;
    });

    useEffect(() => {
        const param = searchParams.get('tab');
        const requested = TAB_KEYS.includes(param) ? param : 'projects';
        const normalized = allowedTabs.includes(requested) ? requested : (allowedTabs[0] ?? null);
        if (normalized !== activeTab) {
            setActiveTab(normalized);
        }
        const canonicalParam = normalized === 'projects' ? null : normalized;
        if ((param ?? null) !== canonicalParam) {
            setSearchParams((previous) => {
                const params = new URLSearchParams(previous);
                if (canonicalParam) params.set('tab', canonicalParam);
                else params.delete('tab');
                return params;
            }, { replace: true });
        }
    }, [searchParams, activeTab, allowedTabs, setSearchParams]);

    const handleTabChange = useCallback((tabKey) => {
        const normalized = allowedTabs.includes(tabKey) ? tabKey : (allowedTabs[0] ?? null);
        if (!normalized) return;
        setActiveTab(normalized);
        setSearchParams((prev) => {
            const params = new URLSearchParams(prev);
            if (normalized === 'projects') {
                params.delete('tab');
            } else {
                params.set('tab', normalized);
            }
            return params;
        });
    }, [allowedTabs, setSearchParams]);

    // Create form state
    const [newName, setNewName] = useState('');
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [newBudget, setNewBudget] = useState('');
    const [newParentId, setNewParentId] = useState('');
    const [newHourlyRate, setNewHourlyRate] = useState('');
    const newProjectNameRef = useRef(null);

    // Edit form state
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');
    const [editingProjectCustomerId, setEditingProjectCustomerId] = useState('');
    const [editingBudget, setEditingBudget] = useState('');
    const [editingParentId, setEditingParentId] = useState('');
    const [editingHourlyRate, setEditingHourlyRate] = useState('');

    const today = useMemo(() => new Date(), []);
    const defaultStart = useMemo(() => {
        const d = new Date();
        d.setDate(d.getDate() - 30);
        return d;
    }, []);

    const [analyticsStart, setAnalyticsStart] = useState(formatDateInput(defaultStart));
    const [analyticsEnd, setAnalyticsEnd] = useState(formatDateInput(today));
    const [analytics, setAnalytics] = useState([]);
    const [analyticsLoading, setAnalyticsLoading] = useState(false);
    const [analyticsError, setAnalyticsError] = useState(null);
    const analyticsInitialLoadRef = useRef(false);
    const analyticsRequestRef = useRef(0);

    const [projectSaving, setProjectSaving] = useState(false);
    const [projectDeletingId, setProjectDeletingId] = useState(null);
    const [customerSaving, setCustomerSaving] = useState(false);
    const [customerDeletingId, setCustomerDeletingId] = useState(null);
    const [taskSaving, setTaskSaving] = useState(false);
    const [taskDeletingId, setTaskDeletingId] = useState(null);

    // Customer management state
    const [newCustomerName, setNewCustomerName] = useState('');
    const [editingCustomerId, setEditingCustomerId] = useState(null);
    const [editingCustomerName, setEditingCustomerName] = useState('');

    // Task management state
    const [selectedTaskProjectId, setSelectedTaskProjectId] = useState('');
    const [newTaskName, setNewTaskName] = useState('');
    const [newTaskBudget, setNewTaskBudget] = useState('');
    const [newTaskBillable, setNewTaskBillable] = useState(false);
    const [editingTaskId, setEditingTaskId] = useState(null);
    const [editingTaskName, setEditingTaskName] = useState('');
    const [editingTaskBudget, setEditingTaskBudget] = useState('');
    const [editingTaskBillable, setEditingTaskBillable] = useState(false);

    const hierarchyIndex = useMemo(() => {
        const map = new Map();
        const traverse = (node) => {
            if (!node) return;
            map.set(node.id, node);
            node.children?.forEach(traverse);
        };
        projectHierarchy?.forEach(traverse);
        return map;
    }, [projectHierarchy]);

    const analyticsMap = useMemo(() => {
        const map = new Map();
        const traverse = (node) => {
            if (!node) return;
            map.set(node.id, node);
            node.children?.forEach(traverse);
        };
        analytics?.forEach(traverse);
        return map;
    }, [analytics]);

    const parentOptions = useMemo(() => flattenHierarchy(projectHierarchy), [projectHierarchy]);

    const customerList = useMemo(() => customers ?? [], [customers]);
    const projectList = useMemo(() => projects ?? [], [projects]);
    const taskList = useMemo(() => tasks ?? [], [tasks]);

    const sortedProjects = useMemo(() => {
        return [...projectList].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    }, [projectList]);

    const blockedParentIds = useMemo(() => {
        if (!editingId) return new Set();
        const node = hierarchyIndex.get(editingId);
        const ids = new Set();
        if (editingId) ids.add(Number(editingId));
        if (node) {
            collectDescendantIdsFromNode(node).forEach((id) => ids.add(id));
        }
        return ids;
    }, [hierarchyIndex, editingId]);

    // Keep the selected customer inside the currently loaded company data.
    useEffect(() => {
        if (customerList.length === 0) {
            if (selectedCustomerId !== '') setSelectedCustomerId('');
            return;
        }
        const selectionExists = customerList.some(
            (customer) => String(customer.id) === String(selectedCustomerId)
        );
        if (!selectionExists) {
            setSelectedCustomerId(customerList[0].id ?? '');
        }
    }, [customerList, selectedCustomerId]);

    useEffect(() => {
        if (!projectList.length) {
            if (selectedTaskProjectId) {
                setSelectedTaskProjectId('');
            }
            return;
        }

        const hasSelectedProject =
            selectedTaskProjectId !== '' && projectList.some((project) => String(project.id) === String(selectedTaskProjectId));

        if (!hasSelectedProject) {
            setSelectedTaskProjectId(projectList[0].id);
        }
    }, [projectList, selectedTaskProjectId]);

    useEffect(() => {
        if (canViewTasks && activeTab === 'tasks' && selectedTaskProjectId !== '') {
            fetchTasks(coerceId(selectedTaskProjectId));
        }
    }, [activeTab, selectedTaskProjectId, fetchTasks, canViewTasks]);

    const resetCreateForm = () => {
        setNewName('');
        setNewBudget('');
        setNewParentId('');
        setNewHourlyRate('');
        if (customerList.length) setSelectedCustomerId(customerList[0].id ?? '');
        else setSelectedCustomerId('');
    };

    const startEdit = (project) => {
        if (!canManageProjects) return;
        setEditingId(project.id);
        setEditingName(project.name ?? '');
        setEditingProjectCustomerId(project.customer?.id ?? '');
        setEditingBudget(project.budgetMinutes ?? '');
        setEditingParentId(project.parent?.id ? String(project.parent.id) : '');
        setEditingHourlyRate(project.hourlyRate ?? '');
    };

    const cancelEdit = useCallback(() => {
        setEditingId(null);
        setEditingName('');
        setEditingProjectCustomerId('');
        setEditingBudget('');
        setEditingParentId('');
        setEditingHourlyRate('');
    }, []);

    const handleCreateCustomer = async (event) => {
        event.preventDefault();
        if (!canManageCustomers) return;
        if (!newCustomerName.trim()) return;
        setCustomerSaving(true);
        try {
            await createCustomer(newCustomerName);
            setNewCustomerName('');
            notify(t('customer.createSuccess', 'Kunde erfolgreich angelegt!'), 'success');
        } catch (err) {
            logUnexpectedError('Error creating customer', err);
            notify(t('customer.createError', 'Fehler beim Anlegen des Kunden.'), 'error');
        } finally {
            setCustomerSaving(false);
        }
    };

    const handleUpdateCustomer = async (event) => {
        event.preventDefault();
        if (!canManageCustomers) return;
        if (!editingCustomerName.trim()) return;
        setCustomerSaving(true);
        try {
            await updateCustomer(editingCustomerId, editingCustomerName);
            setEditingCustomerId(null);
            setEditingCustomerName('');
            if (canViewProjects) {
                await Promise.allSettled([fetchProjects(), loadAnalytics()]);
            }
            notify(t('customer.updateSuccess', 'Kunde erfolgreich gespeichert!'), 'success');
        } catch (err) {
            logUnexpectedError('Error updating customer', err);
            notify(t('customer.updateError', 'Fehler beim Speichern des Kunden.'), 'error');
        } finally {
            setCustomerSaving(false);
        }
    };

    const handleDeleteCustomer = async (id) => {
        if (!canManageCustomers || customerDeletingId !== null) return;
        if (!window.confirm(t('customer.deleteConfirm', 'Sind Sie sicher, dass Sie diesen Kunden löschen möchten?'))) return;
        setCustomerDeletingId(id);
        try {
            await deleteCustomer(id);
            if (canViewProjects) {
                await Promise.allSettled([fetchProjects(), loadAnalytics()]);
            }
            notify(t('customer.deleteSuccess', 'Kunde erfolgreich gelöscht!'), 'success');
        } catch (err) {
            logUnexpectedError('Error deleting customer', err);
            notify(t('customer.deleteError', 'Fehler beim Löschen des Kunden.'), 'error');
        } finally {
            setCustomerDeletingId(null);
        }
    };

    const startCustomerEdit = (customer) => {
        if (!canManageCustomers) return;
        setEditingCustomerId(customer.id);
        setEditingCustomerName(customer.name ?? '');
    };

    const cancelCustomerEdit = () => {
        setEditingCustomerId(null);
        setEditingCustomerName('');
    };

    const handleCreateTask = async (event) => {
        event.preventDefault();
        if (!canManageTasks) return;
        if (!newTaskName.trim() || !selectedTaskProjectId) {
            notify(t('task.create.validationError', 'Bitte Projekt auswählen und Namen eingeben.'), 'warning');
            return;
        }
        const budget = asIntOrNull(newTaskBudget);
        if (budget !== null && budget < 0) {
            notify(t('task.create.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        setTaskSaving(true);
        try {
            await createTask(
                coerceId(selectedTaskProjectId),
                newTaskName,
                budget,
                newTaskBillable
            );
            setNewTaskName('');
            setNewTaskBudget('');
            setNewTaskBillable(false);
            notify(t('task.create.success', 'Aufgabe erfolgreich angelegt!'), 'success');
        } catch (err) {
            logUnexpectedError('Error creating task', err);
            notify(t('task.create.error', 'Fehler beim Anlegen der Aufgabe.'), 'error');
        } finally {
            setTaskSaving(false);
        }
    };

    const handleUpdateTask = async (event) => {
        event.preventDefault();
        if (!canManageTasks) return;
        if (!editingTaskName.trim()) {
            notify(t('task.update.validationError', 'Bitte Namen eingeben.'), 'warning');
            return;
        }
        const budget = asIntOrNull(editingTaskBudget);
        if (budget !== null && budget < 0) {
            notify(t('task.update.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        setTaskSaving(true);
        try {
            await updateTask(
                editingTaskId,
                editingTaskName,
                budget,
                editingTaskBillable
            );
            setEditingTaskId(null);
            setEditingTaskName('');
            setEditingTaskBudget('');
            setEditingTaskBillable(false);
            notify(t('task.update.success', 'Aufgabe erfolgreich gespeichert!'), 'success');
        } catch (err) {
            logUnexpectedError('Error updating task', err);
            notify(t('task.update.error', 'Fehler beim Speichern der Aufgabe.'), 'error');
        } finally {
            setTaskSaving(false);
        }
    };

    const handleDeleteTask = async (id) => {
        if (!canManageTasks || taskDeletingId !== null) return;
        if (!window.confirm(t('task.delete.confirm', 'Sind Sie sicher, dass Sie diese Aufgabe löschen möchten?'))) return;
        setTaskDeletingId(id);
        try {
            await deleteTask(id);
            notify(t('task.delete.success', 'Aufgabe erfolgreich gelöscht!'), 'success');
        } catch (err) {
            logUnexpectedError('Error deleting task', err);
            notify(t('task.delete.error', 'Fehler beim Löschen der Aufgabe.'), 'error');
        } finally {
            setTaskDeletingId(null);
        }
    };

    const startTaskEdit = (task) => {
        if (!canManageTasks) return;
        setEditingTaskId(task.id);
        setEditingTaskName(task.name ?? '');
        setEditingTaskBudget(task.budgetMinutes ?? '');
        setEditingTaskBillable(Boolean(task.billable));
    };

    const cancelTaskEdit = () => {
        setEditingTaskId(null);
        setEditingTaskName('');
        setEditingTaskBudget('');
        setEditingTaskBillable(false);
    };

    const loadAnalytics = useCallback(async () => {
        const requestId = ++analyticsRequestRef.current;
        if (!canViewProjects) {
            setAnalytics([]);
            setAnalyticsError(null);
            setAnalyticsLoading(false);
            return;
        }
        if (!isValidDateRange(analyticsStart, analyticsEnd)) {
            setAnalyticsError(t('project.period.invalid', 'Das Startdatum muss vor oder am Enddatum liegen.'));
            setAnalyticsLoading(false);
            return;
        }
        setAnalyticsLoading(true);
        setAnalyticsError(null);
        try {
            const res = await api.get('/api/report/analytics/projects', {
                params: {
                    startDate: analyticsStart,
                    endDate: analyticsEnd
                }
            });
            if (requestId === analyticsRequestRef.current) {
                setAnalytics(Array.isArray(res.data) ? res.data : []);
            }
        } catch (err) {
            if (requestId === analyticsRequestRef.current) {
                logUnexpectedError('Error loading analytics', err);
                setAnalytics([]);
                setAnalyticsError(getLoadErrorMessage(
                    err,
                    t,
                    'project.analytics.error',
                    'Fehler beim Laden der Projekt-Analytics'
                ));
            }
        } finally {
            if (requestId === analyticsRequestRef.current) {
                setAnalyticsLoading(false);
            }
        }
    }, [analyticsStart, analyticsEnd, canViewProjects, companyContextKey, t]);

    // ESC cancels edit
    useEffect(() => {
        const onKey = (e) => {
            if (e.key === 'Escape' && editingId) cancelEdit();
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [editingId, cancelEdit]);

    useEffect(() => {
        if (previousCompanyContextRef.current === companyContextKey) {
            return;
        }
        previousCompanyContextRef.current = companyContextKey;
        analyticsRequestRef.current += 1;
        analyticsInitialLoadRef.current = false;
        setAnalytics([]);
        setAnalyticsError(null);
        setAnalyticsLoading(false);
        setNewName('');
        setSelectedCustomerId('');
        setNewBudget('');
        setNewParentId('');
        setNewHourlyRate('');
        setNewCustomerName('');
        setEditingCustomerId(null);
        setEditingCustomerName('');
        setSelectedTaskProjectId('');
        setNewTaskName('');
        setNewTaskBudget('');
        setNewTaskBillable(false);
        setEditingTaskId(null);
        setEditingTaskName('');
        setEditingTaskBudget('');
        setEditingTaskBillable(false);
        cancelEdit();
    }, [companyContextKey, cancelEdit]);

    useEffect(() => {
        if (!canViewProjects) {
            analyticsRequestRef.current += 1;
            analyticsInitialLoadRef.current = false;
            setAnalytics([]);
            setAnalyticsError(null);
            setAnalyticsLoading(false);
            return;
        }
        if (!analyticsInitialLoadRef.current) {
            analyticsInitialLoadRef.current = true;
            void loadAnalytics();
        }
    }, [canViewProjects, loadAnalytics]);

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!canManageProjects || projectSaving) return;
        if (!newName.trim() || !selectedCustomerId) {
            notify(t('project.create.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
            return;
        }
        const budget = asIntOrNull(newBudget);
        if (budget !== null && budget < 0) {
            notify(t('project.create.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        const hourlyRate = asDecimalOrNull(newHourlyRate);
        if (hourlyRate !== null && hourlyRate < 0) {
            notify(t('project.create.rateInvalid', 'Der Stundensatz darf nicht negativ sein.'), 'warning');
            return;
        }
        const parentIdValue = newParentId ? Number(newParentId) : null;
        setProjectSaving(true);
        try {
            await createProject({
                name: newName.trim(),
                customerId: selectedCustomerId,
                budgetMinutes: budget,
                parentId: parentIdValue,
                hourlyRate
            });
            resetCreateForm();
            notify(t('project.create.success', 'Projekt erfolgreich angelegt!'), 'success');
            await loadAnalytics();
        } catch (err) {
            logUnexpectedError('Error creating project', err);
            notify(t('project.create.error', 'Fehler beim Anlegen des Projekts.'), 'error');
        } finally {
            setProjectSaving(false);
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        if (!canManageProjects || projectSaving) return;
        if (!editingName.trim() || !editingProjectCustomerId) {
            notify(t('project.update.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
            return;
        }
        const budget = asIntOrNull(editingBudget);
        if (budget !== null && budget < 0) {
            notify(t('project.update.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        const hourlyRate = asDecimalOrNull(editingHourlyRate);
        if (hourlyRate !== null && hourlyRate < 0) {
            notify(t('project.update.rateInvalid', 'Der Stundensatz darf nicht negativ sein.'), 'warning');
            return;
        }
        const parentIdValue = editingParentId ? Number(editingParentId) : null;
        setProjectSaving(true);
        try {
            await updateProject(editingId, {
                name: editingName.trim(),
                customerId: editingProjectCustomerId,
                budgetMinutes: budget,
                parentId: parentIdValue,
                hourlyRate
            });
            cancelEdit();
            notify(t('project.update.success', 'Projekt erfolgreich gespeichert!'), 'success');
            await loadAnalytics();
        } catch (err) {
            logUnexpectedError('Error updating project', err);
            notify(t('project.update.error', 'Fehler beim Speichern des Projekts.'), 'error');
        } finally {
            setProjectSaving(false);
        }
    };

    const handleDelete = async (id) => {
        if (!canManageProjects || projectDeletingId !== null) return;
        if (!window.confirm(t('project.delete.confirm', 'Sind Sie sicher, dass Sie dieses Projekt löschen möchten?'))) return;
        setProjectDeletingId(id);
        try {
            await deleteProject(id);
            notify(t('project.delete.success', 'Projekt erfolgreich gelöscht!'), 'success');
            await loadAnalytics();
        } catch (err) {
            logUnexpectedError('Error deleting project', err);
            notify(t('project.delete.error', 'Fehler beim Löschen des Projekts.'), 'error');
        } finally {
            setProjectDeletingId(null);
        }
    };

    const hasCustomers = customerList.length > 0;
    const hasProjects = projectList.length > 0;

    const tabItems = useMemo(() => ([
        { id: 'projects', label: t('project.management.tabTitle', 'Projekte') },
        { id: 'customers', label: t('customer.management.title', 'Kunden') },
        { id: 'tasks', label: t('task.management.title', 'Aufgaben') }
    ]).filter((tab) => allowedTabs.includes(tab.id)), [allowedTabs, t]);

    const handleTabKeyDown = useCallback((event, tabId) => {
        const currentIndex = allowedTabs.indexOf(tabId);
        if (currentIndex < 0 || allowedTabs.length < 2) return;

        let nextIndex = null;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
            nextIndex = (currentIndex + 1) % allowedTabs.length;
        } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
            nextIndex = (currentIndex - 1 + allowedTabs.length) % allowedTabs.length;
        } else if (event.key === 'Home') {
            nextIndex = 0;
        } else if (event.key === 'End') {
            nextIndex = allowedTabs.length - 1;
        }

        if (nextIndex === null) return;
        event.preventDefault();
        const nextTab = allowedTabs[nextIndex];
        handleTabChange(nextTab);
        window.requestAnimationFrame(() => document.getElementById(`admin-tab-${nextTab}`)?.focus());
    }, [allowedTabs, handleTabChange]);

    const focusProjectCreate = useCallback(() => {
        handleTabChange('projects');
        window.requestAnimationFrame(() => newProjectNameRef.current?.focus());
    }, [handleTabChange]);

    const projectPulse = useMemo(() => {
        const totalProjects = projectList.length;
        const totalCustomers = customerList.length;
        const projectsWithBudget = projectList.filter((project) => Number(project?.budgetMinutes ?? 0) > 0).length;
        const totalBudgetMinutes = projectList.reduce(
            (sum, project) => sum + Number(project?.budgetMinutes ?? 0),
            0
        );
        return {
            totalProjects,
            totalCustomers,
            projectsWithBudget,
            totalBudgetHours: totalBudgetMinutes / 60
        };
    }, [projectList, customerList]);

    const formattedBudgetHours = useMemo(() => {
        if (!projectPulse.totalBudgetHours) return '—';
        const value = projectPulse.totalBudgetHours;
        return value >= 100 ? Math.round(value).toLocaleString() : value.toLocaleString(undefined, { maximumFractionDigits: 1 });
    }, [projectPulse.totalBudgetHours]);

    const showHeroStats = canViewProjects || canViewCustomers;

    if (!projectsFeatureEnabled) {
        return (
            <>
                <Navbar />
                <main className="admin-projects-page scoped-dashboard neo-dashboard">
                    <section className="content-section access-state" role="alert">
                        <h1 className="section-title">{t('project.access.featureTitle', 'Projektmodul nicht verfügbar')}</h1>
                        <p>
                            {t(
                                'project.access.featureDescription',
                                'Das Projektmodul ist für diese Firma nicht aktiv. Bitte prüfe die Firmenkonfiguration.'
                            )}
                        </p>
                    </section>
                </main>
            </>
        );
    }

    if (allowedTabs.length === 0) {
        return (
            <>
                <Navbar />
                <main className="admin-projects-page scoped-dashboard neo-dashboard">
                    <section className="content-section access-state" role="alert">
                        <h1 className="section-title">{t('project.access.title', 'Kein Zugriff auf die Projektverwaltung')}</h1>
                        <p>
                            {t(
                                'project.access.description',
                                'Für dein Konto ist keiner der Bereiche Projekte, Kunden oder Aufgaben freigegeben.'
                            )}
                        </p>
                    </section>
                </main>
            </>
        );
    }

    return (
        <>
            <Navbar />
            <main className="admin-projects-page scoped-dashboard neo-dashboard">
                <section className={`page-hero${showHeroStats ? '' : ' page-hero--single'}`}>
                    <div className="hero-heading">
                        <span className="hero-kicker">{t('project.management.hero.kicker', 'Chronos Control Center')}</span>
                        <h1>{t('project.management.hero.title', 'Projekte & Workflows orchestrieren')}</h1>
                        <p>
                            {t(
                                'project.management.hero.subtitle',
                                'Behalte Budgets, Kundenbeziehungen und Aufgaben in einem modernen Cockpit im Blick.'
                            )}
                        </p>
                        <div className="hero-actions">
                            {canViewProjects && (
                            <button
                                type="button"
                                className="button-ghost hero-action"
                                onClick={loadAnalytics}
                                disabled={analyticsLoading}
                            >
                                {analyticsLoading
                                    ? t('loading', 'Lädt...')
                                    : t('project.management.hero.refreshAnalytics', 'Analytics aktualisieren')}
                            </button>
                            )}
                            {canManageProjects && (
                            <button
                                type="button"
                                className="button-primary hero-action"
                                onClick={focusProjectCreate}
                            >
                                {t('project.management.hero.createProject', 'Neues Projekt starten')}
                            </button>
                            )}
                        </div>
                    </div>
                    {showHeroStats && <div className="hero-stats" role="list">
                        {canViewProjects && <>
                        <div className="hero-stat-card" role="listitem">
                            <span className="stat-label">{t('project.management.hero.totalProjects', 'Projekte gesamt')}</span>
                            <span className="stat-value">{projectPulse.totalProjects}</span>
                            <span className="stat-sublabel">
                                {projectPulse.projectsWithBudget} {t('project.management.hero.projectsWithBudgetSuffix', 'mit Budget')}
                            </span>
                        </div>
                        </>}
                        {canViewCustomers && (
                        <div className="hero-stat-card" role="listitem">
                            <span className="stat-label">{t('project.management.hero.totalCustomers', 'Kunden gesamt')}</span>
                            <span className="stat-value">{projectPulse.totalCustomers}</span>
                            <span className="stat-sublabel">{t('project.management.hero.customersSubtitle', 'In der Kundenverwaltung erfasst')}</span>
                        </div>
                        )}
                        {canViewProjects && (
                        <div className="hero-stat-card" role="listitem">
                            <span className="stat-label">{t('project.management.hero.totalBudget', 'Gesamtbudget')}</span>
                            <span className="stat-value">{formattedBudgetHours}</span>
                            <span className="stat-sublabel">{t('project.management.hero.totalBudgetUnit', 'Stunden hinterlegt')}</span>
                        </div>
                        )}
                    </div>}
                </section>

                <div className="tab-shell">
                    <div
                        className="tab-bar"
                        role="tablist"
                        aria-label={t('project.management.tablist', 'Verwaltungsbereiche auswählen')}
                    >
                        {tabItems.map((tab) => {
                            const isActive = tab.id === activeTab;
                            return (
                                <button
                                    key={tab.id}
                                    type="button"
                                    id={`admin-tab-${tab.id}`}
                                    role="tab"
                                    aria-selected={isActive}
                                    aria-controls={`admin-panel-${tab.id}`}
                                    tabIndex={isActive ? 0 : -1}
                                    className={`tab-button${isActive ? ' is-active' : ''}`}
                                    onClick={() => handleTabChange(tab.id)}
                                    onKeyDown={(event) => handleTabKeyDown(event, tab.id)}
                                >
                                    <span className="tab-label">{tab.label}</span>
                                </button>
                            );
                        })}
                    </div>

                    <div className="tab-panel-wrapper">
                    {canViewProjects && <div
                        id="admin-panel-projects"
                        role="tabpanel"
                        aria-labelledby="admin-tab-projects"
                        className={`tab-panel${activeTab === 'projects' ? ' is-active' : ''}`}
                        hidden={activeTab !== 'projects'}
                    >
                        {!canManageProjects && (
                            <InlineState
                                message={t('project.access.readOnly', 'Du kannst Projekte ansehen, aber nicht verändern.')}
                            />
                        )}
                        <div className="content-grid content-grid--two">
                            {canManageProjects && (
                            <section className="content-section">
                                <h3 className="section-title">{t('project.create.title', 'Neues Projekt anlegen')}</h3>

                                {customersError && (
                                    <InlineState
                                        type="error"
                                        message={customersError}
                                        onRetry={fetchCustomers}
                                        retryLabel={t('retry', 'Erneut versuchen')}
                                    />
                                )}

                                {customersLoading && !hasCustomers && (
                                    <InlineState message={t('customer.loading', 'Kunden werden geladen…')} />
                                )}

                                {!customersLoading && !customersError && !hasCustomers && (
                                    <div className="empty-state">
                                        <h4>{t('project.create.noCustomersTitle', 'Noch keine Kunden angelegt')}</h4>
                                        <p>
                                            {t(
                                                'project.create.noCustomersDesc',
                                                'Lege zuerst einen Kunden an, um Projekte zuordnen zu können.'
                                            )}
                                        </p>
                                    </div>
                                )}

                                <form onSubmit={handleCreate} className="create-form" aria-label={t('project.create.form', 'Projekt anlegen')}>
                                    <label className="field-label" htmlFor="newProjectName">
                                        {t('project.create.nameLabel', 'Projektname')}
                                    </label>
                                    <input
                                        id="newProjectName"
                                        type="text"
                                        placeholder={t('project.create.namePlaceholder', 'Name des neuen Projekts')}
                                        value={newName}
                                        onChange={(e) => setNewName(e.target.value)}
                                        required
                                        maxLength={255}
                                        disabled={projectSaving}
                                        autoComplete="off"
                                        ref={newProjectNameRef}
                                    />

                                    <label className="field-label" htmlFor="newProjectCustomer">
                                        {t('project.create.customerLabel', 'Kunde')}
                                    </label>
                                    <select
                                        id="newProjectCustomer"
                                        value={selectedCustomerId}
                                        onChange={(e) => setSelectedCustomerId(e.target.value)}
                                        required
                                        disabled={!hasCustomers || projectSaving}
                                    >
                                        <option value="" disabled>
                                            {t('project.create.customerPlaceholder', 'Kunde auswählen...')}
                                        </option>
                                        {customerList.map((c) => (
                                            <option key={c.id} value={c.id}>
                                                {c.name}
                                            </option>
                                        ))}
                                    </select>

                                    <label className="field-label" htmlFor="newProjectParent">
                                        {t('project.create.parentLabel', 'Übergeordnetes Projekt')}
                                    </label>
                                    <select
                                        id="newProjectParent"
                                        value={newParentId}
                                        onChange={(e) => setNewParentId(e.target.value)}
                                        disabled={projectSaving}
                                    >
                                        <option value="">
                                            {t('project.create.noParent', 'Kein übergeordnetes Projekt')}
                                        </option>
                                        {parentOptions.map((option) => (
                                            <option key={option.id} value={option.id}>
                                                {option.name}
                                            </option>
                                        ))}
                                    </select>

                                    <label className="field-label" htmlFor="newProjectBudget">
                                        {t('project.create.budgetLabel', 'Budget (Minuten)')}
                                    </label>
                                    <input
                                        id="newProjectBudget"
                                        type="number"
                                        min="0"
                                        inputMode="numeric"
                                        placeholder={t('project.create.budgetPlaceholder', 'Budget (Minuten)')}
                                        value={newBudget}
                                        onChange={(e) => setNewBudget(e.target.value)}
                                        disabled={projectSaving}
                                    />

                                    <label className="field-label" htmlFor="newProjectRate">
                                        {t('project.create.rateLabel', 'Stundensatz (CHF)')}
                                    </label>
                                    <input
                                        id="newProjectRate"
                                        type="number"
                                        min="0"
                                        step="0.01"
                                        inputMode="decimal"
                                        placeholder={t('project.create.ratePlaceholder', 'Stundensatz (optional)')}
                                        value={newHourlyRate}
                                        onChange={(e) => setNewHourlyRate(e.target.value)}
                                        disabled={projectSaving}
                                    />

                                    <button type="submit" className="button-primary" disabled={!hasCustomers || projectSaving}>
                                        {projectSaving ? t('saving', 'Wird gespeichert…') : t('create', 'Anlegen')}
                                    </button>
                                </form>
                            </section>
                            )}

                            <section className="content-section">
                                <h3 className="section-title">{t('project.list.title', 'Bestehende Projekte')}</h3>

                                {projectsError && (
                                    <InlineState
                                        type="error"
                                        message={projectsError}
                                        onRetry={fetchProjects}
                                        retryLabel={t('retry', 'Erneut versuchen')}
                                    />
                                )}

                                {projectsLoading && !hasProjects ? (
                                    <InlineState message={t('project.loading', 'Projekte werden geladen…')} />
                                ) : !projectsError && !hasProjects ? (
                                    <div className="empty-state">
                                        <h4>{t('project.list.emptyTitle', 'Noch keine Projekte')}</h4>
                                        <p>
                                            {t(
                                                'project.list.emptyDesc',
                                                'Lege oben ein neues Projekt an. Projekte können optional ein Budget in Minuten haben.'
                                            )}
                                        </p>
                                    </div>
                                ) : (
                                    <div className="item-list-container">
                                        <ul className="item-list project-list">
                                            {projectList.map((p) => (
                                                <li key={p.id} className="list-item">
                                                    {editingId === p.id ? (
                                                        <form
                                                            onSubmit={handleUpdate}
                                                            className="edit-form"
                                                            aria-label={t('project.edit.form', 'Projekt bearbeiten')}
                                                        >
                                                            <label className="field-label" htmlFor={`editProjectName-${p.id}`}>
                                                                {t('project.edit.nameLabel', 'Projektname')}
                                                            </label>
                                                            <input
                                                                id={`editProjectName-${p.id}`}
                                                                type="text"
                                                                value={editingName}
                                                                onChange={(e) => setEditingName(e.target.value)}
                                                                required
                                                                autoFocus
                                                                maxLength={255}
                                                                disabled={projectSaving}
                                                                autoComplete="off"
                                                            />

                                                            <label className="field-label" htmlFor={`editProjectCustomer-${p.id}`}>
                                                                {t('project.edit.customerLabel', 'Kunde')}
                                                            </label>
                                                            <select
                                                                id={`editProjectCustomer-${p.id}`}
                                                                value={editingProjectCustomerId}
                                                                onChange={(e) => setEditingProjectCustomerId(e.target.value)}
                                                                required
                                                                disabled={projectSaving}
                                                            >
                                                                <option value="" disabled>
                                                                    {t('project.edit.customerPlaceholder', 'Kunde auswählen...')}
                                                                </option>
                                                                {customerList.map((c) => (
                                                                    <option key={c.id} value={c.id}>
                                                                        {c.name}
                                                                    </option>
                                                                ))}
                                                            </select>

                                                            <label className="field-label" htmlFor={`editProjectParent-${p.id}`}>
                                                                {t('project.edit.parentLabel', 'Übergeordnetes Projekt')}
                                                            </label>
                                                            <select
                                                                id={`editProjectParent-${p.id}`}
                                                                value={editingParentId}
                                                                onChange={(e) => setEditingParentId(e.target.value)}
                                                                disabled={projectSaving}
                                                            >
                                                                <option value="">
                                                                    {t('project.edit.noParent', 'Kein übergeordnetes Projekt')}
                                                                </option>
                                                                {parentOptions
                                                                    .filter((option) => !blockedParentIds.has(option.id))
                                                                    .map((option) => (
                                                                        <option key={option.id} value={String(option.id)}>
                                                                            {option.name}
                                                                        </option>
                                                                    ))}
                                                            </select>

                                                            <label className="field-label" htmlFor={`editProjectBudget-${p.id}`}>
                                                                {t('project.edit.budgetLabel', 'Budget (Minuten)')}
                                                            </label>
                                                            <input
                                                                id={`editProjectBudget-${p.id}`}
                                                                type="number"
                                                                min="0"
                                                                inputMode="numeric"
                                                                placeholder={t('project.edit.budgetPlaceholder', 'Budget (Minuten)')}
                                                                value={editingBudget}
                                                                onChange={(e) => setEditingBudget(e.target.value)}
                                                                disabled={projectSaving}
                                                            />

                                                            <label className="field-label" htmlFor={`editProjectRate-${p.id}`}>
                                                                {t('project.edit.rateLabel', 'Stundensatz (CHF)')}
                                                            </label>
                                                            <input
                                                                id={`editProjectRate-${p.id}`}
                                                                type="number"
                                                                min="0"
                                                                step="0.01"
                                                                inputMode="decimal"
                                                                placeholder={t('project.edit.ratePlaceholder', 'Stundensatz (optional)')}
                                                                value={editingHourlyRate}
                                                                onChange={(e) => setEditingHourlyRate(e.target.value)}
                                                                disabled={projectSaving}
                                                            />

                                                            <div className="form-actions">
                                                                <button type="submit" className="button-primary" disabled={projectSaving}>
                                                                    {projectSaving ? t('saving', 'Wird gespeichert…') : t('save', 'Speichern')}
                                                                </button>
                                                                <button type="button" onClick={cancelEdit} className="button-secondary" disabled={projectSaving}>
                                                                    {t('cancel', 'Abbrechen')}
                                                                </button>
                                                            </div>
                                                        </form>
                                                    ) : (
                                                        <>
                                                            <div className="item-details">
                                                                <span className="item-name" title={p.name}>
                                                                    {p.name}
                                                                </span>
                                                                <div className="item-meta">
                                                                    <span className="item-chip">
                                                                        {p.customer?.name || t('project.noCustomer', 'Kein Kunde zugewiesen')}
                                                                    </span>
                                                                    {p.parent && (
                                                                        <span className="item-chip">
                                                                            {t('project.parent', 'Parent')}: {p.parent.name}
                                                                        </span>
                                                                    )}
                                                                    {p.budgetMinutes !== undefined && p.budgetMinutes !== null && (
                                                                        <span className="item-chip">
                                                                            {p.budgetMinutes} {t('project.budget.unit', 'Min')}
                                                                        </span>
                                                                    )}
                                                                    {p.hourlyRate !== undefined && p.hourlyRate !== null && p.hourlyRate !== '' && (
                                                                        <span className="item-chip">
                                                                            {t('project.rate', 'Rate')}: {p.hourlyRate}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                            {canManageProjects && <div className="item-actions">
                                                                <button onClick={() => startEdit(p)} className="button-secondary">
                                                                    {t('edit', 'Bearbeiten')}
                                                                </button>
                                                                <button
                                                                    onClick={() => handleDelete(p.id)}
                                                                    className="button-danger"
                                                                    disabled={projectDeletingId !== null}
                                                                >
                                                                    {projectDeletingId === p.id ? t('deleting', 'Wird gelöscht…') : t('delete', 'Löschen')}
                                                                </button>
                                                            </div>}
                                                        </>
                                                    )}
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </section>
                        </div>

                        <section className="content-section">
                            <div className="section-header">
                                <h3 className="section-title">{t('project.hierarchy.title', 'Projekt-Hierarchie & KPIs')}</h3>
                                <div className="analytics-controls">
                                    <div className="analytics-field">
                                        <label className="field-label" htmlFor="analyticsStart">
                                            {t('project.period.start', 'Von')}
                                        </label>
                                        <input
                                            id="analyticsStart"
                                            type="date"
                                            value={analyticsStart}
                                            onChange={(e) => setAnalyticsStart(e.target.value)}
                                            disabled={analyticsLoading}
                                        />
                                    </div>
                                    <div className="analytics-field">
                                        <label className="field-label" htmlFor="analyticsEnd">
                                            {t('project.period.end', 'Bis')}
                                        </label>
                                        <input
                                            id="analyticsEnd"
                                            type="date"
                                            value={analyticsEnd}
                                            onChange={(e) => setAnalyticsEnd(e.target.value)}
                                            disabled={analyticsLoading}
                                        />
                                    </div>
                                    <button type="button" className="button-secondary" onClick={loadAnalytics} disabled={analyticsLoading}>
                                        {analyticsLoading ? t('loading', 'Lädt...') : t('refresh', 'Aktualisieren')}
                                    </button>
                                </div>
                            </div>
                            {analyticsError && (
                                <InlineState
                                    type="error"
                                    message={analyticsError}
                                    onRetry={loadAnalytics}
                                    retryLabel={t('retry', 'Erneut versuchen')}
                                />
                            )}
                            {analyticsLoading && analytics.length === 0 && (
                                <InlineState message={t('project.analytics.loading', 'Projekt-KPIs werden geladen…')} />
                            )}
                            <ProjectTree nodes={projectHierarchy} analyticsMap={analyticsMap} t={t} />
                        </section>

                    </div>}

                    {canViewCustomers && <div
                        id="admin-panel-customers"
                        role="tabpanel"
                        aria-labelledby="admin-tab-customers"
                        className={`tab-panel${activeTab === 'customers' ? ' is-active' : ''}`}
                        hidden={activeTab !== 'customers'}
                    >
                        {!canManageCustomers && (
                            <InlineState message={t('customer.access.readOnly', 'Du kannst Kunden ansehen, aber nicht verändern.')} />
                        )}
                        <div className="content-grid content-grid--two">
                            {canManageCustomers && (
                            <section className="content-section">
                                <h3 className="section-title">{t('customer.create.title', 'Neuen Kunden anlegen')}</h3>
                                <form onSubmit={handleCreateCustomer} className="create-form" aria-label={t('customer.create.title', 'Neuen Kunden anlegen')}>
                                    <label className="field-label" htmlFor="newCustomerName">
                                        {t('customer.create.nameLabel', 'Kundenname')}
                                    </label>
                                    <input
                                        id="newCustomerName"
                                        type="text"
                                        placeholder={t('customer.create.placeholder', 'Name des neuen Kunden')}
                                        value={newCustomerName}
                                        onChange={(e) => setNewCustomerName(e.target.value)}
                                        required
                                        maxLength={255}
                                        disabled={customerSaving}
                                    />
                                    <button type="submit" className="button-primary" disabled={customerSaving}>
                                        {customerSaving ? t('saving', 'Wird gespeichert…') : t('create', 'Anlegen')}
                                    </button>
                                </form>
                            </section>
                            )}

                            <section className="content-section">
                                <h3 className="section-title">{t('customer.list.title', 'Bestehende Kunden')}</h3>
                                {customersError && (
                                    <InlineState
                                        type="error"
                                        message={customersError}
                                        onRetry={fetchCustomers}
                                        retryLabel={t('retry', 'Erneut versuchen')}
                                    />
                                )}
                                {customersLoading && customerList.length === 0 ? (
                                    <InlineState message={t('customer.loading', 'Kunden werden geladen…')} />
                                ) : !customersError && customerList.length === 0 ? (
                                    <div className="empty-state">
                                        <h4>{t('customer.list.empty', 'Noch keine Kunden vorhanden')}</h4>
                                        <p>{t('customer.list.emptyHint', 'Lege einen Kunden an, um Projekte zuordnen zu können.')}</p>
                                    </div>
                                ) : (
                                <div className="item-list-container">
                                    <ul className="item-list customer-list">
                                        {customerList.map((customer) => (
                                            <li key={customer.id} className="list-item">
                                                {editingCustomerId === customer.id ? (
                                                    <form onSubmit={handleUpdateCustomer} className="edit-form" aria-label={t('customer.edit.title', 'Kunde bearbeiten')}>
                                                        <label className="field-label" htmlFor={`editCustomerName-${customer.id}`}>
                                                            {t('customer.create.nameLabel', 'Kundenname')}
                                                        </label>
                                                        <input
                                                            id={`editCustomerName-${customer.id}`}
                                                            type="text"
                                                            value={editingCustomerName}
                                                            onChange={(e) => setEditingCustomerName(e.target.value)}
                                                            required
                                                            autoFocus
                                                            maxLength={255}
                                                            disabled={customerSaving}
                                                        />
                                                        <div className="form-actions">
                                                            <button type="submit" className="button-primary" disabled={customerSaving}>
                                                                {customerSaving ? t('saving', 'Wird gespeichert…') : t('save', 'Speichern')}
                                                            </button>
                                                            <button type="button" onClick={cancelCustomerEdit} className="button-secondary" disabled={customerSaving}>
                                                                {t('cancel', 'Abbrechen')}
                                                            </button>
                                                        </div>
                                                    </form>
                                                ) : (
                                                    <>
                                                        <span className="item-name">{customer.name}</span>
                                                        {canManageCustomers && <div className="item-actions">
                                                            <button onClick={() => startCustomerEdit(customer)} className="button-secondary">{t('edit', 'Bearbeiten')}</button>
                                                            <button onClick={() => handleDeleteCustomer(customer.id)} className="button-danger" disabled={customerDeletingId !== null}>
                                                                {customerDeletingId === customer.id ? t('deleting', 'Wird gelöscht…') : t('delete', 'Löschen')}
                                                            </button>
                                                        </div>}
                                                    </>
                                                )}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                                )}
                            </section>
                        </div>
                    </div>}

                    {canViewTasks && <div
                        id="admin-panel-tasks"
                        role="tabpanel"
                        aria-labelledby="admin-tab-tasks"
                        className={`tab-panel${activeTab === 'tasks' ? ' is-active' : ''}`}
                        hidden={activeTab !== 'tasks'}
                    >
                        {!canManageTasks && (
                            <InlineState message={t('task.access.readOnly', 'Du kannst Aufgaben ansehen, aber nicht verändern.')} />
                        )}
                        <section className="content-section">
                            <h3 className="section-title">{t('task.projectSelection', 'Projekt auswählen')}</h3>
                            {projectsError && (
                                <InlineState
                                    type="error"
                                    message={projectsError}
                                    onRetry={fetchProjects}
                                    retryLabel={t('retry', 'Erneut versuchen')}
                                />
                            )}
                            {projectsLoading && !hasProjects ? (
                                <InlineState message={t('project.loading', 'Projekte werden geladen…')} />
                            ) : hasProjects ? (
                                <>
                                <label className="field-label" htmlFor="taskProjectSelection">
                                    {t('task.projectSelection', 'Projekt auswählen')}
                                </label>
                                <select
                                    id="taskProjectSelection"
                                    value={selectedTaskProjectId}
                                    onChange={(e) => setSelectedTaskProjectId(e.target.value)}
                                    className="project-selector"
                                    disabled={tasksLoading || taskSaving}
                                >
                                    {sortedProjects.map((project) => (
                                        <option key={project.id} value={project.id}>
                                            {project.customer?.name ? `${project.customer.name} · ` : ''}{project.name}
                                        </option>
                                    ))}
                                </select>
                                </>
                            ) : !projectsError ? (
                                <div className="empty-state">
                                    <h4>{t('task.noProjects.title', 'Noch keine Projekte vorhanden')}</h4>
                                    <p>{t('task.noProjects.description', 'Lege zuerst ein Projekt an, um Aufgaben zu verwalten.')}</p>
                                </div>
                            ) : null}
                        </section>

                        {hasProjects && (
                            <div className="content-grid content-grid--two">
                                {canManageTasks && (
                                <section className="content-section">
                                    <h3 className="section-title">{t('task.create.title', 'Neue Aufgabe anlegen')}</h3>
                                    <form onSubmit={handleCreateTask} className="create-form" aria-label={t('task.create.title', 'Neue Aufgabe anlegen')}>
                                        <label className="field-label" htmlFor="newTaskName">
                                            {t('task.create.nameLabel', 'Aufgabenname')}
                                        </label>
                                        <input
                                            id="newTaskName"
                                            type="text"
                                            placeholder={t('task.create.namePlaceholder', 'Name der neuen Aufgabe')}
                                            value={newTaskName}
                                            onChange={(e) => setNewTaskName(e.target.value)}
                                            required
                                            maxLength={255}
                                            disabled={taskSaving}
                                        />
                                        <label className="field-label" htmlFor="newTaskBudget">
                                            {t('task.create.budgetPlaceholder', 'Budget (Minuten)')}
                                        </label>
                                        <input
                                            id="newTaskBudget"
                                            type="number"
                                            min="0"
                                            step="1"
                                            inputMode="numeric"
                                            placeholder={t('task.create.budgetPlaceholder', 'Budget (Minuten)')}
                                            value={newTaskBudget}
                                            onChange={(e) => setNewTaskBudget(e.target.value)}
                                            disabled={taskSaving}
                                        />
                                        <label className="checkbox-field">
                                            <input
                                                type="checkbox"
                                                checked={newTaskBillable}
                                                onChange={(e) => setNewTaskBillable(e.target.checked)}
                                                disabled={taskSaving}
                                            />
                                            {t('task.create.billable', 'Abrechenbar')}
                                        </label>
                                        <button type="submit" className="button-primary" disabled={taskSaving}>
                                            {taskSaving ? t('saving', 'Wird gespeichert…') : t('create', 'Anlegen')}
                                        </button>
                                    </form>
                                </section>
                                )}

                                <section className="content-section">
                                    <h3 className="section-title">{t('task.list.title', 'Bestehende Aufgaben')}</h3>
                                    {tasksError && (
                                        <InlineState
                                            type="error"
                                            message={tasksError}
                                            onRetry={() => fetchTasks(coerceId(selectedTaskProjectId))}
                                            retryLabel={t('retry', 'Erneut versuchen')}
                                        />
                                    )}
                                    {tasksLoading && taskList.length === 0 ? (
                                        <InlineState message={t('task.loading', 'Aufgaben werden geladen…')} />
                                    ) : !tasksError && taskList.length === 0 ? (
                                        <div className="empty-state">
                                            <h4>{t('task.list.empty', 'Noch keine Aufgaben für dieses Projekt')}</h4>
                                            <p>{t('task.list.emptyHint', 'Lege oben eine neue Aufgabe an, um loszulegen.')}</p>
                                        </div>
                                    ) : (
                                        <div className="item-list-container">
                                            <ul className="item-list project-list">
                                                {taskList.map((task) => (
                                                    <li key={task.id} className="list-item">
                                                        {editingTaskId === task.id ? (
                                                            <form onSubmit={handleUpdateTask} className="edit-form" aria-label={t('task.edit.title', 'Aufgabe bearbeiten')}>
                                                                <label className="field-label" htmlFor={`editTaskName-${task.id}`}>
                                                                    {t('task.create.nameLabel', 'Aufgabenname')}
                                                                </label>
                                                                <input
                                                                    id={`editTaskName-${task.id}`}
                                                                    type="text"
                                                                    value={editingTaskName}
                                                                    onChange={(e) => setEditingTaskName(e.target.value)}
                                                                    required
                                                                    autoFocus
                                                                    maxLength={255}
                                                                    disabled={taskSaving}
                                                                />
                                                                <label className="field-label" htmlFor={`editTaskBudget-${task.id}`}>
                                                                    {t('task.edit.budgetPlaceholder', 'Budget (Minuten)')}
                                                                </label>
                                                                <input
                                                                    id={`editTaskBudget-${task.id}`}
                                                                    type="number"
                                                                    min="0"
                                                                    step="1"
                                                                    inputMode="numeric"
                                                                    placeholder={t('task.edit.budgetPlaceholder', 'Budget (Minuten)')}
                                                                    value={editingTaskBudget}
                                                                    onChange={(e) => setEditingTaskBudget(e.target.value)}
                                                                    disabled={taskSaving}
                                                                />
                                                                <label className="checkbox-field">
                                                                    <input
                                                                        type="checkbox"
                                                                        checked={editingTaskBillable}
                                                                        onChange={(e) => setEditingTaskBillable(e.target.checked)}
                                                                        disabled={taskSaving}
                                                                    />
                                                                    {t('task.edit.billable', 'Abrechenbar')}
                                                                </label>
                                                                <div className="form-actions">
                                                                    <button type="submit" className="button-primary" disabled={taskSaving}>
                                                                        {taskSaving ? t('saving', 'Wird gespeichert…') : t('save', 'Speichern')}
                                                                    </button>
                                                                    <button type="button" onClick={cancelTaskEdit} className="button-secondary" disabled={taskSaving}>{t('cancel', 'Abbrechen')}</button>
                                                                </div>
                                                            </form>
                                                        ) : (
                                                            <>
                                                                <div className="item-details">
                                                                    <span className="item-name">{task.name}</span>
                                                                    <div className="item-meta">
                                                                        {task.budgetMinutes !== undefined && task.budgetMinutes !== null && (
                                                                            <span className="item-chip">
                                                                                {task.budgetMinutes} {t('task.budget.unit', 'Min')}
                                                                            </span>
                                                                        )}
                                                                        {task.billable && (
                                                                            <span className="item-chip">{t('task.billable', 'Abrechenbar')}</span>
                                                                        )}
                                                                    </div>
                                                                </div>
                                                                {canManageTasks && <div className="item-actions">
                                                                    <button onClick={() => startTaskEdit(task)} className="button-secondary">{t('edit', 'Bearbeiten')}</button>
                                                                    <button onClick={() => handleDeleteTask(task.id)} className="button-danger" disabled={taskDeletingId !== null}>
                                                                        {taskDeletingId === task.id ? t('deleting', 'Wird gelöscht…') : t('delete', 'Löschen')}
                                                                    </button>
                                                                </div>}
                                                            </>
                                                        )}
                                                    </li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}
                                </section>
                            </div>
                        )}
                    </div>}
                </div>
                </div>
            </main>
            </>
            );
            };

            export default AdminProjectsPage;
