import React, { useState, useEffect, useMemo } from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { formatTime, minutesToHHMM } from '../utils/timeUtils';
import { formatDate } from '../utils/dateUtils';

const createTimeBlocks = (entries) => {
  const blocks = [];
  let currentBlock = null;
  (entries || []).forEach(entry => {
    if (entry.punchType === 'START') {
      currentBlock = {
        startEntry: entry,
        endEntry: null,
        customerId: entry.customerId || '',
        projectId: entry.projectId || '',
      };
    } else if (entry.punchType === 'ENDE' && currentBlock) {
      currentBlock.endEntry = entry;
      blocks.push(currentBlock);
      currentBlock = null;
    }
  });
  return blocks;
};

const CustomerTimeAssignModal = ({ t, day, summary, customers, projects, onClose, onSave }) => {
  const [timeBlocks, setTimeBlocks] = useState([]);
  const { currentUser } = useAuth();
  const { notify } = useNotification();

  useEffect(() => {
    const sorted = [...(summary?.entries || [])].sort((a, b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));
    setTimeBlocks(createTimeBlocks(sorted));
  }, [day, summary]);

  const handleBlockChange = (index, field, value) => {
    setTimeBlocks(prev => {
      const blocks = [...prev];
      blocks[index] = { ...blocks[index], [field]: value };
      return blocks;
    });
  };

  const handleSave = async () => {
    const tasks = [];
    timeBlocks.forEach(block => {
      if (!block.startEntry || !block.endEntry) return;
      const customerParams = {
        username: currentUser.username,
        date: formatDate(day),
        startTime: formatTime(block.startEntry.entryTimestamp),
        endTime: formatTime(block.endEntry.entryTimestamp)
      };
      if (block.customerId) customerParams.customerId = block.customerId;
      tasks.push(api.put('/api/timetracking/range/customer', null, { params: customerParams }));

      const projectParams = projectId => ({ projectId: projectId || null });
      if (block.startEntry.id)
        tasks.push(api.put(`/api/timetracking/entry/${block.startEntry.id}/project`, null, { params: projectParams(block.projectId) }));
      if (block.endEntry.id)
        tasks.push(api.put(`/api/timetracking/entry/${block.endEntry.id}/project`, null, { params: projectParams(block.projectId) }));
    });
    try {
      await Promise.all(tasks);
      notify(t('assignCustomer.saveSuccess', 'Änderungen gespeichert!'), 'success');
      onSave();
    } catch (err) {
      console.error('Error saving time blocks:', err);
      notify(t('assignCustomer.saveError', 'Fehler beim Speichern.'), 'error');
    }
  };

  const totalWorked = useMemo(() => minutesToHHMM(summary?.workedMinutes || 0), [summary]);

  return (
    <ModalOverlay visible onClose={onClose} className="modal-backdrop">
      <div className="customer-assign-modal">
        <header className="modal-header">
          <h3>{t('assignCustomer.title', 'Zeiten & Kunden für')} {formatDate(day)}</h3>
          <p>{t('assignCustomer.totalWorked', 'Gesamtarbeitszeit')}: <strong>{totalWorked}</strong></p>
        </header>
        <div className="modal-body">
          {timeBlocks.length === 0 ? (
            <p className="no-blocks-message">{t('assignCustomer.noBlocks', 'Keine vollständigen Zeitblöcke zum Bearbeiten gefunden.')}</p>
          ) : (
            timeBlocks.map((block, index) => (
              <div key={index} className="time-block-card">
                <div className="time-block-header">
                  <span className="time-range">
                    {formatTime(block.startEntry.entryTimestamp)} - {block.endEntry ? formatTime(block.endEntry.entryTimestamp) : '...'}
                  </span>
                </div>
                <div className="time-block-form">
                  <div className="form-group">
                    <label htmlFor={`customer-${index}`}>{t('customerLabel', 'Kunde')}</label>
                    <select id={`customer-${index}`} value={block.customerId} onChange={e => handleBlockChange(index, 'customerId', e.target.value)}>
                      <option value="">{t('noCustomer', 'Kein Kunde')}</option>
                      {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label htmlFor={`project-${index}`}>{t('projectLabel', 'Projekt')}</label>
                    <select id={`project-${index}`} value={block.projectId} onChange={e => handleBlockChange(index, 'projectId', e.target.value)}>
                      <option value="">{t('noProject', 'Kein Projekt')}</option>
                      {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
        <footer className="modal-footer">
          <button onClick={onClose} className="button-secondary">{t('cancel', 'Abbrechen')}</button>
          <button onClick={handleSave} className="button-primary">{t('save', 'Speichern')}</button>
        </footer>
      </div>
    </ModalOverlay>
  );
};

CustomerTimeAssignModal.propTypes = {
  t: PropTypes.func.isRequired,
  day: PropTypes.instanceOf(Date).isRequired,
  summary: PropTypes.object,
  customers: PropTypes.array.isRequired,
  projects: PropTypes.array.isRequired,
  onClose: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
};

export default CustomerTimeAssignModal;
