import React from 'react';
import PropTypes from 'prop-types';

const HourlyCorrectionModal = ({
                                   visible,
                                   correctionDate,
                                   correctionData,
                                   handleCorrectionInputChange,
                                   handleCorrectionSubmit,
                                   onClose,
                                   t, // <-- wichtig
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
                        <label>{t("workStart")}</label>
                        <input
                            type="time"
                            name="workStart"
                            value={correctionData.workStart}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("breakStart")}</label>
                        <input
                            type="time"
                            name="breakStart"
                            value={correctionData.breakStart}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("breakEnd")}</label>
                        <input
                            type="time"
                            name="breakEnd"
                            value={correctionData.breakEnd}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("workEnd")}</label>
                        <input
                            type="time"
                            name="workEnd"
                            value={correctionData.workEnd}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("reason")}:</label>
                        <textarea
                            name="reason"
                            value={correctionData.reason}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="modal-buttons">
                        <button type="submit">{t("submitCorrection")}</button>
                        <button type="button" onClick={onClose}>
                            {t("cancel")}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

HourlyCorrectionModal.propTypes = {
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

export default HourlyCorrectionModal;
