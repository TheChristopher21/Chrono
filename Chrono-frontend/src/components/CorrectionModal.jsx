// src/components/CorrectionModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import { formatDate } from '../utils/dateUtils';
import { formatTime } from '../utils/timeUtils';

const CorrectionModal = ({ visible, correctionDate, dailySummary, onClose, onSubmit, t }) => {
  const [entries, setEntries] = useState([]);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (visible && dailySummary) {
      const initialEntries = dailySummary.entries?.map(entry => ({
        time: formatTime(new Date(entry.entryTimestamp)),
        type: entry.punchType
      })) || [];

      // Wenn keine Einträge vorhanden sind, füge ein leeres Start/Ende Paar hinzu
      if (initialEntries.length === 0) {
        setEntries([
          { time: '08:00', type: 'START' },
          { time: '17:00', type: 'ENDE' }
        ]);
      } else {
        setEntries(initialEntries);
      }
      setReason(''); // Grund bei jedem Öffnen zurücksetzen
    }
  }, [visible, dailySummary]);

  if (!visible) return null;

  const handleEntryChange = (index, field, value) => {
    const newEntries = [...entries];
    newEntries[index][field] = value;
    setEntries(newEntries);
  };

  const addEntry = () => {
    const lastEntry = entries[entries.length - 1];
    const newType = lastEntry?.type === 'START' ? 'ENDE' : 'START';
    setEntries([...entries, { time: '', type: newType }]);
  };

  const removeEntry = (index) => {
    const newEntries = entries.filter((_, i) => i !== index);
    setEntries(newEntries);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      alert(t('correctionModal.reasonRequired', 'Bitte geben Sie einen Grund an.'));
      return;
    }
    if (entries.some(entry => !entry.time)) {
      alert(t('correctionModal.timeRequired', 'Bitte füllen Sie alle Zeitfelder aus.'));
      return;
    }
    onSubmit(entries, reason);
  };

  return (
      <ModalOverlay visible={visible} onClose={onClose} className="scoped-dashboard">
        <div className="modal-content user-correction-modal-content">
          <h3>{t("userCorrectionModal.title", "Korrekturantrag für")} {formatDate(new Date(correctionDate + "T00:00:00"))}</h3>
          <form onSubmit={handleSubmit}>
            <div className="entries-list">
              {entries.map((entry, index) => (
                  <div key={index} className="entry-row">
                    <input
                        type="time"
                        value={entry.time}
                        onChange={(e) => handleEntryChange(index, 'time', e.target.value)}
                        required
                        aria-label={t('correctionModal.timeLabel', 'Zeit')}
                    />
                    <select
                        value={entry.type}
                        onChange={(e) => handleEntryChange(index, 'type', e.target.value)}
                        aria-label={t('correctionModal.typeLabel', 'Typ')}
                    >
                      <option value="START">START</option>
                      <option value="ENDE">ENDE</option>
                    </select>
                    <button type="button" className="button-remove" onClick={() => removeEntry(index)} aria-label={t('correctionModal.removeEntry', 'Eintrag entfernen')}>
                      &times;
                    </button>
                  </div>
              ))}
            </div>

            <button type="button" className="button-add-entry" onClick={addEntry}>
              + {t('correctionModal.addEntry', 'Eintrag hinzufügen')}
            </button>

            <div className="form-group">
              <label htmlFor="reason-textarea">{t("reason", "Grund")}:</label>
              <textarea
                  id="reason-textarea"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder={t('userCorrectionModal.reasonPlaceholder', 'Begründung für die Korrektur...')}
                  required
                  rows="4"
              />
            </div>

            <div className="modal-buttons">
              <button type="submit" className="button-primary">
                {t("submitCorrection", "Antrag senden")}
              </button>
              <button type="button" onClick={onClose} className="button-secondary">
                {t("cancel", "Abbrechen")}
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
  dailySummary: PropTypes.shape({
    entries: PropTypes.arrayOf(PropTypes.shape({
      entryTimestamp: PropTypes.string,
      punchType: PropTypes.string,
    })),
  }),
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  t: PropTypes.func.isRequired,
};

export default CorrectionModal;