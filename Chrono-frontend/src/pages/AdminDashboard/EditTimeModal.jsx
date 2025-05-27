// src/pages/AdminDashboard/EditTimeModal.jsx
import React from 'react'; // useEffect wurde entfernt, da nicht mehr benötigt
import PropTypes from 'prop-types';

const EditTimeModal = ({
                           t,
                           editModalVisible,
                           editDate,
                           editData,
                           handleEditInputChange,
                           handleEditSubmit,
                           setEditModalVisible,
                       }) => {
    // Der useEffect-Hook, der window.scrollY verwendet hat, wurde entfernt.

    if (!editModalVisible) return null;

    return (
        <div className="admin-dashboard scoped-dashboard">
            {/*
             * Das CSS in AdminDashboardScoped.css (.modal-overlay)
             * sorgt für die korrekte Zentrierung mittels position: fixed und display: flex.
             */}
            <div className="modal-overlay">
                <div className="modal-content">
                    <h3>
                        {t("adminDashboard.editTrackingTitle", "Zeiterfassung bearbeiten")}{" "}
                        {editDate?.toLocaleDateString("de-DE")}
                    </h3>
                    <form onSubmit={handleEditSubmit}>
                        <div className="form-group">
                            <label>{t("workStart", "Work Start")}:</label>
                            <input
                                type="time"
                                name="workStart"
                                value={editData.workStart}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("breakStart", "Break Start")}:</label>
                            <input
                                type="time"
                                name="breakStart"
                                value={editData.breakStart}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("breakEnd", "Break End")}:</label>
                            <input
                                type="time"
                                name="breakEnd"
                                value={editData.breakEnd}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("workEnd", "Work End")}:</label>
                            <input
                                type="time"
                                name="workEnd"
                                value={editData.workEnd}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="modal-buttons">
                            <button type="submit">{t("save", "Speichern")}</button>
                            <button type="button" onClick={() => setEditModalVisible(false)}>
                                {t("cancel", "Abbrechen")}
                            </button>
                        </div>
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
    }).isRequired,
    handleEditInputChange: PropTypes.func.isRequired,
    handleEditSubmit: PropTypes.func.isRequired,
    setEditModalVisible: PropTypes.func.isRequired,
};

export default EditTimeModal;