// PrintUserTimesModal.jsx
import 'react';
import PropTypes from "prop-types";

const PrintUserTimesModal = ({
                                 printUserModalVisible,
                                 printUser,
                                 printUserStartDate,
                                 printUserEndDate,
                                 setPrintUserStartDate,
                                 setPrintUserEndDate,
                                 handlePrintUserTimesPeriodSubmit,
                                 setPrintUserModalVisible
                             }) => {
    if (!printUserModalVisible) return null;

    return (
        <div className="admin-dashboard scoped-dashboard">

        <div className="modal-overlay">
            <div className="modal-content">
                <h3>Zeiten für {printUser} drucken</h3>
                <div className="form-group">
                    <label>Startdatum:</label>
                    <input
                        type="date"
                        value={printUserStartDate}
                        onChange={(e) => setPrintUserStartDate(e.target.value)}
                    />
                </div>
                <div className="form-group">
                    <label>Enddatum:</label>
                    <input
                        type="date"
                        value={printUserEndDate}
                        onChange={(e) => setPrintUserEndDate(e.target.value)}
                    />
                </div>
                <div className="modal-buttons">
                    <button onClick={handlePrintUserTimesPeriodSubmit}>Drucken</button>
                    <button onClick={() => setPrintUserModalVisible(false)}>Abbrechen</button>
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
    setPrintUserModalVisible: PropTypes.func.isRequired
};
export default PrintUserTimesModal;
