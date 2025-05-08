import React from 'react';
import PropTypes from "prop-types";

const EditTimeModal = ({
                           t,
                           editModalVisible,
                           editDate,
                           editData,
                           handleEditInputChange,
                           handleEditSubmit,
                           setEditModalVisible
                       }) => {
    if (!editModalVisible) return null;

    return (
        <div className="admin-dashboard scoped-dashboard">
            <div className="modal-overlay">
                <div className="modal-content">
                    <h3>
                        {t('adminDashboard.editTrackingTitle', 'Zeiterfassung bearbeiten')}{' '}
                        {editDate?.toLocaleDateString('de-DE')}
                    </h3>
                    <form onSubmit={handleEditSubmit}>
                        <div className="form-group">
                            <label>{t('workStart', 'Work Start')}:</label>
                            <input
                                type="time"
                                name="workStart"
                                value={editData.workStart}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('breakStart', 'Break Start')}:</label>
                            <input
                                type="time"
                                name="breakStart"
                                value={editData.breakStart}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('breakEnd', 'Break End')}:</label>
                            <input
                                type="time"
                                name="breakEnd"
                                value={editData.breakEnd}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('workEnd', 'Work End')}:</label>
                            <input
                                type="time"
                                name="workEnd"
                                value={editData.workEnd}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('adminDashboard.adminPassword', 'Admin Passwort')}:</label>
                            <input
                                type="password"
                                name="adminPassword"
                                value={editData.adminPassword}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('adminDashboard.userPassword', 'Benutzerpasswort')}:</label>
                            <input
                                type="password"
                                name="userPassword"
                                value={editData.userPassword}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <button type="submit">
                            {t('save', 'Speichern')}
                        </button>
                        <button type="button" onClick={() => setEditModalVisible(false)}>
                            {t('cancel', 'Abbrechen')}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
};

EditTimeModal.propTypes = {
    t: PropTypes.func.isRequired,
    editModalVisible: PropTypes.bool.isRequired,
    editDate: PropTypes.instanceOf(Date),
    editData: PropTypes.shape({
        workStart: PropTypes.string.isRequired,
        breakStart: PropTypes.string.isRequired,
        breakEnd: PropTypes.string.isRequired,
        workEnd: PropTypes.string.isRequired,
        adminPassword: PropTypes.string.isRequired,
        userPassword: PropTypes.string.isRequired
    }).isRequired,
    handleEditInputChange: PropTypes.func.isRequired,
    handleEditSubmit: PropTypes.func.isRequired,
    setEditModalVisible: PropTypes.func.isRequired
};

export default EditTimeModal;
