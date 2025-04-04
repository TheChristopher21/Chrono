// UserCorrectionModal.jsx
import React from 'react';
import PropTypes from 'prop-types';

const UserCorrectionModal = ({
                                 visible,
                                 correctionDate,
                                 correctionData,
                                 handleCorrectionInputChange,
                                 handleCorrectionSubmit,
                                 onClose
                             }) => {
    if (!visible) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>Korrekturantrag für {correctionDate}</h3>
                <form onSubmit={handleCorrectionSubmit}>
                    <div className="form-group">
                        <label>Work Start:</label>
                        <input
                            type="time"
                            name="workStart"
                            value={correctionData.workStart}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Break Start:</label>
                        <input
                            type="time"
                            name="breakStart"
                            value={correctionData.breakStart}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>Break End:</label>
                        <input
                            type="time"
                            name="breakEnd"
                            value={correctionData.breakEnd}
                            onChange={handleCorrectionInputChange}
                        />
                    </div>
                    <div className="form-group">
                        <label>Work End:</label>
                        <input
                            type="time"
                            name="workEnd"
                            value={correctionData.workEnd}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Grund (Reason):</label>
                        <textarea
                            name="reason"
                            value={correctionData.reason}
                            onChange={handleCorrectionInputChange}
                            required
                        />
                    </div>
                    <div className="modal-buttons">
                        <button type="submit">Antrag senden</button>
                        <button type="button" onClick={onClose}>Abbrechen</button>
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
        reason: PropTypes.string
    }).isRequired,
    handleCorrectionInputChange: PropTypes.func.isRequired,
    handleCorrectionSubmit: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};

export default UserCorrectionModal;
