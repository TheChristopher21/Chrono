import React from 'react';
import PropTypes from "prop-types";

const PrintUserTimesModal = ({
                                 printUserModalVisible,
                                 printUser,
                                 printUserStartDate,
                                 printUserEndDate,
                                 setPrintUserStartDate,
                                 setPrintUserEndDate,
                                 handlePrintUserTimesPeriodSubmit,
                                 setPrintUserModalVisible,
                                 t
                             }) => {
    if (!printUserModalVisible) return null;

    return (
        <div className="admin-dashboard scoped-dashboard">
            <div className="modal-overlay">
                <div className="modal-content">
                    <h3>{t('adminDashboard.printUserTimesTitle', 'Zeiten drucken für')} {printUser}</h3>
                    <div className="form-group">
                        <label>{t('adminDashboard.startDate', 'Startdatum')}:</label>
                        <input
                            type="date"
                            value={printUserStartDate}
                            onChange={(e) => setPrintUserStartDate(e.target.value)}
                        />
                    </div>
                    <div className="form-group">
                        <label>{t('adminDashboard.endDate', 'Enddatum')}:</label>
                        <input
                            type="date"
                            value={printUserEndDate}
                            onChange={(e) => setPrintUserEndDate(e.target.value)}
                        />
                    </div>
                    <div className="modal-buttons">
                        <button onClick={handlePrintUserTimesPeriodSubmit}>
                            {t('adminDashboard.button.print', 'Drucken')}
                        </button>
                        <button onClick={() => setPrintUserModalVisible(false)}>
                            {t('cancel', 'Abbrechen')}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

PrintUserTimesModal.propTypes = {
    printUserModalVisible: PropTypes.bool.isRequired,
    printUser: PropTypes.string.isRequired,
    printUserStartDate: PropTypes.string.isRequired,
    printUserEndDate: PropTypes.string.isRequired,
    setPrintUserStartDate: PropTypes.func.isRequired,
    setPrintUserEndDate: PropTypes.func.isRequired,
    handlePrintUserTimesPeriodSubmit: PropTypes.func.isRequired,
    setPrintUserModalVisible: PropTypes.func.isRequired,
    t: PropTypes.func
};
export default PrintUserTimesModal;
