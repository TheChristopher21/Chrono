import React from 'react';
import PropTypes from 'prop-types';

const UserCorrectionModal = ({
                                 visible,
                                 correctionDate,
                                 correctionData,
                                 handleCorrectionInputChange,
                                 handleCorrectionSubmit,
                                 onClose,
                                 t, // <-- neu hinzugefügt
                             }) => {
    if (!visible) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>
                    {t("submitCorrectionFor") || "Korrekturantrag für"} {correctionDate}
                </h3>
                <form onSubmit={handleCorrectionSubmit}>
                    <div className="form-group">
                        <label>{t("workStart") || "Work Start"}:</label>
                        <input
                            type="time"
                            name="workStart"
                            value={correctionData.workStart}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("breakStart") || "Break Start"}:</label>
                        <input
                            type="time"
                            name="breakStart"
                            value={correctionData.breakStart}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("breakEnd") || "Break End"}:</label>
                        <input
                            type="time"
                            name="breakEnd"
                            value={correctionData.breakEnd}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("workEnd") || "Work End"}:</label>
                        <input
                            type="time"
                            name="workEnd"
                            value={correctionData.workEnd}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("reason") || "Reason"}:</label>
                        <textarea
                            name="reason"
                            value={correctionData.reason}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="modal-buttons">
                        <button type="submit">
                            {t("submitCorrection") || "Antrag senden"}
                        </button>
                        <button type="button" onClick={onClose}>
                            {t("cancel") || "Abbrechen"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

UserCorrectionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    correctionDate: PropTypes.string.isRequired,
    correctionData: PropTypes.shape({
        workStart: PropTypes.string,
        breakStart: PropTypes.string,
        breakEnd: PropTypes.string,
        workEnd: PropTypes.string,
        reason: PropTypes.string,
    }).isRequired,
    handleCorrectionInputChange: PropTypes.func.isRequired,
    handleCorrectionSubmit: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    t: PropTypes.func,
};

export default UserCorrectionModal;
