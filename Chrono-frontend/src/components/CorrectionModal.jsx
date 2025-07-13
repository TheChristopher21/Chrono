import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import { formatDate } from '../utils/dateUtils';
import { formatTime } from '../utils/timeUtils';

const CorrectionModal = ({
  visible,
  correctionDate,
  dailySummary,
  onClose,
  onSubmit,
  t,
}) => {
  const [entries, setEntries] = useState([]);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (visible) {
      if (dailySummary?.entries && dailySummary.entries.length > 0) {
        setEntries(
          dailySummary.entries.map((e) => ({
            time: formatTime(e.entryTimestamp),
            type: e.punchType,
            key: e.id || `entry-${Math.random()}`,
          }))
        );
      } else {
        setEntries([
          { time: '08:00', type: 'START', key: 'new-1' },
          { time: '17:00', type: 'ENDE', key: 'new-2' },
        ]);
      }
      setReason('');
    }
  }, [visible, dailySummary]);

  if (!visible) return null;

  const handleEntryChange = (idx, field, value) => {
    setEntries((prev) => {
      const newEntries = [...prev];
      newEntries[idx][field] = value;
      return newEntries;
    });
  };

  const handleRemoveEntry = (idx) => {
    setEntries((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleAddEntry = () => {
    const last = entries[entries.length - 1];
    const newType = last && last.type === 'START' ? 'ENDE' : 'START';
    setEntries([...entries, { time: '12:00', type: newType, key: `new-${Date.now()}` }]);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(entries, reason);
  };

  return (
    <ModalOverlay visible={visible} className="modal-backdrop">
      <div className="modal-content">
        <form onSubmit={handleSubmit}>
          <h3>
            {t('correctionFor')} {formatDate(correctionDate)}
          </h3>
          {entries.map((entry, idx) => (
            <div key={entry.key || idx} className="entry-row">
              <input
                type="time"
                value={entry.time}
                onChange={(e) => handleEntryChange(idx, 'time', e.target.value)}
                required
              />
              <select value={entry.type} onChange={(e) => handleEntryChange(idx, 'type', e.target.value)}>
                <option value="START">{t('start')}</option>
                <option value="ENDE">{t('end')}</option>
              </select>
              <button
                type="button"
                aria-label={t('remove', 'Entfernen')}
                onClick={() => handleRemoveEntry(idx)}
                className="button-remove"
              >
                &times;
              </button>
            </div>
          ))}
          <button type="button" onClick={handleAddEntry} className="button-add-entry">
            {t('addEntry', 'Eintrag hinzufügen')}
          </button>
          <div className="form-group">
            <label htmlFor="reasonGeneric">{t('reason')}:</label>
            <textarea
              id="reasonGeneric"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('userCorrectionModal.reasonPlaceholder', 'Begründung...')}
              required
              rows="3"
            />
          </div>
          <div className="modal-buttons">
            <button type="submit" className="button-primary">
              {t('submitCorrection', 'Antrag senden')}
            </button>
            <button type="button" onClick={onClose} className="button-secondary">
              {t('cancel', 'Abbrechen')}
            </button>
          </div>
        </form>
      </div>
    </ModalOverlay>
  );
};

CorrectionModal.propTypes = {
  visible: PropTypes.bool.isRequired,
  correctionDate: PropTypes.string.isRequired,
  dailySummary: PropTypes.object,
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  t: PropTypes.func.isRequired,
};

export default CorrectionModal;
