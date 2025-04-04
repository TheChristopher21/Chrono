// UserPrintModal.jsx
import React from 'react';
import PropTypes from 'prop-types';

const UserPrintModal = ({
                            t,
                            visible,
                            printStartDate,
                            setPrintStartDate,
                            printEndDate,
                            setPrintEndDate,
                            handlePrintReport,
                            onClose
                        }) => {
    if (!visible) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>{t("selectPeriod")}</h3>
                <div className="form-group">
                    <label>{t("startDate")}:</label>
                    <input
                        type="date"
                        value={printStartDate}
                        onChange={(e) => setPrintStartDate(e.target.value)}
                    />
                </div>
                <div className="form-group">
                    <label>{t("endDate")}:</label>
                    <input
                        type="date"
                        value={printEndDate}
                        onChange={(e) => setPrintEndDate(e.target.value)}
                    />
                </div>
                <div className="modal-buttons">
                    <button onClick={handlePrintReport}>{t("printReportButton")}</button>
                    <button onClick={onClose}>{t("cancel")}</button>
                </div>
            </div>
        </div>
    );
};

UserPrintModal.propTypes = {
    t: PropTypes.func.isRequired,
    visible: PropTypes.bool.isRequired,
    printStartDate: PropTypes.string.isRequired,
    setPrintStartDate: PropTypes.func.isRequired,
    printEndDate: PropTypes.string.isRequired,
    setPrintEndDate: PropTypes.func.isRequired,
    handlePrintReport: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};

export default UserPrintModal;
