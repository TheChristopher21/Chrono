// src/components/PrintReportModal.jsx
import React from "react";
import ModalOverlay from './ModalOverlay';
import PropTypes from "prop-types";
import "../styles/PrintReportScoped.css";          // optional – Styles für alle Dashboards

const PrintReportModal = ({
                              t,
                              visible,
                              startDate,
                              setStartDate,
                              endDate,
                              setEndDate,
                              onConfirm,
                              onClose,
                              cssScope = ""
                          }) => {
    if (!visible) return null;

    const cls = cssScope ? ` ${cssScope}-print-modal` : "";

    return (
        <ModalOverlay
            visible={visible}
            className={cls.trim()}
            onClose={onClose}
        >
            <div className="modal-content">
                <h3>{t("selectPeriod")}</h3>

                <div className="form-group">
                    <label>{t("startDate")}:</label>
                    <input
                        type="date"
                        value={startDate}
                        onChange={(e) => setStartDate(e.target.value)}
                    />
                </div>

                <div className="form-group">
                    <label>{t("endDate")}:</label>
                    <input
                        type="date"
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                    />
                </div>

                <div className="modal-buttons">
                    <button onClick={onConfirm}>{t("printReportButton")}</button>
                    <button onClick={onClose}>{t("cancel")}</button>
                </div>
            </div>
        </ModalOverlay>
    );
};

PrintReportModal.propTypes = {
    t: PropTypes.func.isRequired,
    visible: PropTypes.bool.isRequired,
    startDate: PropTypes.string.isRequired,
    setStartDate: PropTypes.func.isRequired,
    endDate: PropTypes.string.isRequired,
    setEndDate: PropTypes.func.isRequired,
    onConfirm: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    cssScope: PropTypes.string
};

export default PrintReportModal;
