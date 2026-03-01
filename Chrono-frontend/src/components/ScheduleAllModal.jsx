import React, { useState } from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import { useTranslation } from '../context/LanguageContext';

const ScheduleAllModal = ({ visible, onConfirm, onClose, defaultDay = 1 }) => {
  const { t } = useTranslation();
  const [day, setDay] = useState(defaultDay);
  if (!visible) return null;

  const confirm = () => {
    onConfirm(parseInt(day, 10));
  };

  return (
    <ModalOverlay visible={visible}>
      <div className="modal-content">
        <h3>{t('payslips.scheduleAll')}</h3>
        <div className="form-group">
          <label>{t('payslips.scheduleDay')}:</label>
          <input
            type="number"
            min="1"
            max="28"
            value={day}
            onChange={e => setDay(e.target.value)}
          />
        </div>
        <div className="modal-buttons">
          <button className="button-primary" onClick={confirm}>{t('payslips.scheduleButton')}</button>
          <button className="button-secondary" onClick={onClose}>{t('cancel')}</button>
        </div>
      </div>
    </ModalOverlay>
  );
};

ScheduleAllModal.propTypes = {
  visible: PropTypes.bool.isRequired,
  onConfirm: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  defaultDay: PropTypes.number,
};

export default ScheduleAllModal;
