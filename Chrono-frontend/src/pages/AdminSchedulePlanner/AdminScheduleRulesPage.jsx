import React, { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../utils/api';
import Navbar from '../../components/Navbar';
import '../../styles/PercentageDashboardScoped.css';
import '../../styles/AdminScheduleRulesPage.css';


const fetchAllRules = async () => {
    const { data } = await api.get('/api/admin/shift-definitions/all');
    return Array.isArray(data) ? data : [];
};

const saveRule = async (rule) => {
    const { data } = await api.post('/api/admin/shift-definitions', rule);
    return data;
};

const RuleCard = ({ rule, onRuleChange, onSave, onToggleActive, isSaving }) => {
    const handleInputChange = (field, value) => {
        onRuleChange({ ...rule, [field]: value });
    };

    const handleToggle = () => {
        onToggleActive(rule);
    };

    return (
        <div className="rule-card">
            <div className="form-grid">
                <div className="form-group">
                    <label>Bezeichnung</label>
                    <input type="text" value={rule.label || ''} onChange={(e) => handleInputChange('label', e.target.value)} />
                </div>
                <div className="form-group">
                    <label>Schlüssel (z.B. EARLY)</label>
                    <input type="text" value={rule.shiftKey || ''} readOnly={!String(rule.id).startsWith('NEW')} placeholder="z.B. MIDDLE" onChange={(e) => handleInputChange('shiftKey', e.target.value.toUpperCase())} />
                </div>
                <div className="form-group">
                    <label>Startzeit</label>
                    <input type="time" value={rule.startTime || '00:00'} onChange={(e) => handleInputChange('startTime', e.target.value)} />
                </div>
                <div className="form-group">
                    <label>Endzeit</label>
                    <input type="time" value={rule.endTime || '00:00'} onChange={(e) => handleInputChange('endTime', e.target.value)} />
                </div>
                <div className="form-group checkbox-group">
                    <label>Aktiv</label>
                    <input type="checkbox" checked={!!rule.isActive} onChange={handleToggle} />
                </div>
                <div className="form-group-actions">
                    <button onClick={() => onSave(rule)} disabled={isSaving}>
                        Speichern
                    </button>
                </div>
            </div>
        </div>
    );
};


const AdminScheduleRulesPage = () => {
    const queryClient = useQueryClient();
    const { data: rulesFromServer = [], isLoading } = useQuery({
        queryKey: ['allScheduleRules'],
        queryFn: fetchAllRules
    });

    const [ruleList, setRuleList] = useState([]);

    useEffect(() => {
        setRuleList(rulesFromServer);
    }, [rulesFromServer]);

    const mutation = useMutation({
        mutationFn: saveRule,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['allScheduleRules'] });
            queryClient.invalidateQueries({ queryKey: ['scheduleRules'] });
        },
        onError: (error) => {
            alert('Fehler beim Speichern: ' + (error.response?.data?.message || error.message));
        }
    });

    const handleSave = useCallback((ruleToSave) => {
        if (typeof ruleToSave.id === 'string' && ruleToSave.id.startsWith('NEW')) {
            const { id, ...newRule } = ruleToSave;
            mutation.mutate(newRule);
        } else {
            mutation.mutate(ruleToSave);
        }
    }, [mutation]);

    const handleToggleActive = useCallback((ruleToToggle) => {
        if (typeof ruleToToggle.id === 'string' && ruleToToggle.id.startsWith('NEW')) {
            setRuleList(currentRules =>
                currentRules.map(r => r.id === ruleToToggle.id ? { ...r, isActive: !r.isActive } : r)
            );
            return;
        }
        const updatedRule = { ...ruleToToggle, isActive: !ruleToToggle.isActive };
        mutation.mutate(updatedRule);
    }, [mutation]);

    const handleRuleChange = useCallback((updatedRule) => {
        setRuleList(currentRules =>
            currentRules.map(rule => rule.id === updatedRule.id ? updatedRule : rule)
        );
    }, []);

    const handleAddNew = () => {
        const newKey = `NEW_${Date.now()}`;
        setRuleList(prev => [...prev, { id: newKey, shiftKey: '', label: '', startTime: '00:00', endTime: '00:00', isActive: true }]);
    };

    if (isLoading) return <div className="loading-indicator">Lade Regeln...</div>;

    return (
        <>
            <Navbar />
            <div className="schedule-rules-page scoped-dashboard">
                <header className="dashboard-header">
                    <h1>Schicht-Einstellungen</h1>
                    <p>Hier können die globalen Schichten und ihre Zeiten für den Dienstplan konfiguriert werden.</p>
                </header>

                <div className="rules-list">
                    {ruleList.map(rule => (
                        <RuleCard
                            key={rule.id}
                            rule={rule}
                            onRuleChange={handleRuleChange}
                            onSave={handleSave}
                            onToggleActive={handleToggleActive}
                            isSaving={mutation.isLoading}
                        />
                    ))}
                </div>
                <button className="add-new-button" onClick={handleAddNew}>+ Neue Schicht hinzufügen</button>
            </div>
        </>
    );
};

export default AdminScheduleRulesPage;