// src/pages/AdminProjects/AdminProjectsPage.jsx
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import Navbar from '../../components/Navbar';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useProjects } from '../../context/ProjectContext';
import { useCustomers } from '../../context/CustomerContext';
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

const formatDateInput = (date) => {
    const d = new Date(date);
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
};

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

const ProjectTree = ({ nodes, analyticsMap }) => {
    if (!nodes || nodes.length === 0) {
        return <p className="tree-empty">Keine Hierarchie vorhanden.</p>;
    }

    const renderNodes = (items) => (
        <ul className="project-tree">
            {items.map((node) => {
                const metrics = analyticsMap.get(node.id);
                const utilizationPct = metrics?.utilization != null
                    ? Math.min(100, Math.round(metrics.utilization * 100))
                    : null;
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
                                {budgetHours && (
                                    <span className="metric-chip" title="Budget Stunden">
                                        Budget: {budgetHours}
                                    </span>
                                )}
                                {totalHours && (
                                    <span className="metric-chip" title="Gebuchte Stunden">
                                        Ist: {totalHours}
                                    </span>
                                )}
                                {utilizationPct != null && (
                                    <div className="metric-progress" title="Auslastung">
                                        <div className="metric-progress-bar" style={{ width: `${utilizationPct}%` }} />
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

const AdminProjectsPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const { projects, projectHierarchy, createProject, updateProject, deleteProject } = useProjects();
    const { customers } = useCustomers();

    // Create form state
    const [newName, setNewName] = useState('');
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [newBudget, setNewBudget] = useState('');
    const [newParentId, setNewParentId] = useState('');
    const [newHourlyRate, setNewHourlyRate] = useState('');

    // Edit form state
    const [editingId, setEditingId] = useState(null);
    const [editingName, setEditingName] = useState('');
    const [editingCustomerId, setEditingCustomerId] = useState('');
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

    const [integrations, setIntegrations] = useState([]);
    const [integrationLoading, setIntegrationLoading] = useState(false);
    const [newIntegration, setNewIntegration] = useState({
        name: '',
        type: 'GENERIC_WEBHOOK',
        endpointUrl: '',
        authHeader: '',
        active: true,
        autoSync: false
    });
    const [lastIntegrationRun, setLastIntegrationRun] = useState(null);

    const [auditLogs, setAuditLogs] = useState([]);
    const [auditLoading, setAuditLoading] = useState(false);

    const [billingProjectId, setBillingProjectId] = useState('');
    const [billingStart, setBillingStart] = useState(formatDateInput(defaultStart));
    const [billingEnd, setBillingEnd] = useState(formatDateInput(today));
    const [billingIncludeChildren, setBillingIncludeChildren] = useState(true);
    const [billingRate, setBillingRate] = useState('');
    const [billingCurrency, setBillingCurrency] = useState('CHF');
    const [invoiceResult, setInvoiceResult] = useState(null);
    const [billingLoading, setBillingLoading] = useState(false);

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

    const sortedProjects = useMemo(() => {
        return [...projects].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    }, [projects]);

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

    // Preselect first customer on mount/update
    useEffect(() => {
        if (customers?.length > 0 && !selectedCustomerId) {
            setSelectedCustomerId(customers[0].id);
        }
    }, [customers, selectedCustomerId]);

    const resetCreateForm = () => {
        setNewName('');
        setNewBudget('');
        setNewParentId('');
        setNewHourlyRate('');
        if (customers?.length) setSelectedCustomerId(customers[0].id ?? '');
        else setSelectedCustomerId('');
    };

    const startEdit = (project) => {
        setEditingId(project.id);
        setEditingName(project.name ?? '');
        setEditingCustomerId(project.customer?.id ?? '');
        setEditingBudget(project.budgetMinutes ?? '');
        setEditingParentId(project.parent?.id ? String(project.parent.id) : '');
        setEditingHourlyRate(project.hourlyRate ?? '');
    };

    const cancelEdit = useCallback(() => {
        setEditingId(null);
        setEditingName('');
        setEditingCustomerId('');
        setEditingBudget('');
        setEditingParentId('');
        setEditingHourlyRate('');
    }, []);

    const integrationTypes = useMemo(() => ([
        { value: 'GENERIC_WEBHOOK', label: 'Webhook' },
        { value: 'SAP', label: 'SAP' },
        { value: 'JIRA', label: 'Jira' },
        { value: 'MICROSOFT_TEAMS', label: 'Microsoft Teams' },
        { value: 'SLACK', label: 'Slack' }
    ]), []);

    const loadAnalytics = useCallback(async () => {
        setAnalyticsLoading(true);
        try {
            const res = await api.get('/api/report/analytics/projects', {
                params: {
                    startDate: analyticsStart,
                    endDate: analyticsEnd
                }
            });
            setAnalytics(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading analytics', err);
            notify(t('project.analytics.error', 'Fehler beim Laden der Projekt-Analytics'), 'error');
        } finally {
            setAnalyticsLoading(false);
        }
    }, [analyticsStart, analyticsEnd, notify, t]);

    const loadIntegrations = useCallback(async () => {
        setIntegrationLoading(true);
        try {
            const res = await api.get('/api/integrations');
            setIntegrations(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading integrations', err);
            notify(t('project.integration.errorLoad', 'Integrationen konnten nicht geladen werden.'), 'error');
        } finally {
            setIntegrationLoading(false);
        }
    }, [notify, t]);

    const loadAuditLogs = useCallback(async () => {
        setAuditLoading(true);
        try {
            const res = await api.get('/api/audit', { params: { limit: 25 } });
            setAuditLogs(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading audit logs', err);
            notify(t('project.audit.errorLoad', 'Audit-Log konnte nicht geladen werden.'), 'error');
        } finally {
            setAuditLoading(false);
        }
    }, [notify, t]);

    const handleIntegrationFieldChange = (field) => (event) => {
        const value = field === 'active' || field === 'autoSync'
            ? event.target.checked
            : event.target.value;
        setNewIntegration(prev => ({ ...prev, [field]: value }));
    };

    const handleCreateIntegration = async (e) => {
        e.preventDefault();
        if (!newIntegration.name.trim()) {
            notify(t('project.integration.nameRequired', 'Name für die Integration angeben.'), 'warning');
            return;
        }
        try {
            await api.post('/api/integrations', {
                ...newIntegration,
                name: newIntegration.name.trim()
            });
            setNewIntegration({
                name: '',
                type: 'GENERIC_WEBHOOK',
                endpointUrl: '',
                authHeader: '',
                active: true,
                autoSync: false
            });
            notify(t('project.integration.created', 'Integration gespeichert!'), 'success');
            loadIntegrations();
            loadAuditLogs();
        } catch (err) {
            console.error('Error creating integration', err);
            notify(t('project.integration.errorCreate', 'Integration konnte nicht gespeichert werden.'), 'error');
        }
    };

    const handleTriggerIntegration = async (id) => {
        try {
            const res = await api.post(`/api/integrations/${id}/trigger`);
            setLastIntegrationRun(res.data);
            notify(t('project.integration.triggered', 'Integration erfolgreich ausgelöst!'), 'success');
            loadIntegrations();
            loadAuditLogs();
        } catch (err) {
            console.error('Error triggering integration', err);
            notify(t('project.integration.errorTrigger', 'Integration konnte nicht ausgelöst werden.'), 'error');
        }
    };

    const handleToggleIntegration = async (integration, field) => {
        try {
            await api.put(`/api/integrations/${integration.id}`, {
                ...integration,
                [field]: !integration[field]
            });
            notify(t('project.integration.updated', 'Integration aktualisiert!'), 'success');
            loadIntegrations();
            loadAuditLogs();
        } catch (err) {
            console.error('Error updating integration', err);
            notify(t('project.integration.errorUpdate', 'Integration konnte nicht aktualisiert werden.'), 'error');
        }
    };

    const handleDeleteIntegration = async (id) => {
        if (!window.confirm(t('project.integration.deleteConfirm', 'Integration wirklich löschen?'))) return;
        try {
            await api.delete(`/api/integrations/${id}`);
            notify(t('project.integration.deleted', 'Integration gelöscht!'), 'success');
            loadIntegrations();
            loadAuditLogs();
        } catch (err) {
            console.error('Error deleting integration', err);
            notify(t('project.integration.errorDelete', 'Integration konnte nicht gelöscht werden.'), 'error');
        }
    };

    const handleGenerateInvoice = async (e) => {
        e.preventDefault();
        if (!billingProjectId || !billingStart || !billingEnd) {
            notify(t('project.billing.validation', 'Bitte Projekt und Zeitraum wählen.'), 'warning');
            return;
        }
        const overrideRate = asDecimalOrNull(billingRate);
        setBillingLoading(true);
        try {
            const res = await api.post('/api/billing/invoice', {
                projectId: Number(billingProjectId),
                startDate: billingStart,
                endDate: billingEnd,
                includeChildren: billingIncludeChildren,
                overrideRate,
                currency: billingCurrency
            });
            setInvoiceResult(res.data);
            notify(t('project.billing.generated', 'Abrechnung erstellt!'), 'success');
            loadAuditLogs();
        } catch (err) {
            console.error('Error generating invoice', err);
            notify(t('project.billing.error', 'Abrechnung konnte nicht erstellt werden.'), 'error');
        } finally {
            setBillingLoading(false);
        }
    };

    // ESC cancels edit
    useEffect(() => {
        const onKey = (e) => {
            if (e.key === 'Escape' && editingId) cancelEdit();
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [editingId, cancelEdit]);

    useEffect(() => {
        loadIntegrations();
        loadAuditLogs();
    }, [loadIntegrations, loadAuditLogs]);

    useEffect(() => {
        loadAnalytics();
    }, [loadAnalytics]);

    const handleCreate = async (e) => {
        e.preventDefault();
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
        const parentIdValue = newParentId ? Number(newParentId) : null;
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
            loadAnalytics();
            loadAuditLogs();
        } catch (err) {
            notify(t('project.create.error', 'Fehler beim Anlegen des Projekts.'), 'error');
        }
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        if (!editingName.trim() || !editingCustomerId) {
            notify(t('project.update.validationError', 'Bitte Projektname und Kunde auswählen.'), 'warning');
            return;
        }
        const budget = asIntOrNull(editingBudget);
        if (budget !== null && budget < 0) {
            notify(t('project.update.budgetInvalid', 'Budget darf nicht negativ sein.'), 'warning');
            return;
        }
        const hourlyRate = asDecimalOrNull(editingHourlyRate);
        const parentIdValue = editingParentId ? Number(editingParentId) : null;
        try {
            await updateProject(editingId, {
                name: editingName.trim(),
                customerId: editingCustomerId,
                budgetMinutes: budget,
                parentId: parentIdValue,
                hourlyRate
            });
            cancelEdit();
            notify(t('project.update.success', 'Projekt erfolgreich gespeichert!'), 'success');
            loadAnalytics();
            loadAuditLogs();
        } catch (err) {
            notify(t('project.update.error', 'Fehler beim Speichern des Projekts.'), 'error');
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm(t('project.delete.confirm', 'Sind Sie sicher, dass Sie dieses Projekt löschen möchten?'))) return;
        try {
            await deleteProject(id);
            notify(t('project.delete.success', 'Projekt erfolgreich gelöscht!'), 'success');
            loadAnalytics();
            loadAuditLogs();
        } catch (err) {
            notify(t('project.delete.error', 'Fehler beim Löschen des Projekts.'), 'error');
        }
    };

    const hasCustomers = customers && customers.length > 0;
    const hasProjects = projects && projects.length > 0;

    return (
        <>
            <Navbar />
            <div className="admin-projects-page scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t('project.management.title', 'Projektverwaltung')}</h1>
                </header>

                {/* Create */}
                <section className="content-section">
                    <h3 className="section-title">{t('project.create.title', 'Neues Projekt anlegen')}</h3>

                    {!hasCustomers && (
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
                        <label className="sr-only" htmlFor="newProjectName">
                            {t('project.create.nameLabel', 'Projektname')}
                        </label>
                        <input
                            id="newProjectName"
                            type="text"
                            placeholder={t('project.create.namePlaceholder', 'Name des neuen Projekts')}
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            required
                            autoComplete="off"
                        />

                        <label className="sr-only" htmlFor="newProjectCustomer">
                            {t('project.create.customerLabel', 'Kunde')}
                        </label>
                        <select
                            id="newProjectCustomer"
                            value={selectedCustomerId}
                            onChange={(e) => setSelectedCustomerId(e.target.value)}
                            required
                            disabled={!hasCustomers}
                        >
                            <option value="" disabled>
                                {t('project.create.customerPlaceholder', 'Kunde auswählen...')}
                            </option>
                            {customers?.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {c.name}
                                </option>
                            ))}
                        </select>

                        <label className="sr-only" htmlFor="newProjectParent">
                            {t('project.create.parentLabel', 'Übergeordnetes Projekt')}
                        </label>
                        <select
                            id="newProjectParent"
                            value={newParentId}
                            onChange={(e) => setNewParentId(e.target.value)}
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

                        <label className="sr-only" htmlFor="newProjectBudget">
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
                        />

                        <label className="sr-only" htmlFor="newProjectRate">
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
                        />

                        <button type="submit" className="button-primary" disabled={!hasCustomers}>
                            {t('create', 'Anlegen')}
                        </button>
                    </form>
                </section>

                {/* List */}
                <section className="content-section">
                    <h3 className="section-title">{t('project.list.title', 'Bestehende Projekte')}</h3>

                    {!hasProjects ? (
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
                                {projects.map((p) => (
                                    <li key={p.id} className="list-item">
                                        {editingId === p.id ? (
                                            <form
                                                onSubmit={handleUpdate}
                                                className="edit-form"
                                                aria-label={t('project.edit.form', 'Projekt bearbeiten')}
                                            >
                                                <label className="sr-only" htmlFor="editProjectName">
                                                    {t('project.edit.nameLabel', 'Projektname')}
                                                </label>
                                                <input
                                                    id="editProjectName"
                                                    type="text"
                                                    value={editingName}
                                                    onChange={(e) => setEditingName(e.target.value)}
                                                    required
                                                    autoFocus
                                                    autoComplete="off"
                                                />

                                                <label className="sr-only" htmlFor="editProjectCustomer">
                                                    {t('project.edit.customerLabel', 'Kunde')}
                                                </label>
                                                <select
                                                    id="editProjectCustomer"
                                                    value={editingCustomerId}
                                                    onChange={(e) => setEditingCustomerId(e.target.value)}
                                                    required
                                                >
                                                    <option value="" disabled>
                                                        {t('project.edit.customerPlaceholder', 'Kunde auswählen...')}
                                                    </option>
                                                    {customers?.map((c) => (
                                                        <option key={c.id} value={c.id}>
                                                            {c.name}
                                                        </option>
                                                    ))}
                                                </select>

                                                <label className="sr-only" htmlFor="editProjectParent">
                                                    {t('project.edit.parentLabel', 'Übergeordnetes Projekt')}
                                                </label>
                                                <select
                                                    id="editProjectParent"
                                                    value={editingParentId}
                                                    onChange={(e) => setEditingParentId(e.target.value)}
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

                                                <label className="sr-only" htmlFor="editProjectBudget">
                                                    {t('project.edit.budgetLabel', 'Budget (Minuten)')}
                                                </label>
                                                <input
                                                    id="editProjectBudget"
                                                    type="number"
                                                    min="0"
                                                    inputMode="numeric"
                                                    placeholder={t('project.edit.budgetPlaceholder', 'Budget (Minuten)')}
                                                    value={editingBudget}
                                                    onChange={(e) => setEditingBudget(e.target.value)}
                                                />

                                                <label className="sr-only" htmlFor="editProjectRate">
                                                    {t('project.edit.rateLabel', 'Stundensatz (CHF)')}
                                                </label>
                                                <input
                                                    id="editProjectRate"
                                                    type="number"
                                                    min="0"
                                                    step="0.01"
                                                    inputMode="decimal"
                                                    placeholder={t('project.edit.ratePlaceholder', 'Stundensatz (optional)')}
                                                    value={editingHourlyRate}
                                                    onChange={(e) => setEditingHourlyRate(e.target.value)}
                                                />

                                                <div className="form-actions">
                                                    <button type="submit" className="button-primary">
                                                        {t('save', 'Speichern')}
                                                    </button>
                                                    <button type="button" onClick={cancelEdit} className="button-secondary">
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
                                                <div className="item-actions">
                                                    <button onClick={() => startEdit(p)} className="button-secondary">
                                                        {t('edit', 'Bearbeiten')}
                                                    </button>
                                                    <button onClick={() => handleDelete(p.id)} className="button-danger">
                                                        {t('delete', 'Löschen')}
                                                    </button>
                                                </div>
                                            </>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </section>

                <section className="content-section">
                    <div className="section-header">
                        <h3 className="section-title">{t('project.hierarchy.title', 'Projekt-Hierarchie & KPIs')}</h3>
                        <div className="analytics-controls">
                            <input
                                type="date"
                                value={analyticsStart}
                                onChange={(e) => setAnalyticsStart(e.target.value)}
                            />
                            <input
                                type="date"
                                value={analyticsEnd}
                                onChange={(e) => setAnalyticsEnd(e.target.value)}
                            />
                            <button type="button" className="button-secondary" onClick={loadAnalytics} disabled={analyticsLoading}>
                                {analyticsLoading ? t('loading', 'Lädt...') : t('refresh', 'Aktualisieren')}
                            </button>
                        </div>
                    </div>
                    <ProjectTree nodes={projectHierarchy} analyticsMap={analyticsMap} />
                </section>

                <section className="content-section">
                    <h3 className="section-title">{t('project.integration.title', 'Automatisierte Integrationen')}</h3>
                    <form className="integration-form" onSubmit={handleCreateIntegration}>
                        <input
                            type="text"
                            placeholder={t('project.integration.name', 'Name der Integration')}
                            value={newIntegration.name}
                            onChange={handleIntegrationFieldChange('name')}
                            required
                        />
                        <select value={newIntegration.type} onChange={handleIntegrationFieldChange('type')}>
                            {integrationTypes.map((type) => (
                                <option key={type.value} value={type.value}>
                                    {type.label}
                                </option>
                            ))}
                        </select>
                        <input
                            type="url"
                            placeholder={t('project.integration.endpoint', 'Ziel-URL / Endpoint')}
                            value={newIntegration.endpointUrl}
                            onChange={handleIntegrationFieldChange('endpointUrl')}
                        />
                        <input
                            type="text"
                            placeholder={t('project.integration.authHeader', 'Auth-Header (optional)')}
                            value={newIntegration.authHeader}
                            onChange={handleIntegrationFieldChange('authHeader')}
                        />
                        <label className="checkbox-inline">
                            <input
                                type="checkbox"
                                checked={newIntegration.active}
                                onChange={handleIntegrationFieldChange('active')}
                            />
                            {t('project.integration.active', 'Aktiv')}
                        </label>
                        <label className="checkbox-inline">
                            <input
                                type="checkbox"
                                checked={newIntegration.autoSync}
                                onChange={handleIntegrationFieldChange('autoSync')}
                            />
                            {t('project.integration.autoSync', 'Auto-Sync')}
                        </label>
                        <button type="submit" className="button-primary" disabled={integrationLoading}>
                            {t('project.integration.add', 'Integration hinzufügen')}
                        </button>
                    </form>

                    <div className="integration-cards">
                        {integrations.length === 0 ? (
                            <p className="empty-state-text">{t('project.integration.none', 'Noch keine Integrationen hinterlegt.')}</p>
                        ) : (
                            integrations.map((integration) => (
                                <article key={integration.id} className="integration-card">
                                    <header>
                                        <h4>{integration.name}</h4>
                                        <span className={`status-pill ${integration.active ? 'active' : 'inactive'}`}>
                                            {integration.active ? t('active', 'Aktiv') : t('inactive', 'Inaktiv')}
                                        </span>
                                    </header>
                                    <ul>
                                        <li><strong>{t('project.integration.type', 'Typ')}:</strong> {integration.type}</li>
                                        {integration.endpointUrl && (
                                            <li><strong>URL:</strong> {integration.endpointUrl}</li>
                                        )}
                                        {integration.authHeader && (
                                            <li><strong>{t('project.integration.authLabel', 'Auth')}:</strong> {integration.authHeader}</li>
                                        )}
                                        {integration.lastTriggeredAt && (
                                            <li><strong>{t('project.integration.lastRun', 'Letzte Ausführung')}:</strong> {new Date(integration.lastTriggeredAt).toLocaleString()}</li>
                                        )}
                                        {integration.lastStatus && (
                                            <li><strong>{t('status', 'Status')}:</strong> {integration.lastStatus}</li>
                                        )}
                                    </ul>
                                    <div className="integration-actions">
                                        <button type="button" className="button-secondary" onClick={() => handleTriggerIntegration(integration.id)}>
                                            {t('project.integration.trigger', 'Test-Übertragung')}
                                        </button>
                                        <button type="button" className="button-secondary" onClick={() => handleToggleIntegration(integration, 'active')}>
                                            {integration.active ? t('project.integration.deactivate', 'Deaktivieren') : t('project.integration.activate', 'Aktivieren')}
                                        </button>
                                        <button type="button" className="button-secondary" onClick={() => handleToggleIntegration(integration, 'autoSync')}>
                                            {integration.autoSync ? t('project.integration.stopAuto', 'Auto-Sync stoppen') : t('project.integration.startAuto', 'Auto-Sync starten')}
                                        </button>
                                        <button type="button" className="button-danger" onClick={() => handleDeleteIntegration(integration.id)}>
                                            {t('delete', 'Löschen')}
                                        </button>
                                    </div>
                                </article>
                            ))
                        )}
                    </div>

                    {lastIntegrationRun && (
                        <div className="integration-result">
                            <h4>{t('project.integration.lastRunTitle', 'Letzte Simulation')}</h4>
                            <pre>{JSON.stringify(lastIntegrationRun, null, 2)}</pre>
                        </div>
                    )}
                </section>

                <section className="content-section">
                    <h3 className="section-title">{t('project.audit.title', 'Compliance & Audit')}</h3>
                    {auditLoading ? (
                        <p>{t('loading', 'Lädt...')}</p>
                    ) : auditLogs.length === 0 ? (
                        <p className="empty-state-text">{t('project.audit.empty', 'Noch keine Audit-Einträge vorhanden.')}</p>
                    ) : (
                        <div className="audit-table-wrapper">
                            <table className="audit-table">
                                <thead>
                                    <tr>
                                        <th>{t('date', 'Datum')}</th>
                                        <th>{t('project.audit.user', 'Benutzer')}</th>
                                        <th>{t('project.audit.action', 'Aktion')}</th>
                                        <th>{t('project.audit.target', 'Ziel')}</th>
                                        <th>{t('details', 'Details')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {auditLogs.map((log) => (
                                        <tr key={log.id}>
                                            <td>{log.createdAt ? new Date(log.createdAt).toLocaleString() : ''}</td>
                                            <td>{log.username}</td>
                                            <td>{`${log.action} / ${log.severity || 'INFO'}`}</td>
                                            <td>{`${log.targetType}${log.targetId ? ` #${log.targetId}` : ''}`}</td>
                                            <td>{log.details}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section className="content-section">
                    <h3 className="section-title">{t('project.billing.title', 'Automatisierte Abrechnung')}</h3>
                    <form className="billing-form" onSubmit={handleGenerateInvoice}>
                        <select value={billingProjectId} onChange={(e) => setBillingProjectId(e.target.value)} required>
                            <option value="">{t('project.billing.selectProject', 'Projekt wählen')}</option>
                            {sortedProjects.map((project) => (
                                <option key={project.id} value={project.id}>
                                    {project.name}
                                </option>
                            ))}
                        </select>
                        <input type="date" value={billingStart} onChange={(e) => setBillingStart(e.target.value)} required />
                        <input type="date" value={billingEnd} onChange={(e) => setBillingEnd(e.target.value)} required />
                        <label className="checkbox-inline">
                            <input
                                type="checkbox"
                                checked={billingIncludeChildren}
                                onChange={(e) => setBillingIncludeChildren(e.target.checked)}
                            />
                            {t('project.billing.includeChildren', 'Unterprojekte einbeziehen')}
                        </label>
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            inputMode="decimal"
                            placeholder={t('project.billing.overrideRate', 'Override-Stundensatz (optional)')}
                            value={billingRate}
                            onChange={(e) => setBillingRate(e.target.value)}
                        />
                        <input
                            type="text"
                            value={billingCurrency}
                            onChange={(e) => setBillingCurrency(e.target.value)}
                            placeholder={t('project.billing.currency', 'Währung')}
                        />
                        <button type="submit" className="button-primary" disabled={billingLoading}>
                            {billingLoading ? t('loading', 'Lädt...') : t('project.billing.generate', 'Abrechnung erstellen')}
                        </button>
                    </form>

                    {invoiceResult && (
                        <div className="billing-summary">
                            <div className="billing-summary-header">
                                <h4>{invoiceResult.projectName}</h4>
                                <span>{invoiceResult.startDate} - {invoiceResult.endDate}</span>
                            </div>
                            <div className="billing-summary-metrics">
                                <span>{t('project.billing.totalHours', 'Billable Stunden')}: {(invoiceResult.totalBillableMinutes / 60).toFixed(2)}</span>
                                <span>{t('project.billing.totalAmount', 'Gesamtbetrag')}: {invoiceResult.totalAmount} {invoiceResult.currency}</span>
                                {invoiceResult.overrideRate && (
                                    <span>{t('project.billing.overrideApplied', 'Override-Satz')}: {invoiceResult.overrideRate}</span>
                                )}
                                {!invoiceResult.overrideRate && invoiceResult.hourlyRate && (
                                    <span>{t('project.billing.projectRate', 'Projekt-Satz')}: {invoiceResult.hourlyRate}</span>
                                )}
                            </div>
                            <table className="billing-lines">
                                <thead>
                                    <tr>
                                        <th>{t('project.billing.projectColumn', 'Projekt')}</th>
                                        <th>{t('project.billing.taskColumn', 'Aufgabe')}</th>
                                        <th>{t('project.billing.minutes', 'Minuten')}</th>
                                        <th>{t('project.billing.amount', 'Betrag')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {invoiceResult.lineItems?.map((line, idx) => (
                                        <tr key={`${line.projectId}-${line.taskId || idx}`}>
                                            <td>{line.projectName}</td>
                                            <td>{line.taskName}</td>
                                            <td>{line.minutes}</td>
                                            <td>{line.amount}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            </div>
        </>
    );
};

export default AdminProjectsPage;
